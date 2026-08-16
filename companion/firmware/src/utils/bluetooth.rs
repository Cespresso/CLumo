use std::collections::VecDeque;
use std::sync::Arc;

use esp32_nimble::enums::{AuthReq, SecurityIOCap};
use esp32_nimble::utilities::mutex::Mutex;
use esp32_nimble::{uuid128, BLEAdvertisementData, BLECharacteristic, BLEDevice, NimbleProperties};

use crate::countdown::{parse_timer_duration, DurationSetting};
use crate::mode::{Mode, MODE_COUNT};
use crate::utils::device_id::{self, DeviceId};

pub const BUTTON_MAIN: u8 = 0;
pub const BUTTON_SUB: u8 = 1;

/// Pomodoro control commands received over BLE (POMODORO characteristic writes).
#[derive(Debug, Clone, Copy)]
pub enum PomodoroCommand {
    Start,
    Pause,
    Reset,
    SetDurations { work_min: u8, break_min: u8 },
}

/// One-shot countdown timer commands received over BLE.
#[derive(Debug, Clone, Copy)]
pub enum TimerCommand {
    Start,
    Pause,
    Cancel,
    SetDuration(DurationSetting),
    /// Restore the five-byte readable status after a malformed GATT write.
    RefreshStatus,
}

/// BLE commands queued for the main loop.
pub enum BleCommand {
    ConnectionChanged(bool),
    BondChanged(bool),
    AuthenticationFailed,
    ClientReady,
    SwitchMode(u8),
    SetDisplayData([u8; 8]),
    SetBrightness(u8),
    Pomodoro(PomodoroCommand),
    Timer(TimerCommand),
}

pub struct BluetoothManager {
    commands: Arc<Mutex<VecDeque<BleCommand>>>,
    has_bonded_peer: bool,
    mode_characteristic: Arc<Mutex<BLECharacteristic>>,
    pomodoro_characteristic: Arc<Mutex<BLECharacteristic>>,
    timer_characteristic: Arc<Mutex<BLECharacteristic>>,
    brightness_characteristic: Arc<Mutex<BLECharacteristic>>,
    button_characteristic: Arc<Mutex<BLECharacteristic>>,
}

