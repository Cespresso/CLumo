//! Minimal single-question captive portal DNS responder.
pub fn response(packet: &[u8], address: [u8; 4]) -> Option<Vec<u8>> {
    if packet.len() < 12 || packet.len() > 512 || packet[2] & 0xf8 != 0 || packet[4..6] != [0, 1] {
        return None;
    }
    let mut end = 12;
    loop {
        let len = *packet.get(end)? as usize;
        if len > 63 {
            return None;
        } // Reject compression pointers and reserved label types.
        end += 1;
        if len == 0 {
            break;
        }
        end = end.checked_add(len)?;
        if end >= packet.len() || end - 12 > 254 {
            return None;
        }
    }
    let tail = packet.get(end..end + 4)?;
    let answer = tail == [0, 1, 0, 1]; // IN / A only; AAAA gets a valid empty answer.
    end += 4;
    let mut reply = packet[..end].to_vec();
    reply[2] = 0x84 | (packet[2] & 1); // Response, authoritative, preserve RD.
    reply[3] = 0; // No recursion or authenticated-data claim.
    reply[6..12].copy_from_slice(&[0, u8::from(answer), 0, 0, 0, 0]);
    if answer {
        reply.extend_from_slice(&[0xc0, 12, 0, 1, 0, 1, 0, 0, 0, 0, 0, 4]);
        reply.extend_from_slice(&address);
    }
    Some(reply)
}
#[cfg(test)]
mod tests {
    use super::*;
    fn query(kind: u16) -> Vec<u8> {
        let mut p = vec![0x12, 0x34, 0x01, 0x20, 0, 1, 0, 0, 0, 0, 0, 0];
        p.extend_from_slice(b"\x07example\x03com\x00");
        p.extend_from_slice(&kind.to_be_bytes());
        p.extend_from_slice(&[0, 1]);
        p
    }
    #[test]
    fn ipv4_answer_preserves_question_and_returns_local_address() {
        let q = query(1);
        let r = response(&q, [192, 168, 71, 1]).unwrap();
        assert_eq!(&r[..4], &[0x12, 0x34, 0x85, 0]);
        assert_eq!(&r[4..12], &[0, 1, 0, 1, 0, 0, 0, 0]);
        assert_eq!(&r[12..q.len()], &q[12..]);
        assert_eq!(
            &r[q.len()..],
            &[0xc0, 12, 0, 1, 0, 1, 0, 0, 0, 0, 0, 4, 192, 168, 71, 1]
        );
    }
    #[test]
    fn ipv6_has_no_fake_ipv4_record() {
        let q = query(28);
        let r = response(&q, [1, 2, 3, 4]).unwrap();
        assert_eq!(r.len(), q.len());
        assert_eq!(&r[6..8], &[0, 0]);
    }
    #[test]
    fn edns_is_removed_before_answer() {
        let mut q = query(1);
        let question_len = q.len();
        q[11] = 1;
        q.extend_from_slice(&[0, 0, 41, 4, 208, 0, 0, 0, 0, 0, 0]);
        let r = response(&q, [192, 168, 71, 1]).unwrap();
        assert_eq!(r.len(), question_len + 16);
        assert_eq!(&r[10..12], &[0, 0]);
        assert_eq!(&r[question_len..question_len + 2], &[0xc0, 12]);
    }
    #[test]
    fn malformed_and_non_queries_are_ignored() {
        let q = query(1);
        for len in 0..q.len() {
            assert!(response(&q[..len], [0; 4]).is_none());
        }
        for (index, value) in [(2, 0x81), (2, 0x09), (5, 2), (12, 0xc0), (12, 64)] {
            let mut bad = q.clone();
            bad[index] = value;
            assert!(response(&bad, [0; 4]).is_none());
        }
    }
}
