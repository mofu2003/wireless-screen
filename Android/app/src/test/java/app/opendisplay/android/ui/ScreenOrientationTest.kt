package app.opendisplay.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenOrientationTest {
    @Test
    fun knownModesKeepTheirPreferenceValue() {
        assertEquals(
            ScreenOrientation.Mode.AUTO,
            ScreenOrientation.mode(AppPrefs.ORIENTATION_AUTO))
        assertEquals(
            ScreenOrientation.Mode.PORTRAIT,
            ScreenOrientation.mode(AppPrefs.ORIENTATION_PORTRAIT))
        assertEquals(
            ScreenOrientation.Mode.LANDSCAPE,
            ScreenOrientation.mode(AppPrefs.ORIENTATION_LANDSCAPE))
    }

    @Test
    fun missingOrUnknownValueFallsBackToAuto() {
        assertEquals(ScreenOrientation.Mode.AUTO, ScreenOrientation.mode(null))
        assertEquals(ScreenOrientation.Mode.AUTO, ScreenOrientation.mode("sideways"))
    }
}
