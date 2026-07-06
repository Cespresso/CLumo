use crate::assets;
use crate::utils::animation::{AnimationClip, AnimationPlayer};
use crate::utils::button::PressType;
use crate::utils::rng::random_range;

use super::ModeHandler;

const DECAY_INTERVAL_TICKS: u32 = 360; // 360 × 50ms = 18s per hunger point
const LOOK_AROUND_MIN_TICKS: u32 = 100; // 5s
const LOOK_AROUND_MAX_TICKS: u32 = 300; // 15s
const FEED_AMOUNT: u8 = 20;
const MAX_HUNGER: u8 = 100;
const INITIAL_HUNGER: u8 = 50;

// --- Mood ---

#[derive(Debug, Clone, Copy, PartialEq)]
enum Mood {
    Happy,
    Normal,
    Sad,
    Angry,
}

fn mood_from_hunger(hunger: u8) -> Mood {
    if hunger >= 70 {
        Mood::Happy
    } else if hunger >= 40 {
        Mood::Normal
    } else if hunger >= 10 {
        Mood::Sad
    } else {
        Mood::Angry
    }
}

// --- Animation clips ---

static HAPPY_IDLE_FRAMES: &[[u8; 8]] = &[
    assets::FACE_HAPPY,
    assets::FACE_HAPPY,
    assets::FACE_HAPPY,
    assets::FACE_BLINK,
];

static NORMAL_IDLE_FRAMES: &[[u8; 8]] = &[
    assets::FACE_SMILE,
    assets::FACE_SMILE,
    assets::FACE_SMILE,
    assets::FACE_BLINK,
];

static SAD_IDLE_FRAMES: &[[u8; 8]] = &[
    assets::FACE_SAD,
    assets::FACE_SAD,
    assets::FACE_SAD,
    assets::FACE_BLINK,
];

static ANGRY_IDLE_FRAMES: &[[u8; 8]] = &[
    assets::FACE_ANGRY,
    assets::FACE_ANGRY,
];

static FEED_REACTION_FRAMES: &[[u8; 8]] = &[
    assets::FACE_HAPPY,
    assets::FACE_SMILE,
    assets::FACE_HAPPY,
    assets::FACE_SMILE,
];

static POKE_REACTION_FRAMES: &[[u8; 8]] = &[
    assets::FACE_ANGRY,
    assets::FACE_SAD,
    assets::FACE_ANGRY,
    assets::FACE_SAD,
];

static LOOK_AROUND_FRAMES: &[[u8; 8]] = &[
    assets::FACE_LOOK_LEFT,
    assets::FACE_LOOK_LEFT,
    assets::FACE_LOOK_RIGHT,
    assets::FACE_LOOK_RIGHT,
];

fn idle_clip(mood: Mood) -> AnimationClip {
    match mood {
        Mood::Happy => AnimationClip::looping(HAPPY_IDLE_FRAMES, 600),
        Mood::Normal => AnimationClip::looping(NORMAL_IDLE_FRAMES, 600),
        Mood::Sad => AnimationClip::looping(SAD_IDLE_FRAMES, 800),
        Mood::Angry => AnimationClip::looping(ANGRY_IDLE_FRAMES, 1000),
    }
}

// --- PetHandler ---

pub struct PetHandler {
    animator: AnimationPlayer,
    hunger: u8,
    idle_mood: Mood,
    decay_tick_counter: u32,
    look_around_tick_counter: u32,
    next_look_around_ticks: u32,
}

impl PetHandler {
    pub fn new() -> Self {
        let mood = mood_from_hunger(INITIAL_HUNGER);
        Self {
            animator: AnimationPlayer::new(idle_clip(mood)),
            hunger: INITIAL_HUNGER,
            idle_mood: mood,
            decay_tick_counter: 0,
            look_around_tick_counter: 0,
            next_look_around_ticks: random_range(LOOK_AROUND_MIN_TICKS, LOOK_AROUND_MAX_TICKS),
        }
    }

    fn sync_idle_mood(&mut self) {
        let mood = mood_from_hunger(self.hunger);
        if mood != self.idle_mood {
            self.idle_mood = mood;
            if !self.animator.is_interrupted() {
                self.animator.set_clip(idle_clip(mood));
            }
        }
    }
}

impl ModeHandler for PetHandler {
    fn on_enter(&mut self) -> [u8; 8] {
        *self.animator.current_frame()
    }

    fn on_red_button(&mut self, press: PressType) {
        if press == PressType::Short {
            self.hunger = (self.hunger + FEED_AMOUNT).min(MAX_HUNGER);
            let clip = AnimationClip::one_shot(FEED_REACTION_FRAMES, 300);
            self.animator.play_interrupt(clip);
        }
    }

    fn on_white_button(&mut self, press: PressType) {
        if press == PressType::Short {
            let clip = AnimationClip::one_shot(POKE_REACTION_FRAMES, 300);
            self.animator.play_interrupt(clip);
        }
    }

    fn tick(&mut self) -> Option<[u8; 8]> {
        // Hunger decay
        self.decay_tick_counter += 1;
        if self.decay_tick_counter >= DECAY_INTERVAL_TICKS {
            self.decay_tick_counter = 0;
            self.hunger = self.hunger.saturating_sub(1);
            self.sync_idle_mood();
        }

        // Look-around random event
        self.look_around_tick_counter += 1;
        if self.look_around_tick_counter >= self.next_look_around_ticks {
            self.look_around_tick_counter = 0;
            self.next_look_around_ticks =
                random_range(LOOK_AROUND_MIN_TICKS, LOOK_AROUND_MAX_TICKS);
            if !self.animator.is_interrupted() {
                let clip = AnimationClip::one_shot(LOOK_AROUND_FRAMES, 400);
                self.animator.play_interrupt(clip);
            }
        }

        // Advance animation, detecting interrupt → idle transition
        let interrupted_before = self.animator.is_interrupted();
        let result = self.animator.tick().copied();
        let interrupted_after = self.animator.is_interrupted();

        // If interrupt just ended, sync restored idle clip to current mood
        if interrupted_before && !interrupted_after {
            let mood = mood_from_hunger(self.hunger);
            if mood != self.idle_mood {
                self.idle_mood = mood;
                self.animator.set_clip(idle_clip(mood));
                return Some(*self.animator.current_frame());
            }
        }

        result
    }
}
