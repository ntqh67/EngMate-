package com.engmate.app.core.datastore

/**
 * Shared DataStore file and key naming rules. Each feature declares its own keys in its own package,
 * using its prefix below, so features never edit each other's keys:
 *
 * - `reminder_*`       study reminder (Đông), e.g. `reminder_enabled`, `reminder_hour`
 * - `theme_*`          appearance settings, e.g. `theme_mode`
 * - `app_language`     appearance settings, app language
 * - `recent_searches`  dictionary search history (Khang)
 *
 * Settings that belong to one account must include the uid in the key (e.g. `reminder_enabled_<uid>`)
 * or be reset on sign-out, so the next user does not inherit them (ADR-0001, section 4).
 */
object PreferenceKeys {
    /** File name of the shared DataStore<Preferences> provided by di/DataStoreModule. */
    const val FILE_NAME = "engmate_settings"

    const val REMINDER_PREFIX = "reminder_"
    const val THEME_PREFIX = "theme_"
    const val APP_LANGUAGE = "app_language"
    const val RECENT_SEARCHES = "recent_searches"
}
