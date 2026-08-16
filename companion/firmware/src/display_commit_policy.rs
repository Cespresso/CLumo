/// Legacy v2 clients never announce explicit preview commits, so keep their
/// DISPLAY writes durable.
pub fn should_auto_commit_display(explicit_commit_capable: bool) -> bool {
    !explicit_commit_capable
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn legacy_clients_auto_commit_until_explicit_handshake() {
        assert!(should_auto_commit_display(false));
        assert!(!should_auto_commit_display(true));
    }
}
