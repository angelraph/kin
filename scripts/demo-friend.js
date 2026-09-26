// Plays a second circle member from the command line, for demos and end-to-end checks on devnet.
//
//   node scripts/demo-friend.js address              -> the friend wallet's address (created on first use)
//   node scripts/demo-friend.js fund                 -> gives the friend devnet SOL and test tUSDC
//   node scripts/demo-friend.js status <circle>      -> circle state, members and timings
//   node scripts/demo-friend.js join <circle>        -> friend locks the bond and takes a seat
//   node scripts/demo-friend.js pay <circle>         -> friend pays this round's contribution
//   node scripts/demo-friend.js collect <circle> <wallet> -> collect a member's autopay contribution
//   node scripts/demo-friend.js cover <circle> <wallet>  -> cover a member who missed the window
//   node scripts/demo-friend.js payout <circle>      -> send the round's pot to its recipient
//
// The friend's key lives in scripts/.friend-keypair.json (git-ignored). Funding uses the devnet
// deploy wallet at ~/.config/solana/id.json, which is also the test token's mint authority.
const fs = require("fs");
const os = require("os");
const anchor = require("@coral-xyz/anchor");
const { Connection, Keypair, PublicKey, SystemProgram, SYSVAR_SLOT_HASHES_PUBKEY, Transaction, clusterApiUrl } = require("@solana/web3.js");
const {
  TOKEN_PROGRAM_ID,
  ASSOCIATED_TOKEN_PROGRAM_ID,
  getOrCreateAssociatedTokenAccount,
  getAssociatedTokenAddressSync,
  mintTo,
} = require("@solana/spl-token");

const MINT = new PublicKey(process.env.KIN_MINT || "3xicv3CvScBBpdsM1LBH1xbm1YNhUWFDhCDxosHEzZR5");
const KEY_FILE = `${__dirname}/.friend-keypair.json`;
const IDL = JSON.parse(fs.readFileSync(`${__dirname}/../idl/kin.json`, "utf8"));
const conn = new Connection(process.env.RPC_URL || clusterApiUrl("devnet"), "confirmed");

const load = (path) => Keypair.fromSecretKey(Uint8Array.from(JSON.parse(fs.readFileSync(path, "utf8"))));
function friendKey() {
  if (fs.existsSync(KEY_FILE)) return load(KEY_FILE);
  const kp = Keypair.generate();
  fs.writeFileSync(KEY_FILE, JSON.stringify(Array.from(kp.secretKey)));
  return kp;
}

const friend = friendKey();
const program = new anchor.Program(IDL, new anchor.AnchorProvider(conn, new anchor.Wallet(friend), { commitment: "confirmed" }));
const pda = (seeds) => PublicKey.findProgramAddressSync(seeds, program.programId)[0];
const member = (circle, w) => pda([Buffer.from("member"), circle.toBuffer(), w.toBuffer()]);
const score = (w) => pda([Buffer.from("score"), w.toBuffer()]);
const vault = (c) => pda([Buffer.from("vault"), c.toBuffer()]);
const bondVault = (c) => pda([Buffer.from("bond_vault"), c.toBuffer()]);
const ata = (w) => getAssociatedTokenAddressSync(MINT, w);
const fmt = (n) => (Number(n) / 1e6).toString();
const short = (k) => k.toBase58().slice(0, 4) + "..." + k.toBase58().slice(-4);

async function status(circleKey) {
  const c = await program.account.circle.fetch(circleKey);
  const members = (await program.account.member.all([{ memcmp: { offset: 8, bytes: circleKey.toBase58() } }]))
    .map((m) => m.account).sort((a, b) => a.index - b.index);
  const now = Math.floor(Date.now() / 1000);
  const state = Object.keys(c.status)[0];
  console.log(`${c.name}  [${state}]  ${c.memberCount}/${c.maxMembers} members  ${fmt(c.contribution)} per round, bond ${fmt(c.bond)}`);
  if (state !== "open") {
    const end = c.roundStartTs.toNumber() + c.periodSecs.toNumber();
    const grace = end + c.graceSecs.toNumber();
    console.log(`round ${c.currentRound + 1}: ${c.resolvedCount}/${c.memberCount} paid, pot ${fmt(c.roundPot)}, ` +
      (now <= end ? `${end - now}s until the round ends` : now <= grace ? `grace, ${grace - now}s left` : "grace is over"));
    console.log("payout order:", Array.from(c.payoutOrder).slice(0, c.memberCount).join(" -> "), c.randomize ? "(random, from on-chain seed)" : "(join order)");
  }
  members.forEach((m) => console.log(`  #${m.index} ${short(m.wallet)}${m.wallet.equals(friend.publicKey) ? " (friend)" : ""}  paid rounds ${m.roundsResolved}  missed ${m.missed}  received ${m.received}`));
  return { c, members };
}

