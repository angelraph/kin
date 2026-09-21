# Kin

Savings circles for people who trust each other, and protection for when someone slips.

A circle is a group that pays a fixed amount every round. Each round, one member receives the whole pot, until everyone has had a turn. It is how millions of people already save, usually on a notebook and a WhatsApp group. Kin puts it on Solana so nobody has to hold the money and nobody has to take anyone's word.

## The problems Kin solves, and how

- **People miss payments.** Every member locks a bond. If a payment is missed, the bond pays that member's share so the pot still goes out on time, and their on-chain Kin Score records it.
- **People forget.** Autopay uses SPL token delegation. A member approves a capped, revocable allowance equal to their remaining dues. Anyone can then trigger the collection, and the program can only move one contribution per round, from that member's own token account into that circle's vault.
- **Arguments about who goes first.** A circle can draw its payout order from on-chain randomness when it fills. The seed is stored on-chain, so anyone can recompute the order and check it. The app does exactly that.
- **Strangers and fake accounts.** A circle can require a Seeker Genesis Token. The program itself checks the token account, its owner and its mint authority, not just the app.
- **"Trust me" accounting.** Every action emits an on-chain event. The app rebuilds a circle's history from real transaction logs and checks that the vaults hold at least what the program says is owed.

Money sits in program-owned vaults. There are no admin keys and no server that can move funds.

## Verify it yourself

- **Program tests.** Run `anchor test` for 23 on-chain scenarios on a local validator, and `cargo test -p kin --lib` for the pure logic (shuffle and Seeker token check).
- **App tests.** Run `./gradlew testDebugUnitTest` in `app`. The Kotlin client is checked against values produced independently by the JavaScript client, including PDAs, token addresses, instruction discriminators and the shuffle.
- **In the app.** Open any circle and tap "Check on-chain" under "Proof on Solana". It reads vault balances, recomputes the payout order from the stored seed, and lists the circle's real transactions with links to the Solana Explorer.

## Repository

- `programs/kin`: the Anchor program (circles, members, bonds, score, autopay, events)
- `tests`: on-chain tests that run on a local validator
- `app`: the Android app (Kotlin, Jetpack Compose, Mobile Wallet Adapter)
- `scripts`: devnet helpers, including a demo token, wallet funding and a test Seeker token issuer

## On-chain program

Devnet program ID: `7CGtKBZVMKgWJRg92SmRiQkeQe8hvTV3SWrHRfsdSMWe`

Instructions: `create_circle`, `join_circle`, `contribute`, `collect`, `cover_missed`, `payout`, `claim_bond`, `refund_open`.

Rules the program enforces:

- A round lasts `period` seconds, followed by a `grace` window for late payments. Payout order is join order, or a random draw if the circle asked for one.
- After the grace window, anyone can call `cover_missed`. The missing member's bond pays their share and their score takes the hit.
- Once the period has ended and every member is paid or covered, anyone can call `payout`, and the pot goes to that round's recipient.
- `collect` only works for members who approved the shared autopay delegate, and only inside the payment window. A collection counts as on time, because the member authorised it in advance.
- When the circle finishes, each member takes back whatever bond was not used, with `claim_bond`.
- If an open circle does not fill within 14 days, members take their bonds back with `refund_open`.

Known limits, stated plainly:

- A bond covers a limited number of missed payments, so it reduces default risk without removing it.
- The random order comes from the recent slot hash when the last member joins. Someone joining last could influence the timing slightly, so it is a fair draw for a group of people who know each other, not a lottery for strangers.
- Seeker Genesis Tokens only exist on mainnet. On devnet the app checks a test authority instead (see `scripts/seeker-test.js`). The code path is identical.
- The size of one round's pot is capped while the program is young.

## Build and test

Program (Linux or WSL, Anchor 0.31.1):

```bash
anchor build
anchor test
```

App (JDK 17 and the Android SDK):

```bash
cd app
./gradlew testDebugUnitTest assembleDebug lintDebug
```
