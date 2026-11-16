package app.opendisplay.android.ui

import android.content.Context

/** The receiver's own settings; same keys and meaning as the iOS app's @AppStorage. */
class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var showAnalytics: Boolean
        get() = prefs.getBoolean(KEY_SHOW_ANALYTICS, false)
        set(v) = prefs.edit().putBoolean(KEY_SHOW_ANALYTICS, v).apply()

    var screenOrientation: String
        get() = prefs.getString(KEY_SCREEN_ORIENTATION, ORIENTATION_AUTO) ?: ORIENTATION_AUTO
        set(v) = prefs.edit().putString(KEY_SCREEN_ORIENTATION, v).apply()

    var hasConnectedBefore: Boolean
        get() = prefs.getBoolean("hasConnectedBefore", false)
        set(v) = prefs.edit().putBoolean("hasConnectedBefore", v).apply()

    var onboardingDismissed: Boolean
        get() = prefs.getBoolean("onboardingDismissed", false)
        set(v) = prefs.edit().putBoolean("onboardingDismissed", v).apply()

    var lastUpdateCheck: Long
        get() = prefs.getLong("lastUpdateCheck", 0L)
        set(v) = prefs.edit().putLong("lastUpdateCheck", v).apply()

    companion object {
        const val FILE = "settings"
        const val KEY_SHOW_ANALYTICS = "showAnalytics"
        const val KEY_SCREEN_ORIENTATION = "screenOrientation"
        const val ORIENTATION_AUTO = "auto"
        const val ORIENTATION_PORTRAIT = "portrait"
        const val ORIENTATION_LANDSCAPE = "landscape"
    }
}
