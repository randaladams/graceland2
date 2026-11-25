package com.randaladams.graceland

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class GracelandMathTest {

    @Test
    fun periodForHour_mapsAllPeriodsCorrectly() {
        assertEquals(GracelandMath.TimeOfDayPeriod.MORNING, GracelandMath.periodForHour(5))
        assertEquals(GracelandMath.TimeOfDayPeriod.MORNING, GracelandMath.periodForHour(10))
        assertEquals(GracelandMath.TimeOfDayPeriod.MIDDAY, GracelandMath.periodForHour(11))
        assertEquals(GracelandMath.TimeOfDayPeriod.MIDDAY, GracelandMath.periodForHour(16))
        assertEquals(GracelandMath.TimeOfDayPeriod.DUSK, GracelandMath.periodForHour(17))
        assertEquals(GracelandMath.TimeOfDayPeriod.DUSK, GracelandMath.periodForHour(19))
        assertEquals(GracelandMath.TimeOfDayPeriod.NIGHT, GracelandMath.periodForHour(20))
        assertEquals(GracelandMath.TimeOfDayPeriod.NIGHT, GracelandMath.periodForHour(0))
        assertEquals(GracelandMath.TimeOfDayPeriod.NIGHT, GracelandMath.periodForHour(4))
    }

    @Test
    fun haversineDistance_nashvilleToGraceland_isAccurate() {
        // Nashville, TN (36.1627, -86.7816) to Graceland (35.046000, -90.022904) ~ 318 km
        val meters = GracelandMath.haversineDistanceMeters(36.1627, -86.7816)
        val km = meters / 1000.0
        assertTrue("Expected ~318 km, got $km", km in 310.0..326.0)
    }

    @Test
    fun initialBearing_dueSouthOfGraceland_pointsNorth() {
        val bearing = GracelandMath.initialBearingDegrees(30.0, GracelandMath.GRACELAND_LON)
        assertEquals(0.0, bearing, 0.5)
    }

    @Test
    fun initialBearing_dueWestOfGraceland_pointsEast() {
        val bearing = GracelandMath.initialBearingDegrees(GracelandMath.GRACELAND_LAT, -95.0)
        assertEquals(90.0, bearing, 2.0)
    }

    @Test
    fun shortestAngleDelta_wrapsCleanlyAcrossNorth() {
        assertEquals(20.0, GracelandMath.shortestAngleDelta(350.0, 10.0), 0.001)
        assertEquals(-20.0, GracelandMath.shortestAngleDelta(10.0, 350.0), 0.001)
    }

    @Test
    fun relativeArrowAngle_pointsStraightAheadWhenFacingTarget() {
        assertEquals(0.0, GracelandMath.relativeArrowAngle(135.0, 135.0), 0.001)
        assertEquals(90.0, GracelandMath.relativeArrowAngle(90.0, 0.0), 0.001)
        assertEquals(270.0, GracelandMath.relativeArrowAngle(0.0, 90.0), 0.001)
    }

    @Test
    fun formatDistance_switchesMetricAndImperial() {
        assertEquals("500 m", GracelandMath.formatDistance(500.0, GracelandMath.MeasurementSystem.METRIC, Locale.US))
        assertEquals("10.0 km", GracelandMath.formatDistance(10_000.0, GracelandMath.MeasurementSystem.METRIC, Locale.US))
        assertEquals("6.2 mi", GracelandMath.formatDistance(10_000.0, GracelandMath.MeasurementSystem.IMPERIAL, Locale.US))
        assertEquals("328 ft", GracelandMath.formatDistance(100.0, GracelandMath.MeasurementSystem.IMPERIAL, Locale.US))
        assertEquals("7 ft", GracelandMath.formatDistance(2.0, GracelandMath.MeasurementSystem.IMPERIAL, Locale.US))
        assertEquals("5,282 mi", GracelandMath.formatDistance(8_500_000.0, GracelandMath.MeasurementSystem.IMPERIAL, Locale.US))
    }

    @Test
    fun defaultMeasurementSystem_matchesLocale() {
        assertEquals(GracelandMath.MeasurementSystem.IMPERIAL, GracelandMath.defaultMeasurementSystemForLocale(Locale.US))
        assertEquals(GracelandMath.MeasurementSystem.METRIC, GracelandMath.defaultMeasurementSystemForLocale(Locale.FRANCE))
    }

    @Test
    fun formatDateTimeWithPattern_respectsPhone24HourAndPattern() {
        val memphis = ZonedDateTime.of(2026, 7, 4, 20, 15, 5, 0, ZoneId.of("America/Chicago"))
        assertEquals(
            "Sat, Jul 4, 8:15 PM",
            GracelandMath.formatDateTimeWithPattern(memphis, "EEE, MMM d", use24Hour = false, locale = Locale.US)
        )
        assertEquals(
            "Sat, 4 Jul, 20:15",
            GracelandMath.formatDateTimeWithPattern(memphis, "EEE, d MMM", use24Hour = true, locale = Locale.UK)
        )
    }

    @Test
    fun formatClockTime_respects12hAnd24h() {
        val memphis = ZonedDateTime.of(2026, 7, 4, 20, 15, 5, 0, ZoneId.of("America/Chicago"))
        assertEquals("8:15:05 PM", GracelandMath.formatClockTime(memphis, GracelandMath.MeasurementSystem.IMPERIAL, Locale.US))
        assertEquals("20:15:05", GracelandMath.formatClockTime(memphis, GracelandMath.MeasurementSystem.METRIC, Locale.US))
    }
}
