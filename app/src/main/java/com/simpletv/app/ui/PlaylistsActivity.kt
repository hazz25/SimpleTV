package com.simpletv.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.simpletv.app.R
import com.simpletv.app.data.Playlist
import com.simpletv.app.data.Prefs
import com.simpletv.app.data.Repo

/** "Mes listes": switch between saved lists, add one, or delete one (long press). */
class PlaylistsActivity : BaseActivity() {

    private val adapter = RowAdapter(onClick = ::onClick, onLongClick = ::onLongClick)
    private var lists: List<Playlist> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        setupTopBar("lists")
        findViewById<TextView>(R.id.screenTitle).text = getString(R.string.my_lists)
        val list = findViewById<RecyclerView>(R.id.list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        refresh()
        val list = findViewById<RecyclerView>(R.id.list)
        list.post { list.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }
    }

    private fun refresh() {
        lists = Prefs.playlists()
        val activeId = Prefs.active?.id
        val rows = lists.map { p ->
            val kind = if (p.isXtream) "Xtream" else "M3U"
            val detail = if (p.isXtream) "${p.user}  •  ${p.server}" else p.url
            RowAdapter.Row(
                title = (if (p.id == activeId) "✓  " else "") + p.name,
                left = kind,
                sub = detail + if (p.id == activeId) "\nListe utilisée en ce moment" else "\nOK pour l'utiliser  •  appui long pour la supprimer",
            )
        }
        adapter.items = rows + RowAdapter.Row(
            title = "＋  Ajouter une liste",
            sub = "Code Xtream ou lien M3U",
        )
    }

    private fun onClick(position: Int) {
        val p = lists.getOrNull(position)
        if (p == null) {
            startActivity(Intent(this, LoginActivity::class.java).putExtra(LoginActivity.EXTRA_ADDING, true))
            return
        }
        if (p.id == Prefs.active?.id) {
            finish()
            return
        }
        Prefs.setActive(p.id)
        Repo.clear()
        toast("Liste « ${p.name} » sélectionnée")
        startActivity(
            Intent(this, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        finish()
    }

    private fun onLongClick(position: Int) {
        val p = lists.getOrNull(position) ?: return
        AlertDialog.Builder(this)
            .setTitle("Supprimer « ${p.name} » ?")
            .setMessage("Ses favoris et son historique seront aussi effacés de cet appareil.")
            .setPositiveButton("Supprimer") { _, _ ->
                val wasActive = p.id == Prefs.active?.id
                Prefs.removePlaylist(p.id)
                Repo.clear()
                if (Prefs.active == null) {
                    startActivity(
                        Intent(this, LoginActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                    )
                    finish()
                } else {
                    if (wasActive) toast("Liste « ${Prefs.active?.name} » sélectionnée")
                    refresh()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
}
