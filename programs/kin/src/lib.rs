use anchor_lang::prelude::*;
use anchor_lang::solana_program::program_option::COption;
use anchor_lang::solana_program::sysvar;
use anchor_spl::associated_token::AssociatedToken;
use anchor_spl::token::{self, Mint, Token, TokenAccount, Transfer};

pub mod errors;
pub mod events;
pub mod logic;
pub mod state;

use errors::KinError;
use events::*;
use logic::*;
use state::*;

declare_id!("7CGtKBZVMKgWJRg92SmRiQkeQe8hvTV3SWrHRfsdSMWe");

#[program]
pub mod kin {
    use super::*;

    /// Create an empty circle. Members (including the creator) join afterwards.
    #[allow(clippy::too_many_arguments)]
    pub fn create_circle(
        ctx: Context<CreateCircle>,
        circle_id: u64,
        name: String,
        contribution: u64,
        bond: u64,
        period_secs: i64,
        grace_secs: i64,
        max_members: u8,
        max_missed_allowed: u32,
        randomize: bool,
        seeker_only: bool,
        seeker_authority: Pubkey,
    ) -> Result<()> {
        require!((2..=MAX_MEMBERS).contains(&max_members), KinError::InvalidMemberCount);
        require!(contribution > 0, KinError::InvalidContribution);
        require!(name.len() <= MAX_NAME_LEN, KinError::NameTooLong);
        require!(period_secs >= MIN_PERIOD_SECS, KinError::PeriodTooShort);
        require!(grace_secs >= 0 && grace_secs <= period_secs, KinError::GraceTooLong);
        let max_bond = contribution
            .checked_mul(max_members as u64)
            .ok_or(KinError::Overflow)?;
        require!(bond >= contribution && bond <= max_bond, KinError::InvalidBond);
        require!(max_bond <= MAX_POT_BASE_UNITS, KinError::PotTooLarge);

        let now = Clock::get()?.unix_timestamp;
        let circle = &mut ctx.accounts.circle;
        circle.creator = ctx.accounts.creator.key();
        circle.circle_id = circle_id;
        circle.mint = ctx.accounts.mint.key();
        circle.contribution = contribution;
        circle.bond = bond;
        circle.period_secs = period_secs;
        circle.grace_secs = grace_secs;
        circle.max_members = max_members;
        circle.member_count = 0;
        circle.max_missed_allowed = max_missed_allowed;
        circle.status = CircleStatus::Open;
        circle.current_round = 0;
        circle.round_start_ts = 0;
        circle.resolved_count = 0;
        circle.round_pot = 0;
        circle.created_ts = now;
        circle.bump = ctx.bumps.circle;
        circle.vault_bump = ctx.bumps.vault;
        circle.bond_vault_bump = ctx.bumps.bond_vault;
        circle.randomize = randomize;
        circle.seeker_only = seeker_only;
        circle.seeker_authority = seeker_authority;
        circle.order_slot = 0;
        circle.order_seed = [0u8; 32];
        circle.payout_order = [0u8; MAX_MEMBERS as usize];
        circle.name = name;
        emit!(CircleCreated {
            circle: circle.key(),
            creator: circle.creator,
            mint: circle.mint,
            contribution,
            bond,
            period_secs,
            grace_secs,
            max_members,
            randomize,
            seeker_only,
        });
        Ok(())
    }

