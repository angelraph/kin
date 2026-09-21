import * as anchor from "@coral-xyz/anchor";
import { Program, BN } from "@coral-xyz/anchor";
import {
  createMint,
  createAssociatedTokenAccount,
  mintTo,
  getAccount,
  approve,
  getAssociatedTokenAddressSync,
  TOKEN_PROGRAM_ID,
  TOKEN_2022_PROGRAM_ID,
  ASSOCIATED_TOKEN_PROGRAM_ID,
} from "@solana/spl-token";
import {
  Keypair,
  PublicKey,
  SystemProgram,
  SYSVAR_SLOT_HASHES_PUBKEY,
  LAMPORTS_PER_SOL,
} from "@solana/web3.js";
import { createHash } from "crypto";
import { expect } from "chai";
import { Kin } from "../target/types/kin";

const CONTRIBUTION = 100;
const BOND = 200;
const PERIOD = 10; // seconds (program minimum)
const GRACE = 5;

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

/** Mirrors `draw_order` in programs/kin/src/logic.rs so anyone can recompute a circle's order. */
function drawOrder(seed: Buffer, n: number): number[] {
  const order = Array.from({ length: n }, (_, i) => i);
  for (let i = n - 1; i >= 1; i--) {
    const h = createHash("sha256")
      .update(Buffer.concat([seed, Buffer.from([i])]))
      .digest();
    const j = Number(h.readBigUInt64LE(0) % BigInt(i + 1));
    [order[i], order[j]] = [order[j], order[i]];
  }
  return order;
}

interface CreateOpts {
  maxMissed?: number;
  bond?: number;
  members?: number;
  randomize?: boolean;
  seekerOnly?: boolean;
  seekerAuthority?: PublicKey;
}

