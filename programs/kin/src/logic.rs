use anchor_lang::prelude::*;
use anchor_lang::solana_program::hash::hashv;

use crate::state::MAX_MEMBERS;

/// Token-2022 program id. Seeker Genesis Tokens are Token-2022 tokens.
pub const TOKEN_2022_ID: Pubkey = pubkey!("TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb");

const SLOT_HASHES_HEADER: usize = 8;
const SLOT_HASHES_ENTRY: usize = 8 + 32;

/// Reads the most recent (slot, hash) pair from the SlotHashes sysvar account data.
/// Layout: u64 entry count, then repeated (u64 slot, [u8; 32] hash), newest first.
pub fn latest_slot_hash(data: &[u8]) -> Option<(u64, [u8; 32])> {
    if data.len() < SLOT_HASHES_HEADER + SLOT_HASHES_ENTRY {
        return None;
    }
    let count = u64::from_le_bytes(data[0..8].try_into().ok()?);
    if count == 0 {
        return None;
    }
    let slot = u64::from_le_bytes(data[8..16].try_into().ok()?);
    let hash: [u8; 32] = data[16..48].try_into().ok()?;
    Some((slot, hash))
}

/// The seed for a draw: bound to the slot hash, the circle and the member count.
pub fn draw_seed(slot_hash: &[u8; 32], circle: &Pubkey, member_count: u8) -> [u8; 32] {
    hashv(&[slot_hash, circle.as_ref(), &[member_count]]).to_bytes()
}

/// Deterministic Fisher-Yates shuffle of `0..n`. Anyone can recompute it from `seed` and `n`.
pub fn draw_order(seed: &[u8; 32], n: usize) -> [u8; MAX_MEMBERS as usize] {
    let mut order = [0u8; MAX_MEMBERS as usize];
    for (i, slot) in order.iter_mut().enumerate().take(n) {
        *slot = i as u8;
    }
    for i in (1..n).rev() {
        let h = hashv(&[seed, &[i as u8]]).to_bytes();
        let r = u64::from_le_bytes(h[0..8].try_into().unwrap());
        let j = (r % (i as u64 + 1)) as usize;
        order.swap(i, j);
    }
    order
}

/// Identity order: members are paid in the order they joined.
pub fn join_order(n: usize) -> [u8; MAX_MEMBERS as usize] {
    let mut order = [0u8; MAX_MEMBERS as usize];
    for (i, slot) in order.iter_mut().enumerate().take(n) {
        *slot = i as u8;
    }
    order
}

/// Checks that `token_data` is a Token-2022 token account owned by `wallet` holding exactly one
/// token of `mint`, and that `mint_data` is a mint whose mint authority is `authority`.
/// Both layouts start with the same base fields as the classic token program.
pub fn is_valid_seeker_token(
    wallet: &Pubkey,
    mint_key: &Pubkey,
    authority: &Pubkey,
    token_data: &[u8],
    mint_data: &[u8],
) -> bool {
    // Token account base layout: mint(32) owner(32) amount(8) ...
    if token_data.len() < 72 || mint_data.len() < 36 {
        return false;
    }
    let token_mint = Pubkey::new_from_array(token_data[0..32].try_into().unwrap());
    let token_owner = Pubkey::new_from_array(token_data[32..64].try_into().unwrap());
    let amount = u64::from_le_bytes(token_data[64..72].try_into().unwrap());
    // Mint base layout: COption<Pubkey> mint_authority (4-byte tag + 32 bytes) ...
    let tag = u32::from_le_bytes(mint_data[0..4].try_into().unwrap());
    let mint_authority = Pubkey::new_from_array(mint_data[4..36].try_into().unwrap());

    token_mint == *mint_key
        && token_owner == *wallet
        && amount == 1
        && tag == 1
        && mint_authority == *authority
}

#[cfg(test)]
mod tests {
    use super::*;

    fn seed(b: u8) -> [u8; 32] {
        [b; 32]
    }

