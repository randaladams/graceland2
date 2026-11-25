package com.randaladams.graceland

import android.content.Context

class UserPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("graceland_prefs", Context.MODE_PRIVATE)

    fun getMeasurementSystem(): GracelandMath.MeasurementSystem {
        val raw = prefs.getString(KEY_SYSTEM, GracelandMath.MeasurementSystem.IMPERIAL.name)
        return runCatching {
            GracelandMath.MeasurementSystem.valueOf(raw ?: GracelandMath.MeasurementSystem.IMPERIAL.name)
        }.getOrDefault(GracelandMath.MeasurementSystem.IMPERIAL)
    }

    fun setMeasurementSystem(system: GracelandMath.MeasurementSystem) {
        prefs.edit().putString(KEY_SYSTEM, system.name).apply()
    }

    private companion object {
        const val KEY_SYSTEM = "measurement_system"
    }
}
