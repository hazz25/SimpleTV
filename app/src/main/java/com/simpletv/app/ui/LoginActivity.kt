package com.simpletv.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.simpletv.app.R
import com.simpletv.app.data.Account
import com.simpletv.app.data.M3u
import com.simpletv.app.data.Playlist
import com.simpletv.app.data.Prefs
import com.simpletv.app.data.Repo
import com.simpletv.app.data.Xtream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Adds a list: an Xtream account or an M3U address. */
class LoginActivity : BaseActivity() {

    companion object {
        /** Set when opened from "Mes listes": Back returns there instead of leaving the app. */
        const val EXTRA_ADDING = "adding"
    }

    private lateinit var name: EditText
    private lateinit var m3uUrl: EditText
    private lateinit var server: EditText
    private lateinit var user: EditText
    private lateinit var pass: EditText
    private lateinit var tabXtream: TextView
    private lateinit var tabM3u: TextView
    private lateinit var help: TextView
    private lateinit var connect: TextView
    private lateinit var error: TextView
    private lateinit var progress: View
    private var m3uMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        name = findViewById(R.id.name)
        m3uUrl = findViewById(R.id.m3uUrl)
        server = findViewById(R.id.server)
        user = findViewById(R.id.user)
        pass = findViewById(R.id.pass)
        tabXtream = findViewById(R.id.tabXtream)
        tabM3u = findViewById(R.id.tabM3u)
        help = findViewById(R.id.help)
        connect = findViewById(R.id.connect)
        error = findViewById(R.id.error)
        progress = findViewById(R.id.progress)

        tabXtream.setOnClickListener { setMode(false) }
        tabM3u.setOnClickListener { setMode(true) }

        // A pasted full link (…get.php?username=…&password=…) fills in the three Xtream fields.
        server.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) fillFromLink() }
        pass.setOnEditorActionListener { _, actionId, _ -> doneToConnect(actionId) }
        m3uUrl.setOnEditorActionListener { _, actionId, _ -> doneToConnect(actionId) }
        connect.setOnClickListener { submit() }

        // On a phone held upright the form takes the full width.
        val scroll = findViewById<View>(R.id.scroll)
        val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        if (widthDp < 560f) {
            scroll.layoutParams = scroll.layoutParams.apply { width = ViewGroup.LayoutParams.MATCH_PARENT }
        }
        setMode(false)
        tabXtream.requestFocus()
    }

    private fun doneToConnect(actionId: Int): Boolean {
        if (actionId != EditorInfo.IME_ACTION_DONE) return false
        connect.requestFocus()
        return true
    }

    private fun setMode(m3u: Boolean) {
        m3uMode = m3u
        val on = ContextCompat.getColor(this, R.color.gold)
        val off = ContextCompat.getColor(this, R.color.text)
        tabXtream.setTextColor(if (m3u) off else on)
        tabM3u.setTextColor(if (m3u) on else off)
        help.setText(if (m3u) R.string.login_help_m3u else R.string.login_help)
        m3uUrl.show(m3u)
        server.show(!m3u)
        user.show(!m3u)
        pass.show(!m3u)
        connect.text = if (m3u) "Ajouter la liste" else getString(R.string.connect)
        error.text = ""
    }

    private fun fillFromLink(): Boolean {
        val parsed = Xtream.parseLink(server.text.toString()) ?: return false
        server.setText(parsed.server)
        user.setText(parsed.user)
        pass.setText(parsed.pass)
        return true
    }

    private fun submit() {
        error.text = ""
        val label = name.text.toString().trim()
        if (m3uMode) {
            val url = m3uUrl.text.toString().trim()
            // An M3U link from an Xtream provider works better as Xtream: switch automatically.
            val asXtream = Xtream.parseLink(url)
            if (asXtream != null) {
                connectXtream(asXtream, label)
                return
            }
            if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
                error.text = "L'adresse doit commencer par http:// ou https://"
                return
            }
            verifyAndSave {
                withContext(Dispatchers.IO) { M3u.load(url) }
                Playlist.m3u(Prefs.newId(), label.ifEmpty { Prefs.hostName(url) }, url)
            }
        } else {
            fillFromLink()
            val s = server.text.toString().trim()
            val u = user.text.toString().trim()
            val p = pass.text.toString().trim()
            if (s.isEmpty() || u.isEmpty() || p.isEmpty()) {
                error.text = "Remplissez les trois champs."
                return
            }
            connectXtream(Account(Xtream.normalizeServer(s), u, p), label)
        }
    }

    private fun connectXtream(account: Account, label: String) {
        verifyAndSave {
            withContext(Dispatchers.IO) { Xtream.login(account) }
            Playlist.xtream(Prefs.newId(), label.ifEmpty { Prefs.hostName(account.server) }, account)
        }
    }

    /** Checks the list, saves it as the active one and opens the home screen. */
    private fun verifyAndSave(check: suspend () -> Playlist) {
        progress.show(true)
        connect.isEnabled = false
        lifecycleScope.launch {
            try {
                val playlist = check()
                Prefs.addPlaylist(playlist)
                Repo.clear()
                startActivity(
                    Intent(this@LoginActivity, HomeActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                )
                finish()
            } catch (e: Exception) {
                error.text = errorText(e)
            } finally {
                progress.show(false)
                connect.isEnabled = true
            }
        }
    }
}
