package com.popuchoco.puricaremini

import android.content.Context

class FeaturePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("device_features", Context.MODE_PRIVATE)

    private val connectionMode: ConnectionMode
        get() = runCatching {
            ConnectionMode.valueOf(preferences.getString(KEY_CONNECTION_MODE, ConnectionMode.NONE.name)!!)
        }.getOrDefault(ConnectionMode.NONE)

    val backgroundConnection: Boolean get() = connectionMode == ConnectionMode.BACKGROUND
    val proximityAutoPower: Boolean get() = connectionMode == ConnectionMode.PROXIMITY_AUTO
    val sensorAlwaysOn: Boolean get() = preferences.getBoolean(KEY_SENSOR_ALWAYS_ON, false)

    fun setBackgroundConnection(enabled: Boolean) {
        preferences.edit().putString(KEY_CONNECTION_MODE, ConnectionModePolicy.background(enabled).name).apply()
    }

    fun setProximityAutoPower(enabled: Boolean) {
        preferences.edit().putString(KEY_CONNECTION_MODE, ConnectionModePolicy.proximityAuto(enabled).name).apply()
    }

    fun setSensorAlwaysOn(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SENSOR_ALWAYS_ON, enabled).apply()
    }

    private companion object {
        const val KEY_CONNECTION_MODE = "connection_mode"
        const val KEY_SENSOR_ALWAYS_ON = "sensor_always_on"
    }
}

enum class ConnectionMode { NONE, BACKGROUND, PROXIMITY_AUTO }

object ConnectionModePolicy {
    fun background(enabled: Boolean) = if (enabled) ConnectionMode.BACKGROUND else ConnectionMode.NONE
    fun proximityAuto(enabled: Boolean) = if (enabled) ConnectionMode.PROXIMITY_AUTO else ConnectionMode.NONE
}