    /// Lock the bond and take a seat. The circle starts, and the payout order is fixed,
    /// when the last seat fills.
    pub fn join_circle(ctx: Context<JoinCircle>) -> Result<()> {
        let now = Clock::get()?.unix_timestamp;
        let circle = &mut ctx.accounts.circle;
        require!(circle.status == CircleStatus::Open, KinError::NotOpen);
        require!(circle.member_count < circle.max_members, KinError::CircleFull);

        if circle.seeker_only {
            let token = ctx.accounts.sgt_token.as_ref().ok_or(KinError::SeekerRequired)?;
            let mint = ctx.accounts.sgt_mint.as_ref().ok_or(KinError::SeekerRequired)?;
            let (token_info, mint_info) = (token.to_account_info(), mint.to_account_info());
            require!(
                *token_info.owner == TOKEN_2022_ID && *mint_info.owner == TOKEN_2022_ID,
                KinError::SeekerRequired
            );
            let token_data = token_info.try_borrow_data()?;
            let mint_data = mint_info.try_borrow_data()?;
            require!(
                is_valid_seeker_token(
                    &ctx.accounts.wallet.key(),
                    &mint_info.key(),
                    &circle.seeker_authority,
                    &token_data,
                    &mint_data,
                ),
                KinError::SeekerRequired
            );
        }

        let score = &mut ctx.accounts.score;
        init_score_if_new(score, ctx.accounts.wallet.key(), ctx.bumps.score);
        require!(score.missed <= circle.max_missed_allowed, KinError::ScoreTooLow);

        token::transfer(
            CpiContext::new(
                ctx.accounts.token_program.to_account_info(),
                Transfer {
                    from: ctx.accounts.wallet_token.to_account_info(),
                    to: ctx.accounts.bond_vault.to_account_info(),
                    authority: ctx.accounts.wallet.to_account_info(),
                },
            ),
            circle.bond,
        )?;

        let member = &mut ctx.accounts.member;
        member.circle = circle.key();
        member.wallet = ctx.accounts.wallet.key();
        member.index = circle.member_count;
        member.bond_locked = circle.bond;
        member.bond_used = 0;
        member.rounds_resolved = 0;
        member.received = false;
        member.bond_claimed = false;
        member.on_time = 0;
        member.late = 0;
        member.missed = 0;
        member.bump = ctx.bumps.member;

        circle.member_count = circle.member_count.checked_add(1).ok_or(KinError::Overflow)?;
        let started = circle.member_count == circle.max_members;
        if started {
            let n = circle.member_count as usize;
            if circle.randomize {
                let data = ctx.accounts.slot_hashes.try_borrow_data()?;
                let (slot, hash) = latest_slot_hash(&data).ok_or(KinError::SlotHashesUnavailable)?;
                let seed = draw_seed(&hash, &circle.key(), circle.member_count);
                circle.order_slot = slot;
                circle.order_seed = seed;
                circle.payout_order = draw_order(&seed, n);
            } else {
                circle.payout_order = join_order(n);
            }
            circle.status = CircleStatus::Active;
            circle.round_start_ts = now;
            emit!(OrderDrawn {
                circle: circle.key(),
                randomized: circle.randomize,
                slot: circle.order_slot,
                seed: circle.order_seed,
                order: circle.payout_order,
            });
        }
        emit!(MemberJoined {
            circle: circle.key(),
            wallet: member.wallet,
            index: member.index,
            bond: circle.bond,
            started,
        });
        Ok(())
    }

    /// Pay this round's contribution. On time = inside the period; late = inside the grace window.
    pub fn contribute(ctx: Context<Contribute>) -> Result<()> {
        let now = Clock::get()?.unix_timestamp;
        let circle = &mut ctx.accounts.circle;
        let member = &mut ctx.accounts.member;
        let period_end = open_payment_window(circle, member, now)?;

        token::transfer(
            CpiContext::new(
                ctx.accounts.token_program.to_account_info(),
                Transfer {
                    from: ctx.accounts.wallet_token.to_account_info(),
                    to: ctx.accounts.vault.to_account_info(),
                    authority: ctx.accounts.wallet.to_account_info(),
                },
            ),
            circle.contribution,
        )?;

        settle_payment(circle, member, &mut ctx.accounts.score, now <= period_end, false)
    }

    /// Anyone can collect a member's contribution if that member approved the Kin autopay
    /// allowance on their token account. Money can only move from the member's own token account
    /// into this circle's vault, at most one contribution per round.
    /// Because the member authorised it in advance and the collector controls timing,
    /// a collection inside the payment window always counts as on time.
    pub fn collect(ctx: Context<Collect>) -> Result<()> {
        let now = Clock::get()?.unix_timestamp;
        let circle = &mut ctx.accounts.circle;
        let member = &mut ctx.accounts.member;
        open_payment_window(circle, member, now)?;

        let source = &ctx.accounts.member_token;
        require!(source.delegate == COption::Some(ctx.accounts.autopay.key()), KinError::NotDelegated);
        require!(source.delegated_amount >= circle.contribution, KinError::AllowanceTooLow);

        let bump = [ctx.bumps.autopay];
        let seeds: &[&[u8]] = &[AUTOPAY_SEED, &bump];
        token::transfer(
            CpiContext::new_with_signer(
                ctx.accounts.token_program.to_account_info(),
                Transfer {
                    from: ctx.accounts.member_token.to_account_info(),
                    to: ctx.accounts.vault.to_account_info(),
                    authority: ctx.accounts.autopay.to_account_info(),
                },
                &[seeds],
            ),
            circle.contribution,
        )?;

        settle_payment(circle, member, &mut ctx.accounts.score, true, true)
    }

