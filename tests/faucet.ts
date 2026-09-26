import * as anchor from "@coral-xyz/anchor";
import { Program } from "@coral-xyz/anchor";
import {
  createMint,
  getAccount,
  getAssociatedTokenAddressSync,
  TOKEN_PROGRAM_ID,
  ASSOCIATED_TOKEN_PROGRAM_ID,
} from "@solana/spl-token";
import { Keypair, PublicKey, SystemProgram, LAMPORTS_PER_SOL, Transaction } from "@solana/web3.js";
import { expect } from "chai";
import { KinFaucet } from "../target/types/kin_faucet";

describe("kin_faucet", () => {
  const provider = anchor.AnchorProvider.env();
  anchor.setProvider(provider);
  const program = anchor.workspace.KinFaucet as Program<KinFaucet>;
  const conn = provider.connection;
  const payer = (provider.wallet as anchor.Wallet).payer;

  const pda = (seed: string) => PublicKey.findProgramAddressSync([Buffer.from(seed)], program.programId)[0];
  const mintAuthority = pda("mint-auth");
  const solVault = pda("sol");

  let mint: PublicKey;

  const fund = async (to: PublicKey, lamports: number) => {
    await provider.sendAndConfirm(
      new Transaction().add(SystemProgram.transfer({ fromPubkey: payer.publicKey, toPubkey: to, lamports })),
    );
  };

  const accountsFor = (user: PublicKey, m: PublicKey) => ({
    user,
    mint: m,
    mintAuthority,
    userToken: getAssociatedTokenAddressSync(m, user),
    record: PublicKey.findProgramAddressSync(
      [Buffer.from("claim"), user.toBuffer(), m.toBuffer()],
      program.programId,
    )[0],
    tokenProgram: TOKEN_PROGRAM_ID,
    associatedTokenProgram: ASSOCIATED_TOKEN_PROGRAM_ID,
    systemProgram: SystemProgram.programId,
  });

  const refuelAndClaim = async (user: Keypair, m: PublicKey) => {
    const refuel = await program.methods
      .refuel()
      .accounts({ user: user.publicKey, solVault, systemProgram: SystemProgram.programId } as any)
      .instruction();
    const claim = await program.methods.claim().accounts(accountsFor(user.publicKey, m) as any).instruction();
    return provider.sendAndConfirm(new Transaction().add(refuel, claim), [user]);
  };

  before(async () => {
    // The test token is controlled by the faucet's PDA, exactly as on devnet.
    mint = await createMint(conn, payer, mintAuthority, null, 6);
    await fund(solVault, 1 * LAMPORTS_PER_SOL);
  });

  it("gives an empty wallet some SOL and test tokens in one transaction", async () => {
    const user = Keypair.generate();
    await fund(user.publicKey, 1_000_000); // too little to even create an account
    const before = await conn.getBalance(user.publicKey);
    await refuelAndClaim(user, mint);

    const after = await conn.getBalance(user.publicKey);
    expect(after).to.be.greaterThan(before);
    const token = await getAccount(conn, getAssociatedTokenAddressSync(mint, user.publicKey));
    expect(token.amount.toString()).to.equal("500000000");
  });

  it("refuses a second claim during the cooldown", async () => {
    const user = Keypair.generate();
    await fund(user.publicKey, 100_000_000);
    await refuelAndClaim(user, mint);
    let failed = false;
    try {
      await refuelAndClaim(user, mint);
    } catch (e: any) {
      failed = true;
      expect(String(e)).to.match(/TooSoon|already claimed/);
    }
    expect(failed).to.equal(true);
    const token = await getAccount(conn, getAssociatedTokenAddressSync(mint, user.publicKey));
    expect(token.amount.toString()).to.equal("500000000");
  });

  it("does not top up a wallet that already has SOL", async () => {
    const user = Keypair.generate();
    await fund(user.publicKey, 200_000_000);
    const vaultBefore = await conn.getBalance(solVault);
    await refuelAndClaim(user, mint);
    expect(await conn.getBalance(solVault)).to.equal(vaultBefore);
  });

  it("only works for a token the faucet controls", async () => {
    const foreign = await createMint(conn, payer, payer.publicKey, null, 6);
    const user = Keypair.generate();
    await fund(user.publicKey, 200_000_000);
    let failed = false;
    try {
      await refuelAndClaim(user, foreign);
    } catch (e: any) {
      failed = true;
      expect(String(e)).to.match(/WrongMint|does not control/);
    }
    expect(failed).to.equal(true);
  });
});
