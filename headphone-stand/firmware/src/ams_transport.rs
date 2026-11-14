//! Thin NimBLE client on the existing peripheral connection. No second BLE link.
//! The process-lifetime listener owns its mailbox. Each C procedure owns one Box
//! until its terminal callback (including disconnect/error); timeout never frees it.
use esp_idf_svc::sys;
use headphone_stand::{
    ams::{parse_update, Epoch, Update},
    connection::LinkSnapshot,
    volume::{Availability, Percent, SessionId, Source, VolumeSession},
};
use std::{ffi::c_void, sync::Mutex};

const AMS: [u8; 16] = [
    0xdc, 0xf8, 0x55, 0xad, 0x02, 0xc5, 0xf4, 0x8e, 0x3a, 0x43, 0x36, 0x0f, 0x2b, 0x50, 0xd3, 0x89,
];
const ENTITY: [u8; 16] = [
    0x02, 0xc1, 0x96, 0xba, 0x92, 0xbb, 0x0c, 0x9a, 0x1f, 0x41, 0x8d, 0x80, 0xce, 0xab, 0x7c, 0x2f,
];
#[derive(Clone, Copy, Debug)]
struct Range {
    start: u16,
    end: u16,
}
#[derive(Clone, Copy)]
enum Target {
    ServiceChanged,
    Entity,
}
#[derive(Clone, Copy, Debug)]
struct Characteristic {
    value: u16,
    end: u16,
    properties: u8,
}
#[derive(Default)]
struct Found {
    ams: Option<Range>,
    gatt: Option<Range>,
    characteristic: Option<Characteristic>,
    cccd: Option<u16>,
}
struct Reply {
    token: u64,
    status: u16,
    found: Found,
}
#[derive(Default)]
struct Mailbox {
    epoch: Epoch,
    handle: Option<u16>,
    busy: Option<u64>,
    reply: Option<Reply>,
    value: Option<Percent>,
    invalid: bool,
    restart: bool,
    entity: u16,
    changed: u16,
}
impl Mailbox {
    fn invalidate(&mut self) {
        self.epoch.invalidate();
        self.reply = None;
        self.value = None;
        self.invalid = false;
        self.entity = 0;
    }
}
struct Bridge {
    data: Mutex<Mailbox>,
}
struct Procedure {
    bridge: &'static Bridge,
    token: u64,
    target: Option<Target>,
    end: u16,
    found: Found,
}
impl Procedure {
    fn finish(self: Box<Self>, status: u16) {
        let mut data = self.bridge.data.lock().unwrap();
        if data.busy == Some(self.token) {
            data.busy = None;
        }
        if data.epoch.accepts(self.token) {
            data.reply = Some(Reply {
                token: self.token,
                status,
                found: self.found,
            });
        }
    }
}
unsafe fn uuid16(uuid: &sys::ble_uuid_any_t, value: u16) -> bool {
    uuid.u.type_ == 16 && uuid.u16_.value == value
}
unsafe fn uuid128(uuid: &sys::ble_uuid_any_t, value: &[u8; 16]) -> bool {
    uuid.u.type_ == 128 && &uuid.u128_.value == value
}
unsafe extern "C" fn services(
    _: u16,
    error: *const sys::ble_gatt_error,
    service: *const sys::ble_gatt_svc,
    arg: *mut c_void,
) -> i32 {
    let context = &mut *(arg as *mut Procedure);
    let status = (*error).status;
    if status != 0 {
        Box::from_raw(arg as *mut Procedure).finish(status);
        return 0;
    }
    if !service.is_null() {
        let service = &*service;
        if service.start_handle > 0 && service.start_handle <= service.end_handle {
            let range = Range {
                start: service.start_handle,
                end: service.end_handle,
            };
            if uuid128(&service.uuid, &AMS) {
                context.found.ams = Some(range);
            }
            if uuid16(&service.uuid, 0x1801) {
                context.found.gatt = Some(range);
            }
        }
    }
    0
}
unsafe extern "C" fn characteristics(
    _: u16,
    error: *const sys::ble_gatt_error,
    chr: *const sys::ble_gatt_chr,
    arg: *mut c_void,
) -> i32 {
    let context = &mut *(arg as *mut Procedure);
    let status = (*error).status;
    if status != 0 {
        Box::from_raw(arg as *mut Procedure).finish(status);
        return 0;
    }
    if !chr.is_null() {
        let chr = &*chr;
        if let Some(target) = &mut context.found.characteristic {
            if chr.def_handle > target.value {
                target.end = target.end.min(chr.def_handle - 1);
            }
        }
        let matches = match context.target {
            Some(Target::ServiceChanged) => uuid16(&chr.uuid, 0x2a05),
            Some(Target::Entity) => uuid128(&chr.uuid, &ENTITY),
            None => false,
        };
        if matches && chr.val_handle > 0 && chr.val_handle <= context.end {
            context.found.characteristic = Some(Characteristic {
                value: chr.val_handle,
                end: context.end,
                properties: chr.properties,
            });
        }
    }
    0
}
unsafe extern "C" fn descriptors(
    _: u16,
    error: *const sys::ble_gatt_error,
    _: u16,
    dsc: *const sys::ble_gatt_dsc,
    arg: *mut c_void,
) -> i32 {
    let context = &mut *(arg as *mut Procedure);
    let status = (*error).status;
    if status != 0 {
        Box::from_raw(arg as *mut Procedure).finish(status);
        return 0;
    }
    if !dsc.is_null() && uuid16(&(*dsc).uuid, 0x2902) {
        context.found.cccd = Some((*dsc).handle);
    }
    0
}
unsafe extern "C" fn written(
    _: u16,
    error: *const sys::ble_gatt_error,
    _: *mut sys::ble_gatt_attr,
    arg: *mut c_void,
) -> i32 {
    Box::from_raw(arg as *mut Procedure).finish((*error).status);
    0
}
unsafe extern "C" fn gap(event: *mut sys::ble_gap_event, arg: *mut c_void) -> i32 {
    let bridge = &*(arg as *const Bridge);
    let event = &*event;
    if event.type_ as u32 == sys::BLE_GAP_EVENT_DISCONNECT {
        let handle = event.__bindgen_anon_1.disconnect.conn.conn_handle;
        let mut data = bridge.data.lock().unwrap();
        if data.handle == Some(handle) {
            data.invalidate();
            data.handle = None;
            data.changed = 0;
            data.restart = true;
        }
    } else if event.type_ as u32 == sys::BLE_GAP_EVENT_NOTIFY_RX {
        let notification = &event.__bindgen_anon_1.notify_rx;
        let mut data = bridge.data.lock().unwrap();
        if data.handle != Some(notification.conn_handle) || notification.om.is_null() {
            return 0;
        }
        let len = sys::os_mbuf_len(notification.om) as usize;
        let mut bytes = [0u8; 35];
        if notification.attr_handle == data.changed && data.changed != 0 {
            // Conservatively invalidate all handles on any Service Changed indication.
            data.invalidate();
            data.restart = true;
        } else if notification.attr_handle == data.entity && data.entity != 0 {
            if len > bytes.len()
                || sys::os_mbuf_copydata(notification.om, 0, len as i32, bytes.as_mut_ptr().cast())
                    != 0
            {
                data.invalid = true;
            } else {
                match parse_update(&bytes[..len]) {
                    Update::Volume(value) => data.value = Some(value),
                    Update::Invalid => data.invalid = true,
                    Update::Ignore => {}
                }
            }
        }
    }
    0
}
#[derive(Clone, Copy, Debug)]
enum Step {
    Services,
    GattChars,
    GattDescriptors,
    GattSubscribe,
    AmsChars,
    AmsDescriptors,
    AmsSubscribe,
    Register,
}
#[derive(Clone, Copy)]
enum Phase {
    Sleep(u64),
    Start(Step),
    Waiting(Step, u64),
    Initial(u64),
    Active,
}
/// All decisions and VolumeSession writes happen on the main task.
pub struct AmsClient {
    bridge: &'static Bridge,
    link: Option<(SessionId, Option<u16>, bool)>,
    session: SessionId,
    token: u64,
    phase: Phase,
    ams: Option<Range>,
    gatt: Option<Range>,
    characteristic: Option<Characteristic>,
    cccd: u16,
}
impl AmsClient {
    pub fn new() -> Result<Self, i32> {
        // Allocated once after BLEDevice::take(), before advertising; lives to reboot.
        let bridge = Box::leak(Box::new(Bridge {
            data: Mutex::new(Mailbox::default()),
        }));
        let listener = Box::into_raw(Box::new(sys::ble_gap_event_listener::default()));
        let rc = unsafe {
            sys::ble_gap_event_listener_register(
                listener,
                Some(gap),
                (bridge as *const Bridge).cast_mut().cast(),
            )
        };
        if rc != 0 {
            unsafe {
                drop(Box::from_raw(listener));
            }
            return Err(rc);
        }
        Ok(Self {
            bridge,
            link: None,
            session: SessionId(0),
            token: 0,
            phase: Phase::Sleep(0),
            ams: None,
            gatt: None,
            characteristic: None,
            cccd: 0,
        })
    }
    fn unavailable(&self, volume: &mut VolumeSession) {
        if volume.source() == Some(Source::Ams) {
            volume.update(self.session, Source::Ams, Availability::Unavailable, None);
        }
    }
    fn retry(&mut self, data: &mut Mailbox, volume: &mut VolumeSession, now: u64, reason: &str) {
        log::warn!("AMS {reason}; retry in 5s");
        self.unavailable(volume);
        data.invalidate();
        self.token = data.epoch.current();
        self.phase = Phase::Sleep(now + 5000);
    }
    fn discover_ams(&mut self, volume: &mut VolumeSession, now: u64) {
        if self.ams.is_some() {
            self.phase = Phase::Start(Step::AmsChars);
        } else {
            self.unavailable(volume);
            self.phase = Phase::Sleep(now + 30000);
            log::info!("AMS not published");
        }
    }
    fn complete(
        &mut self,
        data: &mut Mailbox,
        step: Step,
        reply: Reply,
        volume: &mut VolumeSession,
        now: u64,
    ) {
        let discovery = matches!(
            step,
            Step::Services
                | Step::GattChars
                | Step::AmsChars
                | Step::GattDescriptors
                | Step::AmsDescriptors
        );
        if reply.status != 0 && !(discovery && reply.status as u32 == sys::BLE_HS_EDONE) {
            log::warn!("AMS {step:?} error={}", reply.status);
            self.retry(data, volume, now, "GATT error");
            return;
        }
        match step {
            Step::Services => {
                self.ams = reply.found.ams;
                self.gatt = reply.found.gatt;
                log::info!("AMS services: ams={:?} gatt={:?}", self.ams, self.gatt);
                if self.gatt.is_some() {
                    self.phase = Phase::Start(Step::GattChars);
                } else {
                    self.discover_ams(volume, now);
                }
            }
            Step::GattChars => {
                self.characteristic = reply
                    .found
                    .characteristic
                    .filter(|c| c.properties & 0x20 != 0 && c.end > c.value);
                if let Some(c) = self.characteristic {
                    data.changed = c.value;
                    self.phase = Phase::Start(Step::GattDescriptors);
                } else {
                    self.discover_ams(volume, now);
                }
            }
            Step::GattDescriptors => {
                if let Some(cccd) = reply.found.cccd {
                    self.cccd = cccd;
                    self.phase = Phase::Start(Step::GattSubscribe);
                } else {
                    self.discover_ams(volume, now);
                }
            }
            Step::GattSubscribe => self.discover_ams(volume, now),
            Step::AmsChars => {
                self.characteristic = reply
                    .found
                    .characteristic
                    .filter(|c| c.properties & 0x18 == 0x18 && c.end > c.value);
                if let Some(c) = self.characteristic {
                    data.entity = c.value;
                    volume.update(self.session, Source::Ams, Availability::Preparing, None);
                    self.phase = Phase::Start(Step::AmsDescriptors);
                } else {
                    self.retry(data, volume, now, "Entity Update unavailable");
                }
            }
            Step::AmsDescriptors => {
                if let Some(cccd) = reply.found.cccd {
                    self.cccd = cccd;
                    self.phase = Phase::Start(Step::AmsSubscribe);
                } else {
                    self.retry(data, volume, now, "CCCD unavailable");
                }
            }
            Step::AmsSubscribe => self.phase = Phase::Start(Step::Register),
            Step::Register => {
                self.phase = Phase::Initial(now + 5000);
                log::info!("AMS volume subscribed; waiting for initial value");
            }
        }
    }
    fn start(&mut self, step: Step, now: u64) {
        let (handle, token) = {
            let mut data = self.bridge.data.lock().unwrap();
            if data.busy.is_some() || data.restart || !data.epoch.accepts(self.token) {
                return;
            }
            let Some(handle) = data.handle else {
                return;
            };
            let token = self.token;
            data.busy = Some(token);
            (handle, token)
        };
        let range = match step {
            Step::GattChars => self.gatt,
            Step::AmsChars => self.ams,
            _ => None,
        };
        let target = match step {
            Step::GattChars => Some(Target::ServiceChanged),
            Step::AmsChars => Some(Target::Entity),
            _ => None,
        };
        let context = Box::into_raw(Box::new(Procedure {
            bridge: self.bridge,
            token,
            target,
            end: range.map(|r| r.end).unwrap_or(0),
            found: Found::default(),
        }));
        self.phase = Phase::Waiting(step, now + 35000);
        // Every start API copies request buffers. No bridge lock crosses into NimBLE.
        let rc = unsafe {
            match step {
                Step::Services => {
                    sys::ble_gattc_disc_all_svcs(handle, Some(services), context.cast())
                }
                Step::GattChars | Step::AmsChars => {
                    let r = range.unwrap();
                    sys::ble_gattc_disc_all_chrs(
                        handle,
                        r.start,
                        r.end,
                        Some(characteristics),
                        context.cast(),
                    )
                }
                Step::GattDescriptors | Step::AmsDescriptors => {
                    let c = self.characteristic.unwrap();
                    sys::ble_gattc_disc_all_dscs(
                        handle,
                        c.value,
                        c.end,
                        Some(descriptors),
                        context.cast(),
                    )
                }
                Step::GattSubscribe | Step::AmsSubscribe | Step::Register => {
                    let (attribute, bytes) = match step {
                        Step::GattSubscribe => (self.cccd, [2u8, 0]),
                        Step::AmsSubscribe => (self.cccd, [1u8, 0]),
                        _ => (self.characteristic.unwrap().value, [0u8, 2]),
                    };
                    sys::ble_gattc_write_flat(
                        handle,
                        attribute,
                        bytes.as_ptr().cast(),
                        2,
                        Some(written),
                        context.cast(),
                    )
                }
            }
        };
        if rc != 0 {
            unsafe {
                Box::from_raw(context).finish(rc as u16);
            }
        }
    }
    pub fn tick(&mut self, link: LinkSnapshot, volume: &mut VolumeSession, now: u64) {
        // Keep validation, cache changes, and common-state updates in one critical
        // section. Only the actual NimBLE initiation happens outside this guard.
        let bridge = self.bridge;
        {
            let mut data = bridge.data.lock().unwrap();
            let identity = (link.session, link.handle, link.encrypted);
            if self.link != Some(identity) {
                self.link = Some(identity);
                self.session = link.session;
                data.invalidate();
                self.token = data.epoch.current();
                data.changed = 0;
                data.restart = false;
                data.handle = if link.encrypted { link.handle } else { None };
                self.ams = None;
                self.gatt = None;
                self.characteristic = None;
                self.phase = Phase::Sleep(now + 500);
            }
            if !link.encrypted || link.handle.is_none() {
                self.unavailable(volume);
                return;
            }
            if volume.source() == Some(Source::Android) {
                if data.handle.is_some() {
                    data.invalidate();
                    data.handle = None;
                }
                return;
            }
            if std::mem::take(&mut data.restart) {
                self.unavailable(volume);
                self.token = data.epoch.current();
                self.ams = None;
                self.gatt = None;
                self.characteristic = None;
                self.phase = Phase::Sleep(now + 200);
            }
            if let Some(reply) = data.reply.take() {
                if data.epoch.accepts(reply.token) && reply.token == self.token {
                    if let Phase::Waiting(step, _) = self.phase {
                        self.complete(&mut data, step, reply, volume, now);
                    }
                }
            }
            // A notification arriving before the registration ACK remains buffered.
            if data.epoch.accepts(self.token)
                && matches!(self.phase, Phase::Initial(_) | Phase::Active)
            {
                let invalid = std::mem::take(&mut data.invalid);
                let value = data.value.take();
                if invalid {
                    self.retry(&mut data, volume, now, "invalid volume update");
                } else if let Some(value) = value {
                    volume.update(
                        self.session,
                        Source::Ams,
                        Availability::Available,
                        Some(value),
                    );
                    self.phase = Phase::Active;
                }
            }
            match self.phase {
                Phase::Sleep(at) if now >= at => {
                    self.phase = Phase::Start(Step::Services);
                }
                Phase::Waiting(_, deadline) | Phase::Initial(deadline) if now >= deadline => {
                    self.retry(&mut data, volume, now, "timeout")
                }
                _ => {}
            }
        }
        // start revalidates the cached generation; it never relabels old handles.
        if let Phase::Start(step) = self.phase {
            self.start(step, now);
        }
    }
}
