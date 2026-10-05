package com.petr.toll.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class CommitmentsTest {
    private val current = TollSettings(startDate = LocalDate.of(2026, 10, 5))
    private val now = Instant.parse("2026-10-05T10:00:00Z")
    private fun min(m: Long) = Duration.ofMinutes(m)

    @Test fun tighteningAppliesAtOnce() {
        val plan = Commitments.plan(current, emptyList(), current.copy(weekdayStartLimit = min(150)), now)
        assertEquals(min(150), plan.settings.weekdayStartLimit)
        assertTrue(plan.pending.isEmpty())
    }

    @Test fun looseningWaits24Hours() {
        val plan = Commitments.plan(current, emptyList(), current.copy(quickPassesPerDay = 5), now)
        assertEquals(3, plan.settings.quickPassesPerDay)
        assertEquals(1, plan.pending.size)
        assertEquals(now.plus(Duration.ofHours(24)), plan.pending[0].effectiveAt)

        assertEquals(3, Commitments.mature(plan.settings, plan.pending, now.plus(Duration.ofHours(23))).settings.quickPassesPerDay)
        val matured = Commitments.mature(plan.settings, plan.pending, now.plus(Duration.ofHours(24)))
        assertEquals(5, matured.settings.quickPassesPerDay)
        assertTrue(matured.pending.isEmpty())
    }

    @Test fun oneSaveCanTightenAndLoosen() {
        val desired = current.copy(weekdayStartLimit = min(120), weekendStartLimit = min(360))
        val plan = Commitments.plan(current, emptyList(), desired, now)
        assertEquals(min(120), plan.settings.weekdayStartLimit)
        assertEquals(min(300), plan.settings.weekendStartLimit)
        assertEquals(SettingsPatch(weekendStartLimit = min(360)), plan.pending.single().patch)
    }

    @Test fun tighteningCancelsAWaitingLoosening() {
        val first = Commitments.plan(current, emptyList(), current.copy(floor = min(60)), now)
        val second = Commitments.plan(first.settings, first.pending, current.copy(floor = min(30)), now.plusSeconds(60))
        assertEquals(min(30), second.settings.floor)
        assertTrue(second.pending.isEmpty())
    }

    @Test fun resavingTheSameLooseningKeepsItsTimer() {
        val first = Commitments.plan(current, emptyList(), current.copy(floor = min(60)), now)
        val later = now.plus(Duration.ofHours(5))
        val second = Commitments.plan(first.settings, first.pending, current.copy(floor = min(60)), later)
        assertEquals(first.pending, second.pending)
    }

    @Test fun aDifferentLooseningRestartsTheTimer() {
        val first = Commitments.plan(current, emptyList(), current.copy(floor = min(60)), now)
        val later = now.plus(Duration.ofHours(5))
        val second = Commitments.plan(first.settings, first.pending, current.copy(floor = min(75)), later)
        assertEquals(later.plus(Duration.ofHours(24)), second.pending.single().effectiveAt)
        assertEquals(min(75), second.pending.single().patch.floor)
    }

    @Test fun taperLoosenessIsJudgedByTheLimitsItGives() {
        assertTrue(Commitments.taperIsLooser(current, Taper.Subtract(min(10))))
        assertFalse(Commitments.taperIsLooser(current, Taper.Subtract(min(30))))
        // ×0.9 gives 162 min in week 2, more than 160.
        assertTrue(Commitments.taperIsLooser(current, Taper.Multiply(0.9)))
    }

    @Test fun raisingTheEarnCapWaits24Hours() {
        val plan = Commitments.plan(current, emptyList(), current.copy(earnCapPerDay = min(60)), now)
        assertEquals(min(30), plan.settings.earnCapPerDay)
        assertEquals(SettingsPatch(earnCapPerDay = min(60)), plan.pending.single().patch)

        val lower = Commitments.plan(current, emptyList(), current.copy(earnPerTask = min(5)), now)
        assertEquals(min(5), lower.settings.earnPerTask)
        assertTrue(lower.pending.isEmpty())
    }

    @Test fun turnOffTakes24Hours() {
        assertEquals(now.plus(Duration.ofHours(24)), Commitments.turnOffAt(now))
    }
}
