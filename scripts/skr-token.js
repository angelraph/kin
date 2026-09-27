// Creates the devnet test SKR token for the SKR integration prize track.
//   node scripts/skr-token.js
// Its mint authority is the kin_faucet program's PDA from the start, so the faucet can mint it
// exactly like tUSDC with no extra setup step. Devnet only; not the real $SKR.
const fs = require("fs");
const os = require("os");
const { Connection, Keypair, PublicKey, clusterApiUrl } = require("@solana/web3.js");
const { createMint } = require("@solana/spl-token");

const FAUCET_PROGRAM = new PublicKey("G5MhE85BTiTqLPinKZBg7jh4sNMyTc7WUvGcfMerWsig");
const mintAuthority = PublicKey.findProgramAddressSync([Buffer.from("mint-auth")], FAUCET_PROGRAM)[0];

const payer = Keypair.fromSecretKey(
  Uint8Array.from(JSON.parse(fs.readFileSync(`${os.homedir()}/.config/solana/id.json`, "utf8")))
);
const conn = new Connection(process.env.RPC_URL || clusterApiUrl("devnet"), "confirmed");

(async () => {
  const mint = await createMint(conn, payer, mintAuthority, null, 6);
  console.log("tSKR mint:", mint.toBase58());
  console.log("mint authority (faucet PDA):", mintAuthority.toBase58());
})().catch((e) => { console.error(e.message || e); process.exit(1); });
