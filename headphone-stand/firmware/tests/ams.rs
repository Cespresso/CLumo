use headphone_stand::ams::{parse_update, Epoch, Update};
use headphone_stand::volume::Percent;
#[test]
fn player_volume_is_finite_in_range_and_not_truncated() {
    for (text, percent) in [
        ("0", 0),
        ("1", 100),
        ("0.5", 50),
        ("0.0625", 6),
        ("0.999", 100),
    ] {
        let mut bytes = vec![0, 2, 0];
        bytes.extend_from_slice(text.as_bytes());
        assert_eq!(
            parse_update(&bytes),
            Update::Volume(Percent::new(percent).unwrap())
        );
    }
    for value in ["", "NaN", "inf", "-0.1", "1.1", "abc", "0.5x"] {
        let mut bytes = vec![0, 2, 0];
        bytes.extend_from_slice(value.as_bytes());
        assert_eq!(parse_update(&bytes), Update::Invalid);
    }
    assert_eq!(parse_update(&[0, 2, 1, b'0']), Update::Invalid);
    assert_eq!(parse_update(&[0, 2]), Update::Invalid);
    assert_eq!(parse_update(&[2, 2, 0, b'x']), Update::Ignore);
}
#[test]
fn invalidation_rejects_delayed_results() {
    let mut epoch = Epoch::default();
    let old = epoch.current();
    epoch.invalidate();
    assert!(!epoch.accepts(old));
    assert!(epoch.accepts(epoch.current()));
}