    #[test]
    fn draw_is_a_permutation_for_every_size() {
        for n in 2..=MAX_MEMBERS as usize {
            for b in 0..40u8 {
                let order = draw_order(&seed(b), n);
                let mut seen = vec![false; n];
                for &m in order.iter().take(n) {
                    assert!((m as usize) < n, "member index out of range");
                    assert!(!seen[m as usize], "duplicate member in order");
                    seen[m as usize] = true;
                }
                assert!(seen.iter().all(|s| *s), "missing member in order");
                assert!(order.iter().skip(n).all(|m| *m == 0), "unused slots must stay zero");
            }
        }
    }

    #[test]
    fn draw_is_deterministic_and_seed_sensitive() {
        assert_eq!(draw_order(&seed(1), 8), draw_order(&seed(1), 8));
        let distinct: std::collections::HashSet<_> = (0..40u8).map(|b| draw_order(&seed(b), 8)).collect();
        assert!(distinct.len() > 30, "different seeds should give mostly different orders");
    }

    #[test]
    fn every_position_is_reachable() {
        // Over many seeds, each member should land in each position at least once for a small circle.
        let n = 4;
        let mut counts = [[0u32; 4]; 4];
        for b in 0..=255u8 {
            let order = draw_order(&seed(b), n);
            for (pos, &m) in order.iter().take(n).enumerate() {
                counts[m as usize][pos] += 1;
            }
        }
        for row in counts.iter() {
            for &c in row.iter() {
                assert!(c > 20, "position distribution looks badly skewed: {counts:?}");
            }
        }
    }

    #[test]
    fn join_order_is_identity() {
        assert_eq!(&join_order(3)[..4], &[0, 1, 2, 0]);
    }

    #[test]
    fn slot_hash_parsing() {
        let mut data = vec![0u8; 8 + 40 * 2];
        data[0..8].copy_from_slice(&2u64.to_le_bytes());
        data[8..16].copy_from_slice(&777u64.to_le_bytes());
        data[16..48].copy_from_slice(&[9u8; 32]);
        assert_eq!(latest_slot_hash(&data), Some((777, [9u8; 32])));
        assert_eq!(latest_slot_hash(&[0u8; 8]), None);
        let mut empty = vec![0u8; 48];
        empty[0..8].copy_from_slice(&0u64.to_le_bytes());
        assert_eq!(latest_slot_hash(&empty), None);
    }

    fn token_bytes(mint: &Pubkey, owner: &Pubkey, amount: u64) -> Vec<u8> {
        let mut d = vec![0u8; 165];
        d[0..32].copy_from_slice(mint.as_ref());
        d[32..64].copy_from_slice(owner.as_ref());
        d[64..72].copy_from_slice(&amount.to_le_bytes());
        d
    }

    fn mint_bytes(authority: Option<&Pubkey>) -> Vec<u8> {
        let mut d = vec![0u8; 82];
        if let Some(a) = authority {
            d[0..4].copy_from_slice(&1u32.to_le_bytes());
            d[4..36].copy_from_slice(a.as_ref());
        }
        d
    }

    #[test]
    fn seeker_token_check_accepts_only_the_exact_case() {
        let wallet = Pubkey::new_unique();
        let mint = Pubkey::new_unique();
        let authority = Pubkey::new_unique();
        let good_token = token_bytes(&mint, &wallet, 1);
        let good_mint = mint_bytes(Some(&authority));
        assert!(is_valid_seeker_token(&wallet, &mint, &authority, &good_token, &good_mint));

        let other = Pubkey::new_unique();
        assert!(!is_valid_seeker_token(&other, &mint, &authority, &good_token, &good_mint), "wrong owner");
        assert!(!is_valid_seeker_token(&wallet, &other, &authority, &good_token, &good_mint), "wrong mint");
        assert!(!is_valid_seeker_token(&wallet, &mint, &other, &good_token, &good_mint), "wrong authority");
        assert!(!is_valid_seeker_token(&wallet, &mint, &authority, &token_bytes(&mint, &wallet, 0), &good_mint), "empty balance");
        assert!(!is_valid_seeker_token(&wallet, &mint, &authority, &token_bytes(&mint, &wallet, 2), &good_mint), "not one token");
        assert!(!is_valid_seeker_token(&wallet, &mint, &authority, &good_token, &mint_bytes(None)), "no mint authority");
        assert!(!is_valid_seeker_token(&wallet, &mint, &authority, &good_token[..40], &good_mint), "truncated token");
    }
}
