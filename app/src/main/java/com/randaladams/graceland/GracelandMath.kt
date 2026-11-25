package com.randaladams.graceland

import java.text.NumberFormat
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure domain calculations for Graceland distance, compass bearing,
 * time-of-day photo selection, and Imperial/Metric formatting.
 */
object GracelandMath {
    const val GRACELAND_LAT = 35.046000
    const val GRACELAND_LON = -90.022904
    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val METERS_PER_MILE = 1609.344
    private const val FEET_PER_METER = 3.28084

    val MEMPHIS_ZONE_ID: ZoneId = ZoneId.of("America/Chicago")

    enum class MeasurementSystem {
        IMPERIAL,
        METRIC
    }

    enum class TimeOfDayPeriod(
        val folderName: String,
        val displayName: String
    ) {
        MORNING("morning", "Morning"),
        MIDDAY("midday", "Midday"),
        DUSK("dusk", "Dusk"),
        NIGHT("night", "Night")
    }

    fun periodForHour(hour: Int): TimeOfDayPeriod {
        val normalized = ((hour % 24) + 24) % 24
        return when (normalized) {
            in 5..10 -> TimeOfDayPeriod.MORNING
            in 11..16 -> TimeOfDayPeriod.MIDDAY
            in 17..19 -> TimeOfDayPeriod.DUSK
            else -> TimeOfDayPeriod.NIGHT
        }
    }

    fun haversineDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double = GRACELAND_LAT,
        lon2: Double = GRACELAND_LON
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2).pow(2) +
            cos(rLat1) * cos(rLat2) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Returns the initial great-circle bearing in degrees [0, 360) from (lat1, lon1) to (lat2, lon2).
     */
    fun initialBearingDegrees(
        lat1: Double,
        lon1: Double,
        lat2: Double = GRACELAND_LAT,
        lon2: Double = GRACELAND_LON
    ): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val theta = Math.toDegrees(atan2(y, x))
        return normalizeDegrees(theta)
    }

    fun normalizeDegrees(degrees: Double): Double {
        val mod = degrees % 360.0
        return if (mod < 0.0) mod + 360.0 else mod
    }

    /**
     * Signed shortest angular difference in [-180, 180] from [fromDeg] to [toDeg].
     */
    fun shortestAngleDelta(fromDeg: Double, toDeg: Double): Double {
        var delta = (toDeg - fromDeg) % 360.0
        if (delta > 180.0) delta -= 360.0
        if (delta < -180.0) delta += 360.0
        return delta
    }

    fun smoothAngleDegrees(
        currentDeg: Double,
        targetDeg: Double,
        alpha: Double = 0.2
    ): Double {
        val delta = shortestAngleDelta(currentDeg, targetDeg)
        return normalizeDegrees(currentDeg + alpha * delta)
    }

    /**
     * Arrow rotation in degrees [0, 360) relative to the top of the device screen.
     */
    fun relativeArrowAngle(bearingToTargetDeg: Double, deviceAzimuthDeg: Double): Double {
        return normalizeDegrees(bearingToTargetDeg - deviceAzimuthDeg)
    }

    fun compassCardinal(degrees: Double): String {
        val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val index = ((normalizeDegrees(degrees) + 22.5) / 45.0).toInt() % 8
        return directions[index]
    }

    fun formatDistance(
        distanceMeters: Double,
        system: MeasurementSystem,
        locale: Locale = Locale.US
    ): String {
        val safeMeters = distanceMeters.coerceAtLeast(0.0)
        val numberFormat = NumberFormat.getNumberInstance(locale)
        return when (system) {
            MeasurementSystem.IMPERIAL -> {
                val miles = safeMeters / METERS_PER_MILE
                if (miles < 0.2) {
                    val feet = safeMeters * FEET_PER_METER
                    numberFormat.maximumFractionDigits = 0
                    "${numberFormat.format(feet)} ft"
                } else {
                    numberFormat.minimumFractionDigits = if (miles < 100.0) 1 else 0
                    numberFormat.maximumFractionDigits = if (miles < 100.0) 1 else 0
                    "${numberFormat.format(miles)} mi"
                }
            }
            MeasurementSystem.METRIC -> {
                if (safeMeters < 1000.0) {
                    numberFormat.maximumFractionDigits = 0
                    "${numberFormat.format(safeMeters)} m"
                } else {
                    val km = safeMeters / 1000.0
                    numberFormat.minimumFractionDigits = if (km < 100.0) 1 else 0
                    numberFormat.maximumFractionDigits = if (km < 100.0) 1 else 0
                    "${numberFormat.format(km)} km"
                }
            }
        }
    }

    fun defaultMeasurementSystemForLocale(locale: Locale = Locale.getDefault()): MeasurementSystem {
        return when (locale.country.uppercase(Locale.US)) {
            "US", "GB", "LR", "MM" -> MeasurementSystem.IMPERIAL
            else -> MeasurementSystem.METRIC
        }
    }

    fun formatDateTimeWithPattern(
        dateTime: ZonedDateTime,
        datePattern: String,
        use24Hour: Boolean,
        locale: Locale = Locale.getDefault()
    ): String {
        val timePattern = if (use24Hour) "HH:mm:ss" else "h:mm:ss a"
        val combinedPattern = "$datePattern, $timePattern"
        return dateTime.format(DateTimeFormatter.ofPattern(combinedPattern, locale))
    }

    fun formatClockTime(
        dateTime: ZonedDateTime,
        system: MeasurementSystem,
        locale: Locale = Locale.US
    ): String {
        val pattern = when (system) {
            MeasurementSystem.IMPERIAL -> "h:mm:ss a"
            MeasurementSystem.METRIC -> "HH:mm:ss"
        }
        return dateTime.format(DateTimeFormatter.ofPattern(pattern, locale))
    }
}
