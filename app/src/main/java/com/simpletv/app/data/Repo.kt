package com.simpletv.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/** In-memory cache of the provider's lists, loaded on first use. */
object Repo {
    private val lock = Mutex()

    private var userInfo: UserInfo? = null
    private var liveCats: List<Category>? = null
    private var live: List<LiveStream>? = null
    private var movieCats: List<Category>? = null
    private var movieList: List<Media>? = null
    private var seriesCats: List<Category>? = null
    private var seriesList: List<Media>? = null

    /** What the full-screen player plays, and where it is in that list. */
    var queue: List<PlayItem> = emptyList()
    var queueIndex: Int = 0

    /** The last channel watched full screen, so the live screen can preview it on return. */
    var lastLivePlayed: LiveStream? = null

    fun account(): Account = Prefs.account ?: throw IOException(
        if (Prefs.active == null) "Aucune liste configurée" else "Cette liste M3U ne propose que des chaînes en direct",
    )

    private val isM3u: Boolean get() = Prefs.active?.isXtream == false

    private var m3u: M3u.Result? = null

    private suspend fun m3u(): M3u.Result = withContext(Dispatchers.IO) {
        lock.withLock {
            m3u ?: M3u.load(Prefs.active?.url ?: throw IOException("Aucune liste configurée")).also { m3u = it }
        }
    }

    fun clear() {
        m3u = null
        userInfo = null
        liveCats = null
        live = null
        movieCats = null
        movieList = null
        seriesCats = null
        seriesList = null
    }

    fun setUserInfo(info: UserInfo) {
        userInfo = info
    }

    private suspend fun <T : Any> cached(get: () -> T?, set: (T) -> Unit, fetch: (Account) -> T): T =
        withContext(Dispatchers.IO) {
            lock.withLock {
                get() ?: fetch(account()).also(set)
            }
        }

    /** Account details; null for an M3U list, which has no account. */
    suspend fun userInfo(): UserInfo? {
        if (isM3u) {
            m3u() // checks that the list can be read
            return null
        }
        return cached({ userInfo }, { userInfo = it }) { Xtream.login(it) }
    }

    suspend fun liveCategories(): List<Category> {
        if (isM3u) return m3u().categories
        return cached({ liveCats }, { liveCats = it }) { Xtream.categories(it, "get_live_categories") }
    }

    suspend fun liveStreams(): List<LiveStream> {
        if (isM3u) return m3u().streams
        return cached({ live }, { live = it }) { Xtream.liveStreams(it) }
    }

    suspend fun movieCategories(): List<Category> {
        if (isM3u) return emptyList()
        return cached({ movieCats }, { movieCats = it }) { Xtream.categories(it, "get_vod_categories") }
    }

    suspend fun movies(): List<Media> {
        if (isM3u) return emptyList()
        return cached({ movieList }, { movieList = it }) { Xtream.movies(it) }
    }

    suspend fun seriesCategories(): List<Category> {
        if (isM3u) return emptyList()
        return cached({ seriesCats }, { seriesCats = it }) { Xtream.categories(it, "get_series_categories") }
    }

    suspend fun series(): List<Media> {
        if (isM3u) return emptyList()
        return cached({ seriesList }, { seriesList = it }) { Xtream.series(it) }
    }

    /** True when the active list only has live channels. */
    val liveOnly: Boolean get() = isM3u

    suspend fun shortEpg(streamId: Int): List<EpgEntry> = withContext(Dispatchers.IO) {
        if (isM3u) return@withContext emptyList()
        try {
            val now = System.currentTimeMillis() / 1000
            Xtream.shortEpg(account(), streamId).filter { it.end == 0L || it.end > now }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun movieInfo(id: Int): MovieInfo = withContext(Dispatchers.IO) { Xtream.movieInfo(account(), id) }

    suspend fun seriesInfo(id: Int): SeriesInfo = withContext(Dispatchers.IO) { Xtream.seriesInfo(account(), id) }
}
