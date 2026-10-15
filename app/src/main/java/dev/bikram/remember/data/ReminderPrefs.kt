package dev.bikram.remember.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

enum class SnoozeType {
    RELATIVE,
    ABSOLUTE,
}

enum class CriticalChimeRepeatInterval(val minutes: Int) {
    FIVE(5), TEN(10), FIFTEEN(15), THIRTY(30), SIXTY(60),
}

/**
 * How long a Critical reminder rings each time it alerts. Stored by name. [NO_RING] skips the
 * alarm: each alert plays High's notification tone once instead, and still repeats.
 */
enum class CriticalRingDuration(
    /** Length of each ring; null for [NO_RING], which does not ring. */
    val ringMillis: Long?,
) {
    NO_RING(null),
    THIRTY_SECONDS(30_000L),
    ONE_MINUTE(60_000L),
    TWO_MINUTES(120_000L),
    FIVE_MINUTES(300_000L),
    ;

    val ringsLikeAlarm: Boolean
        get() = ringMillis != null
}

data class ReminderPreferencesState(
    val keepReminderNotificationsUntilDone: Boolean = false,
    val reminderSummaryNotificationEnabled: Boolean = false,
    val snoozeType: SnoozeType = SnoozeType.RELATIVE,
    val criticalRingDuration: CriticalRingDuration = CriticalRingDuration.ONE_MINUTE,
    val criticalChimeRepeatInterval: CriticalChimeRepeatInterval = CriticalChimeRepeatInterval.FIFTEEN,
)

private val Context.reminderDataStore by preferencesDataStore(name = "reminder_prefs")

