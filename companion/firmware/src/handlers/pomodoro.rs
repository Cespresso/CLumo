use std::time::Instant;

use esp_idf_hal::sys::EspError;
use esp_idf_svc::nvs::{EspDefaultNvsPartition, EspNvs, NvsDefault};

use crate::assets;
use crate::countdown::progress_frame;
use crate::utils::animation::{AnimationClip, AnimationPlayer};
use crate::utils::bluetooth::PomodoroCommand;

use super::ModeHandler;

const NVS_NAMESPACE: &str = "TIMER";
const KEY_WORK_MIN: &str = "WORK_MIN";
const KEY_BREAK_MIN: &str = "BREAK_MIN";

pub const DEFAULT_WORK_MIN: u8 = 25;
pub const DEFAULT_BREAK_MIN: u8 = 5;

// Status byte values (see BLE protocol reference in README.md)
const STATE_IDLE: u8 = 0;
const STATE_RUNNING: u8 = 1;
const STATE_PAUSED: u8 = 2;
const PHASE_WORK: u8 = 0;
const PHASE_BREAK: u8 = 1;

const PIXELS_TOTAL: u32 = 64;

static BLINK_FRAMES: &[[u8; 8]] = &[
    assets::PATTERN_ALL_ON,
    assets::PATTERN_ALL_OFF,
    assets::PATTERN_ALL_ON,
    assets::PATTERN_ALL_OFF,
    assets::PATTERN_ALL_ON,
    assets::PATTERN_ALL_OFF,
];

#[derive(Debug, Clone, Copy, PartialEq)]
enum PomodoroState {
    Idle,
    Running,
    Paused,
}

#[derive(Debug, Clone, Copy, PartialEq)]
enum Phase {
    Work,
    Break,
}

/// Load work/break durations (minutes) from NVS, falling back to defaults.
pub fn load_durations(nvs: &EspNvs<NvsDefault>) -> (u8, u8) {
    let work = nvs
        .get_u8(KEY_WORK_MIN)
        .ok()
        .flatten()
        .unwrap_or(DEFAULT_WORK_MIN)
        .clamp(1, 99);
    let brk = nvs
        .get_u8(KEY_BREAK_MIN)
        .ok()
        .flatten()
        .unwrap_or(DEFAULT_BREAK_MIN)
        .clamp(1, 99);
    (work, brk)
}

/// Work/break pomodoro with app-settable durations.
///
/// The LED matrix acts as a progress bar: `lit = ceil(remaining_secs * 64 /
/// phase_total_secs)`. As time passes, pixels turn off row-major from the
/// top-left toward the bottom-right. Idle shows all 64 pixels lit.
pub struct PomodoroHandler {
    nvs: EspNvs<NvsDefault>,
    state: PomodoroState,
    phase: Phase,
    work_min: u8,
    break_min: u8,
    /// Total seconds of the phase currently counting down. Captured at phase
    /// start so duration changes only apply from the next reset/phase change.
    active_total_secs: u32,
    remaining_ms: u64,
    last_tick: Instant,
    last_lit: u8,
    animator: AnimationPlayer,
    transitioning: bool,
    status_pending: bool,
    last_notified_secs: u16,
    dirty: bool,
}

impl PomodoroHandler {
    pub fn new(nvs_partition: EspDefaultNvsPartition) -> Result<Self, EspError> {
        let nvs = EspNvs::new(nvs_partition, NVS_NAMESPACE, true)?;
        let (work_min, break_min) = load_durations(&nvs);
        Ok(Self {
            nvs,
            state: PomodoroState::Idle,
            phase: Phase::Work,
            work_min,
            break_min,
            active_total_secs: work_min as u32 * 60,
            remaining_ms: work_min as u64 * 60_000,
            last_tick: Instant::now(),
            last_lit: PIXELS_TOTAL as u8,
            animator: AnimationPlayer::new(AnimationClip::one_shot(BLINK_FRAMES, 200)),
            transitioning: false,
            status_pending: true, // push initial status when entering the mode
            last_notified_secs: 0,
            dirty: false,
        })
    }

    fn remaining_secs(&self) -> u16 {
        self.remaining_ms.div_ceil(1000) as u16
    }

    fn lit_pixels(&self) -> u8 {
        if self.active_total_secs == 0 {
            return 0;
        }
        let remaining = self.remaining_secs() as u32;
        let lit = (remaining * PIXELS_TOTAL).div_ceil(self.active_total_secs);
        lit.min(PIXELS_TOTAL) as u8
    }

    pub fn current_status(&self) -> [u8; 6] {
        let state = match self.state {
            PomodoroState::Idle => STATE_IDLE,
            PomodoroState::Running => STATE_RUNNING,
            PomodoroState::Paused => STATE_PAUSED,
        };
        let phase = match self.phase {
            Phase::Work => PHASE_WORK,
            Phase::Break => PHASE_BREAK,
        };
        let remaining = self.remaining_secs();
        [
            state,
            phase,
            (remaining >> 8) as u8,
            remaining as u8,
            self.work_min,
            self.break_min,
        ]
    }

    fn phase_duration_secs(&self, phase: Phase) -> u32 {
        match phase {
            Phase::Work => self.work_min as u32 * 60,
            Phase::Break => self.break_min as u32 * 60,
        }
    }

