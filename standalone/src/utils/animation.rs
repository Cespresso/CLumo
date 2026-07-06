use std::time::Instant;

/// How the animation behaves when it reaches the last frame.
#[derive(Debug, Clone, Copy, PartialEq)]
pub enum PlaybackMode {
    /// Loop forever, wrapping back to frame 0 after the last frame.
    Loop,
    /// Play once, then hold on the last frame.
    OneShot,
}

/// Immutable definition of an animation sequence.
/// Uses `&'static` slice to avoid heap allocation — all frame data lives in flash.
#[derive(Debug, Clone, Copy)]
pub struct AnimationClip {
    frames: &'static [[u8; 8]],
    frame_duration_ms: u32,
    playback_mode: PlaybackMode,
}

impl AnimationClip {
    pub const fn new(
        frames: &'static [[u8; 8]],
        frame_duration_ms: u32,
        playback_mode: PlaybackMode,
    ) -> Self {
        Self {
            frames,
            frame_duration_ms,
            playback_mode,
        }
    }

    pub const fn looping(frames: &'static [[u8; 8]], frame_duration_ms: u32) -> Self {
        Self::new(frames, frame_duration_ms, PlaybackMode::Loop)
    }

    pub const fn one_shot(frames: &'static [[u8; 8]], frame_duration_ms: u32) -> Self {
        Self::new(frames, frame_duration_ms, PlaybackMode::OneShot)
    }
}

/// Saved state for restoring the previous animation after an interrupt.
struct InterruptState {
    saved_clip: AnimationClip,
    saved_frame: usize,
}

/// Stateful player that tracks the current frame and elapsed time.
/// Supports interrupt animations that auto-restore the previous clip on completion.
pub struct AnimationPlayer {
    clip: AnimationClip,
    current_frame: usize,
    last_tick: Instant,
    finished: bool,
    interrupt: Option<InterruptState>,
}

impl AnimationPlayer {
    /// Create a new player and start playback immediately.
    pub fn new(clip: AnimationClip) -> Self {
        Self {
            clip,
            current_frame: 0,
            last_tick: Instant::now(),
            finished: false,
            interrupt: None,
        }
    }

    /// Advance the animation based on elapsed time.
    /// Returns `Some(&[u8; 8])` if the frame changed, `None` otherwise.
    pub fn tick(&mut self) -> Option<&[u8; 8]> {
        if self.clip.frames.is_empty() || self.finished {
            return None;
        }

        let elapsed = self.last_tick.elapsed().as_millis() as u32;
        if elapsed < self.clip.frame_duration_ms {
            return None;
        }

        self.last_tick = Instant::now();
        let next = self.current_frame + 1;

        if next >= self.clip.frames.len() {
            match self.clip.playback_mode {
                PlaybackMode::Loop => self.current_frame = 0,
                PlaybackMode::OneShot => {
                    // Restore previous animation if this was an interrupt
                    if let Some(saved) = self.interrupt.take() {
                        self.clip = saved.saved_clip;
                        self.current_frame = saved.saved_frame;
                        return Some(&self.clip.frames[self.current_frame]);
                    }
                    self.finished = true;
                    return None;
                }
            }
        } else {
            self.current_frame = next;
        }

        Some(&self.clip.frames[self.current_frame])
    }

    /// Get the current frame data without advancing.
    pub fn current_frame(&self) -> &[u8; 8] {
        &self.clip.frames[self.current_frame]
    }

    /// Whether a OneShot animation has completed (without an interrupt to restore).
    pub fn is_finished(&self) -> bool {
        self.finished
    }

    /// Play a temporary animation. When it finishes (OneShot), the previous
    /// animation automatically resumes from where it left off.
    /// If already interrupted, the original (non-interrupt) animation is preserved.
    pub fn play_interrupt(&mut self, clip: AnimationClip) {
        if self.interrupt.is_none() {
            self.interrupt = Some(InterruptState {
                saved_clip: self.clip,
                saved_frame: self.current_frame,
            });
        }
        self.clip = clip;
        self.current_frame = 0;
        self.last_tick = Instant::now();
        self.finished = false;
    }

    /// Whether an interrupt animation is currently playing.
    pub fn is_interrupted(&self) -> bool {
        self.interrupt.is_some()
    }

    /// Reset the animation to the beginning, clearing any interrupt state.
    pub fn reset(&mut self) {
        self.current_frame = 0;
        self.last_tick = Instant::now();
        self.finished = false;
        self.interrupt = None;
    }

    /// Replace the current clip and reset playback.
    pub fn set_clip(&mut self, clip: AnimationClip) {
        self.clip = clip;
        self.reset();
    }
}
