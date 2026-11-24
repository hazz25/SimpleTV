package com.simpletv.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.simpletv.app.R
import com.simpletv.app.data.Episode
import com.simpletv.app.data.PlayItem
import com.simpletv.app.data.Prefs
import com.simpletv.app.data.Repo
import com.simpletv.app.data.Season
import com.simpletv.app.data.Xtream
import kotlinx.coroutines.launch

/** A series: seasons on the left, episodes on the right. */
class SeriesDetailActivity : BaseActivity() {

    private var id = 0
    private var name = ""
    private var seasons: List<Season> = emptyList()
    private var currentSeason = 0

    private lateinit var seasonList: RecyclerView
    private lateinit var episodeList: RecyclerView
    private lateinit var btnFav: TextView

    private val seasonAdapter = RowAdapter(onClick = ::onSeasonClick, onFocus = ::onSeasonFocus)
    private val episodeAdapter = RowAdapter(onClick = ::onEpisodeClick)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_series)
        id = intent.getIntExtra(DetailExtras.ID, 0)
        name = intent.getStringExtra(DetailExtras.NAME).orEmpty()
        val image = intent.getStringExtra(DetailExtras.IMAGE)

        val poster = findViewById<ImageView>(R.id.poster)
        val backdrop = findViewById<ImageView>(R.id.backdrop)
        val title = findViewById<TextView>(R.id.title)
        val meta = findViewById<TextView>(R.id.meta)
        val plot = findViewById<TextView>(R.id.plot)
        val progress = findViewById<View>(R.id.progress)
        btnFav = findViewById(R.id.btnFav)
        seasonList = findViewById(R.id.seasons)
        episodeList = findViewById(R.id.episodes)
        seasonList.layoutManager = LinearLayoutManager(this)
        seasonList.adapter = seasonAdapter
        episodeList.layoutManager = LinearLayoutManager(this)
        episodeList.adapter = episodeAdapter

        title.text = name
        if (!image.isNullOrBlank()) poster.load(image)
        btnFav.setOnClickListener {
            val nowFav = Prefs.toggleFavorite("series", id.toString())
            toast(if (nowFav) "Ajoutée aux favoris" else "Retirée des favoris")
            updateFav()
        }
        updateFav()

        progress.show(true)
        lifecycleScope.launch {
            try {
                val info = Repo.seriesInfo(id)
                if (image.isNullOrBlank() && info.cover != null) poster.load(info.cover)
                info.backdrop?.let { backdrop.load(it) }
                meta.text = listOf(
                    info.year,
                    info.genre,
                    info.rating.takeIf { it.isNotEmpty() && it != "0" }?.let { "★ $it" }.orEmpty(),
                    "${info.seasons.size} saison" + if (info.seasons.size > 1) "s" else "",
                ).filter { it.isNotEmpty() }.joinToString("  •  ")
                plot.text = info.plot.ifEmpty { "Pas de résumé disponible." }
                seasons = info.seasons
                seasonAdapter.items = seasons.map { RowAdapter.Row(title = it.name, right = it.episodes.size.toString()) }
                if (seasons.isEmpty()) {
                    plot.text = "Aucun épisode disponible pour cette série."
                    btnFav.requestFocus()
                } else {
                    showSeason(resumeSeason())
                    focusFirstUnwatched()
                }
            } catch (e: Exception) {
                plot.text = errorText(e)
                btnFav.requestFocus()
            } finally {
                progress.show(false)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (seasons.isNotEmpty()) showSeason(currentSeason)
    }

    private fun updateFav() {
        val fav = id.toString() in Prefs.favorites("series")
        btnFav.text = if (fav) "★  Retirer des favoris" else "☆  Ajouter aux favoris"
    }

    private fun key(e: Episode) = "ep_${e.id}"

    /** The season holding the first episode not yet watched to the end. */
    private fun resumeSeason(): Int {
        val i = seasons.indexOfFirst { s -> s.episodes.any { Prefs.position(key(it)) >= 0 } }
        return if (i >= 0) i else 0
    }

    private fun showSeason(index: Int) {
        currentSeason = index.coerceIn(0, (seasons.size - 1).coerceAtLeast(0))
        seasonAdapter.activated = currentSeason
        val eps = seasons.getOrNull(currentSeason)?.episodes.orEmpty()
        episodeAdapter.items = eps.map { e ->
            val pos = Prefs.position(key(e))
            RowAdapter.Row(
                title = e.title.ifEmpty { "Épisode ${e.num}" },
                left = "E" + e.num.toString().padStart(2, '0'),
                right = when {
                    pos < 0 -> "✓ Vu"
                    pos > 0 -> "▶ ${formatDuration(pos)}"
                    else -> e.duration
                },
                sub = e.plot.takeIf { it.isNotEmpty() },
            )
        }
    }

    private fun focusFirstUnwatched() {
        val eps = seasons.getOrNull(currentSeason)?.episodes.orEmpty()
        val i = eps.indexOfFirst { Prefs.position(key(it)) >= 0 }.coerceAtLeast(0)
        episodeList.scrollToPosition(i)
        episodeList.post { episodeList.findViewHolderForAdapterPosition(i)?.itemView?.requestFocus() }
    }

    private fun onSeasonFocus(position: Int) {
        if (position != currentSeason) showSeason(position)
    }

    private fun onSeasonClick(position: Int) {
        showSeason(position)
        focusFirstUnwatched()
    }

    private fun onEpisodeClick(position: Int) {
        val eps = seasons.getOrNull(currentSeason)?.episodes ?: return
        val account = try {
            Repo.account()
        } catch (e: Exception) {
            toast(errorText(e)); return
        }
        Prefs.addRecent("series", id.toString())
        Repo.queue = eps.map { e ->
            PlayItem.Vod(Xtream.episodeUrl(account, e.id, e.ext), "$name — E${e.num} ${e.title}".trim(), key(e))
        }
        Repo.queueIndex = position
        startActivity(Intent(this, PlayerActivity::class.java))
    }
}
