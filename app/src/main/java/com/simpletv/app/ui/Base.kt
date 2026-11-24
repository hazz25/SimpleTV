package com.simpletv.app.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.simpletv.app.R
import com.simpletv.app.data.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Top bar, clock and small helpers shared by every screen. */
open class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(UiScale.wrap(newBase))
    }

    protected fun setupTopBar(current: String) {
        val items = listOf(
            "home" to R.id.navHome,
            "live" to R.id.navLive,
            "movies" to R.id.navMovies,
            "series" to R.id.navSeries,
            "search" to R.id.navSearch,
        )
        for ((key, id) in items) {
            val v = findViewById<TextView>(id) ?: continue
            if (key == current) v.setTextColor(ContextCompat.getColor(this, R.color.gold))
            v.setOnClickListener { if (key != current) go(key) }
        }
        findViewById<TextView>(R.id.clock)?.let { startClock(it) }
    }

    protected fun startClock(clock: TextView) {
        val fmt = SimpleDateFormat("HH:mm", Locale.FRANCE)
        val tick = object : Runnable {
            override fun run() {
                clock.text = fmt.format(Date())
                clock.postDelayed(this, 15_000)
            }
        }
        tick.run()
    }

    /** Opens another main screen. Only the home screen stays underneath, so Back always leads home. */
    protected fun go(key: String) {
        val intent = when (key) {
            "home" -> Intent(this, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            "live" -> Intent(this, LiveActivity::class.java)
            "movies" -> Intent(this, VodActivity::class.java).putExtra(VodActivity.EXTRA_MODE, "movies")
            "series" -> Intent(this, VodActivity::class.java).putExtra(VodActivity.EXTRA_MODE, "series")
            "search" -> Intent(this, SearchActivity::class.java)
            else -> return
        }
        startActivity(intent)
        if (this !is HomeActivity) finish()
    }

    fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    /** Downloads the new version and opens Android's installer. */
    protected fun installUpdate(release: Updater.Release) {
        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(this)
                .setTitle("Autoriser les mises à jour")
                .setMessage(
                    "Pour s'installer, la mise à jour a besoin d'une autorisation, à donner une seule fois.\n\n" +
                        "Sur l'écran suivant, activez Simple TV, puis revenez ici et relancez la mise à jour.",
                )
                .setPositiveButton("Ouvrir les réglages") { _, _ ->
                    try {
                        startActivity(
                            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")),
                        )
                    } catch (e: Exception) {
                        toast("Réglage introuvable : mettez à jour avec Downloader (code 8977165).")
                    }
                }
                .setNegativeButton("Annuler", null)
                .show()
            return
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Mise à jour ${release.versionName}")
            .setMessage("Téléchargement… 0 %")
            .setCancelable(false)
            .show()
        lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    Updater.download(this@BaseActivity, release.apkUrl) { pct ->
                        runOnUiThread { dialog.setMessage("Téléchargement… $pct %") }
                    }
                }
                dialog.dismiss()
                val uri = if (Build.VERSION.SDK_INT >= 24) {
                    FileProvider.getUriForFile(this@BaseActivity, "$packageName.updates", file)
                } else {
                    Uri.fromFile(file)
                }
                startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (e: Exception) {
                dialog.dismiss()
                toast("Mise à jour impossible : ${errorText(e)}")
            }
        }
    }

    protected fun View.show(visible: Boolean) {
        visibility = if (visible) View.VISIBLE else View.GONE
    }

    protected fun errorText(e: Throwable): String = when (e) {
        is java.net.UnknownHostException -> "Serveur introuvable. Vérifiez l'adresse et votre connexion Internet."
        is java.net.SocketTimeoutException -> "Le serveur ne répond pas. Réessayez dans un instant."
        is java.net.ConnectException -> "Connexion au serveur impossible."
        else -> e.message ?: "Erreur inconnue"
    }
}

/**
 * The screens are laid out for a 960 × 540 TV canvas. On a phone that is smaller than that,
 * everything is shrunk to fit instead of being cut off, so the app looks the same as on the TV.
 */
object UiScale {
    private const val CANVAS_W = 960f
    private const val CANVAS_H = 540f

    fun wrap(ctx: Context): Context {
        val res = ctx.resources
        val dm = res.displayMetrics
        val long = maxOf(dm.widthPixels, dm.heightPixels).toFloat()
        val short = minOf(dm.widthPixels, dm.heightPixels).toFloat()
        if (long <= 0f || short <= 0f) return ctx
        val fit = minOf(long / CANVAS_W, short / CANVAS_H) // pixels per dp that make the canvas fit
        if (fit >= dm.density) return ctx // TVs and tablets: already big enough
        val conf = Configuration(res.configuration)
        conf.densityDpi = (fit * 160f).toInt().coerceAtLeast(80)
        return ctx.createConfigurationContext(conf)
    }
}

fun formatTime(epochSeconds: Long): String =
    SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(epochSeconds * 1000))

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.FRANCE, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.FRANCE, "%d:%02d", m, s)
}

private val ACCENTS = Regex("\\p{Mn}+")

/** Lower-case, accent-free text for searching. */
fun searchKey(s: String): String =
    java.text.Normalizer.normalize(s.lowercase(Locale.FRANCE), java.text.Normalizer.Form.NFD)
        .replace(ACCENTS, "")