    fn start_resume(&mut self) {
        match self.state {
            PomodoroState::Idle => {
                log::info!("Pomodoro: starting work phase");
                self.phase = Phase::Work;
                self.active_total_secs = self.phase_duration_secs(Phase::Work);
                self.remaining_ms = self.active_total_secs as u64 * 1000;
                self.state = PomodoroState::Running;
                self.last_tick = Instant::now();
            }
            PomodoroState::Paused => {
                log::info!("Pomodoro: resumed");
                self.state = PomodoroState::Running;
                self.last_tick = Instant::now();
            }
            PomodoroState::Running => {} // no-op; status is echoed anyway
        }
        self.status_pending = true;
    }

    fn pause(&mut self) {
        if self.state == PomodoroState::Running {
            log::info!("Pomodoro: paused");
            let delta = self.last_tick.elapsed().as_millis() as u64;
            self.remaining_ms = self.remaining_ms.saturating_sub(delta);
            self.state = PomodoroState::Paused;
        }
        self.status_pending = true;
    }

    fn reset(&mut self) {
        log::info!("Pomodoro: reset to idle");
        self.state = PomodoroState::Idle;
        self.phase = Phase::Work;
        self.active_total_secs = self.phase_duration_secs(Phase::Work);
        self.remaining_ms = self.active_total_secs as u64 * 1000;
        self.transitioning = false;
        self.dirty = true;
        self.status_pending = true;
    }

    fn set_durations(&mut self, work_min: u8, break_min: u8) {
        log::info!(
            "Pomodoro: durations set to work={}m break={}m",
            work_min,
            break_min
        );
        self.work_min = work_min;
        self.break_min = break_min;
        if let Err(e) = self.nvs.set_u8(KEY_WORK_MIN, work_min) {
            log::warn!("Pomodoro: failed to persist work duration: {:?}", e);
        }
        if let Err(e) = self.nvs.set_u8(KEY_BREAK_MIN, break_min) {
            log::warn!("Pomodoro: failed to persist break duration: {:?}", e);
        }
        // Apply immediately when idle; otherwise the new durations take
        // effect from the next reset/phase change.
        if self.state == PomodoroState::Idle {
            self.active_total_secs = self.phase_duration_secs(Phase::Work);
            self.remaining_ms = self.active_total_secs as u64 * 1000;
            self.dirty = true;
        }
        self.status_pending = true;
    }

    /// Switch to the next phase and start the blink transition animation.
    fn start_phase_transition(&mut self) {
        self.phase = match self.phase {
            Phase::Work => {
                log::info!("Pomodoro: work complete, starting break");
                Phase::Break
            }
            Phase::Break => {
                log::info!("Pomodoro: break complete, starting work");
                Phase::Work
            }
        };
        self.active_total_secs = self.phase_duration_secs(self.phase);
        self.remaining_ms = self.active_total_secs as u64 * 1000;
        self.last_tick = Instant::now();
        self.animator = AnimationPlayer::new(AnimationClip::one_shot(BLINK_FRAMES, 200));
        self.transitioning = true;
        self.status_pending = true;
    }
}

impl ModeHandler for PomodoroHandler {
    fn on_enter(&mut self) -> [u8; 8] {
        let lit = self.lit_pixels();
        self.last_lit = lit;
        self.dirty = false;
        progress_frame(lit)
    }

    fn on_main_button(&mut self) {
        match self.state {
            PomodoroState::Running => self.pause(),
            PomodoroState::Idle | PomodoroState::Paused => self.start_resume(),
        }
    }

    fn on_sub_button(&mut self) {
        self.reset();
    }

    fn on_pomodoro_command(&mut self, cmd: PomodoroCommand) {
        match cmd {
            PomodoroCommand::Start => self.start_resume(),
            PomodoroCommand::Pause => self.pause(),
            PomodoroCommand::Reset => self.reset(),
            PomodoroCommand::SetDurations {
                work_min,
                break_min,
            } => self.set_durations(work_min, break_min),
        }
    }

    fn poll_pomodoro_status(&mut self) -> Option<[u8; 6]> {
        if !self.status_pending {
            return None;
        }
        self.status_pending = false;
        self.last_notified_secs = self.remaining_secs();
        Some(self.current_status())
    }

    fn tick(&mut self) -> Option<[u8; 8]> {
        // Countdown (keeps running during the transition animation)
        if self.state == PomodoroState::Running {
            let delta = self.last_tick.elapsed().as_millis() as u64;
            self.last_tick = Instant::now();
            self.remaining_ms = self.remaining_ms.saturating_sub(delta);

            if self.remaining_ms == 0 {
                self.start_phase_transition();
                return Some(assets::PATTERN_ALL_ON);
            }

            // Once-per-second status notify while running
            if self.remaining_secs() != self.last_notified_secs {
                self.status_pending = true;
            }
        }

        // Phase transition blink animation overrides the progress bar
        if self.transitioning {
            if let Some(frame) = self.animator.tick() {
                return Some(*frame);
            }
            if self.animator.is_finished() {
                self.transitioning = false;
                self.last_lit = self.lit_pixels();
                self.dirty = false;
                return Some(progress_frame(self.last_lit));
            }
            return None;
        }

        let lit = self.lit_pixels();
        if lit != self.last_lit || self.dirty {
            self.last_lit = lit;
            self.dirty = false;
            Some(progress_frame(lit))
        } else {
            None
        }
    }
}
