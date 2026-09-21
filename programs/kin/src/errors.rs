use anchor_lang::prelude::*;

#[error_code]
pub enum KinError {
    #[msg("Circle must have between 2 and 12 members")]
    InvalidMemberCount,
    #[msg("Contribution must be greater than zero")]
    InvalidContribution,
    #[msg("Bond must be at least one contribution and at most one contribution per member")]
    InvalidBond,
    #[msg("Round period is too short")]
    PeriodTooShort,
    #[msg("Grace period cannot be longer than the round period")]
    GraceTooLong,
    #[msg("Round pot exceeds the safety cap")]
    PotTooLarge,
    #[msg("Circle name is too long")]
    NameTooLong,
    #[msg("Circle is not open for joining")]
    NotOpen,
    #[msg("Circle is full")]
    CircleFull,
    #[msg("Circle is not active")]
    NotActive,
    #[msg("Circle is not completed")]
    NotCompleted,
    #[msg("Wallet's missed-payment history exceeds this circle's limit")]
    ScoreTooLow,
    #[msg("Already paid or covered for this round")]
    AlreadyResolved,
    #[msg("The round has not started yet")]
    RoundNotStarted,
    #[msg("The payment window and grace period have passed")]
    WindowClosed,
    #[msg("The payment window and grace period are still open")]
    WindowStillOpen,
    #[msg("Not every member has paid or been covered for this round")]
    RoundNotResolved,
    #[msg("The round period has not ended yet")]
    RoundNotEnded,
    #[msg("Wrong recipient for this round")]
    WrongRecipient,
    #[msg("Bond already claimed")]
    BondAlreadyClaimed,
    #[msg("The join window has not expired")]
    JoinWindowOpen,
    #[msg("Member does not belong to this circle")]
    WrongCircle,
    #[msg("Autopay is not approved on this token account")]
    NotDelegated,
    #[msg("The autopay allowance is smaller than one contribution")]
    AllowanceTooLow,
    #[msg("This circle requires a valid Seeker Genesis Token")]
    SeekerRequired,
    #[msg("Recent slot hashes are unavailable")]
    SlotHashesUnavailable,
    #[msg("Arithmetic overflow")]
    Overflow,
}