    /// Anyone can call this once a member's payment window and grace have passed.
    /// The member's bond pays their share so the round still completes; their score takes the hit.
    pub fn cover_missed(ctx: Context<CoverMissed>) -> Result<()> {
        let now = Clock::get()?.unix_timestamp;
        let circle = &mut ctx.accounts.circle;
        let member = &mut ctx.accounts.member;
        require!(circle.status == CircleStatus::Active, KinError::NotActive);
        require!(member.circle == circle.key(), KinError::WrongCircle);
        require!(member.rounds_resolved == circle.current_round, KinError::AlreadyResolved);
        let window_end = circle
            .round_start_ts
            .checked_add(circle.period_secs)
            .and_then(|t| t.checked_add(circle.grace_secs))
            .ok_or(KinError::Overflow)?;
        require!(now > window_end, KinError::WindowStillOpen);

        let remaining = member
            .bond_locked
            .checked_sub(member.bond_used)
            .ok_or(KinError::Overflow)?;
        let cover = remaining.min(circle.contribution);
        if cover > 0 {
            release_from_bond_vault(
                circle,
                &ctx.accounts.bond_vault,
                &ctx.accounts.vault,
                &ctx.accounts.token_program,
                cover,
            )?;
            member.bond_used = member.bond_used.checked_add(cover).ok_or(KinError::Overflow)?;
            circle.round_pot = circle.round_pot.checked_add(cover).ok_or(KinError::Overflow)?;
        }

        emit!(MissCovered {
            circle: circle.key(),
            wallet: member.wallet,
            round: circle.current_round,
            covered: cover,
            shortfall: circle.contribution.saturating_sub(cover),
        });
        member.missed = member.missed.saturating_add(1);
        let score = &mut ctx.accounts.score;
        score.missed = score.missed.saturating_add(1);
        score.streak = 0;

        member.rounds_resolved = member.rounds_resolved.checked_add(1).ok_or(KinError::Overflow)?;
        circle.resolved_count = circle.resolved_count.checked_add(1).ok_or(KinError::Overflow)?;
        Ok(())
    }

    /// Anyone can crank the payout once the period has ended and every member is resolved.
    /// The pot goes straight to the round's recipient.
    pub fn payout(ctx: Context<Payout>) -> Result<()> {
        let now = Clock::get()?.unix_timestamp;
        let circle = &mut ctx.accounts.circle;
        let recipient = &mut ctx.accounts.recipient_member;
        require!(circle.status == CircleStatus::Active, KinError::NotActive);
        require!(recipient.circle == circle.key(), KinError::WrongCircle);
        require!(
            recipient.index == circle.payout_order[circle.current_round as usize],
            KinError::WrongRecipient
        );
        require!(circle.resolved_count == circle.member_count, KinError::RoundNotResolved);
        let period_end = circle
            .round_start_ts
            .checked_add(circle.period_secs)
            .ok_or(KinError::Overflow)?;
        require!(now >= period_end, KinError::RoundNotEnded);

        let amount = circle.round_pot;
        if amount > 0 {
            release_from_vault(
                circle,
                &ctx.accounts.vault,
                &ctx.accounts.recipient_token,
                &ctx.accounts.token_program,
                amount,
            )?;
        }

        let round = circle.current_round;
        recipient.received = true;
        circle.round_pot = 0;
        circle.resolved_count = 0;
        circle.current_round = circle.current_round.checked_add(1).ok_or(KinError::Overflow)?;
        circle.round_start_ts = now;
        if circle.current_round == circle.member_count {
            circle.status = CircleStatus::Completed;
        }
        emit!(PaidOut {
            circle: circle.key(),
            recipient: recipient.wallet,
            round,
            amount,
            completed: circle.status == CircleStatus::Completed,
        });
        Ok(())
    }

