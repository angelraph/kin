// One-time devnet setup for the test faucet (programs/kin_faucet).
//   node scripts/faucet-setup.js <mint> <sol-to-put-in-the-vault>
// Hands the test token's mint authority to the faucet program's PDA and tops up its SOL vault.
// Run with the wallet that currently controls the mint (~/.config/solana/id.json). Devnet only.
const fs = require("fs");
const os = require("os");
const {
  Connection, Keypair, PublicKey, SystemProgram, Transaction, LAMPORTS_PER_SOL, clusterApiUrl, sendAndConfirmTransaction,
} = require("@solana/web3.js");
const { setAuthority, AuthorityType, getMint } = require("@solana/spl-token");

const FAUCET = new PublicKey("G5MhE85BTiTqLPinKZBg7jh4sNMyTc7WUvGcfMerWsig");
const payer = Keypair.fromSecretKey(
  Uint8Array.from(JSON.parse(fs.readFileSync(`${os.homedir()}/.config/solana/id.json`, "utf8")))
);
const conn = new Connection(process.env.RPC_URL || clusterApiUrl("devnet"), "confirmed");
const pda = (seed) => PublicKey.findProgramAddressSync([Buffer.from(seed)], FAUCET)[0];

(async () => {
  const [mintArg, solArg] = process.argv.slice(2);
  if (!mintArg) throw new Error("usage: node scripts/faucet-setup.js <mint> <sol-for-vault>");
  const mint = new PublicKey(mintArg);
  const authority = pda("mint-auth");
  const vault = pda("sol");

  const info = await getMint(conn, mint);
  if (info.mintAuthority && info.mintAuthority.equals(authority)) {
    console.log("mint authority is already the faucet:", authority.toBase58());
  } else {
    await setAuthority(conn, payer, mint, payer, AuthorityType.MintTokens, authority);
    console.log("mint authority handed to the faucet:", authority.toBase58());
  }

  const sol = Number(solArg || 0);
  if (sol > 0) {
    const tx = new Transaction().add(
      SystemProgram.transfer({ fromPubkey: payer.publicKey, toPubkey: vault, lamports: Math.round(sol * LAMPORTS_PER_SOL) })
    );
    await sendAndConfirmTransaction(conn, tx, [payer]);
  }
  console.log("sol vault:", vault.toBase58(), (await conn.getBalance(vault)) / LAMPORTS_PER_SOL, "SOL");
})().catch((e) => { console.error(e.message || e); process.exit(1); });
