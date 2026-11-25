package com.randaladams.graceland

import android.content.Context
import java.util.Locale

class UserPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("graceland_prefs", Context.MODE_PRIVATE)

    fun getMeasurementSystem(): GracelandMath.MeasurementSystem {
        val fallback = GracelandMath.defaultMeasurementSystemForLocale(Locale.getDefault())
        val raw = prefs.getString(KEY_SYSTEM, null) ?: return fallback
        return runCatching {
            GracelandMath.MeasurementSystem.valueOf(raw)
        }.getOrDefault(fallback)
    }

    fun setMeasurementSystem(system: GracelandMath.MeasurementSystem) {
        prefs.edit().putString(KEY_SYSTEM, system.name).apply()
    }

    private companion object {
        const val KEY_SYSTEM = "measurement_system"
    }
}
