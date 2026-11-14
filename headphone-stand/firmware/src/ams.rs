//! AMS wire parsing and generation checks, independent of Bluetooth and rendering.
use crate::volume::Percent;
#[derive(Debug, PartialEq, Eq)]
pub enum Update {
    Ignore,
    Invalid,
    Volume(Percent),
}
pub fn parse_update(bytes: &[u8]) -> Update {
    if bytes.len() < 3 {
        return Update::Invalid;
    }
    if bytes[0..2] != [0, 2] {
        return Update::Ignore;
    }
    if bytes[2] != 0 || bytes.len() > 35 {
        return Update::Invalid;
    }
    let Some(value) = std::str::from_utf8(&bytes[3..])
        .ok()
        .and_then(|s| s.parse::<f32>().ok())
    else {
        return Update::Invalid;
    };
    if !value.is_finite() || !(0.0..=1.0).contains(&value) {
        return Update::Invalid;
    }
    Update::Volume(Percent::new((value * 100.0).round() as u8).unwrap())
}
#[derive(Default)]
pub struct Epoch(u64);
impl Epoch {
    pub fn current(&self) -> u64 {
        self.0
    }
    pub fn accepts(&self, token: u64) -> bool {
        token == self.0
    }
    pub fn invalidate(&mut self) {
        self.0 = self.0.wrapping_add(1);
    }
}
