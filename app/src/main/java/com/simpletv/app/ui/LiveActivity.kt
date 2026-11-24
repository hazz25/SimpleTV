package com.simpletv.app.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.simpletv.app.R
import com.simpletv.app.data.Category
import com.simpletv.app.data.EpgEntry
import com.simpletv.app.data.LiveStream
import com.simpletv.app.data.PlayItem
import com.simpletv.app.data.Prefs
import com.simpletv.app.data.Repo
import kotlinx.coroutines.launch

/** Live TV in three columns: categories, channels, preview. */
@OptIn(UnstableApi::class)
class LiveActivity : BaseActivity() {

    companion object {
        const val EXTRA_CATEGORY = "category"
        const val RECENT = "__recent"
        const val ALL = "__all"
        const val FAV = "__fav"
    }

    private data class CatEntry(val id: String, val name: String, val count: Int)

    private lateinit var catList: RecyclerView
    private lateinit var chList: RecyclerView
    private lateinit var header: TextView
    private lateinit var previewView: PlayerView
    private lateinit var previewHint: TextView
    private lateinit var chName: TextView
    private lateinit var epgNow: TextView
    private lateinit var epgNext: TextView
    private lateinit var progress: View

    private val catAdapter = RowAdapter(
        onClick = ::onCategoryClick,
        onLongClick = ::onCategoryLongClick,
    )
    private val chAdapter = RowAdapter(
        onClick = ::onChannelClick,
        onLongClick = ::onChannelLongClick,
        showIcons = true,
    )

    private var allStreams: List<LiveStream> = emptyList()
    private var byId: Map<String, LiveStream> = emptyMap()
    private var categories: List<Category> = emptyList()
    private var cats: List<CatEntry> = emptyList()
    private var channels: List<LiveStream> = emptyList()
    private var currentCat: String = ALL

