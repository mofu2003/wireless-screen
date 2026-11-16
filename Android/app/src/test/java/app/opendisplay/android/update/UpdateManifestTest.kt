package app.opendisplay.android.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class UpdateManifestTest {
    @Test
    fun parsesThePublishedManifest() {
        val release = UpdateManifest.parse(
            """
            {
              "versionCode": 1300308,
              "versionName": "1.30.3-zh.8",
              "apkUrl": "https://github.com/mofu2003/wireless-screen/releases/download/v1.30.3-zh.8/WirelessScreen-Android.apk",
              "sha256": "67DBC1B9D32CFAF9CA9B22CBDE4F33D17CDDE21983E288BA417C6E69754B5854",
              "notes": "  修复更新流程  "
            }
            """.trimIndent(),
        )

        assertEquals(1300308L, release.versionCode)
        assertEquals("1.30.3-zh.8", release.versionName)
        assertEquals("67dbc1b9d32cfaf9ca9b22cbde4f33d17cdde21983e288ba417c6e69754b5854", release.sha256)
        assertEquals("修复更新流程", release.notes)
    }

    @Test
    fun emptyNotesBecomeNull() {
        val release = UpdateManifest.parse(validJson(notes = "   "))
        assertNull(release.notes)
    }

    @Test(expected = IOException::class)
    fun rejectsPlainHttp() {
        UpdateManifest.parse(validJson(apkUrl = "http://example.com/update.apk"))
    }

    @Test(expected = IOException::class)
    fun rejectsBadChecksum() {
        UpdateManifest.parse(validJson(sha = "not-a-sha256"))
    }

    @Test(expected = IOException::class)
    fun rejectsZeroVersionCode() {
        UpdateManifest.parse(validJson(versionCode = 0))
    }

    private fun validJson(
        versionCode: Long = 1300308,
        apkUrl: String = "https://example.com/update.apk",
        sha: String = "a".repeat(64),
        notes: String = "",
    ) = """
        {
          "versionCode": $versionCode,
          "versionName": "1.30.3-zh.8",
          "apkUrl": "$apkUrl",
          "sha256": "$sha",
          "notes": "$notes"
        }
    """.trimIndent()
}
