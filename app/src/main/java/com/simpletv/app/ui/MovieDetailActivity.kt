package com.simpletv.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import coil.load
import com.simpletv.app.R
import com.simpletv.app.data.PlayItem
import com.simpletv.app.data.Prefs
import com.simpletv.app.data.Repo
import com.simpletv.app.data.Xtream
import kotlinx.coroutines.launch

class MovieDetailActivity : BaseActivity() {

    private var id = 0
    private var name = ""
    private var ext = "mp4"
    private val key get() = "vod_$id"

    private lateinit var btnPlay: TextView
    private lateinit var btnRestart: TextView
    private lateinit var btnFav: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_movie)
        id = intent.getIntExtra(DetailExtras.ID, 0)
        name = intent.getStringExtra(DetailExtras.NAME).orEmpty()
        ext = intent.getStringExtra(DetailExtras.EXT)?.ifEmpty { null } ?: "mp4"
        val image = intent.getStringExtra(DetailExtras.IMAGE)

        val poster = findViewById<ImageView>(R.id.poster)
        val backdrop = findViewById<ImageView>(R.id.backdrop)
        val title = findViewById<TextView>(R.id.title)
        val meta = findViewById<TextView>(R.id.meta)
        val plot = findViewById<TextView>(R.id.plot)
        val cast = findViewById<TextView>(R.id.cast)
        val progress = findViewById<View>(R.id.progress)
        btnPlay = findViewById(R.id.btnPlay)
        btnRestart = findViewById(R.id.btnRestart)
        btnFav = findViewById(R.id.btnFav)

        title.text = name
        if (!image.isNullOrBlank()) poster.load(image)

        btnPlay.setOnClickListener { play(fromStart = false) }
        btnRestart.setOnClickListener { play(fromStart = true) }
        btnFav.setOnClickListener {
            val nowFav = Prefs.toggleFavorite("vod", id.toString())
            toast(if (nowFav) "Ajouté aux favoris" else "Retiré des favoris")
            updateButtons()
        }
        updateButtons()
        btnPlay.requestFocus()

        progress.show(true)
        lifecycleScope.launch {
            try {
                val info = Repo.movieInfo(id)
                info.ext?.let { ext = it }
                if (image.isNullOrBlank() && info.poster != null) poster.load(info.poster)
                info.backdrop?.let { backdrop.load(it) }
                meta.text = listOf(
                    info.year,
                    info.genre,
                    info.duration,
                    info.rating.takeIf { it.isNotEmpty() && it != "0" }?.let { "★ $it" }.orEmpty(),
                ).filter { it.isNotEmpty() }.joinToString("  •  ")
                plot.text = info.plot.ifEmpty { "Pas de résumé disponible." }
                cast.text = listOf(
                    info.director.takeIf { it.isNotEmpty() }?.let { "Réalisation : $it" }.orEmpty(),
                    info.cast.takeIf { it.isNotEmpty() }?.let { "Avec : $it" }.orEmpty(),
                ).filter { it.isNotEmpty() }.joinToString("\n")
            } catch (e: Exception) {
                plot.text = "Informations indisponibles. Le film peut quand même être lu."
            } finally {
                progress.show(false)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateButtons()
    }

    private fun updateButtons() {
        val pos = Prefs.position(key)
        if (pos > 0) {
            btnPlay.text = "▶  Reprendre à ${formatDuration(pos)}"
            btnRestart.show(true)
        } else {
            btnPlay.text = getString(R.string.play)
            btnRestart.show(false)
        }
        val fav = id.toString() in Prefs.favorites("vod")
        btnFav.text = if (fav) "★  Retirer des favoris" else "☆  Ajouter aux favoris"
    }

    private fun play(fromStart: Boolean) {
        if (fromStart) Prefs.savePosition(key, 0)
        val url = try {
            Repo.movieUrl(id, ext)
        } catch (e: Exception) {
            toast(errorText(e)); return
        }
        Prefs.addRecent("vod", id.toString())
        Repo.queue = listOf(PlayItem.Vod(url, name, key))
        Repo.queueIndex = 0
        startActivity(Intent(this, PlayerActivity::class.java))
    }
}
