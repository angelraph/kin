package app.kin.watch

import app.kin.solana.Allowance
import app.kin.solana.CircleData
import app.kin.solana.CircleStatus
import app.kin.solana.MemberData
import app.kin.solana.PublicKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertRulesTest {
    private fun key(seed: Int) = PublicKey(ByteArray(32) { seed.toByte() })
    private val me = key(1)
    private val bob = key(2)
    private val cara = key(3)
    private val circleAddress = key(9)

    private val start = 1_000_000L
    private val period = 100L
    private val grace = 25L

    private fun circle(
        status: CircleStatus = CircleStatus.Active,
        round: Int = 0,
        resolved: Int = 0,
        pot: Long = 0,
        order: List<Int> = listOf(0, 1, 2) + List(9) { 0 },
    ) = CircleData(
        address = circleAddress, creator = me, circleId = 1, mint = key(7),
        contribution = 100, bond = 200, periodSecs = period, graceSecs = grace,
        maxMembers = 3, memberCount = 3, maxMissedAllowed = 100, status = status,
        currentRound = round, roundStartTs = start, resolvedCount = resolved, roundPot = pot, createdTs = start - 10,
        randomize = false, seekerOnly = false, seekerAuthority = PublicKey.DEFAULT, orderSlot = 0,
        orderSeed = ByteArray(32), payoutOrder = order, name = "Family",
    )

    private fun member(wallet: PublicKey, index: Int, resolvedRounds: Int = 0) = MemberData(
        address = key(50 + index), circle = circleAddress, wallet = wallet, index = index,
        bondLocked = 200, bondUsed = 0, roundsResolved = resolvedRounds, received = false, bondClaimed = false,
        onTime = 0, late = 0, missed = 0,
    )

    private val members = listOf(member(me, 0), member(bob, 1), member(cara, 2))
    private val autopayOn = Allowance(delegatedToKin = true, amount = 300)
    private val autopayOff = Allowance(delegatedToKin = false, amount = 0)

    private fun kinds(alerts: List<Alert>) = alerts.map { it.kind }.toSet()

    @Test
    fun aRoundThatJustOpenedTellsMembersToPay() {
        val alerts = AlertRules.evaluate(me, circle(), members, emptyMap(), start + 5)
        assertEquals(setOf(AlertKind.PayOpen), kinds(alerts))
        assertTrue(alerts.single().body.contains("0.0001"))
    }

    @Test
    fun amountsAreFormattedWithoutLosingSmallValues() {
        assertEquals("10 tUSDC", app.kin.ui.formatAmount(10_000_000))
        assertEquals("12.5 tUSDC", app.kin.ui.formatAmount(12_500_000))
        assertEquals("0.0001 tUSDC", app.kin.ui.formatAmount(100))
        assertEquals("0 tUSDC", app.kin.ui.formatAmount(0))
        assertEquals("0.05 tUSDC", app.kin.ui.formatAmount(50_000))
        assertEquals("1,000".replace(",", ""), app.kin.ui.formatAmount(1_000_000_000, withSymbol = false))
    }

    @Test
    fun laterInTheRoundItBecomesUrgent() {
        val alerts = AlertRules.evaluate(me, circle(), members, emptyMap(), start + 85)
        assertEquals(setOf(AlertKind.PayClosing), kinds(alerts))
    }

    @Test
    fun theGraceWindowStillCountsAsClosingSoon() {
        val alerts = AlertRules.evaluate(me, circle(), members, emptyMap(), start + period + 10)
        assertEquals(setOf(AlertKind.PayClosing), kinds(alerts))
    }

    @Test
    fun nothingToPayMeansNoPaymentReminder() {
        val paid = listOf(member(me, 0, resolvedRounds = 1), member(bob, 1), member(cara, 2))
        val alerts = AlertRules.evaluate(me, circle(), paid, emptyMap(), start + 5)
        assertTrue(alerts.none { it.kind == AlertKind.PayOpen || it.kind == AlertKind.PayClosing })
    }

    @Test
    fun autopayUsersAreNotNaggedToPayButSomeoneIsToldToCollect() {
        val allowances = mapOf(me to autopayOn, bob to autopayOn, cara to autopayOff)
        val alerts = AlertRules.evaluate(me, circle(), members, allowances, start + 5)
        assertEquals(setOf(AlertKind.AutopayDue), kinds(alerts))
        assertTrue(alerts.single().body.contains("2 payments"))
    }

    @Test
    fun autopayWithTooSmallAnAllowanceDoesNotCount() {
        val small = Allowance(delegatedToKin = true, amount = 99)
        assertTrue(AlertRules.dueForAutopay(circle(), members, mapOf(bob to small), start + 5).isEmpty())
    }

    @Test
    fun autopayCannotBeCollectedAfterTheWindowCloses() {
        val allowances = mapOf(bob to autopayOn)
        assertTrue(AlertRules.dueForAutopay(circle(), members, allowances, start + period + grace + 1).isEmpty())
    }

    @Test
    fun anAlreadyPaidMemberIsNotCollectedTwice() {
        val paid = listOf(member(me, 0), member(bob, 1, resolvedRounds = 1), member(cara, 2))
        assertTrue(AlertRules.dueForAutopay(circle(), paid, mapOf(bob to autopayOn), start + 5).isEmpty())
    }

    @Test
    fun afterTheGraceWindowTheBondCanCoverAMiss() {
        val alerts = AlertRules.evaluate(me, circle(), members, emptyMap(), start + period + grace + 5)
        assertTrue(AlertKind.CoverNeeded in kinds(alerts))
    }

    @Test
    fun aFullyPaidRoundTellsTheRecipientTheirPotIsReady() {
        val done = listOf(member(me, 0, 1), member(bob, 1, 1), member(cara, 2, 1))
        val alerts = AlertRules.evaluate(me, circle(resolved = 3, pot = 300), done, emptyMap(), start + period + 1)
        val payout = alerts.single { it.kind == AlertKind.PayoutReady }
        assertEquals("Your pot is ready", payout.title)
    }

    @Test
    fun otherMembersAreToldWhoWillBePaid() {
        val done = listOf(member(me, 0, 1), member(bob, 1, 1), member(cara, 2, 1))
        val alerts = AlertRules.evaluate(bob, circle(resolved = 3, pot = 300), done, emptyMap(), start + period + 1)
        assertEquals("Payout is ready", alerts.single { it.kind == AlertKind.PayoutReady }.title)
    }

    @Test
    fun payoutFollowsTheDrawnOrderNotJoinOrder() {
        val done = listOf(member(me, 0, 1), member(bob, 1, 1), member(cara, 2, 1))
        val drawn = circle(resolved = 3, pot = 300, order = listOf(2, 0, 1) + List(9) { 0 })
        val forMe = AlertRules.evaluate(me, drawn, done, emptyMap(), start + period + 1)
        assertEquals("Payout is ready", forMe.single { it.kind == AlertKind.PayoutReady }.title)
        val forCara = AlertRules.evaluate(cara, drawn, done, emptyMap(), start + period + 1)
        assertEquals("Your pot is ready", forCara.single { it.kind == AlertKind.PayoutReady }.title)
    }

    @Test
    fun inactiveCirclesAndNonMembersGetNothing() {
        assertTrue(AlertRules.evaluate(me, circle(status = CircleStatus.Open), members, emptyMap(), start + 5).isEmpty())
        assertTrue(AlertRules.evaluate(me, circle(status = CircleStatus.Completed), members, emptyMap(), start + 5).isEmpty())
        assertTrue(AlertRules.evaluate(key(99), circle(), members, emptyMap(), start + 5).isEmpty())
    }

    @Test
    fun alertKeysAreStablePerCircleRoundAndKind() {
        val a = AlertRules.evaluate(me, circle(), members, emptyMap(), start + 5).single()
        val b = AlertRules.evaluate(me, circle(), members, emptyMap(), start + 20).single()
        assertEquals("the same reminder must not fire twice", a.key, b.key)
        val nextRound = AlertRules.evaluate(
            me, circle(round = 1),
            listOf(member(me, 0, 1), member(bob, 1, 1), member(cara, 2, 1)), emptyMap(), start + 5,
        )
        assertTrue("a new round is a new reminder", nextRound.isNotEmpty() && nextRound.none { it.key == a.key })
    }
}
