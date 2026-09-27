package app.kin.watch

import app.kin.solana.Allowance
import app.kin.solana.CircleData
import app.kin.solana.CircleStatus
import app.kin.solana.MemberData
import app.kin.solana.PublicKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSnapshotTest {
    private fun key(seed: Int) = PublicKey(ByteArray(32) { seed.toByte() })
    private val me = key(1)
    private val bob = key(2)

    private val start = 1_000_000L
    private val period = 100L
    private val grace = 25L

    private fun circle(status: CircleStatus = CircleStatus.Active, round: Int = 0, resolved: Int = 0) = CircleData(
        address = key(9), creator = me, circleId = 1, mint = key(7),
        contribution = 100, bond = 200, periodSecs = period, graceSecs = grace,
        maxMembers = 2, memberCount = 2, maxMissedAllowed = 100, status = status,
        currentRound = round, roundStartTs = start, resolvedCount = resolved, roundPot = 200, createdTs = start - 10,
        randomize = false, seekerOnly = false, seekerAuthority = PublicKey.DEFAULT, orderSlot = 0,
        orderSeed = ByteArray(32), payoutOrder = listOf(0, 1), name = "Family",
    )

    private fun member(wallet: PublicKey, index: Int, resolvedRounds: Int = 0) = MemberData(
        address = key(50 + index), circle = key(9), wallet = wallet, index = index,
        bondLocked = 200, bondUsed = 0, roundsResolved = resolvedRounds, received = false, bondClaimed = false,
        onTime = 0, late = 0, missed = 0,
    )

    private val noAllowance = emptyMap<PublicKey, Allowance>()

    @Test
    fun aggregateFallsBackWhenThereIsNothingActive() {
        assertEquals("No circles yet", aggregateSnapshot(emptyList(), start).headline)
        val done = circle(status = CircleStatus.Completed)
        assertEquals("All circles settled", aggregateSnapshot(listOf(done), start).headline)
    }

    @Test
    fun aggregatePicksTheOpenCircleWhenNoneAreActive() {
        val open = circle(status = CircleStatus.Open)
        val s = aggregateSnapshot(listOf(open), start)
        assertEquals("Waiting for members", s.headline)
        assertTrue(s.showProgress)
    }

    @Test
    fun aggregateShowsRoundProgressForAnActiveCircle() {
        val active = circle(round = 0, resolved = 1)
        val s = aggregateSnapshot(listOf(active), start)
        assertEquals("Round 1 of 2", s.headline)
        assertEquals(1, s.resolved)
        assertEquals(2, s.total)
    }

    @Test
    fun aggregateAnnouncesAReadyPayout() {
        val active = circle(round = 0, resolved = 2)
        val s = aggregateSnapshot(listOf(active), now = active.roundEndTs)
        assertEquals("Payout is ready", s.headline)
    }

    @Test
    fun personalSnapshotIsNullForAnythingNotActive() {
        val open = circle(status = CircleStatus.Open)
        assertNull(personalSnapshot(me, open, listOf(member(me, 0)), noAllowance, start))
    }

    @Test
    fun personalSnapshotTellsMeToPayBeforeAnythingElse() {
        val c = circle()
        val members = listOf(member(me, 0), member(bob, 1))
        val s = personalSnapshot(me, c, members, noAllowance, start + 1)!!
        assertTrue(s.headline.startsWith("Pay"))
    }

    @Test
    fun personalSnapshotFlagsMyOwnReadyPayout() {
        val c = circle(resolved = 2)
        val members = listOf(member(me, 0, resolvedRounds = 1), member(bob, 1, resolvedRounds = 1))
        val s = personalSnapshot(me, c, members, noAllowance, now = c.roundEndTs)!!
        assertEquals("Your pot is ready", s.headline)
    }

    @Test
    fun bestOfPrefersPayingOverAGenericUpdate() {
        val urgent = WidgetSnapshot(headline = "Pay 10 tUSDC")
        val quiet = WidgetSnapshot(headline = "Round 2 of 5")
        assertEquals(urgent, bestOf(listOf(quiet, urgent)))
        assertNull(bestOf(emptyList()))
    }
}
