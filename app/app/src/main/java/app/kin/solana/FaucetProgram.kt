package app.kin.solana

/**
 * Client for the devnet test faucet (programs/kin_faucet). It hands out test tokens and a little SOL
 * so anyone can try Kin without asking us for funds. It does not exist on mainnet.
 */
object FaucetProgram {
    val PROGRAM_ID = PublicKey.fromBase58("G5MhE85BTiTqLPinKZBg7jh4sNMyTc7WUvGcfMerWsig")

    private fun pda(vararg seeds: ByteArray) = Pda.find(seeds.toList(), PROGRAM_ID).first

    val MINT_AUTHORITY: PublicKey by lazy { pda("mint-auth".toByteArray()) }
    val SOL_VAULT: PublicKey by lazy { pda("sol".toByteArray()) }

    fun claimRecord(user: PublicKey, mint: PublicKey) = pda("claim".toByteArray(), user.bytes, mint.bytes)

    /** Tops up an almost empty wallet with SOL. Does nothing for wallets that already have some. */
    fun refuel(user: PublicKey): Instruction = Instruction(
        PROGRAM_ID,
        listOf(
            AccountMeta(user, isSigner = true, isWritable = true),
            AccountMeta(SOL_VAULT, isSigner = false, isWritable = true),
            AccountMeta(KinProgram.SYSTEM_PROGRAM, isSigner = false, isWritable = false),
        ),
        KinProgram.instructionDiscriminator("refuel"),
    )

    /** Mints test tokens to the user's token account, creating it if needed. */
    fun claim(user: PublicKey, mint: PublicKey): Instruction = Instruction(
        PROGRAM_ID,
        listOf(
            AccountMeta(user, isSigner = true, isWritable = true),
            AccountMeta(mint, isSigner = false, isWritable = true),
            AccountMeta(MINT_AUTHORITY, isSigner = false, isWritable = false),
            AccountMeta(KinProgram.associatedTokenAddress(user, mint), isSigner = false, isWritable = true),
            AccountMeta(claimRecord(user, mint), isSigner = false, isWritable = true),
            AccountMeta(KinProgram.TOKEN_PROGRAM, isSigner = false, isWritable = false),
            AccountMeta(KinProgram.ATA_PROGRAM, isSigner = false, isWritable = false),
            AccountMeta(KinProgram.SYSTEM_PROGRAM, isSigner = false, isWritable = false),
        ),
        KinProgram.instructionDiscriminator("claim"),
    )

    /** Turns the faucet's error codes into words. Anchor numbers custom errors from 6000. */
    fun explain(rejection: String?): String? = when {
        rejection == null -> null
        rejection.contains("TooSoon") || rejection.contains("0x1771") ->
            "You already claimed a moment ago. Try again in a few minutes."
        rejection.contains("VaultEmpty") || rejection.contains("0x1772") ->
            "The faucet is out of SOL. Get devnet SOL from faucet.solana.com, then try again."
        rejection.contains("0x1770") -> "This faucet does not control that token."
        else -> null
    }
}
