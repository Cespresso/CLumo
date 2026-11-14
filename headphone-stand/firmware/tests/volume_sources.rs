use headphone_stand::volume::*;
fn pct(v: u8) -> Percent {
    Percent::new(v).unwrap()
}
#[test]
fn android_expires_and_recovers_without_a_volume_change() {
    let mut session = VolumeSession::new(SessionId(1));
    let mut android = AndroidVolume::default();
    assert!(android.receive(&mut session, SessionId(1), Some(pct(0)), 100));
    assert_eq!(session.value(), Some(pct(0)));
    android.tick(&mut session, 2099);
    assert_eq!(session.availability(), Availability::Available);
    android.tick(&mut session, 2100);
    assert_eq!(session.value(), None);
    assert_eq!(session.availability(), Availability::Unavailable);
    android.receive(&mut session, SessionId(1), Some(pct(0)), 2200);
    assert_eq!(session.value(), Some(pct(0)));
}
#[test]
fn stale_sessions_and_competing_sources_cannot_overwrite() {
    let mut s = VolumeSession::new(SessionId(2));
    assert!(!s.update(
        SessionId(1),
        Source::Ams,
        Availability::Available,
        Some(pct(90))
    ));
    assert!(s.update(SessionId(2), Source::Ams, Availability::Preparing, None));
    assert!(!s.update(
        SessionId(2),
        Source::Android,
        Availability::Available,
        Some(pct(90))
    ));
    assert!(s.update(
        SessionId(2),
        Source::Ams,
        Availability::Available,
        Some(pct(30))
    ));
    let mut android = AndroidVolume::default();
    android.tick(&mut s, u64::MAX);
    assert_eq!(s.value(), Some(pct(30)));
    s.update(SessionId(2), Source::Ams, Availability::Unavailable, None);
    assert_eq!(s.value(), None);
    s.reset(SessionId(3));
    assert!(!s.update(
        SessionId(2),
        Source::Ams,
        Availability::Available,
        Some(pct(90))
    ));
    assert!(s.update(
        SessionId(3),
        Source::Android,
        Availability::Available,
        Some(pct(50))
    ));
}
#[test]
fn protocol_distinguishes_zero_unknown_and_invalid() {
    assert_eq!(decode_android(&[0]), Some(None));
    assert_eq!(decode_android(&[1, 0]), Some(Some(pct(0))));
    assert_eq!(decode_android(&[1, 100]), Some(Some(pct(100))));
    for packet in [
        &[][..],
        &[255],
        &[100],
        &[0, 0],
        &[1],
        &[1, 101],
        &[1, 50, 0],
    ] {
        assert_eq!(decode_android(packet), None);
    }
    assert_eq!(Percent::new(101), None);
}
#[test]
fn disconnect_clears_transients_but_keeps_settings() {
    use headphone_stand::{Action, Command, State};
    let mut state = State::default();
    state.settings.brightness = 7;
    state.set_volume(Some(pct(100)));
    state.apply(Command::Visualizer([8; 8]), 0);
    state.action(Action::VolumeUp, 0);
    state.clear_transient();
    assert_eq!(state.settings.brightness, 7);
    assert_eq!(state.led_mask(1), 0);
    assert_eq!(state.frame(1), state.settings.idle_frame(1));
}
#[test]
fn mailbox_drops_old_connection_and_keeps_latest_only() {
    use headphone_stand::connection::*;
    let mut link = Connection::default();
    link.connect(7);
    let old = link.snapshot().session;
    assert!(!link.volume(7, Some(pct(10)), 0));
    link.authenticate(7, true);
    assert!(link.volume(7, Some(pct(10)), 0));
    assert!(link.volume(7, Some(pct(20)), 10));
    assert_eq!(link.drain().1.volume.unwrap().1, Some(pct(20)));
    link.volume(7, Some(pct(40)), 20);
    link.disconnect(7);
    link.connect(7);
    assert_ne!(link.snapshot().session, old);
    assert!(link.drain().1.volume.is_none());
    assert!(!link.snapshot().ready());
    link.authenticate(7, true);
    link.subscribe(7, true);
    assert!(link.snapshot().ready());
    link.suspend(7, true);
    assert!(!link.snapshot().ready());
    assert!(link.volume(7, Some(pct(50)), 30)); // HID suspend doesn't disable volume.
    link.authenticate(7, false);
    assert!(link.drain().1.volume.is_none());
}
#[test]
fn hid_send_rechecks_session_even_when_handle_is_reused() {
    use headphone_stand::connection::Connection;
    let mut link = Connection::default();
    link.connect(7);
    link.authenticate(7, true);
    link.subscribe(7, true);
    let old = link.snapshot().session;
    assert_eq!(link.hid_handle(old), Some(7));
    link.disconnect(7);
    link.connect(7);
    link.authenticate(7, true);
    link.subscribe(7, true);
    assert_eq!(link.hid_handle(old), None);
    assert_eq!(link.hid_handle(link.snapshot().session), Some(7));
}
