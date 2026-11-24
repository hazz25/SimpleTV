package com.simpletv.app.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.simpletv.app.R
import com.simpletv.app.data.Category
import com.simpletv.app.data.Media
import com.simpletv.app.data.Prefs
import com.simpletv.app.data.Repo
import kotlinx.coroutines.launch

/** Films or series: categories on the left, a poster grid on the right. */
class VodActivity : BaseActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        private const val RECENT = "__recent"
        private const val NEW = "__new"
        private const val ALL = "__all"
        private const val FAV = "__fav"
    }

    private data class CatEntry(val id: String, val name: String, val count: Int)

    private val isSeries by lazy { intent.getStringExtra(EXTRA_MODE) == "series" }
    private val type get() = if (isSeries) "series" else "vod"

    private lateinit var catList: RecyclerView
    private lateinit var grid: RecyclerView
    private lateinit var header: TextView
    private lateinit var progress: View

    private val catAdapter = RowAdapter(
        onClick = ::onCategoryClick,
        onLongClick = ::onCategoryLongClick,
    )
    private val posterAdapter = PosterAdapter(onClick = ::onPosterClick, onLongClick = ::onPosterLongClick)

    private var all: List<Media> = emptyList()
    private var byId: Map<String, Media> = emptyMap()
    private var categories: List<Category> = emptyList()
    private var cats: List<CatEntry> = emptyList()
    private var shown: List<Media> = emptyList()
    private var currentCat = ALL
    private var loaded = false

    private val handler = Handler(Looper.getMainLooper())
    private var pendingCategory: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vod)
        setupTopBar(if (isSeries) "series" else "movies")
        catList = findViewById(R.id.categories)
        grid = findViewById(R.id.grid)
        header = findViewById(R.id.gridHeader)
        progress = findViewById(R.id.progress)

        catList.layoutManager = LinearLayoutManager(this)
        catList.adapter = catAdapter
        grid.layoutManager = GridLayoutManager(this, 6)
        grid.adapter = posterAdapter

        progress.show(true)
        header.text = if (isSeries) "Chargement des séries…" else "Chargement des films…"
        lifecycleScope.launch {
            try {
                if (isSeries) {
                    categories = Repo.seriesCategories()
                    all = Repo.series()
                } else {
                    categories = Repo.movieCategories()
                    all = Repo.movies()
                }
                byId = all.associateBy { it.id.toString() }
                if (Repo.freeList && isSeries) {
                    header.text = "Il n'existe pas de séries gratuites et libres de droits en français ou en arabe. " +
                        "Retrouvez des séries en direct dans TV en direct → « Films & séries »."
                    return@launch
                }
                if (Repo.liveOnly) {
                    header.text = "Cette liste M3U ne contient que des chaînes en direct. Les films et séries " +
                        "sont disponibles avec un code Xtream (Paramètres → Mes listes)."
                    return@launch
                }
                buildCategories()
                showCategory(if (Prefs.recents(type).any { it in byId }) RECENT else ALL)
                focusCategory()
                loaded = true
            } catch (e: Exception) {
                header.text = errorText(e)
            } finally {
                progress.show(false)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Favourites and "recently watched" may have changed on the detail screen.
        if (loaded) {
            buildCategories()
            if (currentCat == RECENT || currentCat == FAV) showCategory(currentCat) else refreshStars()
        }
    }

    private fun visible(): List<Media> {
        val hidden = Prefs.hiddenCategories(type)
        return if (hidden.isEmpty()) all else all.filter { it.categoryId !in hidden }
    }

    private fun buildCategories() {
        val hidden = Prefs.hiddenCategories(type)
        val counts = all.groupingBy { it.categoryId }.eachCount()
        val list = ArrayList<CatEntry>()
        list.add(CatEntry(RECENT, "Vus récemment", Prefs.recents(type).count { it in byId }))
        list.add(CatEntry(NEW, "Nouveautés", minOf(100, visible().size)))
        list.add(CatEntry(ALL, if (isSeries) "Toutes les séries" else "Tous les films", visible().size))
        list.add(CatEntry(FAV, "★ Favoris", Prefs.favorites(type).count { it in byId }))
        for (c in categories) {
            val n = counts[c.id] ?: 0
            if (n > 0 && c.id !in hidden) list.add(CatEntry(c.id, c.name, n))
        }
        cats = list
        catAdapter.items = list.map { RowAdapter.Row(title = it.name, right = it.count.toString()) }
        catAdapter.activated = list.indexOfFirst { it.id == currentCat }
    }

    private fun itemsFor(id: String): List<Media> = when (id) {
        RECENT -> Prefs.recents(type).mapNotNull { byId[it] }
        FAV -> Prefs.favorites(type).mapNotNull { byId[it] }
        NEW -> visible().sortedByDescending { it.added }.take(100)
        ALL -> visible()
        else -> all.filter { it.categoryId == id }
    }

    private fun showCategory(id: String) {
        currentCat = id
        shown = itemsFor(id)
        val idx = cats.indexOfFirst { it.id == id }
        catAdapter.activated = idx
        val name = cats.getOrNull(idx)?.name ?: ""
        header.text = if (shown.isEmpty()) "$name — vide" else "$name (${shown.size})"
        posterAdapter.items = posters(shown)
        grid.scrollToPosition(0)
    }

    private fun posters(list: List<Media>): List<PosterAdapter.Poster> {
        val favs = Prefs.favorites(type).toHashSet()
        return list.map {
            PosterAdapter.Poster(
                title = it.name,
                image = it.image,
                star = it.id.toString() in favs,
                badge = it.rating?.let { r -> "★ $r" },
            )
        }
    }

    private fun refreshStars() {
        posterAdapter.items = posters(shown)
    }

    private fun focusCategory() {
        val idx = cats.indexOfFirst { it.id == currentCat }.coerceAtLeast(0)
        catList.scrollToPosition(idx)
        catList.post { catList.findViewHolderForAdapterPosition(idx)?.itemView?.requestFocus() }
    }


    private fun onCategoryClick(position: Int) {
        pendingCategory?.let { handler.removeCallbacks(it) }
        val id = cats.getOrNull(position)?.id ?: return
        if (id != currentCat) showCategory(id)
        if (shown.isNotEmpty()) grid.post { grid.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }
    }

    private fun onCategoryLongClick(position: Int) {
        val c = cats.getOrNull(position) ?: return
        if (c.id.startsWith("__")) return
        Prefs.hideCategory(type, c.id)
        toast("« ${c.name} » masquée. Paramètres → Catégories masquées pour la réafficher.")
        if (currentCat == c.id) currentCat = ALL
        buildCategories()
        showCategory(currentCat)
        focusCategory()
    }

    private fun onPosterClick(position: Int) {
        val m = shown.getOrNull(position) ?: return
        val cls = if (isSeries) SeriesDetailActivity::class.java else MovieDetailActivity::class.java
        startActivity(
            Intent(this, cls)
                .putExtra(DetailExtras.ID, m.id)
                .putExtra(DetailExtras.NAME, m.name)
                .putExtra(DetailExtras.IMAGE, m.image)
                .putExtra(DetailExtras.EXT, m.ext),
        )
    }

    private fun onPosterLongClick(position: Int) {
        val m = shown.getOrNull(position) ?: return
        val nowFav = Prefs.toggleFavorite(type, m.id.toString())
        toast(if (nowFav) "Ajouté aux favoris" else "Retiré des favoris")
        buildCategories()
        if (currentCat == FAV) showCategory(FAV) else posterAdapter.update(position, posters(listOf(m))[0])
    }

    override fun onDestroy() {
        pendingCategory?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }
}

object DetailExtras {
    const val ID = "id"
    const val NAME = "name"
    const val IMAGE = "image"
    const val EXT = "ext"
}
