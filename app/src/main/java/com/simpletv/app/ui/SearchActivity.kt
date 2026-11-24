package com.simpletv.app.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.simpletv.app.R
import com.simpletv.app.data.LiveStream
import com.simpletv.app.data.Media
import com.simpletv.app.data.PlayItem
import com.simpletv.app.data.Prefs
import com.simpletv.app.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One search box across channels, films and series. */
class SearchActivity : BaseActivity() {

    private sealed class Hit {
        data class Channel(val s: LiveStream) : Hit()
        data class Movie(val m: Media) : Hit()
        data class Show(val m: Media) : Hit()
    }

    private lateinit var query: EditText
    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private lateinit var progress: View
    private val adapter = RowAdapter(onClick = ::onHitClick, showIcons = true)
    private var hits: List<Hit> = emptyList()
    private var job: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        setupTopBar("search")
        findViewById<TextView>(R.id.screenTitle).text = getString(R.string.search)
        query = findViewById(R.id.query)
        list = findViewById(R.id.list)
        empty = findViewById(R.id.empty)
        progress = findViewById(R.id.progress)
        query.show(true)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        query.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                search(s?.toString().orEmpty(), 450)
            }
        })
        query.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                search(query.text.toString(), 0)
                list.post { list.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }
                true
            } else {
                false
            }
        }
        query.requestFocus()
    }

    private fun search(text: String, wait: Long) {
        job?.cancel()
        val q = searchKey(text.trim())
        if (q.length < 2) {
            hits = emptyList()
            adapter.items = emptyList()
            empty.text = "Tapez au moins 2 lettres."
            empty.show(true)
            return
        }
        job = lifecycleScope.launch {
            delay(wait)
            progress.show(true)
            try {
                val liveHidden = Prefs.hiddenCategories("live")
                val vodHidden = Prefs.hiddenCategories("vod")
                val seriesHidden = Prefs.hiddenCategories("series")
                val live = Repo.liveStreams()
                val films = runCatching { Repo.movies() }.getOrDefault(emptyList())
                val shows = runCatching { Repo.series() }.getOrDefault(emptyList())
                val found = withContext(Dispatchers.Default) {
                    val out = ArrayList<Hit>()
                    live.asSequence().filter { it.categoryId !in liveHidden && searchKey(it.name).contains(q) }
                        .take(60).forEach { out.add(Hit.Channel(it)) }
                    films.asSequence().filter { it.categoryId !in vodHidden && searchKey(it.name).contains(q) }
                        .take(60).forEach { out.add(Hit.Movie(it)) }
                    shows.asSequence().filter { it.categoryId !in seriesHidden && searchKey(it.name).contains(q) }
                        .take(60).forEach { out.add(Hit.Show(it)) }
                    out
                }
                hits = found
                adapter.items = found.map { h ->
                    when (h) {
                        is Hit.Channel -> RowAdapter.Row(title = h.s.name, left = "TV", icon = h.s.icon)
                        is Hit.Movie -> RowAdapter.Row(title = h.m.name, left = "Film", icon = h.m.image)
                        is Hit.Show -> RowAdapter.Row(title = h.m.name, left = "Série", icon = h.m.image)
                    }
                }
                empty.text = "Aucun résultat."
                empty.show(found.isEmpty())
            } catch (e: Exception) {
                empty.text = errorText(e)
                empty.show(true)
            } finally {
                progress.show(false)
            }
        }
    }

    private fun onHitClick(position: Int) {
        when (val h = hits.getOrNull(position) ?: return) {
            is Hit.Channel -> {
                val channels = hits.filterIsInstance<Hit.Channel>().map { it.s }
                Repo.queue = channels.map { PlayItem.Live(it) }
                Repo.queueIndex = channels.indexOfFirst { it.id == h.s.id }.coerceAtLeast(0)
                startActivity(Intent(this, PlayerActivity::class.java))
            }
            is Hit.Movie -> openDetail(MovieDetailActivity::class.java, h.m)
            is Hit.Show -> openDetail(SeriesDetailActivity::class.java, h.m)
        }
    }

    private fun openDetail(cls: Class<*>, m: Media) {
        startActivity(
            Intent(this, cls)
                .putExtra(DetailExtras.ID, m.id)
                .putExtra(DetailExtras.NAME, m.name)
                .putExtra(DetailExtras.IMAGE, m.image)
                .putExtra(DetailExtras.EXT, m.ext),
        )
    }
}
