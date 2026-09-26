package app.kin.ui

import app.kin.solana.CircleData
import app.kin.solana.CircleStatus
import app.kin.solana.MemberData
import app.kin.solana.PublicKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiLogicTest {
    private fun key(seed: Int) = PublicKey(ByteArray(32) { seed.toByte() })

    private fun circle(status: CircleStatus, round: Int, order: List<Int>) = CircleData(
        address = key(9), creator = key(1), circleId = 1, mint = key(7),
        contribution = 100_000_000, bond = 200_000_000, periodSecs = 1000, graceSecs = 250,
        maxMembers = 4, memberCount = 4, maxMissedAllowed = 100, status = status,
        currentRound = round, roundStartTs = 5_000, resolvedCount = 0, roundPot = 0, createdTs = 4_000,
        randomize = false, seekerOnly = false, seekerAuthority = PublicKey.DEFAULT, orderSlot = 0,
        orderSeed = ByteArray(32), payoutOrder = order + List(12 - order.size) { 0 }, name = "Test",
    )

    private fun member(index: Int) = MemberData(
        address = key(50 + index), circle = key(9), wallet = key(index), index = index,
        bondLocked = 200, bondUsed = 0, roundsResolved = 0, received = false, bondClaimed = false,
        onTime = 0, late = 0, missed = 0,
    )

    @Test
    fun everyTemplateFitsTheProgramLimits() {
        val ids = Templates.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        Templates.all.forEach { t ->
            assertTrue("${t.id} members", t.members in 2..12)
            assertTrue("${t.id} bond", t.bondMultiple in 1..t.members)
            assertTrue("${t.id} pot", t.contribution.toLong() * t.members <= 1_000)
            assertTrue("${t.id} period", t.periodSecs >= 10)
            assertTrue("${t.id} name fits", t.name.length <= 32)
            assertNotNull(Templates.byId(t.id))
        }
    }

    @Test
    fun spanLabelUsesTheNaturalUnit() {
        assertEquals("5 weeks", spanLabel(5, 604_800))
        assertEquals("1 day", spanLabel(1, 86_400))
        assertEquals("3 minutes", spanLabel(3, 60))
        assertEquals("12 months", spanLabel(12, 2_592_000))
        assertEquals("2 weeks", spanLabel(2, 604_800))
    }

    @Test
    fun calendarMarksDoneCurrentAndUpcoming() {
        val rows = Schedule.rows(circle(CircleStatus.Active, round = 1, order = listOf(2, 0, 3, 1)))
        assertEquals(listOf(RoundState.Done, RoundState.Current, RoundState.Upcoming, RoundState.Upcoming), rows.map { it.state })
        assertEquals(listOf(2, 0, 3, 1), rows.map { it.memberIndex })
        assertEquals(6_000L, rows[1].opensAt)
        assertEquals(8_000L, rows[3].opensAt)
        assertNull(rows[0].opensAt)
    }

    @Test
    fun calendarHasNoOrderBeforeTheCircleStarts() {
        val rows = Schedule.rows(circle(CircleStatus.Open, round = 0, order = emptyList()))
        assertTrue(rows.all { it.memberIndex == null && it.state == RoundState.Upcoming && it.opensAt == null })
        assertNull(Schedule.positionNote(circle(CircleStatus.Open, 0, emptyList()), member(0)))
    }

    @Test
    fun positionNoteExplainsFirstMiddleAndLast() {
        val c = circle(CircleStatus.Active, round = 0, order = listOf(0, 1, 2, 3))
        val first = Schedule.positionNote(c, member(0))!!
        val middle = Schedule.positionNote(c, member(1))!!
        val last = Schedule.positionNote(c, member(3))!!
        assertTrue(first.contains("paid first"))
        assertTrue(first.contains("300"))
        assertTrue(middle.contains("round 2"))
        assertTrue(last.contains("paid last"))
        assertTrue(last.contains("savings goal"))
    }

    @Test
    fun simulationPlaysEveryRuleOnce() {
        var s = SimState()
        repeat(ROUNDS) { s = Simulation.playRound(s) }
        assertTrue(s.done)
        assertNull(s.recipient)
        // Everyone is paid exactly once, the full pot.
        assertTrue(s.members.all { it.received == PAY * NAMES.size })
        // Diego missed once, his bond covered it, everyone else kept a clean record.
        val diego = s.members.first { it.name == "Diego" }
        assertEquals(1, diego.missed)
        assertEquals(BOND - PAY, diego.bondLeft)
        assertEquals(80, diego.scorePercent)
        assertTrue(s.members.filter { it.name != "Diego" }.all { it.missed == 0 && it.scorePercent == 100 && it.bondLeft == BOND })
        assertTrue(s.events.any { it.text.contains("autopay") })
        // Playing past the end changes nothing.
        assertEquals(s, Simulation.playRound(s))
    }

    @Test
    fun payoutOrderIsAPermutation() {
        assertEquals(NAMES.indices.toSet(), ORDER.toSet())
    }
}
