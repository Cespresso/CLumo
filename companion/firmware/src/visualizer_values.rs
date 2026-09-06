pub const fn idle_frame() -> [u8; 8] {
    [0u8; 8]
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn idle_frame_is_fully_off() {
        assert_eq!(idle_frame(), [0u8; 8]);
    }
}