    /// After the circle completes, each member reclaims whatever bond was not used to cover misses.
    pub fn claim_bond(ctx: Context<ClaimBond>) -> Result<()> {
        let circle = &ctx.accounts.circle;
        let member = &mut ctx.accounts.member;
        require!(circle.status == CircleStatus::Completed, KinError::NotCompleted);
        require!(!member.bond_claimed, KinError::BondAlreadyClaimed);

        let amount = member
            .bond_locked
            .checked_sub(member.bond_used)
            .ok_or(KinError::Overflow)?;
        member.bond_claimed = true;
        if member.missed == 0 {
            let score = &mut ctx.accounts.score;
            score.circles_completed = score.circles_completed.saturating_add(1);
        }
        if amount > 0 {
            release_from_bond_vault(
                circle,
                &ctx.accounts.bond_vault,
                &ctx.accounts.wallet_token,
                &ctx.accounts.token_program,
                amount,
            )?;
        }
        emit!(BondReturned {
            circle: circle.key(),
            wallet: member.wallet,
            amount,
            refunded_open: false,
        });
        Ok(())
    }

    /// If an open circle never filled within the join window, members take their bond back.
    pub fn refund_open(ctx: Context<ClaimBond>) -> Result<()> {
        let now = Clock::get()?.unix_timestamp;
        let circle = &ctx.accounts.circle;
        let member = &mut ctx.accounts.member;
        require!(circle.status == CircleStatus::Open, KinError::NotOpen);
        let expiry = circle
            .created_ts
            .checked_add(JOIN_WINDOW_SECS)
            .ok_or(KinError::Overflow)?;
        require!(now > expiry, KinError::JoinWindowOpen);
        require!(!member.bond_claimed, KinError::BondAlreadyClaimed);

        member.bond_claimed = true;
        release_from_bond_vault(
            circle,
            &ctx.accounts.bond_vault,
            &ctx.accounts.wallet_token,
            &ctx.accounts.token_program,
            member.bond_locked,
        )?;
        emit!(BondReturned {
            circle: circle.key(),
            wallet: member.wallet,
            amount: member.bond_locked,
            refunded_open: true,
        });
        Ok(())
    }
}

fn init_score_if_new(score: &mut Account<KinScore>, wallet: Pubkey, bump: u8) {
    if score.wallet == Pubkey::default() {
        score.wallet = wallet;
        score.bump = bump;
    }
}

/// Checks a payment is allowed right now and returns the end of the on-time period.
fn open_payment_window(circle: &Account<Circle>, member: &Account<Member>, now: i64) -> Result<i64> {
    require!(circle.status == CircleStatus::Active, KinError::NotActive);
    require!(member.circle == circle.key(), KinError::WrongCircle);
    require!(member.rounds_resolved == circle.current_round, KinError::AlreadyResolved);
    require!(now >= circle.round_start_ts, KinError::RoundNotStarted);
    let period_end = circle
        .round_start_ts
        .checked_add(circle.period_secs)
        .ok_or(KinError::Overflow)?;
    let window_end = period_end.checked_add(circle.grace_secs).ok_or(KinError::Overflow)?;
    require!(now <= window_end, KinError::WindowClosed);
    Ok(period_end)
}

/// Records a payment on the circle, the member and the wallet's Kin Score, and emits its event.
fn settle_payment(
    circle: &mut Account<Circle>,
    member: &mut Account<Member>,
    score: &mut Account<KinScore>,
    on_time: bool,
    autopay: bool,
) -> Result<()> {
    emit!(Contributed {
        circle: circle.key(),
        wallet: member.wallet,
        round: circle.current_round,
        amount: circle.contribution,
        on_time,
        autopay,
    });
    if on_time {
        member.on_time = member.on_time.saturating_add(1);
        score.on_time = score.on_time.saturating_add(1);
        score.streak = score.streak.saturating_add(1);
        score.best_streak = score.best_streak.max(score.streak);
    } else {
        member.late = member.late.saturating_add(1);
        score.late = score.late.saturating_add(1);
        score.streak = 0;
    }
    member.rounds_resolved = member.rounds_resolved.checked_add(1).ok_or(KinError::Overflow)?;
    circle.resolved_count = circle.resolved_count.checked_add(1).ok_or(KinError::Overflow)?;
    circle.round_pot = circle
        .round_pot
        .checked_add(circle.contribution)
        .ok_or(KinError::Overflow)?;
    Ok(())
}

fn circle_seeds(circle: &Circle) -> ([u8; 32], [u8; 8], [u8; 1]) {
    (circle.creator.to_bytes(), circle.circle_id.to_le_bytes(), [circle.bump])
}

