package com.petr.toll.rules

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object Limits {
    /** The Toll day [at] belongs to. 02:00 on Saturday still counts as Friday. */
    fun tollDay(at: Instant, zone: ZoneId, dayStartHour: Int): LocalDate =
        at.atZone(zone).minusHours(dayStartHour.toLong()).toLocalDate()

    /** When Toll day [day] starts. */
    fun dayStart(day: LocalDate, zone: ZoneId, dayStartHour: Int): Instant =
        day.atTime(dayStartHour, 0).atZone(zone).toInstant()

    fun isWeekend(day: LocalDate): Boolean =
        day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY

    /** Week 1 is the 7 days from [startDate]. Days before it also count as week 1. */
    fun weekNumber(day: LocalDate, startDate: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(startDate, day)
        return if (days < 0) 1 else (days / 7).toInt() + 1
    }

    fun limitFor(day: LocalDate, settings: TollSettings): Duration {
        val start = if (isWeekend(day)) settings.weekendStartLimit else settings.weekdayStartLimit
        return limitForWeek(start, weekNumber(day, settings.startDate), settings.taper, settings.floor)
    }

    fun limitForWeek(start: Duration, week: Int, taper: Taper, floor: Duration): Duration {
        val steps = (week - 1).coerceAtLeast(0)
        val tapered = when (taper) {
            is Taper.Subtract -> start.minus(taper.amount.multipliedBy(steps.toLong()))
            is Taper.Multiply -> Duration.ofMinutes(Math.round(start.toMinutes() * Math.pow(taper.factor, steps.toDouble())))
        }
        return maxOf(tapered, floor)
    }
}

/** How expensive Instagram is right now, by paid time used today as a share of the limit. */
enum class Tier {
    /** Under 50%: opens normally. */
    FREE,

    /** 50–75%: type a sentence to get in. */
    TYPING,

    /** 75–100%: type a longer sentence; greyscale and "Still here?" while inside. */
    GREY,

    /** 100% and up: QR code plus a thumb hold for every 10 minutes of paid time. */
    OVER;

    fun next(): Tier = if (this == OVER) OVER else values()[ordinal + 1]

    /** Paid time at which this tier starts. */
    fun threshold(limit: Duration): Duration = when (this) {
        FREE -> Duration.ZERO
        TYPING -> limit.dividedBy(2)
        GREY -> limit.multipliedBy(3).dividedBy(4)
        OVER -> limit
    }

    companion object {
        fun of(paid: Duration, limit: Duration): Tier =
            values().last { paid >= it.threshold(limit) }
    }
}
