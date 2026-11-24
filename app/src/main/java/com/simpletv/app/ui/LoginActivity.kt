package com.simpletv.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.simpletv.app.R
import com.simpletv.app.data.Account
import com.simpletv.app.data.Prefs
import com.simpletv.app.data.Repo
import com.simpletv.app.data.Xtream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : BaseActivity() {

    private lateinit var server: EditText
    private lateinit var user: EditText
    private lateinit var pass: EditText
    private lateinit var connect: TextView
    private lateinit var error: TextView
    private lateinit var progress: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        server = findViewById(R.id.server)
        user = findViewById(R.id.user)
        pass = findViewById(R.id.pass)
        connect = findViewById(R.id.connect)
        error = findViewById(R.id.error)
        progress = findViewById(R.id.progress)

        Prefs.account?.let {
            server.setText(it.server)
            user.setText(it.user)
            pass.setText(it.pass)
        }

        // A pasted M3U link fills in all three fields.
        server.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) fillFromLink()
        }
        pass.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                connect.requestFocus()
                true
            } else {
                false
            }
        }
        connect.setOnClickListener { submit() }
        server.requestFocus()
    }

    private fun fillFromLink(): Boolean {
        val parsed = Xtream.parseLink(server.text.toString()) ?: return false
        server.setText(parsed.server)
        user.setText(parsed.user)
        pass.setText(parsed.pass)
        return true
    }

    private fun submit() {
        fillFromLink()
        val s = server.text.toString().trim()
        val u = user.text.toString().trim()
        val p = pass.text.toString().trim()
        if (s.isEmpty() || u.isEmpty() || p.isEmpty()) {
            error.text = "Remplissez les trois champs."
            return
        }
        val account = Account(Xtream.normalizeServer(s), u, p)
        error.text = ""
        progress.show(true)
        connect.isEnabled = false
        lifecycleScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { Xtream.login(account) }
                Prefs.account = account
                Repo.clear()
                Repo.setUserInfo(info)
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
