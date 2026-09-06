use std::collections::VecDeque;
use std::sync::Arc;

use esp32_nimble::enums::{AuthReq, SecurityIOCap};
use esp32_nimble::utilities::mutex::Mutex;
use esp32_nimble::{uuid128, BLEAdvertisementData, BLECharacteristic, BLEDevice, NimbleProperties};

use crate::mode::MODE_COUNT;
use crate::utils::device_id::{self, DeviceId};

/// Timer control commands received over BLE (TIMER characteristic writes).
#[derive(Debug, Clone, Copy)]
pub enum TimerCommand {
    Start,
    Pause,
    Reset,
    SetDurations { work_min: u8, break_min: u8 },
}

/// BLE commands queued for the main loop.
pub enum BleCommand {
    SwitchMode(u8),
    SetDisplayData([u8; 8]),
    SetBrightness(u8),
    Timer(TimerCommand),
}

pub struct BluetoothManager {
    commands: Arc<Mutex<VecDeque<BleCommand>>>,
    mode_characteristic: Arc<Mutex<BLECharacteristic>>,
    timer_characteristic: Arc<Mutex<BLECharacteristic>>,
    brightness_characteristic: Arc<Mutex<BLECharacteristic>>,
}

impl BluetoothManager {
    pub fn init(
        initial_mode: u8,
        initial_timer_status: [u8; 6],
        device_id: DeviceId,
    ) -> Result<Self, Box<dyn std::error::Error>> {
        let commands: Arc<Mutex<VecDeque<BleCommand>>> = Arc::new(Mutex::new(VecDeque::new()));

        let ble_device = BLEDevice::take();
        let ble_advertiser = ble_device.get_advertising();

        // Configure device security
        ble_device
            .security()
            .set_auth(AuthReq::all())
            .set_passkey(123456)
            .set_io_cap(SecurityIOCap::DisplayOnly)
            .resolve_rpa();

        let server = ble_device.get_server();

        server.on_connect(|server, clntdesc| {
            log::info!("BLE client connected: {:?}", clntdesc);
            server
                .update_conn_params(clntdesc.conn_handle(), 24, 48, 0, 200)
                .unwrap();
        });

        server.on_disconnect(|_desc, _reason| {
            log::info!("BLE client disconnected");
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

        // --- Timer Characteristic (READ | WRITE | NOTIFY) ---
        // Writes: [0x01] start/resume, [0x02] pause, [0x03] reset,
        //         [0x10, work_min, break_min] set durations.
        // Value/notify: [state, phase, rem_hi, rem_lo, work_min, break_min].
        let timer_characteristic = service.lock().create_characteristic(
            uuid128!("681285a6-247f-48c6-80ad-68c3dce18587"),
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

            if data.is_empty() {
                log::warn!("BLE: timer write empty, ignoring");
                return;
            }

            let cmd = match data[0] {
                0x01 => Some(TimerCommand::Start),
                0x02 => Some(TimerCommand::Pause),
                0x03 => Some(TimerCommand::Reset),
                0x10 if data.len() >= 3 => Some(TimerCommand::SetDurations {
                    work_min: data[1].clamp(1, 99),
                    break_min: data[2].clamp(1, 99),
                }),
                _ => None,
            };
            match cmd {
                Some(c) => {
                    log::info!("BLE cmd: Timer({:?})", c);
                    commands_clone.lock().push_back(BleCommand::Timer(c));
                }
                None => log::warn!("BLE: invalid timer command {:?}", data),
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
        brightness_characteristic.lock().set_value(&[0x0F]); // default: max brightness

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
        device_id_characteristic.lock().set_value(&device_id);

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
            mode_characteristic,
            timer_characteristic,
            brightness_characteristic,
        })
    }

    /// Pop the oldest pending command, if any.
    pub fn take_command(&self) -> Option<BleCommand> {
        self.commands.lock().pop_front()
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

    /// Update the Timer Characteristic value and notify connected clients.
    pub fn notify_timer_status(&self, status: &[u8; 6]) {
        self.timer_characteristic.lock().set_value(status).notify();
    }

    /// Update the Timer Characteristic value without notifying.
    /// Used to keep READ accurate after writes that don't change timer state.
    pub fn set_timer_status(&self, status: &[u8; 6]) {
        self.timer_characteristic.lock().set_value(status);
    }
}
