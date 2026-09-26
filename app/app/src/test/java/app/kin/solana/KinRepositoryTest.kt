package app.kin.solana

import org.junit.Assert.assertEquals
import org.junit.Test

class KinRepositoryTest {
    private fun key(seed: Int) = PublicKey(ByteArray(32) { seed.toByte() })

    private fun circle(seed: Int, createdTs: Long) = CircleData(
        address = key(seed), creator = key(1), circleId = seed.toLong(), mint = key(7),
        contribution = 100, bond = 200, periodSecs = 60, graceSecs = 15, maxMembers = 2, memberCount = 0,
        maxMissedAllowed = 100, status = CircleStatus.Open, currentRound = 0, roundStartTs = 0,
        resolvedCount = 0, roundPot = 0, createdTs = createdTs, randomize = false, seekerOnly = false,
        seekerAuthority = PublicKey.DEFAULT, orderSlot = 0, orderSeed = ByteArray(32),
        payoutOrder = List(12) { 0 }, name = "c$seed",
    )

    @Test
    fun aCircleYouCreatedButHaveNotJoinedStillAppears() {
        val created = circle(seed = 2, createdTs = 500)
        val merged = KinRepository.mergeCircles(joined = emptyList(), created = listOf(created))
        assertEquals(listOf(created.address), merged.map { it.address })
    }

    @Test
    fun aCircleYouCreatedAndJoinedAppearsOnce() {
        val both = circle(seed = 2, createdTs = 500)
        val merged = KinRepository.mergeCircles(joined = listOf(both), created = listOf(both))
        assertEquals(1, merged.size)
    }

    @Test
    fun theNewestCircleComesFirst() {
        val old = circle(seed = 2, createdTs = 100)
        val newer = circle(seed = 3, createdTs = 900)
        val middle = circle(seed = 4, createdTs = 400)
        val merged = KinRepository.mergeCircles(joined = listOf(old, middle), created = listOf(newer))
        assertEquals(listOf(3L, 4L, 2L), merged.map { it.circleId })
    }
}
