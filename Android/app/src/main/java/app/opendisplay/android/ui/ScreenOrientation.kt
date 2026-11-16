package app.opendisplay.android.ui

/** User-facing orientation policy for the receiver window. */
object ScreenOrientation {
    enum class Mode { AUTO, PORTRAIT, LANDSCAPE }

    fun mode(preference: String?): Mode = when (preference) {
        AppPrefs.ORIENTATION_PORTRAIT -> Mode.PORTRAIT
        AppPrefs.ORIENTATION_LANDSCAPE -> Mode.LANDSCAPE
        else -> Mode.AUTO
    }
}