impl BluetoothManager {
    pub fn init(
        initial_mode: u8,
        initial_pomodoro_status: [u8; 6],
        initial_timer_status: [u8; 5],
        initial_brightness: u8,
        device_id: DeviceId,
    ) -> Result<Self, Box<dyn std::error::Error>> {
        let commands: Arc<Mutex<VecDeque<BleCommand>>> = Arc::new(Mutex::new(VecDeque::new()));

        let ble_device = BLEDevice::take();
        let has_bonded_peer = match ble_device.bonded_addresses() {
            Ok(addresses) => !addresses.is_empty(),
            Err(e) => {
                log::warn!("Failed to read BLE bond store: {:?}", e);
                false
            }
        };
        log::info!("BLE bonded peer present: {}", has_bonded_peer);
        let ble_advertiser = ble_device.get_advertising();

        // Configure device security
        ble_device
            .security()
            .set_auth(AuthReq::all())
            .set_passkey(123456)
            .set_io_cap(SecurityIOCap::DisplayOnly)
            .resolve_rpa();

        let server = ble_device.get_server();

        let commands_clone = commands.clone();
        server.on_connect(move |server, clntdesc| {
            log::info!("BLE client connected: {:?}", clntdesc);
            commands_clone
                .lock()
                .push_back(BleCommand::ConnectionChanged(true));
            server
                .update_conn_params(clntdesc.conn_handle(), 24, 48, 0, 200)
                .unwrap();
        });

        let commands_clone = commands.clone();
        server.on_disconnect(move |_desc, _reason| {
            log::info!("BLE client disconnected");
            commands_clone
                .lock()
                .push_back(BleCommand::ConnectionChanged(false));
        });

        let commands_clone = commands.clone();
        server.on_authentication_complete(move |_server, desc, result| match result {
            Ok(()) => {
                log::info!(
                    "BLE client authenticated (encrypted={}, bonded={})",
                    desc.encrypted(),
                    desc.bonded()
                );
                commands_clone
                    .lock()
                    .push_back(BleCommand::BondChanged(desc.bonded()));
            }
            Err(e) => {
                log::warn!("BLE client authentication failed: {:?}", e);
                commands_clone
                    .lock()
                    .push_back(BleCommand::AuthenticationFailed);
            }
        });

        let service = server.create_service(uuid128!("455aa9f0-2999-43de-81b4-54e0de255927"));

        // --- Mode Characteristic (READ | WRITE | NOTIFY) ---
        let mode_characteristic = service.lock().create_characteristic(
            uuid128!("681285a6-247f-48c6-80ad-68c3dce18586"),
            NimbleProperties::READ
                | NimbleProperties::READ_ENC
                | NimbleProperties::WRITE
                | NimbleProperties::WRITE_ENC
                | NimbleProperties::NOTIFY,
        );
        mode_characteristic.lock().set_value(&[initial_mode]);

        let commands_clone = commands.clone();
        mode_characteristic.lock().on_write(move |value| {
            let data = value.recv_data();
            log::info!("BLE mode write: {:?}", data);

            if data.is_empty() {
                log::warn!("BLE: mode write empty, ignoring");
                return;
            }

            let mode = data[0];
            if mode < MODE_COUNT {
                log::info!("BLE cmd: SwitchMode({})", mode);
                commands_clone
                    .lock()
                    .push_back(BleCommand::SwitchMode(mode));
            } else {
                log::warn!("BLE: invalid mode value 0x{:02X}", mode);
            }
        });

        // --- Display Data Characteristic (READ | WRITE | WRITE_NR) ---
        // 8 bytes, interpreted by the current mode:
        // Display mode -> row bitmap, Visualizer mode -> column heights.
        let display_characteristic = service.lock().create_characteristic(
            uuid128!("681285a6-247f-48c6-80ad-68c3dce18585"),
            NimbleProperties::READ
                | NimbleProperties::READ_ENC
                | NimbleProperties::WRITE
                | NimbleProperties::WRITE_ENC
                | NimbleProperties::WRITE_NO_RSP,
        );

        let commands_clone = commands.clone();
        display_characteristic.lock().on_write(move |value| {
            let data = value.recv_data();

            if data.len() >= 8 {
                let mut buf = [0u8; 8];
                buf.copy_from_slice(&data[..8]);
                commands_clone
                    .lock()
                    .push_back(BleCommand::SetDisplayData(buf));
            } else {
                log::warn!("BLE: display data needs 8 bytes, got {}", data.len());
            }
        });

        // --- Pomodoro Characteristic (READ | WRITE | NOTIFY) ---
        // Writes: [0x01] start/resume, [0x02] pause, [0x03] reset,
        //         [0x10, work_min, break_min] set durations.
        // Value/notify: [state, phase, rem_hi, rem_lo, work_min, break_min].
        let pomodoro_characteristic = service.lock().create_characteristic(
            uuid128!("681285a6-247f-48c6-80ad-68c3dce18587"),
            NimbleProperties::READ
                | NimbleProperties::READ_ENC
                | NimbleProperties::WRITE
                | NimbleProperties::WRITE_ENC
                | NimbleProperties::NOTIFY,
        );
        pomodoro_characteristic
            .lock()
            .set_value(&initial_pomodoro_status);

        let commands_clone = commands.clone();
        pomodoro_characteristic.lock().on_write(move |value| {
            let data = value.recv_data();
            log::info!("BLE pomodoro write: {:?}", data);

            if data.is_empty() {
                log::warn!("BLE: pomodoro write empty, ignoring");
                return;
            }

            let cmd = match data[0] {
                0x01 => Some(PomodoroCommand::Start),
                0x02 => Some(PomodoroCommand::Pause),
                0x03 => Some(PomodoroCommand::Reset),
                0x10 if data.len() >= 3 => Some(PomodoroCommand::SetDurations {
                    work_min: data[1].clamp(1, 99),
                    break_min: data[2].clamp(1, 99),
                }),
                _ => None,
            };
            match cmd {
                Some(c) => {
                    log::info!("BLE cmd: Pomodoro({:?})", c);
                    commands_clone.lock().push_back(BleCommand::Pomodoro(c));
                }
                None => log::warn!("BLE: invalid pomodoro command {:?}", data),
            }
        });

        // --- Timer Characteristic (READ | WRITE | NOTIFY) ---
        // Writes: [0x01] start/resume, [0x02] pause, [0x03] cancel,
        //         [0x10, minutes, seconds] set duration.
        // Value/notify: [state, rem_hi, rem_lo, minutes, seconds].
        let timer_characteristic = service.lock().create_characteristic(
            uuid128!("681285a6-247f-48c6-80ad-68c3dce1858a"),
            NimbleProperties::READ
                | NimbleProperties::READ_ENC
                | NimbleProperties::WRITE
                | NimbleProperties::WRITE_ENC
                | NimbleProperties::NOTIFY,
        );
        timer_characteristic.lock().set_value(&initial_timer_status);

        let commands_clone = commands.clone();
        timer_characteristic.lock().on_write(move |value| {
            let data = value.recv_data();
            log::info!("BLE timer write: {:?}", data);

            let cmd = match data {
                [0x01] => Some(TimerCommand::Start),
                [0x02] => Some(TimerCommand::Pause),
                [0x03] => Some(TimerCommand::Cancel),
                [0x10, _, _] => parse_timer_duration(data).map(TimerCommand::SetDuration),
                _ => None,
            };
            match cmd {
                Some(c) => {
                    log::info!("BLE cmd: Timer({:?})", c);
                    commands_clone.lock().push_back(BleCommand::Timer(c));
                }
                None => {
                    log::warn!("BLE: invalid timer command {:?}", data);
                    // NimBLE has already replaced the characteristic value with
                    // the write payload. Queue a status refresh so READ remains
                    // a valid five-byte Timer status after malformed writes.
                    commands_clone
                        .lock()
                        .push_back(BleCommand::Timer(TimerCommand::RefreshStatus));
                }
            }
        });

        // --- Brightness Characteristic (READ | WRITE | NOTIFY) ---
        let brightness_characteristic = service.lock().create_characteristic(
            uuid128!("681285a6-247f-48c6-80ad-68c3dce18588"),
            NimbleProperties::READ
                | NimbleProperties::READ_ENC
                | NimbleProperties::WRITE
                | NimbleProperties::WRITE_ENC
                | NimbleProperties::NOTIFY,
        );
        brightness_characteristic
            .lock()
            .set_value(&[initial_brightness.min(0x0F)]);

        let commands_clone = commands.clone();
        brightness_characteristic.lock().on_write(move |value| {
            let data = value.recv_data();
            log::info!("BLE brightness write: {:?}", data);

            if data.is_empty() {
                log::warn!("BLE: brightness write empty, ignoring");
                return;
            }

            let level = data[0].min(0x0F);
            log::info!("BLE cmd: SetBrightness({})", level);
            commands_clone
                .lock()
                .push_back(BleCommand::SetBrightness(level));
        });

        // --- Device ID Characteristic (READ only) ---
        let device_id_characteristic = service.lock().create_characteristic(
            uuid128!("681285a6-247f-48c6-80ad-68c3dce18589"),
            NimbleProperties::READ | NimbleProperties::READ_ENC,
        );
        let commands_clone = commands.clone();
        device_id_characteristic
            .lock()
            .set_value(&device_id)
            .on_read(move |_characteristic, desc| {
                log::info!("BLE client initial sync reached DEVICE_ID read");
                if desc.encrypted() {
                    commands_clone.lock().push_back(BleCommand::ClientReady);
                }
            });

        // --- Button Characteristic (NOTIFY only) ---
        // MUST stay last: NimBLE assigns ATT handles in creation order, so a
        // characteristic added ahead of an existing one shifts that one's handle and
        // breaks every bonded client reconnecting from its cached GATT database.
        // Encryption flags would not restrict subscribers here either, because NimBLE
        // registers the auto-generated CCCD with plain READ|WRITE.
        let button_characteristic = service.lock().create_characteristic(
            uuid128!("681285a6-247f-48c6-80ad-68c3dce1858b"),
            NimbleProperties::NOTIFY,
        );

        // Configure and start advertising
        let advertising_name = format!("CLumo-{}", device_id::short(&device_id));
        ble_advertiser
            .lock()
            .set_data(
                BLEAdvertisementData::new()
                    .name(&advertising_name)
                    .add_service_uuid(uuid128!("455aa9f0-2999-43de-81b4-54e0de255927")),
            )
            .unwrap();
        ble_advertiser.lock().start().unwrap();
        log::info!("BLE advertising started as '{}'", advertising_name);

        Ok(Self {
            commands,
            has_bonded_peer,
            mode_characteristic,
            pomodoro_characteristic,
            timer_characteristic,
            brightness_characteristic,
            button_characteristic,
        })
    }

