// Devnet demo token helper.
//   node scripts/demo-token.js create            -> creates a 6-decimal mint, prints its address
//   node scripts/demo-token.js fund <wallet> <n> -> mints n whole tokens to <wallet>
// Uses ~/.config/solana/id.json as mint authority and fee payer. Devnet only.
const fs = require("fs");
const os = require("os");
const { Connection, Keypair, PublicKey, clusterApiUrl } = require("@solana/web3.js");
const { createMint, getOrCreateAssociatedTokenAccount, mintTo } = require("@solana/spl-token");

const payer = Keypair.fromSecretKey(
  Uint8Array.from(JSON.parse(fs.readFileSync(`${os.homedir()}/.config/solana/id.json`, "utf8")))
);
const conn = new Connection(process.env.RPC_URL || clusterApiUrl("devnet"), "confirmed");
const MINT_FILE = `${__dirname}/.demo-mint`;

(async () => {
  const [cmd, wallet, amount] = process.argv.slice(2);
  if (cmd === "create") {
    const mint = await createMint(conn, payer, payer.publicKey, null, 6);
    fs.writeFileSync(MINT_FILE, mint.toBase58());
    console.log("mint:", mint.toBase58());
  } else if (cmd === "fund") {
    const mint = new PublicKey(fs.readFileSync(MINT_FILE, "utf8").trim());
    const owner = new PublicKey(wallet);
    const ata = await getOrCreateAssociatedTokenAccount(conn, payer, mint, owner);
    await mintTo(conn, payer, mint, ata.address, payer, BigInt(Math.round(Number(amount) * 1e6)));
    console.log(`funded ${wallet} with ${amount} tokens (mint ${mint.toBase58()})`);
  } else {
    console.log("usage: create | fund <wallet> <amount>");
  }
})().catch((e) => { console.error(e.message || e); process.exit(1); });
