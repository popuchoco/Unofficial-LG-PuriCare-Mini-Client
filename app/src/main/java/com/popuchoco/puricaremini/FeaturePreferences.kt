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
    val filterReminderThreshold: Int?
        get() = preferences.getInt(KEY_FILTER_REMINDER_THRESHOLD, 0).takeIf { it in FILTER_REMINDER_THRESHOLDS }

    fun setBackgroundConnection(enabled: Boolean) {
        preferences.edit().putString(KEY_CONNECTION_MODE, ConnectionModePolicy.background(enabled).name).apply()
    }

    fun setProximityAutoPower(enabled: Boolean) {
        preferences.edit().putString(KEY_CONNECTION_MODE, ConnectionModePolicy.proximityAuto(enabled).name).apply()
    }

    fun setSensorAlwaysOn(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SENSOR_ALWAYS_ON, enabled).apply()
    }

    fun setFilterReminderThreshold(threshold: Int?) {
        require(threshold == null || threshold in FILTER_REMINDER_THRESHOLDS)
        preferences.edit().putInt(KEY_FILTER_REMINDER_THRESHOLD, threshold ?: 0).apply()
        if (threshold == null) preferences.edit().remove(KEY_FILTER_REMINDER_SENT_THRESHOLD).apply()
    }

    internal fun filterReminderSentThreshold(): Int? =
        preferences.getInt(KEY_FILTER_REMINDER_SENT_THRESHOLD, 0).takeIf { it in FILTER_REMINDER_THRESHOLDS }

    internal fun setFilterReminderSentThreshold(threshold: Int?) {
        val edit = preferences.edit()
        if (threshold == null) edit.remove(KEY_FILTER_REMINDER_SENT_THRESHOLD)
        else edit.putInt(KEY_FILTER_REMINDER_SENT_THRESHOLD, threshold)
        edit.apply()
    }

    private companion object {
        const val KEY_CONNECTION_MODE = "connection_mode"
        const val KEY_SENSOR_ALWAYS_ON = "sensor_always_on"
        const val KEY_FILTER_REMINDER_THRESHOLD = "filter_reminder_threshold"
        const val KEY_FILTER_REMINDER_SENT_THRESHOLD = "filter_reminder_sent_threshold"
    }
}

enum class ConnectionMode { NONE, BACKGROUND, PROXIMITY_AUTO }

object ConnectionModePolicy {
    fun background(enabled: Boolean) = if (enabled) ConnectionMode.BACKGROUND else ConnectionMode.NONE
    fun proximityAuto(enabled: Boolean) = if (enabled) ConnectionMode.PROXIMITY_AUTO else ConnectionMode.NONE
}

object BackgroundReconnectPolicy {
    private val delays = longArrayOf(3_000, 6_000, 15_000, 30_000, 60_000)

    fun delayForAttempt(attempt: Int): Long? = delays.getOrNull(attempt)
}

internal fun isCurrentConnection(current: Any?, callbackSource: Any?): Boolean =
    current != null && current === callbackSource

internal fun backgroundNotificationText(status: BackgroundConnectionStatus, message: String): String =
    when (status) {
        BackgroundConnectionStatus.CONNECTED,
        BackgroundConnectionStatus.RETRYING -> message
        BackgroundConnectionStatus.PAUSED -> "重連已暫停，點此開啟 App"
    }
