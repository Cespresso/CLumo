use esp32_nimble::{
    enums::{AuthReq, SecurityIOCap},
    utilities::{mutex::Mutex, BleUuid},
    uuid128, BLEAdvertisementData, BLECharacteristic, BLEDevice, DescriptorProperties,
    NimbleProperties,
};
use esp_idf_svc::{
    nvs::{EspDefaultNvsPartition, EspNvs, NvsDefault},
    sys,
};
use headphone_stand::controls::{Bindings, Gesture};
use headphone_stand::{
    connection::Connection,
    volume::{decode_android, AndroidVolume, VolumeSession},
};
use headphone_stand::{
    parse, Encoder, Hid, Knob, KnobEvent, PairingWindow, Settings, SetupButtons, SetupEvent, State,
};
use std::sync::{
    atomic::{AtomicBool, Ordering},
    Arc,
};
use std::time::Instant;
#[path = "ams_transport.rs"]
mod ams_transport;
#[path = "hardware.rs"]
mod hardware;
#[path = "setup.rs"]
mod setup;
type Characteristic = Arc<Mutex<BLECharacteristic>>;
type Error = Box<dyn std::error::Error>;
struct Shared {
    settings: Settings,
    bindings: Bindings,
    nvs: EspNvs<NvsDefault>,
}
fn now(start: &Instant) -> u64 {
    start.elapsed().as_millis() as u64
}
fn load(nvs: &mut EspNvs<NvsDefault>) -> Result<(Settings, [u8; 16]), Error> {
    let mut settings = Settings::default();
    let mut b = [0; Settings::ENCODED_LEN];
    if let Some(v) = nvs.get_blob("settings", &mut b)? {
        settings = Settings::decode(v).unwrap_or_default();
    }
    let mut id = [0; 16];
    if !matches!(nvs.get_blob("device_id",&mut id)?,Some(v) if v.len()==16) {
        for chunk in id.chunks_mut(4) {
            chunk.copy_from_slice(&unsafe { sys::esp_random() }.to_le_bytes());
        }
        id[6] = (id[6] & 15) | 0x40;
        id[8] = (id[8] & 63) | 0x80;
        nvs.set_blob("device_id", &id)?;
    }
    Ok((settings, id))
}
fn ch(
    service: &Arc<Mutex<esp32_nimble::BLEService>>,
    uuid: BleUuid,
    props: NimbleProperties,
    value: &[u8],
) -> Characteristic {
    let c = service.lock().create_characteristic(uuid, props);
    c.lock().set_value(value);
    c
}
// Lock order matches BLE callbacks: characteristic, then connection. Keep the
// connection guard through enqueueing so a reused handle cannot receive old input.
fn send_hid(
    report: &Characteristic,
    link: &Arc<Mutex<Connection>>,
    session: headphone_stand::volume::SessionId,
    bytes: &[u8; 2],
) -> bool {
    let mut characteristic = report.lock();
    let connection = link.lock();
    let Some(handle) = connection.hid_handle(session) else {
        return false;
    };
    characteristic.set_value(bytes);
    match characteristic.notify_with(bytes, handle) {
        Ok(()) => {
            log::info!("HID sent handle={} bytes={:02x?}", handle, bytes);
            true
        }
        Err(e) => {
            log::warn!("HID report: {e:?}");
            false
        }
    }
}
const REPORT_MAP: &[u8] = &[
    0x05, 0x0c, // Usage Page Consumer
    0x09, 0x01, 0xa1, 0x01, // Consumer Control application
    0x85, 0x01, // Report ID 1
    0x15, 0x00, 0x26, 0xff, 0x03, // Logical 0..1023
    0x19, 0x00, 0x2a, 0xff, 0x03, // Usage 0..1023
    0x75, 0x10, 0x95, 0x01, 0x81, 0x00, 0xc0,
]; // one 16 bit array
pub fn run() -> Result<(), Error> {
    sys::link_patches();
    esp_idf_svc::log::EspLogger::initialize_default();
    let start = Instant::now();
    let partition = EspDefaultNvsPartition::take()?;
    let mut nvs = EspNvs::new(partition.clone(), "headphone", true)?;
    let (settings, id) = load(&mut nvs)?;
    let mut binding_bytes = [0; 7];
    let bindings = nvs
        .get_blob("bindings", &mut binding_bytes)?
        .and_then(Bindings::decode)
        .unwrap_or_default();
    let setup_boot = nvs.get_u8("setup_boot")? == Some(1);
    if setup_boot {
        nvs.set_u8("setup_boot", 0)?;
    }
    let (mut hw, modem) = hardware::Hardware::new()?;
    if setup_boot {
        return setup::run(hw, modem, partition, nvs, settings, bindings, id);
    }
    let mut state = State::default();
    state.settings = settings;
    let shared = Arc::new(Mutex::new(Shared {
        settings,
        bindings,
        nvs,
    }));
    let pairing = Arc::new(Mutex::new(PairingWindow::default()));
    let link = Arc::new(Mutex::new(Connection::default()));
    let ble = BLEDevice::take();
    let mut ams = ams_transport::AmsClient::new().map_err(|rc| format!("AMS listener: {rc}"))?;
    ble.security()
        .set_auth(AuthReq::Bond | AuthReq::Sc)
        .set_io_cap(SecurityIOCap::NoInputNoOutput)
        .resolve_rpa();
    let advertised_name = format!("CLumo-Stand-{:02X}{:02X}", id[0], id[1]);
    BLEDevice::set_device_name(&advertised_name)?;
    log::info!("BLE stored bonds: {:?}", ble.bonded_addresses());
    let server = ble.get_server();
    let conn = link.clone();
    let new_peer = Arc::new(AtomicBool::new(false));
    let pending = new_peer.clone();
    let window = pairing.clone();
    server.on_connect(move |server, desc| {
        let known = BLEDevice::take()
            .bonded_addresses()
            .map(|peers| peers.contains(&desc.id_address()))
            .unwrap_or(false);
        if !window.lock().allows(known, now(&start)) {
            log::info!(
                "Reject peer={} known={} pairing={}",
                desc.id_address(),
                known,
                window.lock().active(now(&start))
            );
            let _ = server.disconnect(desc.conn_handle());
            return;
        }
        pending.store(!known, Ordering::SeqCst);
        log::info!(
            "BLE connected handle={} peer={}",
            desc.conn_handle(),
            desc.id_address()
        );
        conn.lock().connect(desc.conn_handle());
    });
    let e = link.clone();
    let window = pairing.clone();
    server.on_authentication_complete(move |server, desc, result| {
        if new_peer.load(Ordering::SeqCst) && !window.lock().active(now(&start)) {
            let _ = server.disconnect(desc.conn_handle());
            return;
        }
        e.lock()
            .authenticate(desc.conn_handle(), result.is_ok() && desc.encrypted());
        if result.is_ok() && desc.encrypted() {
            window.lock().close();
        }
        log::info!(
            "BLE authentication result={:?} encrypted={}",
            result,
            desc.encrypted()
        );
    });
    let conn = link.clone();
    server.on_disconnect(move |desc, reason| {
        log::info!(
            "BLE disconnected peer={} reason={:?}",
            desc.id_address(),
            reason
        );
        conn.lock().disconnect(desc.conn_handle());
    });
    let read = NimbleProperties::READ | NimbleProperties::READ_ENC;
    let write = NimbleProperties::WRITE | NimbleProperties::WRITE_ENC;
    let hid_service = server.create_service(BleUuid::from_uuid16(0x1812));
    ch(
        &hid_service,
        BleUuid::from_uuid16(0x2a4a),
        read,
        &[0x11, 0x01, 0, 0x02],
    );
    ch(&hid_service, BleUuid::from_uuid16(0x2a4b), read, REPORT_MAP);
    let control = ch(
        &hid_service,
        BleUuid::from_uuid16(0x2a4c),
        NimbleProperties::WRITE_NO_RSP | NimbleProperties::WRITE_ENC,
        &[1],
    );
    let conn = link.clone();
    control.lock().on_write(move |v| match v.recv_data() {
        [0] => conn.lock().suspend(v.desc().conn_handle(), true),
        [1] => conn.lock().suspend(v.desc().conn_handle(), false),
        _ => v.reject_with_error_code(0x13),
    });
    let report = ch(
        &hid_service,
        BleUuid::from_uuid16(0x2a4d),
        read | NimbleProperties::NOTIFY,
        &[0, 0],
    );
    report
        .lock()
        .create_descriptor(
            BleUuid::from_uuid16(0x2908),
            DescriptorProperties::READ | DescriptorProperties::READ_ENC,
        )
        .lock()
        .set_value(&[1, 1]);
    let conn = link.clone();
    report.lock().on_subscribe(move |_, desc, sub| {
        conn.lock().subscribe(desc.conn_handle(), !sub.is_empty())
    });
    let service_uuid = uuid128!("d9730000-b19e-4c81-a5e8-36fbd47a9c10");
    let service = server.create_service(service_uuid);
    ch(
        &service,
        uuid128!("d9730001-b19e-4c81-a5e8-36fbd47a9c10"),
        NimbleProperties::READ,
        &[4, 1],
    );
    ch(
        &service,
        uuid128!("d9730002-b19e-4c81-a5e8-36fbd47a9c10"),
        read,
        &id,
    );
    let definitions = [
        (
            3,
            uuid128!("d9730003-b19e-4c81-a5e8-36fbd47a9c10"),
            vec![settings.brightness],
        ),
        (
            4,
            uuid128!("d9730004-b19e-4c81-a5e8-36fbd47a9c10"),
            settings.frame.to_vec(),
        ),
        (5, uuid128!("d9730005-b19e-4c81-a5e8-36fbd47a9c10"), vec![]),
        (
            6,
            uuid128!("d9730006-b19e-4c81-a5e8-36fbd47a9c10"),
            vec![settings.standby],
        ),
        (7, uuid128!("d9730007-b19e-4c81-a5e8-36fbd47a9c10"), vec![]),
        (
            8,
            uuid128!("d9730008-b19e-4c81-a5e8-36fbd47a9c10"),
            settings.encode().to_vec(),
        ),
        (
            9,
            uuid128!("d9730009-b19e-4c81-a5e8-36fbd47a9c10"),
            bindings.encode().to_vec(),
        ),
    ];

    for (index, uuid, initial) in definitions {
        let persistent = matches!(index, 3 | 4 | 6 | 8 | 9);
        let props = if persistent {
            read | write | NimbleProperties::NOTIFY
        } else {
            NimbleProperties::WRITE_NO_RSP | NimbleProperties::WRITE_ENC
        };
        let c = ch(&service, uuid, props, &initial);
        if persistent {
            let data = shared.clone();
            c.lock().on_read(move |characteristic, _| {
                let shared = data.lock();
                let settings = shared.settings;
                let bytes = match index {
                    9 => shared.bindings.encode().to_vec(),
                    3 => vec![settings.brightness],
                    4 => settings.frame.to_vec(),
                    6 => vec![settings.standby],
                    _ => settings.encode().to_vec(),
                };
                characteristic.set_value(&bytes);
            });
        }
        let shared = shared.clone();
        let conn = link.clone();
        c.lock().on_write(move |value| {
            let handle = value.desc().conn_handle();
            if !conn.lock().accepts(handle) {
                value.reject_with_error_code(0x05);
                return;
            }
            if index == 7 {
                match decode_android(value.recv_data()) {
                    Some(sample) => {
                        conn.lock().volume(handle, sample, now(&start));
                    }
                    None => value.reject_with_error_code(0x13),
                }
                return;
            }
            if index == 9 {
                let Some(next) = Bindings::decode(value.recv_data()) else {
                    value.reject_with_error_code(0x13);
                    return;
                };
                let mut shared = shared.lock();
                if next != shared.bindings {
                    if shared.nvs.set_blob("bindings", &next.encode()).is_err() {
                        value.reject_with_error_code(0x0e);
                        return;
                    }
                    shared.bindings = next;
                }
                return;
            }
            if index == 8 {
                let Some(next) = Settings::decode(value.recv_data())
                    .filter(|_| value.recv_data().len() == Settings::ENCODED_LEN)
                else {
                    value.reject_with_error_code(0x13);
                    return;
                };
                let mut shared = shared.lock();
                if next != shared.settings {
                    if let Err(e) = shared.nvs.set_blob("settings", &next.encode()) {
                        value.reject_with_error_code(0x0e);
                        log::warn!("NVS display: {e}");
                        return;
                    }
                    shared.settings = next;
                }
                return;
            }
            let Some(command) = parse(index, value.recv_data()) else {
                value.reject_with_error_code(0x13);
                return;
            };
            if let headphone_stand::Command::Visualizer(frame) = command {
                conn.lock().visualizer(handle, frame, now(&start));
                return;
            }
            // Durable settings retain their synchronous write/rollback semantics.
            let mut shared = shared.lock();
            let mut next = State::default();
            next.settings = shared.settings;
            next.apply(command, now(&start));
            if next.settings != shared.settings {
                if let Err(e) = shared.nvs.set_blob("settings", &next.settings.encode()) {
                    value.reject_with_error_code(0x0e);
                    log::warn!("NVS settings: {e}");
                    return;
                }
                shared.settings = next.settings;
            }
            value.notify();
        });
    }
    let advertiser = ble.get_advertising();
    advertiser.lock().set_data(
        BLEAdvertisementData::new()
            .name(&advertised_name)
            .appearance(0x03c0)
            .add_service_uuid(BleUuid::from_uuid16(0x1812))
            .add_service_uuid(service_uuid),
    )?;

    advertiser.lock().start()?;
    let mut buttons = SetupButtons::default();
    let mut knob = Knob::default();
    let mut encoder = Encoder::new(hw.encoder());
    let mut hid = Hid::default();
    let mut last_session = link.lock().snapshot().session;
    let mut volume = VolumeSession::new(last_session);
    let mut android_volume = AndroidVolume::default();
    let mut last_volume_status = (volume.availability(), volume.value());
    let mut was_ready = false;
    let mut last_frame = [0; 8];
    let mut last_leds = 255;
    let mut last_brightness = 255;
    let mut render_at = 0;
    let mut was_pairing = false;
    loop {
        let time = now(&start);
        let (connection, incoming) = link.lock().drain();
        if connection.session != last_session {
            hid = Hid::default();
            knob.cancel_clicks();
            report.lock().set_value(&[0, 0]);
            state.clear_transient();
            volume.reset(connection.session);
            android_volume = AndroidVolume::default();
            was_ready = false;
            last_session = connection.session;
        }
        if let Some((session, sample, at)) = incoming.volume {
            android_volume.receive(&mut volume, session, sample, at);
        }
        if let Some((session, frame, at)) = incoming.visualizer {
            if session == last_session {
                state.apply(headphone_stand::Command::Visualizer(frame), at);
            }
        }
        android_volume.tick(&mut volume, time);
        ams.tick(connection, &mut volume, time);
        let volume_status = (volume.availability(), volume.value());
        if volume_status != last_volume_status {
            log::info!(
                "VOLUME session={} status={:?} percent={:?}",
                last_session.0,
                volume_status.0,
                volume_status.1.map(|v| v.get())
            );
            last_volume_status = volume_status;
        }
        state.set_volume(volume.value());
        state.settings = shared.lock().settings;
        let ready = connection.ready();
        let handle = connection.handle.unwrap_or(u16::MAX);
        // A fresh HID subscription/encryption must observe an all-keys-up baseline.
        if !ready || !was_ready {
            report.lock().set_value(&[0, 0]);
            if ready && !send_hid(&report, &link, last_session, &[0, 0]) {
                hid.retry_release(time);
            }
        }
        was_ready = ready;
        if let Some(bytes) = hid.tick(ready, time) {
            if !send_hid(&report, &link, last_session, &bytes) && bytes == [0, 0] {
                hid.retry_release(time);
            }
        }
        let inputs = hw.buttons();
        let mut gestures = Vec::new();
        match buttons.update([inputs[0], inputs[1]], time) {
            Some(SetupEvent::Left) => gestures.push(Gesture::LeftButton),
            Some(SetupEvent::Right) => gestures.push(Gesture::RightButton),
            Some(SetupEvent::Toggle) => {
                // Commit only the next boot destination, preserving bonds and settings.
                shared.lock().nvs.set_u8("setup_boot", 1)?;
                log::info!("SETUP entering Wi-Fi mode");
                unsafe { sys::esp_restart() }
            }
            None => {}
        }
        match knob.update(inputs[2], time) {
            Some(KnobEvent::Click) => gestures.push(Gesture::KnobClick),
            Some(KnobEvent::DoubleClick) => gestures.push(Gesture::KnobDoubleClick),
            Some(KnobEvent::TripleClick) => gestures.push(Gesture::KnobTripleClick),
            Some(KnobEvent::Pair) => {
                pairing.lock().open(time);
                log::info!("PAIRING open for 60 seconds");
                if handle != u16::MAX {
                    let _ = ble.get_server().disconnect(handle);
                }
            }
            None => {}
        }
        match encoder.update(hw.encoder()) {
            1 => gestures.push(Gesture::Clockwise),
            -1 => gestures.push(Gesture::Counterclockwise),
            _ => {}
        }
        let bindings = shared.lock().bindings;
        for action in gestures
            .into_iter()
            .filter_map(|gesture| bindings.action(gesture))
        {
            log::info!("Button action={action:?} hid_ready={ready}");
            state.action(action, time);
            if let Some(bytes) = hid.input(action, ready, time) {
                if !send_hid(&report, &link, last_session, &bytes) && bytes == [0, 0] {
                    hid.retry_release(time);
                }
            }
        }
        let (settings, mut frame, leds) = (state.settings, state.frame(time), state.led_mask(time));

        let is_pairing = pairing.lock().active(time);
        if was_pairing && !is_pairing {
            log::info!("PAIRING closed");
            if !connection.encrypted {
                if handle != u16::MAX {
                    let _ = ble.get_server().disconnect(handle);
                }
            }
        }
        was_pairing = is_pairing;
        if is_pairing {
            frame = if (time / 400) % 2 == 0 {
                [0x18, 0x14, 0x52, 0x34, 0x34, 0x52, 0x14, 0x18]
            } else {
                [0; 8]
            };
        }
        if let Some(progress) = buttons.progress(time) {
            frame = setup::progress_frame(progress);
        }
        if link.lock().snapshot().session != last_session {
            continue;
        }
        if time >= render_at {
            if settings.brightness != last_brightness {
                hw.brightness(settings.brightness)?;
                last_brightness = settings.brightness;
            }
            if frame != last_frame {
                hw.frame(frame)?;
                last_frame = frame;
            }
            if leds != last_leds {
                hw.led_mask(leds)?;
                log::info!("VOLUME LED mask={leds:03b}");
                last_leds = leds;
            }
            render_at = time + 20;
        }
        std::thread::sleep(std::time::Duration::from_millis(1));
    }
}
