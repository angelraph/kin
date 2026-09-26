//! Test funds for Kin on devnet. This program exists only so that anyone opening the app can try it:
//! it is not part of Kin and is never deployed to mainnet.
//!
//! The test token's mint authority is a PDA of this program, so tokens can be handed out with no server
//! and no secret key. A per wallet cooldown limits how much one wallet can take.

use anchor_lang::prelude::*;
use anchor_lang::solana_program::program_option::COption;
use anchor_lang::system_program::{self, Transfer};
use anchor_spl::associated_token::AssociatedToken;
use anchor_spl::token::{self, Mint, MintTo, Token, TokenAccount};

declare_id!("G5MhE85BTiTqLPinKZBg7jh4sNMyTc7WUvGcfMerWsig");

/// 500 test tokens with 6 decimals.
pub const TOKENS_PER_CLAIM: u64 = 500_000_000;
/// 0.05 SOL, enough to create accounts and pay fees for a full circle.
pub const REFUEL_LAMPORTS: u64 = 50_000_000;
/// Wallets holding at least this much SOL are not topped up.
pub const REFUEL_BELOW: u64 = 30_000_000;
pub const COOLDOWN_SECS: i64 = 300;

#[program]
pub mod kin_faucet {
    use super::*;

    /// Sends a little SOL to wallets that are nearly empty. Does nothing for wallets that already have some,
    /// so it is safe to put in front of `claim` in the same transaction.
    pub fn refuel(ctx: Context<Refuel>) -> Result<()> {
        if ctx.accounts.user.lamports() >= REFUEL_BELOW {
            return Ok(());
        }
        require!(ctx.accounts.sol_vault.lamports() >= REFUEL_LAMPORTS, FaucetError::VaultEmpty);
        let seeds: &[&[u8]] = &[b"sol", &[ctx.bumps.sol_vault]];
        system_program::transfer(
            CpiContext::new_with_signer(
                ctx.accounts.system_program.to_account_info(),
                Transfer { from: ctx.accounts.sol_vault.to_account_info(), to: ctx.accounts.user.to_account_info() },
                &[seeds],
            ),
            REFUEL_LAMPORTS,
        )
    }

    /// Mints test tokens to the caller, at most once per cooldown.
    pub fn claim(ctx: Context<Claim>) -> Result<()> {
        let now = Clock::get()?.unix_timestamp;
        let record = &mut ctx.accounts.record;
        require!(now >= record.last_claim.saturating_add(COOLDOWN_SECS), FaucetError::TooSoon);
        record.last_claim = now;
        record.claims = record.claims.saturating_add(1);

        let seeds: &[&[u8]] = &[b"mint-auth", &[ctx.bumps.mint_authority]];
        token::mint_to(
            CpiContext::new_with_signer(
                ctx.accounts.token_program.to_account_info(),
                MintTo {
                    mint: ctx.accounts.mint.to_account_info(),
                    to: ctx.accounts.user_token.to_account_info(),
                    authority: ctx.accounts.mint_authority.to_account_info(),
                },
                &[seeds],
            ),
            TOKENS_PER_CLAIM,
        )
    }
}

#[derive(Accounts)]
pub struct Refuel<'info> {
    #[account(mut)]
    pub user: Signer<'info>,
    #[account(mut, seeds = [b"sol"], bump)]
    pub sol_vault: SystemAccount<'info>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct Claim<'info> {
    #[account(mut)]
    pub user: Signer<'info>,
    #[account(
        mut,
        constraint = mint.mint_authority == COption::Some(mint_authority.key()) @ FaucetError::WrongMint,
    )]
    pub mint: Account<'info, Mint>,
    /// CHECK: only signs the mint through its seeds, holds no data.
    #[account(seeds = [b"mint-auth"], bump)]
    pub mint_authority: UncheckedAccount<'info>,
    #[account(
        init_if_needed,
        payer = user,
        associated_token::mint = mint,
        associated_token::authority = user,
    )]
    pub user_token: Account<'info, TokenAccount>,
    #[account(
        init_if_needed,
        payer = user,
        space = 8 + ClaimRecord::INIT_SPACE,
        seeds = [b"claim", user.key().as_ref(), mint.key().as_ref()],
        bump,
    )]
    pub record: Account<'info, ClaimRecord>,
    pub token_program: Program<'info, Token>,
    pub associated_token_program: Program<'info, AssociatedToken>,
    pub system_program: Program<'info, System>,
}

#[account]
#[derive(InitSpace)]
pub struct ClaimRecord {
    pub last_claim: i64,
    pub claims: u32,
}

#[error_code]
pub enum FaucetError {
    #[msg("This faucet does not control that token")]
    WrongMint,
    #[msg("You already claimed a moment ago. Try again in a few minutes")]
    TooSoon,
    #[msg("The faucet has run out of SOL. Get devnet SOL from faucet.solana.com")]
    VaultEmpty,
}