fn release_from_vault<'info>(
    circle: &Account<'info, Circle>,
    from: &Account<'info, TokenAccount>,
    to: &Account<'info, TokenAccount>,
    token_program: &Program<'info, Token>,
    amount: u64,
) -> Result<()> {
    let (creator, id, bump) = circle_seeds(circle);
    let seeds: &[&[u8]] = &[b"circle", &creator, &id, &bump];
    token::transfer(
        CpiContext::new_with_signer(
            token_program.to_account_info(),
            Transfer {
                from: from.to_account_info(),
                to: to.to_account_info(),
                authority: circle.to_account_info(),
            },
            &[seeds],
        ),
        amount,
    )
}

fn release_from_bond_vault<'info>(
    circle: &Account<'info, Circle>,
    bond_vault: &Account<'info, TokenAccount>,
    to: &Account<'info, TokenAccount>,
    token_program: &Program<'info, Token>,
    amount: u64,
) -> Result<()> {
    release_from_vault(circle, bond_vault, to, token_program, amount)
}

#[derive(Accounts)]
#[instruction(circle_id: u64)]
pub struct CreateCircle<'info> {
    #[account(mut)]
    pub creator: Signer<'info>,
    pub mint: Account<'info, Mint>,
    #[account(
        init,
        payer = creator,
        space = 8 + Circle::INIT_SPACE,
        seeds = [b"circle", creator.key().as_ref(), &circle_id.to_le_bytes()],
        bump
    )]
    pub circle: Account<'info, Circle>,
    #[account(
        init,
        payer = creator,
        seeds = [b"vault", circle.key().as_ref()],
        bump,
        token::mint = mint,
        token::authority = circle
    )]
    pub vault: Account<'info, TokenAccount>,
    #[account(
        init,
        payer = creator,
        seeds = [b"bond_vault", circle.key().as_ref()],
        bump,
        token::mint = mint,
        token::authority = circle
    )]
    pub bond_vault: Account<'info, TokenAccount>,
    pub token_program: Program<'info, Token>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct JoinCircle<'info> {
    #[account(mut)]
    pub wallet: Signer<'info>,
    #[account(mut)]
    pub circle: Account<'info, Circle>,
    #[account(
        init,
        payer = wallet,
        space = 8 + Member::INIT_SPACE,
        seeds = [b"member", circle.key().as_ref(), wallet.key().as_ref()],
        bump
    )]
    pub member: Account<'info, Member>,
    #[account(
        init_if_needed,
        payer = wallet,
        space = 8 + KinScore::INIT_SPACE,
        seeds = [b"score", wallet.key().as_ref()],
        bump
    )]
    pub score: Account<'info, KinScore>,
    #[account(
        mut,
        token::mint = circle.mint,
        token::authority = wallet
    )]
    pub wallet_token: Account<'info, TokenAccount>,
    #[account(
        mut,
        seeds = [b"bond_vault", circle.key().as_ref()],
        bump = circle.bond_vault_bump
    )]
    pub bond_vault: Account<'info, TokenAccount>,
    /// CHECK: the SlotHashes sysvar, read only when the circle fills with a randomized order.
    #[account(address = sysvar::slot_hashes::ID)]
    pub slot_hashes: UncheckedAccount<'info>,
    /// CHECK: Seeker Genesis Token account. Validated in `join_circle` when the circle is Seeker-only.
    pub sgt_token: Option<UncheckedAccount<'info>>,
    /// CHECK: Seeker Genesis Token mint. Validated in `join_circle` when the circle is Seeker-only.
    pub sgt_mint: Option<UncheckedAccount<'info>>,
    pub token_program: Program<'info, Token>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct Contribute<'info> {
    #[account(mut)]
    pub wallet: Signer<'info>,
    #[account(mut)]
    pub circle: Account<'info, Circle>,
    #[account(
        mut,
        seeds = [b"member", circle.key().as_ref(), wallet.key().as_ref()],
        bump = member.bump
    )]
    pub member: Account<'info, Member>,
    #[account(
        mut,
        seeds = [b"score", wallet.key().as_ref()],
        bump = score.bump
    )]
    pub score: Account<'info, KinScore>,
    #[account(
        mut,
        token::mint = circle.mint,
        token::authority = wallet
    )]
    pub wallet_token: Account<'info, TokenAccount>,
    #[account(
        mut,
        seeds = [b"vault", circle.key().as_ref()],
        bump = circle.vault_bump
    )]
    pub vault: Account<'info, TokenAccount>,
    pub token_program: Program<'info, Token>,
}

