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
    private lateinit var tabFree: TextView
    private lateinit var tabXtream: TextView
    private lateinit var tabM3u: TextView
    private lateinit var help: TextView
    private lateinit var connect: TextView
    private lateinit var error: TextView
    private lateinit var progress: View
    private enum class Mode { FREE, XTREAM, M3U }

    private var mode = Mode.FREE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        name = findViewById(R.id.name)
        m3uUrl = findViewById(R.id.m3uUrl)
        server = findViewById(R.id.server)
        user = findViewById(R.id.user)
        pass = findViewById(R.id.pass)
        tabFree = findViewById(R.id.tabFree)
        tabXtream = findViewById(R.id.tabXtream)
        tabM3u = findViewById(R.id.tabM3u)
        help = findViewById(R.id.help)
        connect = findViewById(R.id.connect)
        error = findViewById(R.id.error)
        progress = findViewById(R.id.progress)

        tabFree.setOnClickListener { setMode(Mode.FREE) }
        tabXtream.setOnClickListener { setMode(Mode.XTREAM) }
        tabM3u.setOnClickListener { setMode(Mode.M3U) }

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
        // Someone without a subscription can start right away; with one already saved, Xtream comes first.
        val hasFree = Prefs.playlists().any { it.isFree }
        setMode(if (hasFree) Mode.XTREAM else Mode.FREE)
        (if (hasFree) tabXtream else connect).requestFocus()
    }

    private fun doneToConnect(actionId: Int): Boolean {
        if (actionId != EditorInfo.IME_ACTION_DONE) return false
        connect.requestFocus()
        return true
    }

    private fun setMode(m: Mode) {
        mode = m
        val on = ContextCompat.getColor(this, R.color.gold)
        val off = ContextCompat.getColor(this, R.color.text)
        tabFree.setTextColor(if (m == Mode.FREE) on else off)
        tabXtream.setTextColor(if (m == Mode.XTREAM) on else off)
        tabM3u.setTextColor(if (m == Mode.M3U) on else off)
        help.setText(
            when (m) {
                Mode.FREE -> R.string.login_help_free
                Mode.XTREAM -> R.string.login_help
                Mode.M3U -> R.string.login_help_m3u
            },
        )
        name.show(m != Mode.FREE)
        m3uUrl.show(m == Mode.M3U)
        server.show(m == Mode.XTREAM)
        user.show(m == Mode.XTREAM)
        pass.show(m == Mode.XTREAM)
        connect.text = when (m) {
            Mode.FREE -> "Commencer"
            Mode.XTREAM -> getString(R.string.connect)
            Mode.M3U -> "Ajouter la liste"
        }
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
        if (mode == Mode.FREE) {
            verifyAndSave {
                val list = Prefs.playlists().firstOrNull { it.isFree } ?: Playlist.free(Prefs.newId())
                withContext(Dispatchers.IO) { M3u.load("${list.url}/live.m3u") }
                list
            }
            return
        }
        if (mode == Mode.M3U) {
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
