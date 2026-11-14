//! One BLE connection and a bounded, latest-value mailbox. No display state lives here.
use crate::volume::{Percent, SessionId};
#[derive(Clone, Copy)]
pub struct LinkSnapshot {
    pub session: SessionId,
    pub handle: Option<u16>,
    pub encrypted: bool,
    subscribed: bool,
    suspended: bool,
}
impl LinkSnapshot {
    pub fn ready(self) -> bool {
        self.handle.is_some() && self.encrypted && self.subscribed && !self.suspended
    }
}
#[derive(Default)]
pub struct Incoming {
    pub volume: Option<(SessionId, Option<Percent>, u64)>,
    pub visualizer: Option<(SessionId, [u8; 8], u64)>,
}
pub struct Connection {
    link: LinkSnapshot,
    incoming: Incoming,
}
impl Default for Connection {
    fn default() -> Self {
        Self {
            link: LinkSnapshot {
                session: SessionId(0),
                handle: None,
                encrypted: false,
                subscribed: false,
                suspended: false,
            },
            incoming: Incoming::default(),
        }
    }
}
impl Connection {
    fn clear(&mut self, handle: Option<u16>) {
        self.link = LinkSnapshot {
            session: SessionId(self.link.session.0.wrapping_add(1)),
            handle,
            encrypted: false,
            subscribed: false,
            suspended: false,
        };
        self.incoming = Incoming::default();
    }
    pub fn connect(&mut self, handle: u16) {
        self.clear(Some(handle));
    }
    pub fn disconnect(&mut self, handle: u16) {
        if self.link.handle == Some(handle) {
            self.clear(None);
        }
    }
    pub fn authenticate(&mut self, handle: u16, encrypted: bool) {
        if self.link.handle != Some(handle) {
            return;
        }
        if !encrypted {
            self.clear(Some(handle));
        } else {
            self.link.encrypted = true;
        }
    }
    pub fn subscribe(&mut self, handle: u16, enabled: bool) {
        if self.link.handle == Some(handle) {
            self.link.subscribed = enabled;
        }
    }
    pub fn suspend(&mut self, handle: u16, enabled: bool) {
        if self.link.handle == Some(handle) {
            self.link.suspended = enabled;
        }
    }
    pub fn accepts(&self, handle: u16) -> bool {
        self.link.handle == Some(handle) && self.link.encrypted
    }
    pub fn volume(&mut self, handle: u16, value: Option<Percent>, at: u64) -> bool {
        if !self.accepts(handle) {
            return false;
        }
        self.incoming.volume = Some((self.link.session, value, at));
        true
    }
    pub fn visualizer(&mut self, handle: u16, value: [u8; 8], at: u64) -> bool {
        if !self.accepts(handle) {
            return false;
        }
        self.incoming.visualizer = Some((self.link.session, value, at));
        true
    }
    pub fn hid_handle(&self, session: SessionId) -> Option<u16> {
        if self.link.session == session && self.link.ready() {
            self.link.handle
        } else {
            None
        }
    }
    pub fn snapshot(&self) -> LinkSnapshot {
        self.link
    }
    pub fn drain(&mut self) -> (LinkSnapshot, Incoming) {
        (self.link, std::mem::take(&mut self.incoming))
    }
}