describe("kin", () => {
  const provider = anchor.AnchorProvider.env();
  anchor.setProvider(provider);
  const program = anchor.workspace.Kin as Program<Kin>;
  const conn = provider.connection;

  let mint: PublicKey;
  const [a, b, c, d, stranger] = [1, 2, 3, 4, 5].map(() => Keypair.generate());
  const atas: Record<string, PublicKey> = {};

  const pda = (seeds: (Buffer | Uint8Array)[]) =>
    PublicKey.findProgramAddressSync(seeds, program.programId)[0];
  const idBuf = (n: number) => new BN(n).toArrayLike(Buffer, "le", 8);
  const circlePda = (creator: PublicKey, id: number) =>
    pda([Buffer.from("circle"), creator.toBuffer(), idBuf(id)]);
  const memberPda = (circle: PublicKey, w: PublicKey) =>
    pda([Buffer.from("member"), circle.toBuffer(), w.toBuffer()]);
  const scorePda = (w: PublicKey) => pda([Buffer.from("score"), w.toBuffer()]);
  const vaultPda = (circle: PublicKey) => pda([Buffer.from("vault"), circle.toBuffer()]);
  const bondVaultPda = (circle: PublicKey) =>
    pda([Buffer.from("bond_vault"), circle.toBuffer()]);
  const autopayPda = pda([Buffer.from("autopay")]);
  const bal = async (ata: PublicKey) => Number((await getAccount(conn, ata)).amount);
  const ataOf = (k: Keypair) => atas[k.publicKey.toBase58()];

  async function expectFail(p: Promise<unknown>, code: string) {
    try {
      await p;
    } catch (e: any) {
      const msg = `${e?.error?.errorCode?.code ?? ""} ${e?.message ?? ""} ${(e?.logs ?? []).join(" ")}`;
      expect(msg, `expected ${code}, got: ${msg}`).to.include(code);
      return;
    }
    expect.fail(`expected failure with ${code}`);
  }

  /** Decodes the program events a transaction emitted, straight from its logs. */
  async function eventsOf(sig: string) {
    // A freshly confirmed transaction can take a moment to become readable, so retry briefly.
    let tx = null;
    for (let attempt = 0; attempt < 20 && !tx; attempt++) {
      tx = await conn.getTransaction(sig, {
        commitment: "confirmed",
        maxSupportedTransactionVersion: 0,
      });
      if (!tx) await sleep(300);
    }
    expect(tx, `transaction ${sig} never became readable`).to.not.equal(null);
    const parser = new anchor.EventParser(program.programId, program.coder);
    return [...parser.parseLogs(tx!.meta!.logMessages!)].map((e) => ({
      name: e.name.toLowerCase(),
      data: e.data as any,
    }));
  }

  const create = (creator: Keypair, id: number, o: CreateOpts = {}) => {
    const circle = circlePda(creator.publicKey, id);
    return program.methods
      .createCircle(
        new BN(id),
        "Test circle",
        new BN(CONTRIBUTION),
        new BN(o.bond ?? BOND),
        new BN(PERIOD),
        new BN(GRACE),
        o.members ?? 3,
        o.maxMissed ?? 100,
        o.randomize ?? false,
        o.seekerOnly ?? false,
        o.seekerAuthority ?? PublicKey.default
      )
      .accountsPartial({
        creator: creator.publicKey,
        mint,
        circle,
        vault: vaultPda(circle),
        bondVault: bondVaultPda(circle),
        tokenProgram: TOKEN_PROGRAM_ID,
        systemProgram: SystemProgram.programId,
      })
      .signers([creator])
      .rpc();
  };

  const join = (
    w: Keypair,
    circle: PublicKey,
    sgt?: { token: PublicKey; mint: PublicKey }
  ) =>
    program.methods
      .joinCircle()
      .accountsPartial({
        wallet: w.publicKey,
        circle,
        member: memberPda(circle, w.publicKey),
        score: scorePda(w.publicKey),
        walletToken: ataOf(w),
        bondVault: bondVaultPda(circle),
        slotHashes: SYSVAR_SLOT_HASHES_PUBKEY,
        sgtToken: sgt?.token ?? null,
        sgtMint: sgt?.mint ?? null,
        tokenProgram: TOKEN_PROGRAM_ID,
        systemProgram: SystemProgram.programId,
      })
      .signers([w])
      .rpc();

  const contribute = (w: Keypair, circle: PublicKey) =>
    program.methods
      .contribute()
      .accountsPartial({
        wallet: w.publicKey,
        circle,
        member: memberPda(circle, w.publicKey),
        score: scorePda(w.publicKey),
        walletToken: ataOf(w),
        vault: vaultPda(circle),
        tokenProgram: TOKEN_PROGRAM_ID,
      })
      .signers([w])
      .rpc();

  const collect = (caller: Keypair, circle: PublicKey, target: Keypair) =>
    program.methods
      .collect()
      .accountsPartial({
        caller: caller.publicKey,
        circle,
        member: memberPda(circle, target.publicKey),
        score: scorePda(target.publicKey),
        memberToken: ataOf(target),
        vault: vaultPda(circle),
        autopay: autopayPda,
        tokenProgram: TOKEN_PROGRAM_ID,
      })
      .signers([caller])
      .rpc();

  const cover = (caller: Keypair, circle: PublicKey, target: PublicKey) =>
    program.methods
      .coverMissed()
      .accountsPartial({
        caller: caller.publicKey,
        circle,
        member: memberPda(circle, target),
        score: scorePda(target),
        vault: vaultPda(circle),
        bondVault: bondVaultPda(circle),
        tokenProgram: TOKEN_PROGRAM_ID,
      })
      .signers([caller])
      .rpc();

  const payout = (caller: Keypair, circle: PublicKey, recipient: PublicKey) =>
    program.methods
      .payout()
      .accountsPartial({
        caller: caller.publicKey,
        circle,
        mint,
        recipientMember: memberPda(circle, recipient),
        recipientWallet: recipient,
        recipientToken: getAssociatedTokenAddressSync(mint, recipient),
        vault: vaultPda(circle),
        tokenProgram: TOKEN_PROGRAM_ID,
        associatedTokenProgram: ASSOCIATED_TOKEN_PROGRAM_ID,
        systemProgram: SystemProgram.programId,
      })
      .signers([caller])
      .rpc();

  const claim = (w: Keypair, circle: PublicKey) =>
    program.methods
      .claimBond()
      .accountsPartial({
        wallet: w.publicKey,
        circle,
        member: memberPda(circle, w.publicKey),
        score: scorePda(w.publicKey),
        walletToken: ataOf(w),
        bondVault: bondVaultPda(circle),
        tokenProgram: TOKEN_PROGRAM_ID,
      })
      .signers([w])
      .rpc();

  before(async () => {
    for (const k of [a, b, c, d, stranger]) {
      const sig = await conn.requestAirdrop(k.publicKey, 5 * LAMPORTS_PER_SOL);
      await conn.confirmTransaction(sig, "confirmed");
    }
    mint = await createMint(conn, a, a.publicKey, null, 6);
    for (const k of [a, b, c, d, stranger]) {
      const ata = await createAssociatedTokenAccount(conn, k, mint, k.publicKey);
      atas[k.publicKey.toBase58()] = ata;
      await mintTo(conn, a, mint, ata, a, 10_000);
    }
  });

  describe("validation", () => {
    it("rejects a bond smaller than one contribution", async () => {
      await expectFail(create(a, 99, { bond: 50 }), "InvalidBond");
    });

    it("rejects a period that is too short", async () => {
      const circle = circlePda(a.publicKey, 98);
      await expectFail(
        program.methods
          .createCircle(new BN(98), "x", new BN(100), new BN(200), new BN(1), new BN(0), 3, 0, false, false, PublicKey.default)
          .accountsPartial({
            creator: a.publicKey,
            mint,
            circle,
            vault: vaultPda(circle),
            bondVault: bondVaultPda(circle),
            tokenProgram: TOKEN_PROGRAM_ID,
            systemProgram: SystemProgram.programId,
          })
          .signers([a])
          .rpc(),
        "PeriodTooShort"
      );
    });
  });

  describe("full bonded circle", () => {
    const id = 1;
    let circle: PublicKey;

    before(() => {
      circle = circlePda(a.publicKey, id);
    });

    it("creates the circle and three members join, locking bonds", async () => {
      await create(a, id);
      await join(a, circle);
      await join(b, circle);
      let state = await program.account.circle.fetch(circle);
      expect(state.memberCount).to.equal(2);
      expect(JSON.stringify(state.status)).to.equal(JSON.stringify({ open: {} }));

      const sig = await join(c, circle);
      state = await program.account.circle.fetch(circle);
      expect(state.memberCount).to.equal(3);
      expect(JSON.stringify(state.status)).to.equal(JSON.stringify({ active: {} }));
      expect(await bal(bondVaultPda(circle))).to.equal(3 * BOND);
      // A non-randomized circle pays in join order.
      expect(Array.from(state.payoutOrder).slice(0, 3)).to.deep.equal([0, 1, 2]);

      const ev = await eventsOf(sig);
      const names = ev.map((e) => e.name);
      expect(names).to.include("orderdrawn");
      expect(names).to.include("memberjoined");
      expect(ev.find((e) => e.name === "memberjoined")!.data.started).to.equal(true);
    });

    it("rejects a fourth member (circle full)", async () => {
      await expectFail(join(stranger, circle), "NotOpen");
    });

    it("blocks abuse before the round resolves", async () => {
      await contribute(a, circle);
      await expectFail(contribute(a, circle), "AlreadyResolved");
      await expectFail(cover(stranger, circle, c.publicKey), "WindowStillOpen");
      await expectFail(payout(stranger, circle, a.publicKey), "RoundNotResolved");
      await expectFail(claim(a, circle), "NotCompleted");
    });

    it("round 0: bond covers the member who never paid, and events prove it", async () => {
      await contribute(b, circle); // c never pays
      await sleep((PERIOD + GRACE + 2) * 1000);

      const coverSig = await cover(stranger, circle, c.publicKey); // anyone can crank
      const missed = (await eventsOf(coverSig)).find((e) => e.name === "misscovered")!;
      expect(missed.data.wallet.toBase58()).to.equal(c.publicKey.toBase58());
      expect(missed.data.covered.toNumber()).to.equal(CONTRIBUTION);
      expect(missed.data.shortfall.toNumber()).to.equal(0);

      const cm = await program.account.member.fetch(memberPda(circle, c.publicKey));
      expect(cm.bondUsed.toNumber()).to.equal(CONTRIBUTION);
      expect(cm.missed).to.equal(1);
      const cs = await program.account.kinScore.fetch(scorePda(c.publicKey));
      expect(cs.missed).to.equal(1);
      expect(cs.streak).to.equal(0);

      await expectFail(payout(stranger, circle, b.publicKey), "WrongRecipient");

      const before = await bal(ataOf(a));
      const paySig = await payout(stranger, circle, a.publicKey);
      expect((await bal(ataOf(a))) - before).to.equal(3 * CONTRIBUTION); // full pot despite the miss

      const paid = (await eventsOf(paySig)).find((e) => e.name === "paidout")!;
      expect(paid.data.amount.toNumber()).to.equal(3 * CONTRIBUTION);
      expect(paid.data.recipient.toBase58()).to.equal(a.publicKey.toBase58());
      expect(paid.data.round).to.equal(0);
    });

    it("rounds 1 and 2: everyone pays on time, payouts rotate", async () => {
      for (const recipient of [b, c]) {
        await contribute(a, circle);
        await contribute(b, circle);
        await contribute(c, circle);
        await sleep((PERIOD + 1) * 1000);
        const before = await bal(ataOf(recipient));
        await payout(stranger, circle, recipient.publicKey);
        expect((await bal(ataOf(recipient))) - before).to.equal(3 * CONTRIBUTION);
      }
      const state = await program.account.circle.fetch(circle);
      expect(JSON.stringify(state.status)).to.equal(JSON.stringify({ completed: {} }));
    });

    it("returns unused bonds and updates completion score", async () => {
      const balA = await bal(ataOf(a));
      await claim(a, circle);
      expect((await bal(ataOf(a))) - balA).to.equal(BOND);
      await claim(b, circle);

      const balC = await bal(ataOf(c));
      await claim(c, circle);
      expect((await bal(ataOf(c))) - balC).to.equal(BOND - CONTRIBUTION);

      await expectFail(claim(a, circle), "BondAlreadyClaimed");

      expect((await program.account.kinScore.fetch(scorePda(a.publicKey))).circlesCompleted).to.equal(1);
      expect((await program.account.kinScore.fetch(scorePda(c.publicKey))).circlesCompleted).to.equal(0);
      expect(await bal(vaultPda(circle))).to.equal(0);
      expect(await bal(bondVaultPda(circle))).to.equal(0);
    });
  });

  describe("late payments and exhausted bonds", () => {
    const id = 3;
    let circle: PublicKey;
    const score = (w: Keypair) => program.account.kinScore.fetch(scorePda(w.publicKey));

    before(() => {
      circle = circlePda(a.publicKey, id);
    });

    it("a payment inside the grace window counts as late and resets the streak", async () => {
      await create(a, id, { bond: CONTRIBUTION });
      await join(a, circle);
      await join(b, circle);
      await join(c, circle);

      const lateBefore = (await score(b)).late;
      await contribute(a, circle);
      await contribute(c, circle);
      await sleep((PERIOD + 1) * 1000);
      await contribute(b, circle);

      const s = await score(b);
      expect(s.late).to.equal(lateBefore + 1);
      expect(s.streak).to.equal(0);
      expect((await program.account.member.fetch(memberPda(circle, b.publicKey))).late).to.equal(1);

      await payout(stranger, circle, a.publicKey);
    });

    it("once the bond is spent, further misses shrink the pot instead of blocking the circle", async () => {
      await contribute(a, circle);
      await contribute(b, circle);
      await sleep((PERIOD + GRACE + 2) * 1000);
      await cover(stranger, circle, c.publicKey);
      const balB = await bal(ataOf(b));
      await payout(stranger, circle, b.publicKey);
      expect((await bal(ataOf(b))) - balB).to.equal(3 * CONTRIBUTION);
      expect((await program.account.member.fetch(memberPda(circle, c.publicKey))).bondUsed.toNumber()).to.equal(CONTRIBUTION);

      await contribute(a, circle);
      await contribute(b, circle);
      await sleep((PERIOD + GRACE + 2) * 1000);
      const shortSig = await cover(stranger, circle, c.publicKey);
      const short = (await eventsOf(shortSig)).find((e) => e.name === "misscovered")!;
      expect(short.data.covered.toNumber()).to.equal(0);
      expect(short.data.shortfall.toNumber()).to.equal(CONTRIBUTION);

      const balC = await bal(ataOf(c));
      await payout(stranger, circle, c.publicKey);
      expect((await bal(ataOf(c))) - balC).to.equal(2 * CONTRIBUTION);

      const cm = await program.account.member.fetch(memberPda(circle, c.publicKey));
      expect(cm.missed).to.equal(2);
      const state = await program.account.circle.fetch(circle);
      expect(JSON.stringify(state.status)).to.equal(JSON.stringify({ completed: {} }));

      const before = await bal(ataOf(c));
      await claim(c, circle);
      expect(await bal(ataOf(c))).to.equal(before);
      await claim(a, circle);
      await claim(b, circle);
      expect(await bal(bondVaultPda(circle))).to.equal(0);
      expect(await bal(vaultPda(circle))).to.equal(0);
    });
  });

  describe("verifiable random payout order", () => {
    it("draws an order anyone can recompute from the seed stored on-chain", async () => {
      const id = 7;
      const circle = circlePda(a.publicKey, id);
      await create(a, id, { members: 4, randomize: true });
      await join(a, circle);
      await join(b, circle);
      await join(c, circle);
      const sig = await join(d, circle);

      const state = await program.account.circle.fetch(circle);
      const order = Array.from(state.payoutOrder).slice(0, 4);
      expect([...order].sort()).to.deep.equal([0, 1, 2, 3]); // a valid permutation
      expect(state.orderSlot.toNumber()).to.be.greaterThan(0);

      // Recompute the shuffle independently from the seed. It must match the chain exactly.
      expect(drawOrder(Buffer.from(state.orderSeed), 4)).to.deep.equal(order);

      const drawn = (await eventsOf(sig)).find((e) => e.name === "orderdrawn")!;
      expect(drawn.data.randomized).to.equal(true);
      expect(Buffer.from(drawn.data.seed).equals(Buffer.from(state.orderSeed))).to.equal(true);
      expect(Array.from(drawn.data.order).slice(0, 4)).to.deep.equal(order);
    });

    it("pays the member the drawn order names, and nobody else", async () => {
      const id = 8;
      const circle = circlePda(a.publicKey, id);
      await create(a, id, { members: 2, randomize: true });
      await join(a, circle);
      await join(b, circle);
      const state = await program.account.circle.fetch(circle);
      const first = state.payoutOrder[0] === 0 ? a : b;
      const second = first === a ? b : a;
      await contribute(a, circle);
      await contribute(b, circle);
      await sleep((PERIOD + 1) * 1000);
      await expectFail(payout(stranger, circle, second.publicKey), "WrongRecipient");
      const before = await bal(ataOf(first));
      await payout(stranger, circle, first.publicKey);
      expect((await bal(ataOf(first))) - before).to.equal(2 * CONTRIBUTION);
    });
  });

  describe("autopay allowance", () => {
    const id = 6;
    let circle: PublicKey;

    before(() => {
      circle = circlePda(a.publicKey, id);
    });

    it("collects a pre-approved contribution and counts it on time", async () => {
      await create(a, id, { members: 2 });
      await join(a, circle);
      await join(b, circle);

      // a approves the shared autopay delegate for exactly their total dues (2 rounds).
      await approve(conn, a, ataOf(a), autopayPda, a, 2 * CONTRIBUTION);
      const onTimeBefore = (await program.account.kinScore.fetch(scorePda(a.publicKey))).onTime;

      const sig = await collect(stranger, circle, a); // a stranger cranks it, a's phone can be off
      const ev = (await eventsOf(sig)).find((e) => e.name === "contributed")!;
      expect(ev.data.autopay).to.equal(true);
      expect(ev.data.onTime).to.equal(true);
      expect(ev.data.wallet.toBase58()).to.equal(a.publicKey.toBase58());

      const acct = await getAccount(conn, ataOf(a));
      expect(Number(acct.delegatedAmount)).to.equal(CONTRIBUTION); // allowance shrank by one contribution
      expect((await program.account.kinScore.fetch(scorePda(a.publicKey))).onTime).to.equal(onTimeBefore + 1);
      expect(await bal(vaultPda(circle))).to.equal(CONTRIBUTION);
    });

    it("cannot collect twice in one round", async () => {
      await expectFail(collect(stranger, circle, a), "AlreadyResolved");
    });

    it("cannot collect from a member who never approved autopay", async () => {
      await expectFail(collect(stranger, circle, b), "NotDelegated");
    });

    it("a member can still pay by hand, and the round pays out", async () => {
      await contribute(b, circle);
      await sleep((PERIOD + 1) * 1000);
      const before = await bal(ataOf(a));
      await payout(stranger, circle, a.publicKey);
      expect((await bal(ataOf(a))) - before).to.equal(2 * CONTRIBUTION);
    });

    it("refuses to collect when the allowance is too small", async () => {
      await approve(conn, a, ataOf(a), autopayPda, a, CONTRIBUTION - 1);
      await expectFail(collect(stranger, circle, a), "AllowanceTooLow");
    });

    it("revoking the allowance stops collection", async () => {
      await approve(conn, a, ataOf(a), Keypair.generate().publicKey, a, 0); // points the delegate elsewhere
      await expectFail(collect(stranger, circle, a), "NotDelegated");
    });
  });

  describe("Seeker-verified circles", () => {
    const seekerAuthority = Keypair.generate(); // stands in for the real Genesis Token mint authority
    const impostorAuthority = Keypair.generate();
    let sgtA: { token: PublicKey; mint: PublicKey };
    let sgtB: { token: PublicKey; mint: PublicKey };
    let fake: { token: PublicKey; mint: PublicKey };

    // Seeker Genesis Tokens are Token-2022 tokens, one mint per holder.
    async function issueToken(owner: Keypair, authority: Keypair) {
      const m = await createMint(conn, a, authority.publicKey, null, 0, undefined, undefined, TOKEN_2022_PROGRAM_ID);
      const token = await createAssociatedTokenAccount(conn, a, m, owner.publicKey, undefined, TOKEN_2022_PROGRAM_ID);
      await mintTo(conn, a, m, token, authority, 1, [], undefined, TOKEN_2022_PROGRAM_ID);
      return { token, mint: m };
    }

    before(async () => {
      sgtA = await issueToken(a, seekerAuthority);
      sgtB = await issueToken(b, seekerAuthority);
      fake = await issueToken(c, impostorAuthority);
    });

    it("lets holders of a real Genesis Token in, and only them", async () => {
      const id = 9;
      const circle = circlePda(a.publicKey, id);
      await create(a, id, { members: 3, seekerOnly: true, seekerAuthority: seekerAuthority.publicKey });

      await expectFail(join(a, circle), "SeekerRequired"); // no token supplied
      await join(a, circle, sgtA);
      await expectFail(join(b, circle, sgtA), "SeekerRequired"); // someone else's token
      await expectFail(join(c, circle, fake), "SeekerRequired"); // token from the wrong authority
      await expectFail(join(stranger, circle, sgtB), "SeekerRequired"); // b's token, not stranger's
      await join(b, circle, sgtB);

      const state = await program.account.circle.fetch(circle);
      expect(state.memberCount).to.equal(2);
      expect(state.seekerOnly).to.equal(true);
    });

    it("rejects a classic token account passed as the Genesis Token", async () => {
      const id = 10;
      const circle = circlePda(a.publicKey, id);
      await create(a, id, { members: 2, seekerOnly: true, seekerAuthority: seekerAuthority.publicKey });
      await expectFail(join(a, circle, { token: ataOf(a), mint }), "SeekerRequired");
    });

    it("does not ask ordinary circles for a token", async () => {
      const id = 11;
      const circle = circlePda(a.publicKey, id);
      await create(a, id, { members: 2 });
      await join(stranger, circle);
    });
  });

  describe("open circles", () => {
    it("cannot refund a bond before the join window expires", async () => {
      const circle = circlePda(a.publicKey, 4);
      await create(a, 4);
      await join(a, circle);
      await expectFail(
        program.methods
          .refundOpen()
          .accountsPartial({
            wallet: a.publicKey,
            circle,
            member: memberPda(circle, a.publicKey),
            score: scorePda(a.publicKey),
            walletToken: ataOf(a),
            bondVault: bondVaultPda(circle),
            tokenProgram: TOKEN_PROGRAM_ID,
          })
          .signers([a])
          .rpc(),
        "JoinWindowOpen"
      );
    });
  });

  describe("score gate", () => {
    it("blocks a wallet with a missed payment from a strict circle", async () => {
      const id = 2;
      const circle = circlePda(a.publicKey, id);
      await create(a, id, { maxMissed: 0 });
      await join(a, circle);
      await expectFail(join(c, circle), "ScoreTooLow"); // c has missed payments on record
      await join(b, circle); // clean history is fine
    });
  });
});
