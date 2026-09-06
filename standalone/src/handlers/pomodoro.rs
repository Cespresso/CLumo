use std::time::Instant;

use crate::assets;
use crate::utils::animation::{AnimationClip, AnimationPlayer};
use crate::utils::button::PressType;

use super::ModeHandler;

const WORK_DURATION_MS: u64 = 25 * 60 * 1000;
const BREAK_DURATION_MS: u64 = 5 * 60 * 1000;
const PIXELS_TOTAL: u64 = 64;

static BLINK_FRAMES: &[[u8; 8]] = &[
    assets::PATTERN_ALL_ON,
    assets::PATTERN_ALL_OFF,
    assets::PATTERN_ALL_ON,
    assets::PATTERN_ALL_OFF,
    assets::PATTERN_ALL_ON,
    assets::PATTERN_ALL_OFF,
];

#[derive(Debug, Clone, Copy, PartialEq)]
enum TimerPhase {
    Idle,
    Working,
    Paused { from: ActivePhase },
    Breaking,
}

#[derive(Debug, Clone, Copy, PartialEq)]
enum ActivePhase {
    Working,
    Breaking,
}

pub struct PomodoroHandler {
    phase: TimerPhase,
    elapsed_ms: u64,
    last_tick: Instant,
    last_pixel_count: u8,
    animator: AnimationPlayer,
    notifying: bool,
}

impl PomodoroHandler {
    pub fn new() -> Self {
        let clip = AnimationClip::one_shot(BLINK_FRAMES, 200);
        Self {
            phase: TimerPhase::Idle,
            elapsed_ms: 0,
            last_tick: Instant::now(),
            last_pixel_count: 0,
            animator: AnimationPlayer::new(clip),
            notifying: false,
        }
    }

    fn active_duration_ms(&self) -> u64 {
        match self.phase {
            TimerPhase::Working | TimerPhase::Paused { from: ActivePhase::Working } => {
                WORK_DURATION_MS
            }
            TimerPhase::Breaking | TimerPhase::Paused { from: ActivePhase::Breaking } => {
                BREAK_DURATION_MS
            }
            TimerPhase::Idle => 0,
        }
    }

    fn is_work_phase(&self) -> bool {
        matches!(
            self.phase,
            TimerPhase::Working | TimerPhase::Paused { from: ActivePhase::Working }
        )
    }

    fn filled_pixels(&self) -> u8 {
        let duration = self.active_duration_ms();
        if duration == 0 {
            return 0;
        }
        let elapsed = self.elapsed_ms.min(duration);
        if self.is_work_phase() {
            // Work: start full (64), turn off as time passes
            let turned_off = elapsed * PIXELS_TOTAL / duration;
            (PIXELS_TOTAL - turned_off) as u8
        } else {
            // Break: start empty (0), fill up as time passes
            (elapsed * PIXELS_TOTAL / duration) as u8
        }
    }

    fn start_notification(&mut self) {
        self.notifying = true;
        let clip = AnimationClip::one_shot(BLINK_FRAMES, 200);
        self.animator = AnimationPlayer::new(clip);
    }

    fn transition_to_next_phase(&mut self) {
        self.elapsed_ms = 0;
        self.last_tick = Instant::now();
        match self.phase {
            TimerPhase::Working => {
                log::info!("Pomodoro: work complete, starting break");
                self.phase = TimerPhase::Breaking;
            }
            TimerPhase::Breaking => {
                log::info!("Pomodoro: break complete, starting work");
                self.phase = TimerPhase::Working;
            }
            _ => {}
        }
        self.last_pixel_count = self.filled_pixels();
    }

    fn reset(&mut self) {
        self.phase = TimerPhase::Idle;
        self.elapsed_ms = 0;
        self.notifying = false;
        self.last_pixel_count = 0;
        log::info!("Pomodoro: reset to idle");
    }
}

/// Generate an 8x8 frame with `filled` pixels lit, from top-left to bottom-right.
fn generate_progress_frame(filled: u8) -> [u8; 8] {
    let mut frame = [0u8; 8];
    let full_rows = (filled / 8) as usize;
    let remaining = filled % 8;
    for row in frame.iter_mut().take(full_rows) {
        *row = 0xFF;
    }
    if full_rows < 8 && remaining > 0 {
        frame[full_rows] = 0xFF << (8 - remaining);
    }
    frame
}

impl ModeHandler for PomodoroHandler {
    fn on_enter(&mut self) -> [u8; 8] {
        match self.phase {
            TimerPhase::Idle => assets::ICON_POMODORO,
            _ => generate_progress_frame(self.filled_pixels()),
        }
    }

    fn on_red_button(&mut self, press: PressType) {
        if press != PressType::Short || self.notifying {
            return;
        }
        match self.phase {
            TimerPhase::Idle => {
                log::info!("Pomodoro: starting work timer");
                self.phase = TimerPhase::Working;
                self.elapsed_ms = 0;
                self.last_tick = Instant::now();
                self.last_pixel_count = 64;
            }
            TimerPhase::Working => {
                log::info!("Pomodoro: paused (work)");
                let delta = self.last_tick.elapsed().as_millis() as u64;
                self.elapsed_ms += delta;
                self.phase = TimerPhase::Paused {
                    from: ActivePhase::Working,
                };
            }
            TimerPhase::Breaking => {
                log::info!("Pomodoro: paused (break)");
                let delta = self.last_tick.elapsed().as_millis() as u64;
                self.elapsed_ms += delta;
                self.phase = TimerPhase::Paused {
                    from: ActivePhase::Breaking,
                };
            }
            TimerPhase::Paused { from } => {
                log::info!("Pomodoro: resumed");
                self.last_tick = Instant::now();
                self.phase = match from {
                    ActivePhase::Working => TimerPhase::Working,
                    ActivePhase::Breaking => TimerPhase::Breaking,
                };
            }
        }
    }

    fn on_white_button(&mut self, press: PressType) {
        if press == PressType::Short {
            self.reset();
        }
    }

    fn tick(&mut self) -> Option<[u8; 8]> {
        // Handle completion notification animation
        if self.notifying {
            if let Some(frame) = self.animator.tick() {
                return Some(*frame);
            }
            if self.animator.is_finished() {
                self.notifying = false;
                self.transition_to_next_phase();
                return Some(generate_progress_frame(self.filled_pixels()));
            }
            return None;
        }

        match self.phase {
            TimerPhase::Idle | TimerPhase::Paused { .. } => None,
            TimerPhase::Working | TimerPhase::Breaking => {
                let delta = self.last_tick.elapsed().as_millis() as u64;
                self.last_tick = Instant::now();
                self.elapsed_ms += delta;

                let duration = self.active_duration_ms();
                if self.elapsed_ms >= duration {
                    self.start_notification();
                    return Some(assets::PATTERN_ALL_ON);
                }

                let pixels = self.filled_pixels();
                if pixels != self.last_pixel_count {
                    self.last_pixel_count = pixels;
                    Some(generate_progress_frame(pixels))
                } else {
                    None
                }
            }
        }
    }
}
