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

    const val EXPLORER_TX = "https://explorer.solana.com/tx/%s?cluster=devnet"
    const val IDENTITY_NAME = "Kin"
    const val IDENTITY_URI = "https://kin.app"
}
