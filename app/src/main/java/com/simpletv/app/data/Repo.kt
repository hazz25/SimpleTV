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

    fun account(): Account = Prefs.account ?: throw IOException("Aucun compte configuré")

    fun clear() {
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

    suspend fun userInfo(): UserInfo = cached({ userInfo }, { userInfo = it }) { Xtream.login(it) }

    suspend fun liveCategories(): List<Category> =
        cached({ liveCats }, { liveCats = it }) { Xtream.categories(it, "get_live_categories") }

    suspend fun liveStreams(): List<LiveStream> = cached({ live }, { live = it }) { Xtream.liveStreams(it) }

    suspend fun movieCategories(): List<Category> =
        cached({ movieCats }, { movieCats = it }) { Xtream.categories(it, "get_vod_categories") }

    suspend fun movies(): List<Media> = cached({ movieList }, { movieList = it }) { Xtream.movies(it) }

    suspend fun seriesCategories(): List<Category> =
        cached({ seriesCats }, { seriesCats = it }) { Xtream.categories(it, "get_series_categories") }

    suspend fun series(): List<Media> = cached({ seriesList }, { seriesList = it }) { Xtream.series(it) }

    suspend fun shortEpg(streamId: Int): List<EpgEntry> = withContext(Dispatchers.IO) {
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
