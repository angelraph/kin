import * as anchor from "@coral-xyz/anchor";
import { Program, BN } from "@coral-xyz/anchor";
import {
  createMint,
  createAssociatedTokenAccount,
  mintTo,
  getAccount,
  getAssociatedTokenAddressSync,
  TOKEN_PROGRAM_ID,
  ASSOCIATED_TOKEN_PROGRAM_ID,
} from "@solana/spl-token";
import { Keypair, PublicKey, SystemProgram, LAMPORTS_PER_SOL } from "@solana/web3.js";
import { expect } from "chai";
import { Kin } from "../target/types/kin";

const CONTRIBUTION = 100;
const BOND = 200;
const PERIOD = 10; // seconds (program minimum)
const GRACE = 5;

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

describe("kin", () => {
  const provider = anchor.AnchorProvider.env();
  anchor.setProvider(provider);
  const program = anchor.workspace.Kin as Program<Kin>;
  const conn = provider.connection;

  let mint: PublicKey;
  const [a, b, c, stranger] = [1, 2, 3, 4].map(() => Keypair.generate());
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
  const bal = async (ata: PublicKey) => Number((await getAccount(conn, ata)).amount);

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

  const create = (creator: Keypair, id: number, maxMissed = 100, bond = BOND) =>
    program.methods
      .createCircle(
        new BN(id),
        "Test circle",
        new BN(CONTRIBUTION),
        new BN(bond),
        new BN(PERIOD),
        new BN(GRACE),
        3,
        maxMissed
      )
      .accountsPartial({
        creator: creator.publicKey,
        mint,
        circle: circlePda(creator.publicKey, id),
        vault: vaultPda(circlePda(creator.publicKey, id)),
        bondVault: bondVaultPda(circlePda(creator.publicKey, id)),
        tokenProgram: TOKEN_PROGRAM_ID,
        systemProgram: SystemProgram.programId,
      })
      .signers([creator])
      .rpc();

  const join = (w: Keypair, circle: PublicKey) =>
    program.methods
      .joinCircle()
      .accountsPartial({
        wallet: w.publicKey,
        circle,
        member: memberPda(circle, w.publicKey),
        score: scorePda(w.publicKey),
        walletToken: atas[w.publicKey.toBase58()],
        bondVault: bondVaultPda(circle),
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
        walletToken: atas[w.publicKey.toBase58()],
        vault: vaultPda(circle),
        tokenProgram: TOKEN_PROGRAM_ID,
      })
      .signers([w])
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
        walletToken: atas[w.publicKey.toBase58()],
        bondVault: bondVaultPda(circle),
        tokenProgram: TOKEN_PROGRAM_ID,
      })
      .signers([w])
      .rpc();

  before(async () => {
    for (const k of [a, b, c, stranger]) {
      const sig = await conn.requestAirdrop(k.publicKey, 5 * LAMPORTS_PER_SOL);
      await conn.confirmTransaction(sig, "confirmed");
    }
    mint = await createMint(conn, a, a.publicKey, null, 6);
    for (const k of [a, b, c, stranger]) {
      const ata = await createAssociatedTokenAccount(conn, k, mint, k.publicKey);
      atas[k.publicKey.toBase58()] = ata;
      await mintTo(conn, a, mint, ata, a, 10_000);
    }
  });

  describe("validation", () => {
    it("rejects a bond smaller than one contribution", async () => {
      await expectFail(
        program.methods
          .createCircle(new BN(99), "x", new BN(100), new BN(50), new BN(PERIOD), new BN(GRACE), 3, 0)
          .accountsPartial({
            creator: a.publicKey,
            mint,
            circle: circlePda(a.publicKey, 99),
            vault: vaultPda(circlePda(a.publicKey, 99)),
            bondVault: bondVaultPda(circlePda(a.publicKey, 99)),
            tokenProgram: TOKEN_PROGRAM_ID,
            systemProgram: SystemProgram.programId,
          })
          .signers([a])
          .rpc(),
        "InvalidBond"
      );
    });

    it("rejects a period that is too short", async () => {
      await expectFail(
        program.methods
          .createCircle(new BN(98), "x", new BN(100), new BN(200), new BN(1), new BN(0), 3, 0)
          .accountsPartial({
            creator: a.publicKey,
            mint,
            circle: circlePda(a.publicKey, 98),
            vault: vaultPda(circlePda(a.publicKey, 98)),
            bondVault: bondVaultPda(circlePda(a.publicKey, 98)),
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

      await join(c, circle);
      state = await program.account.circle.fetch(circle);
      expect(state.memberCount).to.equal(3);
      expect(JSON.stringify(state.status)).to.equal(JSON.stringify({ active: {} }));
      expect(await bal(bondVaultPda(circle))).to.equal(3 * BOND);
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

    it("round 0: bond covers the member who never paid, pot still pays out", async () => {
      await contribute(b, circle); // c never pays
      await sleep((PERIOD + GRACE + 2) * 1000);

      await cover(stranger, circle, c.publicKey); // anyone can crank

      const cm = await program.account.member.fetch(memberPda(circle, c.publicKey));
      expect(cm.bondUsed.toNumber()).to.equal(CONTRIBUTION);
      expect(cm.missed).to.equal(1);
      const cs = await program.account.kinScore.fetch(scorePda(c.publicKey));
      expect(cs.missed).to.equal(1);
      expect(cs.streak).to.equal(0);

      await expectFail(payout(stranger, circle, b.publicKey), "WrongRecipient");

      const before = await bal(atas[a.publicKey.toBase58()]);
      await payout(stranger, circle, a.publicKey);
      const after = await bal(atas[a.publicKey.toBase58()]);
      expect(after - before).to.equal(3 * CONTRIBUTION); // full pot despite the miss
    });

    it("rounds 1 and 2: everyone pays on time, payouts rotate", async () => {
      for (const recipient of [b, c]) {
        await contribute(a, circle);
        await contribute(b, circle);
        await contribute(c, circle);
        await sleep((PERIOD + 1) * 1000);
        const before = await bal(atas[recipient.publicKey.toBase58()]);
        await payout(stranger, circle, recipient.publicKey);
        const after = await bal(atas[recipient.publicKey.toBase58()]);
        expect(after - before).to.equal(3 * CONTRIBUTION);
      }
      const state = await program.account.circle.fetch(circle);
      expect(JSON.stringify(state.status)).to.equal(JSON.stringify({ completed: {} }));
    });

    it("returns unused bonds and updates completion score", async () => {
      const balA = await bal(atas[a.publicKey.toBase58()]);
      await claim(a, circle);
      expect((await bal(atas[a.publicKey.toBase58()])) - balA).to.equal(BOND);
      await claim(b, circle);

      const balC = await bal(atas[c.publicKey.toBase58()]);
      await claim(c, circle);
      expect((await bal(atas[c.publicKey.toBase58()])) - balC).to.equal(BOND - CONTRIBUTION);

      await expectFail(claim(a, circle), "BondAlreadyClaimed");

      expect((await program.account.kinScore.fetch(scorePda(a.publicKey))).circlesCompleted).to.equal(1);
      expect((await program.account.kinScore.fetch(scorePda(c.publicKey))).circlesCompleted).to.equal(0);
      // vaults are drained
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
      await create(a, id, 100, CONTRIBUTION); // bond covers exactly one miss
      await join(a, circle);
      await join(b, circle);
      await join(c, circle);

      const lateBefore = (await score(b)).late;
      await contribute(a, circle);
      await contribute(c, circle);
      await sleep((PERIOD + 1) * 1000); // past the period, inside grace
      await contribute(b, circle);

      const s = await score(b);
      expect(s.late).to.equal(lateBefore + 1);
      expect(s.streak).to.equal(0);
      expect((await program.account.member.fetch(memberPda(circle, b.publicKey))).late).to.equal(1);

      await payout(stranger, circle, a.publicKey);
    });

    it("once the bond is spent, further misses shrink the pot instead of blocking the circle", async () => {
      // round 1: c misses, bond covers it fully
      await contribute(a, circle);
      await contribute(b, circle);
      await sleep((PERIOD + GRACE + 2) * 1000);
      await cover(stranger, circle, c.publicKey);
      const balB = await bal(atas[b.publicKey.toBase58()]);
      await payout(stranger, circle, b.publicKey);
      expect((await bal(atas[b.publicKey.toBase58()])) - balB).to.equal(3 * CONTRIBUTION);
      expect((await program.account.member.fetch(memberPda(circle, c.publicKey))).bondUsed.toNumber()).to.equal(
        CONTRIBUTION
      );

      // round 2: c misses again with nothing left to cover it; the circle still completes
      await contribute(a, circle);
      await contribute(b, circle);
      await sleep((PERIOD + GRACE + 2) * 1000);
      await cover(stranger, circle, c.publicKey);
      const balC = await bal(atas[c.publicKey.toBase58()]);
      await payout(stranger, circle, c.publicKey);
      expect((await bal(atas[c.publicKey.toBase58()])) - balC).to.equal(2 * CONTRIBUTION);

      const cm = await program.account.member.fetch(memberPda(circle, c.publicKey));
      expect(cm.missed).to.equal(2);
      const state = await program.account.circle.fetch(circle);
      expect(JSON.stringify(state.status)).to.equal(JSON.stringify({ completed: {} }));

      // the defaulter gets nothing back; honest members get their bonds
      const before = await bal(atas[c.publicKey.toBase58()]);
      await claim(c, circle);
      expect(await bal(atas[c.publicKey.toBase58()])).to.equal(before);
      await claim(a, circle);
      await claim(b, circle);
      expect(await bal(bondVaultPda(circle))).to.equal(0);
      expect(await bal(vaultPda(circle))).to.equal(0);
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
            walletToken: atas[a.publicKey.toBase58()],
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
      await create(a, id, 0); // max_missed_allowed = 0
      await join(a, circle);
      await expectFail(join(c, circle), "ScoreTooLow"); // c missed once in the previous circle
      await join(b, circle); // clean history is fine
    });
  });
});
