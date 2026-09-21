use anchor_lang::prelude::*;

// Every state change emits one of these, so a circle's full history can be rebuilt from chain data alone.

#[event]
pub struct CircleCreated {
    pub circle: Pubkey,
    pub creator: Pubkey,
    pub mint: Pubkey,
    pub contribution: u64,
    pub bond: u64,
    pub period_secs: i64,
    pub grace_secs: i64,
    pub max_members: u8,
    pub randomize: bool,
    pub seeker_only: bool,
}

#[event]
pub struct MemberJoined {
    pub circle: Pubkey,
    pub wallet: Pubkey,
    pub index: u8,
    pub bond: u64,
    /// True when this join filled the circle and started round 0.
    pub started: bool,
}

/// Emitted once, when the circle fills and the payout order is fixed.
#[event]
pub struct OrderDrawn {
    pub circle: Pubkey,
    pub randomized: bool,
    pub slot: u64,
    pub seed: [u8; 32],
    pub order: [u8; 12],
}

#[event]
pub struct Contributed {
    pub circle: Pubkey,
    pub wallet: Pubkey,
    pub round: u8,
    pub amount: u64,
    pub on_time: bool,
    /// True when collected through the member's pre-approved autopay allowance.
    pub autopay: bool,
}

#[event]
pub struct MissCovered {
    pub circle: Pubkey,
    pub wallet: Pubkey,
    pub round: u8,
    /// Amount the bond contributed. Less than the contribution when the bond ran low.
    pub covered: u64,
    pub shortfall: u64,
}

#[event]
pub struct PaidOut {
    pub circle: Pubkey,
    pub recipient: Pubkey,
    pub round: u8,
    pub amount: u64,
    pub completed: bool,
}

#[event]
pub struct BondReturned {
    pub circle: Pubkey,
    pub wallet: Pubkey,
    pub amount: u64,
    /// True when returned because the circle never filled.
    pub refunded_open: bool,
}
