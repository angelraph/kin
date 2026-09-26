# Kin

Savings circles for people who trust each other, and protection for when someone slips.

A circle is a group that pays a fixed amount every round. Each round, one member receives the whole pot, until everyone has had a turn. It is how millions of people already save, usually on a notebook and a WhatsApp group. Kin puts it on Solana so nobody has to hold the money and nobody has to take anyone's word.

## Try it in two minutes

1. **Install.** Download `kin-0.2.0.apk` from the [latest release](https://github.com/angelraph/kin/releases/latest) and open it on any Android phone. Kin also runs fine on a Seeker.
2. **See the idea first.** On the first screen tap "See a circle run, no wallet needed". It plays five people through five rounds locally, including a missed payment covered by a bond.
3. **Set your wallet to devnet.** Kin works with any Mobile Wallet Adapter wallet. In Phantom: Settings, Developer Settings, turn on Testnet Mode, and choose Solana Devnet.
4. **Connect and get funds.** Tap Connect wallet, open the You tab and tap "Get test funds". One signature gives you 500 test tokens and, if your wallet is nearly empty, a little SOL for network fees.
5. **Run a circle.** On the Circles tab tap New circle and choose "One minute test run". Share the invite link to a second phone to fill the circle, or run the friend helper (`node scripts/demo-friend.js`, see the top of that file) to play the other members from a computer.

Phantom may also show a red notice that the app's identity could not be verified. It appears for both the debug and the release build, even though Google's Digital Asset Links checker reports Kin's package and both signing keys as linked to ngelraph.github.io and Android reports the domain as verified for the app, so it comes from Phantom's side. Tap Connect to continue. Wallets that follow the Mobile Wallet Adapter specification, such as the Seed Vault Wallet on a Seeker, verify against the same file.

The wallet may show "Failed to simulate the results of this request" on devnet. That is the wallet's own simulator, which does not cover devnet. The transaction itself is valid and the same flow simulates normally on mainnet.

## The problems Kin solves, and how

- **People miss payments.** Every member locks a bond. If a payment is missed, the bond pays that member's share so the pot still goes out on time, and their on-chain Kin Score records it.
- **People forget.** Autopay uses SPL token delegation. A member approves a capped, revocable allowance equal to their remaining dues. Anyone can then trigger the collection, and the program can only move one contribution per round, from that member's own token account into that circle's vault.
- **Nobody notices what is due.** A background watcher checks the chain every 15 minutes, read-only, and sends a notification when a payment is due, autopay is ready to collect, a bond can cover a miss, or a payout can be sent. Tapping it opens the circle. Signing still happens in the wallet on the member's tap, so the app never holds a key. One member collecting covers everyone on autopay.
- **Arguments about who goes first.** A circle can draw its payout order from on-chain randomness when it fills. The seed is stored on-chain, so anyone can recompute the order and check it. The app does exactly that.
- **Strangers and fake accounts.** A circle can require a Seeker Genesis Token. The program itself checks the token account, its owner and its mint authority, not just the app.
- **"Trust me" accounting.** Every action emits an on-chain event. The app rebuilds a circle's history from real transaction logs and checks that the vaults hold at least what the program says is owed.

Money sits in program-owned vaults. There are no admin keys and no server that can move funds.

## Verify it yourself

- **Program tests.** Run `anchor test` for 27 on-chain scenarios (23 for Kin, 4 for the faucet) on a local validator, and `cargo test -p kin --lib` for the pure logic (shuffle and Seeker token check).
- **App tests.** Run `./gradlew testDebugUnitTest` in `app`. The Kotlin client is checked against values produced independently by the JavaScript client, including PDAs, token addresses, instruction discriminators and the shuffle.
- **In the app.** Open any circle and tap "Check on-chain" under "Proof on Solana". It reads vault balances, recomputes the payout order from the stored seed, and lists the circle's real transactions with links to the Solana Explorer.
- **Verified build.** `solana-verify build` reproduces the exact bytes deployed on devnet. Running it yourself and comparing the hash to the deployed program should print the same value on both sides:

  ```bash
  solana-verify build --library-name kin
  solana-verify get-executable-hash target/deploy/kin.so
  solana-verify get-program-hash -u https://api.devnet.solana.com 7CGtKBZVMKgWJRg92SmRiQkeQe8hvTV3SWrHRfsdSMWe
  # both hashes: 65d5423a6d2e0237a3392854efbc7d551d0f09e04cf257cd7567c9e104243b73
  ```

## Repository

- `programs/kin`: the Anchor program (circles, members, bonds, score, autopay, events)
- `programs/kin_faucet`: a devnet only faucet that hands out test tokens and a little SOL, so anyone can try the app (`G5MhE85BTiTqLPinKZBg7jh4sNMyTc7WUvGcfMerWsig`). It is separate from Kin and is never deployed to mainnet.
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

## Releasing

The release build is signed with a key kept outside the repository. Put its properties at `~/.kin/keystore.properties` (or point `KIN_KEYSTORE_PROPERTIES` at the file) and run `./gradlew assembleRelease`. Wallets only authorise an app whose package and signing certificate appear in `https://angelraph.github.io/.well-known/assetlinks.json`, so a new signing key must be added there first.

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

The interface follows one design system: white paper, ink type, graphite panels and a single aqua signal colour, set in Inter and JetBrains Mono. Both typefaces are bundled and released under the SIL Open Font Licence (see `FONTS-LICENSE-*.txt`).
