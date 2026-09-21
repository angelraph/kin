# Kin

**Save together with people you trust, and never get burned by the ones you shouldn't.**

Kin is a mobile-native rotating savings circle for Android and the Solana Seeker. A group pools a fixed amount each round and one member receives the pot each round until everyone has been paid. Kin makes that safe:

- **Bonded circles.** Every member locks a bond on joining. If someone misses a payment, their bond covers their share on-chain, so the pot still pays out on time.
- **Kin Score.** Each wallet carries an on-chain reliability record (on-time, late, missed, streaks) across every circle it joins, so people can vet each other before they commit.
- **No custody.** Funds sit in program-owned vaults. There are no admin keys and no backend that can touch money.
- **Seeker-native.** Signing goes through Mobile Wallet Adapter and the Seed Vault. Identity shows as a .skr name with a Genesis Token badge. Circles can be denominated in USDC or SKR.

## Repo layout

| Path | What |
|---|---|
| `programs/kin` | Anchor program: circles, members, bonds, score |
| `tests` | Program tests (localnet) |
| `app` | Android app (Kotlin, Jetpack Compose, Mobile Wallet Adapter) |

## On-chain program

Devnet program ID: `7CGtKBZVMKgWJRg92SmRiQkeQe8hvTV3SWrHRfsdSMWe`

Instructions: `create_circle`, `join_circle`, `contribute`, `cover_missed`, `payout`, `claim_bond`, `refund_open`.

Rules enforced by the program:
- Payout order is join order. Each round runs for `period` seconds, plus a `grace` window for late payments.
- After the grace window anyone can call `cover_missed`; the missing member's bond pays their share and their score drops.
- After the period ends and every member is paid or covered, anyone can call `payout`; the pot goes to that round's recipient.
- When the circle completes, each member reclaims any unused bond with `claim_bond`.
- If an open circle never fills within 14 days, members reclaim their bonds with `refund_open`.

Limits (stated plainly): a bond covers a bounded number of missed payments, so it reduces default risk but does not eliminate it. A per-round pot cap is enforced while the program is young.

## Build

```bash
# program (Linux/WSL)
anchor build
anchor test

# app
cd app && ./gradlew assembleDebug
```