#[derive(Accounts)]
pub struct Collect<'info> {
    /// Whoever collects pays only the transaction fee.
    pub caller: Signer<'info>,
    #[account(mut)]
    pub circle: Account<'info, Circle>,
    #[account(
        mut,
        seeds = [b"member", circle.key().as_ref(), member.wallet.as_ref()],
        bump = member.bump
    )]
    pub member: Account<'info, Member>,
    #[account(
        mut,
        seeds = [b"score", member.wallet.as_ref()],
        bump = score.bump
    )]
    pub score: Account<'info, KinScore>,
    #[account(
        mut,
        token::mint = circle.mint,
        token::authority = member.wallet
    )]
    pub member_token: Account<'info, TokenAccount>,
    #[account(
        mut,
        seeds = [b"vault", circle.key().as_ref()],
        bump = circle.vault_bump
    )]
    pub vault: Account<'info, TokenAccount>,
    /// CHECK: PDA used only as the SPL token delegate. It holds no data and signs only inside `collect`.
    #[account(seeds = [AUTOPAY_SEED], bump)]
    pub autopay: UncheckedAccount<'info>,
    pub token_program: Program<'info, Token>,
}

#[derive(Accounts)]
pub struct CoverMissed<'info> {
    /// Whoever cranks the cover; pays no fee beyond the transaction itself.
    pub caller: Signer<'info>,
    #[account(mut)]
    pub circle: Account<'info, Circle>,
    #[account(
        mut,
        seeds = [b"member", circle.key().as_ref(), member.wallet.as_ref()],
        bump = member.bump
    )]
    pub member: Account<'info, Member>,
    #[account(
        mut,
        seeds = [b"score", member.wallet.as_ref()],
        bump = score.bump
    )]
    pub score: Account<'info, KinScore>,
    #[account(
        mut,
        seeds = [b"vault", circle.key().as_ref()],
        bump = circle.vault_bump
    )]
    pub vault: Account<'info, TokenAccount>,
    #[account(
        mut,
        seeds = [b"bond_vault", circle.key().as_ref()],
        bump = circle.bond_vault_bump
    )]
    pub bond_vault: Account<'info, TokenAccount>,
    pub token_program: Program<'info, Token>,
}

#[derive(Accounts)]
pub struct Payout<'info> {
    #[account(mut)]
    pub caller: Signer<'info>,
    #[account(mut)]
    pub circle: Account<'info, Circle>,
    pub mint: Account<'info, Mint>,
    #[account(
        mut,
        seeds = [b"member", circle.key().as_ref(), recipient_member.wallet.as_ref()],
        bump = recipient_member.bump
    )]
    pub recipient_member: Account<'info, Member>,
    /// CHECK: must equal the recipient member's wallet; used only as the token account owner.
    #[account(address = recipient_member.wallet)]
    pub recipient_wallet: UncheckedAccount<'info>,
    #[account(
        init_if_needed,
        payer = caller,
        associated_token::mint = mint,
        associated_token::authority = recipient_wallet
    )]
    pub recipient_token: Account<'info, TokenAccount>,
    #[account(
        mut,
        seeds = [b"vault", circle.key().as_ref()],
        bump = circle.vault_bump,
        token::mint = mint
    )]
    pub vault: Account<'info, TokenAccount>,
    pub token_program: Program<'info, Token>,
    pub associated_token_program: Program<'info, AssociatedToken>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct ClaimBond<'info> {
    #[account(mut)]
    pub wallet: Signer<'info>,
    pub circle: Account<'info, Circle>,
    #[account(
        mut,
        seeds = [b"member", circle.key().as_ref(), wallet.key().as_ref()],
        bump = member.bump
    )]
    pub member: Account<'info, Member>,
    #[account(
        mut,
        seeds = [b"score", wallet.key().as_ref()],
        bump = score.bump
    )]
    pub score: Account<'info, KinScore>,
    #[account(
        mut,
        token::mint = circle.mint,
        token::authority = wallet
    )]
    pub wallet_token: Account<'info, TokenAccount>,
    #[account(
        mut,
        seeds = [b"bond_vault", circle.key().as_ref()],
        bump = circle.bond_vault_bump
    )]
    pub bond_vault: Account<'info, TokenAccount>,
    pub token_program: Program<'info, Token>,
}