class ReminderPrefs(
    private val context: Context,
) {
    private object Keys {
        val KEEP_UNTIL_DONE = booleanPreferencesKey("keep_reminder_notifications_until_done")
        val SUMMARY_NOTIFICATION = booleanPreferencesKey("reminder_summary_notification")
        val SNOOZE_TYPE = stringPreferencesKey("snooze_type")
        val CRITICAL_RING_DURATION = stringPreferencesKey("critical_ring_duration")
        val CRITICAL_CHIME_REPEAT_INTERVAL = stringPreferencesKey("critical_chime_repeat_interval")
    }

    val state: Flow<ReminderPreferencesState> =
        context.reminderDataStore.data.map { prefs ->
            ReminderPreferencesState(
                keepReminderNotificationsUntilDone = prefs[Keys.KEEP_UNTIL_DONE] ?: false,
                reminderSummaryNotificationEnabled = prefs[Keys.SUMMARY_NOTIFICATION] ?: false,
                snoozeType =
                    prefs[Keys.SNOOZE_TYPE]?.let { raw ->
                        runCatching { SnoozeType.valueOf(raw) }.getOrNull()
                    } ?: SnoozeType.RELATIVE,
                criticalRingDuration =
                    prefs[Keys.CRITICAL_RING_DURATION]?.let { raw ->
                        runCatching { CriticalRingDuration.valueOf(raw) }.getOrNull()
                    } ?: CriticalRingDuration.ONE_MINUTE,
                criticalChimeRepeatInterval = prefs[Keys.CRITICAL_CHIME_REPEAT_INTERVAL]?.let { raw ->
                    runCatching { CriticalChimeRepeatInterval.valueOf(raw) }.getOrNull()
                } ?: CriticalChimeRepeatInterval.FIFTEEN,
            )
        }

    suspend fun snapshot(): ReminderPreferencesState = state.first()

    suspend fun setKeepReminderNotificationsUntilDone(enabled: Boolean) {
        context.reminderDataStore.edit { prefs ->
            prefs[Keys.KEEP_UNTIL_DONE] = enabled
        }
    }

    suspend fun setReminderSummaryNotificationEnabled(enabled: Boolean) {
        context.reminderDataStore.edit { prefs ->
            prefs[Keys.SUMMARY_NOTIFICATION] = enabled
        }
    }

    suspend fun setSnoozeType(snoozeType: SnoozeType) {
        context.reminderDataStore.edit { prefs ->
            prefs[Keys.SNOOZE_TYPE] = snoozeType.name
        }
    }

    suspend fun setCriticalRingDuration(duration: CriticalRingDuration) {
        context.reminderDataStore.edit { prefs ->
            prefs[Keys.CRITICAL_RING_DURATION] = duration.name
        }
    }

    suspend fun setCriticalChimeRepeatInterval(interval: CriticalChimeRepeatInterval) {
        context.reminderDataStore.edit { prefs -> prefs[Keys.CRITICAL_CHIME_REPEAT_INTERVAL] = interval.name }
    }

    suspend fun exportForBackup(): JSONObject {
        val prefs = context.reminderDataStore.data.first()
        return JSONObject().apply {
            put(Keys.KEEP_UNTIL_DONE.name, prefs[Keys.KEEP_UNTIL_DONE] ?: false)
            put(Keys.SUMMARY_NOTIFICATION.name, prefs[Keys.SUMMARY_NOTIFICATION] ?: false)
            put(
                Keys.SNOOZE_TYPE.name,
                prefs[Keys.SNOOZE_TYPE] ?: SnoozeType.RELATIVE.name,
            )
            put(
                Keys.CRITICAL_RING_DURATION.name,
                prefs[Keys.CRITICAL_RING_DURATION] ?: CriticalRingDuration.ONE_MINUTE.name,
            )
            put(Keys.CRITICAL_CHIME_REPEAT_INTERVAL.name, prefs[Keys.CRITICAL_CHIME_REPEAT_INTERVAL] ?: CriticalChimeRepeatInterval.FIFTEEN.name)
        }
    }

    suspend fun importFromBackup(json: JSONObject?) {
        if (json == null || json.length() == 0) return
        context.reminderDataStore.edit { mutable ->
            if (json.has(Keys.KEEP_UNTIL_DONE.name) && !json.isNull(Keys.KEEP_UNTIL_DONE.name)) {
                mutable[Keys.KEEP_UNTIL_DONE] = json.getBoolean(Keys.KEEP_UNTIL_DONE.name)
            }
            if (json.has(Keys.SUMMARY_NOTIFICATION.name) && !json.isNull(Keys.SUMMARY_NOTIFICATION.name)) {
                mutable[Keys.SUMMARY_NOTIFICATION] = json.getBoolean(Keys.SUMMARY_NOTIFICATION.name)
            }
            if (json.has(Keys.SNOOZE_TYPE.name) && !json.isNull(Keys.SNOOZE_TYPE.name)) {
                val snoozeType =
                    runCatching { SnoozeType.valueOf(json.getString(Keys.SNOOZE_TYPE.name)) }.getOrNull()
                if (snoozeType != null) {
                    mutable[Keys.SNOOZE_TYPE] = snoozeType.name
                }
            }
            if (json.has(Keys.CRITICAL_RING_DURATION.name) && !json.isNull(Keys.CRITICAL_RING_DURATION.name)) {
                val duration =
                    runCatching { CriticalRingDuration.valueOf(json.getString(Keys.CRITICAL_RING_DURATION.name)) }.getOrNull()
                if (duration != null) {
                    mutable[Keys.CRITICAL_RING_DURATION] = duration.name
                }
            }
            if (json.has(Keys.CRITICAL_CHIME_REPEAT_INTERVAL.name) && !json.isNull(Keys.CRITICAL_CHIME_REPEAT_INTERVAL.name)) {
                runCatching { CriticalChimeRepeatInterval.valueOf(json.getString(Keys.CRITICAL_CHIME_REPEAT_INTERVAL.name)) }.getOrNull()?.let {
                    mutable[Keys.CRITICAL_CHIME_REPEAT_INTERVAL] = it.name
                }
            }
        }
    }

    suspend fun reset() {
        context.reminderDataStore.edit { it.clear() }
    }
}
