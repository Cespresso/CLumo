#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct DisplayState {
    committed: [u8; 8],
    preview: Option<PreviewFrame>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
struct PreviewFrame {
    frame: [u8; 8],
    received_at_ms: u64,
}

impl DisplayState {
    pub fn new(committed: [u8; 8]) -> Self {
        Self {
            committed,
            preview: None,
        }
    }

    pub fn preview(&mut self, frame: [u8; 8], now_ms: u64) {
        self.preview = Some(PreviewFrame {
            frame,
            received_at_ms: now_ms,
        });
    }

    pub fn commit_preview(&mut self) -> Option<[u8; 8]> {
        let preview = self.preview.take()?;
        self.committed = preview.frame;
        Some(self.committed)
    }

    pub fn cancel_preview(&mut self) -> bool {
        self.preview.take().is_some()
    }

    pub fn expire_preview(&mut self, now_ms: u64, ttl_ms: u64) -> bool {
        let expired = self
            .preview
            .is_some_and(|preview| now_ms.saturating_sub(preview.received_at_ms) >= ttl_ms);
        if expired {
            self.preview = None;
        }
        expired
    }

    pub fn visible_frame(&self) -> [u8; 8] {
        self.preview.map_or(self.committed, |preview| preview.frame)
    }

    pub fn committed_frame(&self) -> [u8; 8] {
        self.committed
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const COMMITTED: [u8; 8] = [0x18; 8];
    const PREVIEW: [u8; 8] = [0x81; 8];

    #[test]
    fn preview_changes_visible_frame_without_changing_committed_frame() {
        let mut state = DisplayState::new(COMMITTED);

        state.preview(PREVIEW, 100);

        assert_eq!(state.visible_frame(), PREVIEW);
        assert_eq!(state.committed_frame(), COMMITTED);
    }

    #[test]
    fn explicit_commit_promotes_preview() {
        let mut state = DisplayState::new(COMMITTED);
        state.preview(PREVIEW, 100);

        assert_eq!(state.commit_preview(), Some(PREVIEW));
        assert_eq!(state.committed_frame(), PREVIEW);
        assert_eq!(state.visible_frame(), PREVIEW);
    }

    #[test]
    fn stale_preview_expires_back_to_committed_frame() {
        let mut state = DisplayState::new(COMMITTED);
        state.preview(PREVIEW, 100);

        assert!(!state.expire_preview(5_099, 5_000));
        assert!(state.expire_preview(5_100, 5_000));
        assert_eq!(state.visible_frame(), COMMITTED);
    }

    #[test]
    fn cancel_discards_preview_without_committing_it() {
        let mut state = DisplayState::new(COMMITTED);
        state.preview(PREVIEW, 100);

        assert!(state.cancel_preview());
        assert_eq!(state.visible_frame(), COMMITTED);
        assert_eq!(state.committed_frame(), COMMITTED);
    }
}
