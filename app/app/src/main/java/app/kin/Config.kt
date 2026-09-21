package app.kin

import app.kin.solana.PublicKey

/** Single place for cluster and token settings so devnet -> mainnet is a one-file change. */
object Config {
    const val RPC_URL = "https://api.devnet.solana.com"
    const val CLUSTER_LABEL = "Devnet"

    /**
     * Token circles are denominated in. On devnet this is our own test token (see scripts/demo-token.js),
     * labelled "tUSDC" so it is never mistaken for real USDC. Swap in the real USDC/SKR mint for mainnet.
     */
    val MINT: PublicKey = PublicKey.fromBase58("3xicv3CvScBBpdsM1LBH1xbm1YNhUWFDhCDxosHEzZR5")
    const val TOKEN_SYMBOL = "tUSDC"
    const val TOKEN_DECIMALS = 6

    /**
     * Mint authority a Seeker Genesis Token must have. On mainnet this is Solana Mobile's real authority,
     * GT2zuHVaZQYZSyQMgJPLzvkmyztfyXg2NJunqFp4p3A4. On devnet it is our own test authority
     * (see scripts/seeker-test.js), because the real one only exists on mainnet.
     */
    val SEEKER_AUTHORITY: PublicKey = PublicKey.fromBase58("8LVUyuikCE2hBhb8uveLNXoCxN4Ty2Q2so5caDTQEVrN")

    const val EXPLORER_TX = "https://explorer.solana.com/tx/%s?cluster=devnet"
    const val EXPLORER_ADDRESS = "https://explorer.solana.com/address/%s?cluster=devnet"

    /** Compute budget for every transaction Kin sends. The price is a few thousandths of a cent. */
    const val COMPUTE_UNIT_LIMIT = 300_000
    const val PRIORITY_MICRO_LAMPORTS = 2_000L
    const val IDENTITY_NAME = "Kin"
    const val IDENTITY_URI = "https://kin.app"
}
