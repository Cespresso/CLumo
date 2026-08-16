use std::time::Instant;

use crate::assets;
use crate::countdown::{
    encode_timer_status, progress_frame, progress_pixels, Countdown, CountdownState,
    DurationSetting,
};
use crate::utils::bluetooth::TimerCommand;

use super::ModeHandler;

const COMPLETION_BLINK_MS: u128 = 400;

pub struct TimerHandler {
    timer: Countdown,
    last_tick: Instant,
    last_lit: u8,
    last_notified_secs: u16,
    status_pending: bool,
    dirty: bool,
    blink_on: bool,
    last_blink: Instant,
}

impl TimerHandler {
    pub fn new() -> Self {
        let timer = Countdown::default();
        Self {
            last_lit: progress_pixels(timer.remaining_secs(), timer.setting().total_secs()),
            last_notified_secs: timer.remaining_secs(),
            timer,
            last_tick: Instant::now(),
            status_pending: true,
            dirty: true,
            blink_on: false,
            last_blink: Instant::now(),
        }
    }

    fn start_resume(&mut self) {
        if self.timer.start_resume() {
            self.last_tick = Instant::now();
            self.blink_on = false;
            self.dirty = true;
        }
        self.status_pending = true;
    }

    fn pause(&mut self) {
        if self.timer.state() == CountdownState::Running {
            self.advance_running_timer();
            self.timer.pause();
        }
        self.status_pending = true;
    }

    fn cancel(&mut self) {
        self.timer.cancel();
        self.blink_on = false;
        self.dirty = true;
        self.status_pending = true;
    }

    fn set_duration(&mut self, setting: DurationSetting) {
        if self.timer.set_duration(setting) {
            self.dirty = true;
        }
        // A GATT write temporarily replaces the characteristic value with the
        // command payload. Echo the current status even when a running timer
        // rejects the setting so subsequent reads remain a valid 5-byte status.
        self.status_pending = true;
    }

    pub fn current_status(&self) -> [u8; 5] {
        encode_timer_status(&self.timer)
    }

    fn advance_running_timer(&mut self) {
        let elapsed_ms = self.last_tick.elapsed().as_millis() as u64;
        self.last_tick = Instant::now();
        if self.timer.tick(elapsed_ms) {
            self.status_pending = true;
        }
        if self.timer.state() == CountdownState::Completed {
            self.blink_on = true;
            self.last_blink = Instant::now();
            self.dirty = true;
        }
    }

    fn progress_frame(&mut self) -> Option<[u8; 8]> {
        let lit = progress_pixels(
            self.timer.remaining_secs(),
            self.timer.setting().total_secs(),
        );
        if self.dirty || lit != self.last_lit {
            self.last_lit = lit;
            self.dirty = false;
            Some(progress_frame(lit))
        } else {
            None
        }
    }
}

impl ModeHandler for TimerHandler {
    fn on_enter(&mut self) -> [u8; 8] {
        self.last_lit = progress_pixels(
            self.timer.remaining_secs(),
            self.timer.setting().total_secs(),
        );
        self.dirty = false;
        progress_frame(self.last_lit)
    }

    fn on_main_button(&mut self) {
        match self.timer.state() {
            CountdownState::Running => self.pause(),
            CountdownState::Idle | CountdownState::Paused | CountdownState::Completed => {
                self.start_resume()
            }
        }
    }

    fn on_sub_button(&mut self) {
        self.cancel();
    }

    fn on_timer_command(&mut self, cmd: TimerCommand) {
        match cmd {
            TimerCommand::Start => self.start_resume(),
            TimerCommand::Pause => self.pause(),
            TimerCommand::Cancel => self.cancel(),
            TimerCommand::SetDuration(setting) => self.set_duration(setting),
            TimerCommand::RefreshStatus => self.status_pending = true,
        }
    }

    fn poll_timer_status(&mut self) -> Option<[u8; 5]> {
        if !self.status_pending {
            return None;
        }
        self.status_pending = false;
        self.last_notified_secs = self.timer.remaining_secs();
        Some(self.current_status())
    }

    fn tick(&mut self) -> Option<[u8; 8]> {
        if self.timer.state() == CountdownState::Running {
            self.advance_running_timer();
            if self.timer.remaining_secs() != self.last_notified_secs {
                self.status_pending = true;
            }
        }

        if self.timer.state() == CountdownState::Completed {
            if self.dirty {
                self.dirty = false;
                return Some(if self.blink_on {
                    assets::PATTERN_ALL_ON
                } else {
                    assets::PATTERN_ALL_OFF
                });
            }
            if self.last_blink.elapsed().as_millis() >= COMPLETION_BLINK_MS {
                self.blink_on = !self.blink_on;
                self.last_blink = Instant::now();
                return Some(if self.blink_on {
                    assets::PATTERN_ALL_ON
                } else {
                    assets::PATTERN_ALL_OFF
                });
            }
            return None;
        }

        self.progress_frame()
    }
}
