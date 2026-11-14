//! Transport-independent volume state. Only adapters decide whether a sample is valid.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Percent(u8);
impl Percent {
    pub fn new(value: u8) -> Option<Self> {
        (value <= 100).then_some(Self(value))
    }
    pub fn get(self) -> u8 {
        self.0
    }
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct SessionId(pub u32);
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Source {
    Android,
    Ams,
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Availability {
    Preparing,
    Available,
    Unavailable,
}
pub struct VolumeSession {
    id: SessionId,
    source: Option<Source>,
    availability: Availability,
    value: Option<Percent>,
}
impl VolumeSession {
    pub fn new(id: SessionId) -> Self {
        Self {
            id,
            source: None,
            availability: Availability::Preparing,
            value: None,
        }
    }
    pub fn reset(&mut self, id: SessionId) {
        *self = Self::new(id);
    }
    pub fn source(&self) -> Option<Source> {
        self.source
    }
    pub fn value(&self) -> Option<Percent> {
        self.value
    }
    pub fn availability(&self) -> Availability {
        self.availability
    }
    /// A source is selected once per connection. Late/competing producers cannot steal it.
    pub fn update(
        &mut self,
        id: SessionId,
        source: Source,
        status: Availability,
        value: Option<Percent>,
    ) -> bool {
        if id != self.id || self.source.is_some_and(|active| active != source) {
            return false;
        }
        // Available without a value is not a valid snapshot.
        if status == Availability::Available && value.is_none() {
            return false;
        }
        self.source = Some(source);
        self.availability = status;
        self.value = if status == Availability::Available {
            value
        } else {
            None
        };
        true
    }
}
/// Android reports both its current observation and liveness in one atomic packet.
/// Missing packets invalidate Android only; AMS has subscription-based validity.
#[derive(Default)]
pub struct AndroidVolume {
    last: Option<(SessionId, u64)>,
}
impl AndroidVolume {
    pub fn receive(
        &mut self,
        session: &mut VolumeSession,
        id: SessionId,
        value: Option<Percent>,
        now: u64,
    ) -> bool {
        let status = if value.is_some() {
            Availability::Available
        } else {
            Availability::Unavailable
        };
        if !session.update(id, Source::Android, status, value) {
            return false;
        }
        self.last = Some((id, now));
        true
    }
    pub fn tick(&mut self, session: &mut VolumeSession, now: u64) {
        if let Some((id, at)) = self.last {
            if now.saturating_sub(at) >= 2000 {
                session.update(id, Source::Android, Availability::Unavailable, None);
                self.last = None;
            }
        }
    }
}
pub fn decode_android(bytes: &[u8]) -> Option<Option<Percent>> {
    match bytes {
        [0] => Some(None),
        [1, value] => Percent::new(*value).map(Some),
        _ => None,
    }
}