    /// Pop the oldest pending command, if any.
    pub fn take_command(&self) -> Option<BleCommand> {
        self.commands.lock().pop_front()
    }

    /// Whether the device had at least one peer in its bond store at startup.
    pub fn has_bonded_peer(&self) -> bool {
        self.has_bonded_peer
    }

    /// Update the Mode Characteristic value and notify connected clients.
    /// Call this on every mode change, whether triggered by BLE or buttons.
    pub fn notify_mode_change(&self, mode: u8) {
        self.mode_characteristic.lock().set_value(&[mode]).notify();
    }

    /// Update the Brightness Characteristic value and notify connected clients.
    pub fn notify_brightness_change(&self, level: u8) {
        self.brightness_characteristic
            .lock()
            .set_value(&[level])
            .notify();
    }

    /// Update the Pomodoro Characteristic value and notify connected clients.
    pub fn notify_pomodoro_status(&self, status: &[u8; 6]) {
        self.pomodoro_characteristic
            .lock()
            .set_value(status)
            .notify();
    }

    /// Update the Pomodoro Characteristic value without notifying.
    /// Used to keep READ accurate after writes that don't change timer state.
    pub fn set_pomodoro_status(&self, status: &[u8; 6]) {
        self.pomodoro_characteristic.lock().set_value(status);
    }

    /// Update the Timer Characteristic value and notify connected clients.
    pub fn notify_timer_status(&self, status: &[u8; 5]) {
        self.timer_characteristic.lock().set_value(status).notify();
    }

    /// Update the Timer Characteristic value without notifying.
    pub fn set_timer_status(&self, status: &[u8; 5]) {
        self.timer_characteristic.lock().set_value(status);
    }

    /// Notify subscribers that a physical button was pressed in a mode the
    /// firmware does not handle itself.
    pub fn notify_button(&self, mode: Mode, button: u8) {
        self.button_characteristic
            .lock()
            .set_value(&[mode as u8, button])
            .notify();
    }
}