(async () => {
  const [cmd, arg, arg2] = process.argv.slice(2);
  if (cmd === "address") return console.log(friend.publicKey.toBase58());

  if (cmd === "fund") {
    const payer = load(`${os.homedir()}/.config/solana/id.json`);
    const bal = await conn.getBalance(friend.publicKey);
    if (bal < 0.3e9) {
      const tx = new Transaction().add(SystemProgram.transfer({ fromPubkey: payer.publicKey, toPubkey: friend.publicKey, lamports: 0.5e9 }));
      await anchor.web3.sendAndConfirmTransaction(conn, tx, [payer]);
    }
    const acct = await getOrCreateAssociatedTokenAccount(conn, payer, MINT, friend.publicKey);
    await mintTo(conn, payer, MINT, acct.address, payer, 500_000_000n);
    return console.log(`friend ${friend.publicKey.toBase58()} funded: ${(await conn.getBalance(friend.publicKey)) / 1e9} SOL, 500 tUSDC added`);
  }

  const circleKey = new PublicKey(arg);
  if (cmd === "status") return void (await status(circleKey));

  const { c, members } = await status(circleKey);
  if (cmd === "join") {
    const sig = await program.methods.joinCircle().accountsPartial({
      wallet: friend.publicKey, circle: circleKey, member: member(circleKey, friend.publicKey), score: score(friend.publicKey),
      walletToken: ata(friend.publicKey), bondVault: bondVault(circleKey), slotHashes: SYSVAR_SLOT_HASHES_PUBKEY,
      sgtToken: null, sgtMint: null, tokenProgram: TOKEN_PROGRAM_ID, systemProgram: SystemProgram.programId,
    }).rpc();
    console.log("joined:", sig);
  } else if (cmd === "pay") {
    const sig = await program.methods.contribute().accountsPartial({
      wallet: friend.publicKey, circle: circleKey, member: member(circleKey, friend.publicKey), score: score(friend.publicKey),
      walletToken: ata(friend.publicKey), vault: vault(circleKey), tokenProgram: TOKEN_PROGRAM_ID,
    }).rpc();
    console.log("paid:", sig);
  } else if (cmd === "collect") {
    // Pulls one contribution from a member who turned autopay on. Anyone can do this for them.
    const target = new PublicKey(arg2);
    const sig = await program.methods.collect().accountsPartial({
      caller: friend.publicKey, circle: circleKey, member: member(circleKey, target), score: score(target),
      memberToken: ata(target), vault: vault(circleKey), autopay: pda([Buffer.from("autopay")]), tokenProgram: TOKEN_PROGRAM_ID,
    }).rpc();
    console.log("collected by autopay:", sig);
  } else if (cmd === "cover") {
    const target = new PublicKey(arg2);
    const sig = await program.methods.coverMissed().accountsPartial({
      caller: friend.publicKey, circle: circleKey, member: member(circleKey, target), score: score(target),
      vault: vault(circleKey), bondVault: bondVault(circleKey), tokenProgram: TOKEN_PROGRAM_ID,
    }).rpc();
    console.log("covered:", sig);
  } else if (cmd === "payout") {
    const recipient = members.find((m) => m.index === c.payoutOrder[c.currentRound]);
    const sig = await program.methods.payout().accountsPartial({
      caller: friend.publicKey, circle: circleKey, mint: MINT, recipientMember: member(circleKey, recipient.wallet),
      recipientWallet: recipient.wallet, recipientToken: ata(recipient.wallet), vault: vault(circleKey),
      tokenProgram: TOKEN_PROGRAM_ID, associatedTokenProgram: ASSOCIATED_TOKEN_PROGRAM_ID, systemProgram: SystemProgram.programId,
    }).rpc();
    console.log(`paid out to ${short(recipient.wallet)}:`, sig);
  } else {
    console.log("commands: address | fund | status <circle> | join <circle> | pay <circle> | cover <circle> <wallet> | payout <circle>");
    return;
  }
  await status(circleKey);
})().catch((e) => { console.error("ERROR:", e.message || e); if (e.logs) console.error(e.logs.slice(-6).join("\n")); process.exit(1); });
