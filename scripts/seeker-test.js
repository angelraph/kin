// Devnet stand-in for Seeker Genesis Tokens.
//   node scripts/seeker-test.js authority        -> prints the test mint authority (creates it if needed)
//   node scripts/seeker-test.js issue <wallet>   -> issues one Token-2022 test token to <wallet>
// Real Genesis Tokens are Token-2022 tokens whose mint authority is GT2zuHVaZQYZSyQMgJPLzvkmyztfyXg2NJunqFp4p3A4.
// On mainnet the app checks that authority. On devnet it checks the test authority created here.
const fs = require("fs");
const os = require("os");
const { Connection, Keypair, PublicKey, clusterApiUrl } = require("@solana/web3.js");
const { createMint, createAssociatedTokenAccount, mintTo, TOKEN_2022_PROGRAM_ID } = require("@solana/spl-token");

const payer = Keypair.fromSecretKey(
  Uint8Array.from(JSON.parse(fs.readFileSync(`${os.homedir()}/.config/solana/id.json`, "utf8")))
);
const conn = new Connection(process.env.RPC_URL || clusterApiUrl("devnet"), "confirmed");
const AUTH_FILE = `${__dirname}/.seeker-authority.json`;

function authority() {
  if (fs.existsSync(AUTH_FILE)) {
    return Keypair.fromSecretKey(Uint8Array.from(JSON.parse(fs.readFileSync(AUTH_FILE, "utf8"))));
  }
  const kp = Keypair.generate();
  fs.writeFileSync(AUTH_FILE, JSON.stringify(Array.from(kp.secretKey)));
  return kp;
}

(async () => {
  const [cmd, wallet] = process.argv.slice(2);
  const auth = authority();
  if (cmd === "authority") {
    console.log("test seeker authority:", auth.publicKey.toBase58());
  } else if (cmd === "issue") {
    const owner = new PublicKey(wallet);
    const mint = await createMint(conn, payer, auth.publicKey, null, 0, undefined, undefined, TOKEN_2022_PROGRAM_ID);
    const token = await createAssociatedTokenAccount(conn, payer, mint, owner, undefined, TOKEN_2022_PROGRAM_ID);
    await mintTo(conn, payer, mint, token, auth, 1, [], undefined, TOKEN_2022_PROGRAM_ID);
    console.log(`issued test Genesis Token ${mint.toBase58()} to ${wallet}`);
  } else {
    console.log("usage: authority | issue <wallet>");
  }
})().catch((e) => { console.error(e.message || e); process.exit(1); });