    private var previewing: LiveStream? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pendingCategory: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live)
        setupTopBar("live")
        catList = findViewById(R.id.categories)
        chList = findViewById(R.id.channels)
        header = findViewById(R.id.channelsHeader)
        previewView = findViewById(R.id.preview)
        previewHint = findViewById(R.id.previewHint)
        chName = findViewById(R.id.chName)
        epgNow = findViewById(R.id.epgNow)
        epgNext = findViewById(R.id.epgNext)
        progress = findViewById(R.id.progress)

        catList.layoutManager = LinearLayoutManager(this)
        catList.adapter = catAdapter
        chList.layoutManager = LinearLayoutManager(this)
        chList.adapter = chAdapter
        previewView.isFocusable = false
        previewView.descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS

        // Keep the preview 16:9 whatever the screen size.
        val box = findViewById<View>(R.id.previewBox)
        box.post {
            val lp = box.layoutParams
            lp.height = box.width * 9 / 16
            box.layoutParams = lp
        }

        load(intent.getStringExtra(EXTRA_CATEGORY))
    }

    private fun load(requested: String?) {
        progress.show(true)
        lifecycleScope.launch {
            try {
                categories = Repo.liveCategories()
                allStreams = Repo.liveStreams()
                byId = allStreams.associateBy { it.id.toString() }
                buildCategories()
                val start = requested ?: Prefs.lastLiveCategory ?: ALL
                showCategory(if (cats.any { it.id == start }) start else ALL)
                focusCategory()
            } catch (e: Exception) {
                header.text = errorText(e)
            } finally {
                progress.show(false)
            }
        }
    }

    // ---- Categories ------------------------------------------------------------------------

    private fun visibleStreams(): List<LiveStream> {
        val hidden = Prefs.hiddenCategories("live")
        return if (hidden.isEmpty()) allStreams else allStreams.filter { it.categoryId !in hidden }
    }

    private fun buildCategories() {
        val hidden = Prefs.hiddenCategories("live")
        val counts = allStreams.groupingBy { it.categoryId }.eachCount()
        val list = ArrayList<CatEntry>()
        list.add(CatEntry(RECENT, "Vus récemment", Prefs.recents("live").count { it in byId }))
        list.add(CatEntry(ALL, "Toutes les chaînes", visibleStreams().size))
        list.add(CatEntry(FAV, "★ Favoris", Prefs.favorites("live").count { it in byId }))
        for (c in categories) {
            val n = counts[c.id] ?: 0
            if (n > 0 && c.id !in hidden) list.add(CatEntry(c.id, c.name, n))
        }
        cats = list
        catAdapter.items = list.map { RowAdapter.Row(title = it.name, right = it.count.toString()) }
        catAdapter.activated = list.indexOfFirst { it.id == currentCat }
    }

    private fun channelsFor(id: String): List<LiveStream> = when (id) {
        RECENT -> Prefs.recents("live").mapNotNull { byId[it] }
        FAV -> Prefs.favorites("live").mapNotNull { byId[it] }
        ALL -> visibleStreams()
        else -> allStreams.filter { it.categoryId == id }
    }

    private fun showCategory(id: String) {
        currentCat = id
        Prefs.lastLiveCategory = id
        channels = channelsFor(id)
        val idx = cats.indexOfFirst { it.id == id }
        catAdapter.activated = idx
        val name = cats.getOrNull(idx)?.name ?: ""
        header.text = if (channels.isEmpty()) "$name — aucune chaîne" else "$name (${channels.size})"
        chAdapter.items = rows(channels)
        chList.scrollToPosition(0)
    }

    private fun rows(list: List<LiveStream>): List<RowAdapter.Row> {
        val favs = Prefs.favorites("live").toHashSet()
        return list.mapIndexed { i, s ->
            RowAdapter.Row(
                title = s.name,
                left = (if (s.num > 0) s.num else i + 1).toString(),
                icon = s.icon,
                star = s.id.toString() in favs,
            )
        }
    }

    private fun focusCategory() {
        val idx = cats.indexOfFirst { it.id == currentCat }.coerceAtLeast(0)
        catList.scrollToPosition(idx)
        catList.post { catList.findViewHolderForAdapterPosition(idx)?.itemView?.requestFocus() }
    }

    private fun focusChannel(index: Int) {
        if (index !in channels.indices) return
        chList.scrollToPosition(index)
        chList.post { chList.findViewHolderForAdapterPosition(index)?.itemView?.requestFocus() }
    }


    private fun onCategoryClick(position: Int) {
        pendingCategory?.let { handler.removeCallbacks(it) }
        val id = cats.getOrNull(position)?.id ?: return
        if (id != currentCat) showCategory(id)
        focusChannel(0)
    }

    private fun onCategoryLongClick(position: Int) {
        val c = cats.getOrNull(position) ?: return
        if (c.id == RECENT || c.id == ALL || c.id == FAV) return
        Prefs.hideCategory("live", c.id)
        toast("« ${c.name} » masquée. Paramètres → Catégories masquées pour la réafficher.")
        if (currentCat == c.id) currentCat = ALL
        buildCategories()
        showCategory(currentCat)
        focusCategory()
    }

    // ---- Channels --------------------------------------------------------------------------

    private fun onChannelClick(position: Int) {
        val s = channels.getOrNull(position) ?: return
        if (previewing?.id == s.id) openFullScreen(position) else startPreview(s)
    }

    private fun onChannelLongClick(position: Int) {
        val s = channels.getOrNull(position) ?: return
        val nowFav = Prefs.toggleFavorite("live", s.id.toString())
        toast(if (nowFav) "Ajoutée aux favoris" else "Retirée des favoris")
        buildCategories()
        if (currentCat == FAV) {
            showCategory(FAV)
            focusChannel(position.coerceAtMost(channels.size - 1))
        } else {
            chAdapter.update(position, rows(listOf(s))[0].copy(left = chAdapter.items[position].left))
        }
    }

    private fun openFullScreen(position: Int) {
        Repo.queue = channels.map { PlayItem.Live(it) }
        Repo.queueIndex = position
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    // ---- Preview ---------------------------------------------------------------------------

    private val preview by lazy {
        RetryingPlayer(this, previewView, object : RetryingPlayer.Callback {
            override fun onReady() {
                previewHint.show(false)
            }

            override fun onFailed(message: String) {
                previewHint.text = message
                previewHint.show(true)
            }
        })
    }

    private fun startPreview(s: LiveStream) {
        previewing = s
        Prefs.addRecent("live", s.id.toString())
        chName.text = s.name
        epgNow.text = ""
        epgNext.text = ""
        previewHint.text = getString(R.string.loading)
        previewHint.show(true)
        try {
            preview.play(Streams.live(Repo.account(), s.id), isLive = true)
        } catch (e: Exception) {
            previewHint.text = errorText(e)
        }
        lifecycleScope.launch {
            val epg = Repo.shortEpg(s.id)
            if (previewing?.id == s.id) showEpg(epg)
        }
    }

    private fun showEpg(list: List<EpgEntry>) {
        val now = list.getOrNull(0)
        val next = list.getOrNull(1)
        epgNow.text = now?.let { "En ce moment : ${formatTime(it.start)} – ${formatTime(it.end)}  ${it.title}" }
            ?: "Pas de programme disponible"
        epgNext.text = next?.let { "Ensuite : ${formatTime(it.start)}  ${it.title}" } ?: ""
    }

    private fun releasePlayer() {
        preview.release()
    }

    override fun onPause() {
        super.onPause()
        // Free the connection right away: many subscriptions allow only one stream at a time.
        releasePlayer()
    }

    override fun onResume() {
        super.onResume()
        val back = Repo.lastLivePlayed
        if (back != null) {
            Repo.lastLivePlayed = null
            startPreview(back)
            focusChannel(channels.indexOfFirst { it.id == back.id })
        } else {
            previewing?.let { startPreview(it) }
        }
    }

    override fun onDestroy() {
        pendingCategory?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // The Menu key also toggles the favourite on the focused channel.
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            val focused = chList.focusedChild
            if (focused != null) {
                val pos = chList.getChildAdapterPosition(focused)
                if (pos != RecyclerView.NO_POSITION) onChannelLongClick(pos)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
