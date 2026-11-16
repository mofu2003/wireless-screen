package app.opendisplay.android.update

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import app.opendisplay.android.Log
import app.opendisplay.android.ProductInfo
import app.opendisplay.android.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Checks a small JSON manifest on the fork's main branch, downloads a signed
 * APK, verifies its SHA-256, then hands it to Android's package installer.
 *
 * Android never lets an app silently replace itself.  The update is automatic
 * up to the point where the user confirms installation in the system UI.
 */
class UpdateManager(
    private val activity: Activity,
    private val listener: Listener,
) {
    interface Listener {
        fun onChecking(manual: Boolean)
        fun onUpToDate(manual: Boolean)
        fun onUpdateAvailable(release: UpdateRelease, manual: Boolean)
        fun onDownloadProgress(release: UpdateRelease, downloaded: Long, total: Long)
        fun onDownloadReady(release: UpdateRelease, file: File, automatic: Boolean)
        fun onInstallPermissionRequired(file: File)
        fun onError(message: String, manual: Boolean)
    }

    private val context: Context = activity.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "od-update").apply { isDaemon = true }
    }
    @Volatile private var closed = false
    @Volatile private var checking = false
    @Volatile private var downloading = false

    fun check(manual: Boolean) {
        if (checking) return
        checking = true
        post { listener.onChecking(manual) }
        io.execute {
            val result = try {
                val release = fetchRelease()
                if (release.versionCode > currentVersionCode()) {
                    post { listener.onUpdateAvailable(release, manual) }
                } else {
                    post { listener.onUpToDate(manual) }
                }
            } catch (error: Exception) {
                Log.w(TAG, "update check failed", error)
                post { listener.onError(error.message ?: context.getString(R.string.update_error_network), manual) }
            } finally {
                checking = false
            }
        }
    }

    fun download(release: UpdateRelease, manual: Boolean) {
        if (downloading) return
        downloading = true
        io.execute {
            try {
                val file = downloadFile(release)
                post { listener.onDownloadReady(release, file, automatic = !manual) }
            } catch (error: Exception) {
                Log.w(TAG, "update download failed", error)
                post { listener.onError(error.message ?: context.getString(R.string.update_error_download), manual) }
            } finally {
                downloading = false
            }
        }
    }

    fun install(file: File) {
        if (!file.isFile) {
            post { listener.onError(context.getString(R.string.update_error_missing_file), true) }
            return
        }
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            post { listener.onInstallPermissionRequired(file) }
            return
        }
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updateprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            post { listener.onError(context.getString(R.string.update_error_installer), true) }
        } catch (error: Exception) {
            Log.w(TAG, "could not open package installer", error)
            post { listener.onError(error.message ?: context.getString(R.string.update_error_installer), true) }
        }
    }

    fun openInstallPermissionSettings() {
        try {
            val intent = if (Build.VERSION.SDK_INT >= 26) {
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"))
            } else {
                Intent(Settings.ACTION_SECURITY_SETTINGS)
            }
            activity.startActivity(intent)
        } catch (error: Exception) {
            Log.w(TAG, "could not open unknown-sources settings", error)
            post { listener.onError(context.getString(R.string.update_error_settings), true) }
        }
    }

    fun close() {
        closed = true
        io.shutdownNow()
        main.removeCallbacksAndMessages(null)
    }

    private fun fetchRelease(): UpdateRelease {
        // raw.githubusercontent.com sits behind a CDN that can keep serving the
        // previous main-branch manifest for a few minutes. A changing query
        // string makes the manifest check fetch the current object.
        val url = context.getString(R.string.update_manifest_url) + "?t=" + System.currentTimeMillis()
        val connection = open(url, "application/json")
        try {
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return try {
                UpdateManifest.parse(body)
            } catch (_: Exception) {
                throw IOException(context.getString(R.string.update_error_manifest))
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadFile(release: UpdateRelease): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "WirelessScreen-${release.versionCode}.apk")
        if (target.isFile && fileSha256(target) == release.sha256) {
            Log.i(TAG, "using verified cached APK ${target.name}")
            return target
        }

        val partial = File(dir, "${target.name}.part")
        partial.delete()
        val connection = open(release.apkUrl, "application/octet-stream")
        var total = 0L
        var downloaded = 0L
        try {
            total = connection.contentLength.toLong().coerceAtLeast(0)
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            var lastReport = 0L
            FileOutputStream(partial).use { out ->
                connection.inputStream.use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastReport >= 250) {
                            lastReport = now
                            post { listener.onDownloadProgress(release, downloaded, total) }
                        }
                    }
                }
                out.fd.sync()
            }
            val actual = digest.digest().joinToString("") {
                "%02x".format(Locale.US, it.toInt() and 0xff)
            }
            if (actual != release.sha256) {
                throw IOException(context.getString(R.string.update_error_checksum))
            }
            if (target.exists() && !target.delete()) {
                throw IOException(context.getString(R.string.update_error_save))
            }
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }
            post { listener.onDownloadProgress(release, downloaded, downloaded) }
            return target
        } catch (error: Exception) {
            partial.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, accept: String): HttpURLConnection {
        var current = URL(url)
        repeat(6) {
            val connection = current.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", accept)
            connection.setRequestProperty("Cache-Control", "no-cache")
            connection.setRequestProperty("Pragma", "no-cache")
            connection.setRequestProperty("User-Agent", ProductInfo.USER_AGENT)
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) throw IOException("HTTP $code without Location")
                current = URL(current, location)
                return@repeat
            }
            if (code != 200) {
                connection.disconnect()
                throw IOException("HTTP $code")
            }
            return connection
        }
        throw IOException("too many redirects")
    }

    @Suppress("DEPRECATION")
    private fun currentVersionCode(): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }

    private fun fileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") {
            "%02x".format(Locale.US, it.toInt() and 0xff)
        }
    }

    private fun post(action: () -> Unit) {
        if (!closed) main.post { if (!closed) action() }
    }

    private companion object {
        const val TAG = "od-update"
    }
}
