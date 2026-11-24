package com.simpletv.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.simpletv.app.BuildConfig
import com.simpletv.app.R
import com.simpletv.app.data.Prefs
import com.simpletv.app.data.Repo
import com.simpletv.app.data.Updater
import com.simpletv.app.data.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A short list of settings: only what is useful. */
class SettingsActivity : BaseActivity() {

    private class Item(val title: String, val value: String, val action: (() -> Unit)?)

    private val adapter = RowAdapter(onClick = ::onItemClick)
    private var items: List<Item> = emptyList()
    private var info: UserInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        setupTopBar("settings")
        findViewById<TextView>(R.id.screenTitle).text = getString(R.string.settings)
        val list = findViewById<RecyclerView>(R.id.list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        refresh()
        list.post { list.findViewHolderForAdapterPosition(1)?.itemView?.requestFocus() }
        lifecycleScope.launch {
            info = runCatching { Repo.userInfo() }.getOrNull()
            refresh()
        }
    }

    private fun accountLine(): String {
        val list = Prefs.active ?: return "Aucune liste"
        if (!list.isXtream) return "${list.name}  •  liste M3U\n${list.url}"
        val i = info ?: return "${list.name}  •  ${list.user}\n${list.server}"
        val exp = i.expDate?.let { "expire le " + SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date(it * 1000)) }
            ?: "sans date d'expiration"
        val screens = if (i.maxConnections.isNotEmpty()) "  •  ${i.maxConnections} écran(s) max" else ""
        return "${list.name}  •  ${list.user}  •  $exp$screens\n${list.server}"
    }

    private fun refresh() {
        val hidden = Prefs.hiddenCount()
        items = listOf(
            Item("Liste utilisée", accountLine()) { startActivity(Intent(this, PlaylistsActivity::class.java)) },
            Item(
                "Format des chaînes en direct",
                if (Prefs.liveFormat == "ts") "TS (recommandé). Changez si les chaînes ne se lancent pas."
                else "HLS (m3u8). Changez si les chaînes ne se lancent pas.",
            ) {
                Prefs.liveFormat = if (Prefs.liveFormat == "ts") "m3u8" else "ts"
                refresh()
            },
            Item(
                "Changer de chaîne avec ▲ ▼",
                if (Prefs.invertZap) "Inversé : ▲ chaîne suivante" else "Normal : ▼ chaîne suivante",
            ) {
                Prefs.invertZap = !Prefs.invertZap
                refresh()
            },
            Item(
                "Catégories masquées",
                if (hidden == 0) "Aucune. Appui long sur une catégorie pour la masquer."
                else "$hidden masquée(s). OK pour toutes les réafficher.",
            ) {
                if (hidden > 0) {
                    Prefs.clearHidden()
                    toast("Toutes les catégories sont réaffichées")
                    refresh()
                }
            },
            Item("Effacer l'historique", "Vide les listes « Vus récemment »") {
                Prefs.clearRecents()
                toast("Historique effacé")
            },
            Item("Recharger les listes", "Récupère les nouvelles chaînes, films et séries") {
                Repo.clear()
                info = null
                toast("Les listes seront rechargées")
                lifecycleScope.launch {
                    info = runCatching { Repo.userInfo() }.getOrNull()
                    refresh()
                }
            },
            Item("Mes listes", "Changer de liste, en ajouter une (Xtream ou M3U) ou en supprimer") {
                startActivity(Intent(this, PlaylistsActivity::class.java))
            },
            Item("Mises à jour", updateLine()) { onUpdateClick() },
        )
        adapter.items = items.map { RowAdapter.Row(title = it.title, sub = it.value) }
    }

    private var checking = false

    private fun updateLine(): String {
        val current = "Version installée : ${BuildConfig.VERSION_NAME}"
        val latest = Updater.latest
        return when {
            checking -> "$current\nRecherche d'une mise à jour…"
            latest != null && Updater.hasUpdate() -> "$current\nNouvelle version ${latest.versionName} disponible : OK pour l'installer"
            Updater.checked -> "$current\nVous avez la dernière version. OK pour vérifier à nouveau"
            else -> "$current\nOK pour rechercher une mise à jour"
        }
    }

    private fun onUpdateClick() {
        val latest = Updater.latest
        if (latest != null && Updater.hasUpdate()) {
            installUpdate(latest)
            return
        }
        if (checking) return
        checking = true
        refresh()
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { Updater.check() }
            } catch (e: Exception) {
                toast("Vérification impossible : ${errorText(e)}")
            }
            checking = false
            refresh()
        }
    }

    private fun onItemClick(position: Int) {
        items.getOrNull(position)?.action?.invoke()
    }
}
