use std::time::Instant;

use crate::assets;
use crate::utils::button::PressType;
use crate::utils::rng;

use super::ModeHandler;

const DICE_FACES: &[[u8; 8]] = &[
    assets::DICE_1,
    assets::DICE_2,
    assets::DICE_3,
    assets::DICE_4,
    assets::DICE_5,
    assets::DICE_6,
];

/// State tracking for the slot-style dice roll animation.
/// The animation runs for ~1.5 seconds, showing random faces that decelerate
/// before landing on the pre-determined result.
struct DiceRollState {
    start: Instant,
    last_change: Instant,
    current_face: usize,
    result: usize,
}

impl DiceRollState {
    /// Returns the interval (ms) between frame changes based on elapsed time.
    /// Increases over time to create a deceleration effect.
    fn interval_ms(elapsed_ms: u32) -> Option<u32> {
        match elapsed_ms {
            0..=500 => Some(60),
            501..=900 => Some(110),
            901..=1200 => Some(180),
            1201..=1500 => Some(300),
            _ => None, // animation complete
        }
    }

    /// Pick a random dice face index (0-5) that differs from `exclude`.
    fn random_face_except(exclude: usize) -> usize {
        loop {
            let face = rng::random_range(0, 6) as usize;
            if face != exclude {
                return face;
            }
        }
    }
}

pub struct DiceHandler {
    rolling: Option<DiceRollState>,
    dice_frame: [u8; 8],
    dirty: bool,
}

impl DiceHandler {
    pub fn new() -> Self {
        Self {
            rolling: None,
            dice_frame: assets::ICON_DICE,
            dirty: false,
        }
    }
}

impl ModeHandler for DiceHandler {
    fn on_enter(&mut self) -> [u8; 8] {
        self.dice_frame
    }

    fn on_red_button(&mut self, press: PressType) {
        if press != PressType::Short {
            return;
        }
        if self.rolling.is_some() {
            return;
        }

        let result = rng::random_range(0, 6) as usize;
        let first_face = DiceRollState::random_face_except(result);
        let now = Instant::now();
        self.rolling = Some(DiceRollState {
            start: now,
            last_change: now,
            current_face: first_face,
            result,
        });
        self.dice_frame = DICE_FACES[first_face];
        self.dirty = true;
    }

    fn tick(&mut self) -> Option<[u8; 8]> {
        if let Some(ref mut roll) = self.rolling {
            let total_elapsed = roll.start.elapsed().as_millis() as u32;
            let since_change = roll.last_change.elapsed().as_millis() as u32;

            match DiceRollState::interval_ms(total_elapsed) {
                Some(interval) if since_change >= interval => {
                    roll.current_face = DiceRollState::random_face_except(roll.current_face);
                    roll.last_change = Instant::now();
                    self.dice_frame = DICE_FACES[roll.current_face];
                    Some(self.dice_frame)
                }
                Some(_) => None, // waiting for next interval
                None => {
                    // Animation complete — show final result
                    let result = roll.result;
                    self.rolling = None;
                    self.dice_frame = DICE_FACES[result];
                    Some(self.dice_frame)
                }
            }
        } else if self.dirty {
            self.dirty = false;
            Some(self.dice_frame)
        } else {
            None
        }
    }
}
