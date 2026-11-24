package com.simpletv.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.simpletv.app.R
import com.simpletv.app.data.Prefs
import com.simpletv.app.data.Repo
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActivity : BaseActivity() {

    private lateinit var expiry: TextView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Prefs.account == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_home)
        expiry = findViewById(R.id.expiry)
        status = findViewById(R.id.status)
        startClock(findViewById(R.id.clock))

        click(R.id.tileLive) { go("live") }
        click(R.id.tileMovies) { go("movies") }
        click(R.id.tileSeries) { go("series") }
        click(R.id.tileSearch) { go("search") }
        click(R.id.tileFav) {
            startActivity(Intent(this, LiveActivity::class.java).putExtra(LiveActivity.EXTRA_CATEGORY, LiveActivity.FAV))
        }
        click(R.id.btnSettings) { startActivity(Intent(this, SettingsActivity::class.java)) }
        click(R.id.btnReload) { reload() }
        click(R.id.btnExit) { finishAffinity() }

        findViewById<View>(R.id.tileLive).requestFocus()
        loadAccount()
    }

    private fun click(id: Int, action: () -> Unit) {
        findViewById<View>(id).setOnClickListener { action() }
    }

    private fun loadAccount() {
        expiry.text = getString(R.string.loading)
        lifecycleScope.launch {
            try {
                val info = Repo.userInfo()
                val date = info.expDate?.let {
                    "Expire le " + SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date(it * 1000))
                } ?: "Abonnement sans date d'expiration"
                val state = if (info.status.isNotEmpty() && !info.status.equals("Active", true)) "  •  ${info.status}" else ""
                expiry.text = date + state
                status.text = ""
                // Warm up the channel list so "TV en direct" opens instantly.
                try {
                    Repo.liveCategories()
                    Repo.liveStreams()
                } catch (e: Exception) {
                    // The live screen shows its own error.
                }
            } catch (e: Exception) {
                expiry.text = ""
                status.text = "Connexion impossible : ${errorText(e)}"
            }
        }
    }

    private fun reload() {
        Repo.clear()
        toast("Mise à jour des listes…")
        loadAccount()
    }
}
