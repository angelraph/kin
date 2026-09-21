use anchor_lang::prelude::*;

pub const MAX_MEMBERS: u8 = 12;
pub const MAX_NAME_LEN: usize = 32;
/// Shortest round the program accepts. Short enough to demo on devnet, long enough to be sane.
pub const MIN_PERIOD_SECS: i64 = 10;
/// If an open circle does not fill within this window, members can take their bond back.
pub const JOIN_WINDOW_SECS: i64 = 14 * 24 * 60 * 60;
/// Safety cap on the size of one round's pot, in base units of the circle's mint
/// (1_000_000_000 = 1,000 USDC at 6 decimals). Keeps early mainnet exposure small.
pub const MAX_POT_BASE_UNITS: u64 = 1_000_000_000;

/// The one delegate authority every member approves for autopay. Seeds: ["autopay"].
pub const AUTOPAY_SEED: &[u8] = b"autopay";

#[derive(AnchorSerialize, AnchorDeserialize, Clone, Copy, PartialEq, Eq, InitSpace)]
pub enum CircleStatus {
    /// Waiting for members to join and lock their bonds.
    Open,
    /// Full; rounds are running.
    Active,
    /// Every member has received a payout; bonds can be claimed.
    Completed,
}

#[account]
#[derive(InitSpace)]
pub struct Circle {
    pub creator: Pubkey,
    pub circle_id: u64,
    pub mint: Pubkey,
    /// Amount each member pays per round.
    pub contribution: u64,
    /// Amount each member locks on joining. Covers missed contributions.
    pub bond: u64,
    pub period_secs: i64,
    pub grace_secs: i64,
    pub max_members: u8,
    pub member_count: u8,
    /// A wallet whose lifetime missed count exceeds this cannot join.
    pub max_missed_allowed: u32,
    pub status: CircleStatus,
    /// Index into `payout_order`: the round currently being collected.
    pub current_round: u8,
    pub round_start_ts: i64,
    /// Members whose contribution for the current round is paid or covered.
    pub resolved_count: u8,
    pub round_pot: u64,
    pub created_ts: i64,
    pub bump: u8,
    pub vault_bump: u8,
    pub bond_vault_bump: u8,
    /// When true, payout order is drawn from on-chain randomness when the circle fills.
    pub randomize: bool,
    /// When true, joining requires proof of a Seeker Genesis Token.
    pub seeker_only: bool,
    /// Mint authority that a valid Seeker Genesis Token must have. Only read when `seeker_only`.
    pub seeker_authority: Pubkey,
    /// Slot whose hash seeded the draw. Zero when the order was not randomized.
    pub order_slot: u64,
    /// Seed of the draw. Anyone can recompute `payout_order` from it.
    pub order_seed: [u8; 32],
    /// `payout_order[round]` is the member index paid in that round.
    pub payout_order: [u8; MAX_MEMBERS as usize],
    #[max_len(MAX_NAME_LEN)]
    pub name: String,
}

#[account]
#[derive(InitSpace)]
pub struct Member {
    pub circle: Pubkey,
    pub wallet: Pubkey,
    /// Join order. Which round pays this member is decided by `Circle::payout_order`.
    pub index: u8,
    pub bond_locked: u64,
    pub bond_used: u64,
    /// Rounds for which this member's contribution is paid or covered.
    pub rounds_resolved: u8,
    pub received: bool,
    pub bond_claimed: bool,
    pub on_time: u16,
    pub late: u16,
    pub missed: u16,
    pub bump: u8,
}

/// Portable reliability record for a wallet, shared across every circle it joins.
#[account]
#[derive(InitSpace)]
pub struct KinScore {
    pub wallet: Pubkey,
    pub on_time: u32,
    pub late: u32,
    pub missed: u32,
    pub circles_completed: u32,
    pub streak: u32,
    pub best_streak: u32,
    pub bump: u8,
}
