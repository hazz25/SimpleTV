package com.simpletv.app.ui

import android.content.Intent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.simpletv.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Top bar, clock and small helpers shared by every screen. */
open class BaseActivity : AppCompatActivity() {

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
