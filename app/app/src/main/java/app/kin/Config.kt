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
     * A second circle currency, for the hackathon's separate SKR integration prize (see scripts/skr-token.js).
     * On devnet this is our own test token, labelled "tSKR" so it is never mistaken for the real $SKR.
     * Swap in the real SKR mint for mainnet. Its mint authority is the kin_faucet program's PDA, the same
     * as tUSDC, so both tokens are claimed the same way.
     */
    val SKR_MINT: PublicKey = PublicKey.fromBase58("DYe6xo6h5UWXn3JU8eFFa6QUHKkWftag2Z1CvLaCndVb")
    const val SKR_SYMBOL = "tSKR"

    /** Every token a circle can be denominated in, in the order offered when creating one. */
    val SUPPORTED_MINTS: List<PublicKey> = listOf(MINT, SKR_MINT)

    fun tokenSymbol(mint: PublicKey): String = if (mint == SKR_MINT) SKR_SYMBOL else TOKEN_SYMBOL

    /**
     * Mint authority a Seeker Genesis Token must have. On mainnet this is Solana Mobile's real authority,
     * GT2zuHVaZQYZSyQMgJPLzvkmyztfyXg2NJunqFp4p3A4. On devnet it is our own test authority
     * (see scripts/seeker-test.js), because the real one only exists on mainnet.
     */
    val SEEKER_AUTHORITY: PublicKey = PublicKey.fromBase58("8LVUyuikCE2hBhb8uveLNXoCxN4Ty2Q2so5caDTQEVrN")

    const val EXPLORER_TX = "https://explorer.solana.com/tx/%s?cluster=devnet"
    const val EXPLORER_ADDRESS = "https://explorer.solana.com/address/%s?cluster=devnet"

    /** Compute budget for every transaction Kin sends. The price is a few thousandths of a cent. */
    const val COMPUTE_UNIT_LIMIT = 400_000
    const val PRIORITY_MICRO_LAMPORTS = 2_000L
    const val IDENTITY_NAME = "Kin"

    /**
     * The web identity wallets show and verify. Mobile Wallet Adapter wallets fetch
     * /.well-known/assetlinks.json here and only authorize an app whose package and signing certificate
     * are listed in it. The file is served from the angelraph.github.io repository. When the app is
     * signed with a release key, add that key's SHA-256 fingerprint to the file alongside the debug one.
     */
    const val IDENTITY_URI = "https://angelraph.github.io"
}
