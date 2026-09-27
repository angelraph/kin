# Demo video script

Target length 2:50, filmed on a real phone with the screen recorder running. One take per scene, cut together at the end. The rules score the video for completion and mobile features, so every scene shows a real action on the device.

## Edit style: kinetic word cuts

The app now opens with a short animated intro (oversized words SAVE, BONDED, VERIFIED, PAID flashing on Kin's palette with a sliding progress line, closing on the Kin mark). Carry that same rhythm into the video edit, so the intro is not the only place it appears:

- **Open on the real intro.** Film the app's own cold-start intro as the first thing on screen. It already is the kinetic-word opener; do not recreate it separately.
- **Between scenes, cut in a one-word title card** instead of a plain jump cut, in the same style: a single bold word, full bleed, on one of Kin's fills (ink, aqua, graphite, lime), on screen for about 12 frames (roughly 0.4s at 30fps). Use the word that names what the next scene shows: JOIN, PAY, DRAW, COVER, CLAIM, PROOF. This mirrors the intro's own beats (SAVE, BONDED, VERIFIED, PAID) so the whole video reads as one system, not a screen recording with slides bolted on.
- **Keep a thin progress line** across the bottom of the whole video, filling left to right as it plays, the same visual as the intro's sweep. Simple to add in any editor as a rectangle that grows in width over the video's duration.
- **Do not** recreate the full font/motion of the source reference video, or its colours. Kin's cuts use Kin's palette and Kin's words, not a copy of someone else's ad.

## Before filming

- Install `kin-0.3.0.apk`, put the wallet on devnet, connect, and tap Get test funds so the balance is not empty.
- Turn on Stay awake (Developer options) so the phone does not lock mid take.
- Have a second member ready. Either a second phone with Kin and its own wallet, or the friend helper on a laptop: `node scripts/demo-friend.js fund`, then `join`, `pay` and `payout` as the scenes need it.
- Use the "One minute test run" use case so a full circle finishes while you talk.
- Phantom may show a devnet notice before you confirm. Say so in one sentence when it first appears and move on.

## Scenes

| Time | On screen | Say |
|---|---|---|
| 0:00 | The app's own intro (SAVE, BONDED, VERIFIED, PAID, then Kin) | Silent, or one line over it: "Kin is a savings circle on Solana." |
| 0:05 | A group chat and a spreadsheet, then the Kin landing page | "Millions of people save in circles called ajo, susu, tanda. The money lives in a notebook and the trust lives in a group chat. When someone stops paying, nobody can prove what happened." |
| 0:25 | Word card: WATCH. Tap See a circle run. Play rounds 1 to 3 | "This runs with no wallet. Here a member misses a payment. Their bond covers it, the pot still pays out, and their score drops." |
| 0:50 | Word card: CONNECT. Connect wallet, then You tab, Get test funds | "Kin never holds a key. Every action is signed in the wallet. Test funds come from a faucet program, so anyone can try this." |
| 1:10 | Word card: JOIN. Discover, Use cases, pick One minute test run, then Create circle | "Each use case is the same on-chain program with different settings." |
| 1:30 | Word card: DRAW. Second phone or friend helper joins. Show the circle filling, then the order being drawn | "When the circle fills, the payout order is drawn from on-chain data. The app recomputes it to check." |
| 1:50 | Word card: PAY. Pay a round on the phone. Turn on autopay and let the other member collect | "Autopay is a capped allowance into this one circle. Anyone can collect, and nobody can take more." |
| 2:10 | Word card: COVER. One member skips a round. After the grace window tap Cover | "This member did not pay. Their bond paid for them, and the record shows it." |
| 2:25 | Word card: CLAIM. Payout, then claim the bond, then the You tab score | "The pot goes out, unused bonds come back, and the score follows the wallet into the next circle." |
| 2:38 | Word card: PROOF. Open Proof, tap Check on-chain, then open one transaction in the explorer | "You do not have to trust us. This screen reads Solana and checks the vaults, the order and every payment." |
| 2:50 | Landing page and the repo link | "Kin is live on devnet. The code, the signed APK and a two minute guide are on GitHub." |

## Notes

- If the timing runs long, cut the explorer step first, then the second use case tour. Keep the word cards; they cost almost no time and hold the video's rhythm together.
- Say "devnet" and "test tokens" out loud once. It is true, and it stops a judge wondering.
- Do not claim mainnet, an audit or the dApp Store. The roadmap slide says what comes next.
