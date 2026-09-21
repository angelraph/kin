# Kin

Savings circles for people who trust each other, and protection for when someone slips.

A circle is a group that pays a fixed amount every round. Each round, one member receives the whole pot, until everyone has had a turn. It is how millions of people already save, usually on a notebook and a WhatsApp group. Kin puts it on Solana so nobody has to hold the money and nobody has to take anyone's word.

## What makes it different

- **Bonds.** Every member locks a bond when joining. If a payment is missed, the bond pays that member's share, so the pot still goes out on time.
- **Kin Score.** Each wallet has an on-chain record of on-time, late and missed payments, shared across every circle it joins. You can see how reliable someone is before you sit down with them.
- **No custody.** Money sits in program-owned vaults. There are no admin keys and no server that can move funds.
- **Built for the phone.** Android app that signs through Mobile Wallet Adapter, so on a Seeker every payment is approved in the Seed Vault.

## Repository

- `programs/kin`: the Anchor program (circles, members, bonds, score)
- `tests`: program tests that run on a local validator
- `app`: the Android app (Kotlin, Jetpack Compose, Mobile Wallet Adapter)
- `scripts`: devnet helpers, including a demo token and wallet funding

## On-chain program

Devnet program ID: `7CGtKBZVMKgWJRg92SmRiQkeQe8hvTV3SWrHRfsdSMWe`

Instructions: `create_circle`, `join_circle`, `contribute`, `cover_missed`, `payout`, `claim_bond`, `refund_open`.

Rules the program enforces:

- Payout order is join order. A round lasts `period` seconds, followed by a `grace` window for late payments.
- After the grace window, anyone can call `cover_missed`. The missing member's bond pays their share and their score takes the hit.
- Once the period has ended and every member is paid or covered, anyone can call `payout`, and the pot goes to that round's recipient.
- When the circle finishes, each member takes back whatever bond was not used, with `claim_bond`.
- If an open circle does not fill within 14 days, members take their bonds back with `refund_open`.

Known limits: a bond covers a limited number of missed payments, so it reduces default risk without removing it. The size of one round's pot is capped while the program is young.

## Build and test

Program (Linux or WSL, Anchor 0.31.1):

```bash
anchor build
anchor test
```

App (JDK 17 and the Android SDK):

```bash
cd app
./gradlew testDebugUnitTest assembleDebug
```
