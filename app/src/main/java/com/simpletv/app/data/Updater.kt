package com.simpletv.app.data

import android.content.Context
import android.os.Build
import com.simpletv.app.BuildConfig
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Checks GitHub for a newer Simple TV and downloads it. Blocking: call from a background thread. */
object Updater {
    private const val REPO = "hazz25/SimpleTV"

    data class Release(val versionCode: Int, val versionName: String, val apkUrl: String)

    /** The newest release found during this session, or null if not checked yet. */
    @Volatile
    var latest: Release? = null
        private set

    @Volatile
    var checked = false
        private set

    val currentName: String get() = BuildConfig.VERSION_NAME

    fun hasUpdate(): Boolean = (latest?.versionCode ?: 0) > BuildConfig.VERSION_CODE

    fun check(): Release? {
        val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "SimpleTV-updater")
        try {
            if (c.responseCode != 200) throw IOException("Vérification impossible (code ${c.responseCode})")
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val tag = o.optString("tag_name") // "v1.0.12": the last number is the build number
            val code = tag.substringAfterLast('.').toIntOrNull() ?: return null
            val assets = o.optJSONArray("assets") ?: return null
            var url: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                if (a.optString("name").endsWith(".apk")) {
                    url = a.optString("browser_download_url")
                    break
                }
            }
            val release = Release(code, tag.removePrefix("v"), url ?: return null)
            latest = release
            checked = true
            return release
        } finally {
            c.disconnect()
        }
    }

    /** Downloads the APK. [onProgress] receives 0–100 from the download thread. */
    fun download(ctx: Context, url: String, onProgress: (Int) -> Unit): File {
        // Android 7+ hands the file to the installer through a FileProvider; older versions need a
        // readable file in external storage.
        val base = if (Build.VERSION.SDK_INT >= 24) ctx.cacheDir else (ctx.externalCacheDir ?: ctx.cacheDir)
        val dir = File(base, "updates")
        dir.mkdirs()
        val file = File(dir, "SimpleTV.apk")
        val c = URL(url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = true
        c.connectTimeout = 15_000
        c.readTimeout = 60_000
        c.setRequestProperty("User-Agent", "SimpleTV-updater")
        try {
            if (c.responseCode != 200) throw IOException("Téléchargement impossible (code ${c.responseCode})")
            val total = c.contentLength.toLong()
            c.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var last = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != last) {
                                last = pct
                                onProgress(pct)
                            }
                        }
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        if (Build.VERSION.SDK_INT < 24) file.setReadable(true, false)
        return file
    }
}
