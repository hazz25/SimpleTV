package com.simpletv.app.data

data class Account(val server: String, val user: String, val pass: String)

/** A saved source of channels: an Xtream account or an M3U playlist address. */
data class Playlist(
    val id: String,
    val name: String,
    val type: String,
    val server: String = "",
    val user: String = "",
    val pass: String = "",
    val url: String = "",
) {
    val isXtream: Boolean get() = type == XTREAM
    val isFree: Boolean get() = type == FREE
    val account: Account get() = Account(server, user, pass)

    fun sameSourceAs(o: Playlist): Boolean =
        type == o.type && (if (isXtream) server == o.server && user == o.user else url == o.url)

    companion object {
        const val XTREAM = "xtream"
        const val M3U = "m3u"
        const val FREE = "free"

        /** Built-in free list, rebuilt every night on GitHub (French and Arabic channels, public-domain films). */
        const val FREE_BASE = "https://raw.githubusercontent.com/hazz25/SimpleTV/main/free"

        fun free(id: String) = Playlist(id = id, name = "Chaînes gratuites", type = FREE, url = FREE_BASE)

        fun xtream(id: String, name: String, a: Account) =
            Playlist(id = id, name = name, type = XTREAM, server = a.server, user = a.user, pass = a.pass)

        fun m3u(id: String, name: String, url: String) = Playlist(id = id, name = name, type = M3U, url = url)
    }
}

data class UserInfo(
    val status: String,
    val expDate: Long?,
    val maxConnections: String,
    val activeConnections: String,
)

data class Category(val id: String, val name: String)

data class LiveStream(
    val id: Int,
    val num: Int,
    val name: String,
    val icon: String?,
    val categoryId: String,
    /** M3U lists give the stream address directly (Xtream builds it from the account). */
    val url: String? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
    /** Other addresses for the same channel, tried when the first one fails. */
    val backups: List<String> = emptyList(),
)

/** A film or a series as it appears in a grid. [ext] is the file extension (films only). */
data class Media(
    val id: Int,
    val name: String,
    val image: String?,
    val categoryId: String,
    val rating: String?,
    val added: Long,
    val ext: String,
)

data class EpgEntry(val title: String, val start: Long, val end: Long)

data class MovieInfo(
    val plot: String,
    val genre: String,
    val year: String,
    val duration: String,
    val rating: String,
    val cast: String,
    val director: String,
    val poster: String?,
    val backdrop: String?,
    val ext: String?,
)

data class Episode(
    val id: String,
    val num: Int,
    val title: String,
    val ext: String,
    val plot: String,
    val image: String?,
    val duration: String,
)

data class Season(val number: Int, val name: String, val episodes: List<Episode>)

data class SeriesInfo(
    val plot: String,
    val genre: String,
    val year: String,
    val rating: String,
    val cast: String,
    val cover: String?,
    val backdrop: String?,
    val seasons: List<Season>,
)

/** One thing the player can play. Live channels keep the stream so the player can switch TS/HLS. */
sealed class PlayItem {
    abstract val title: String

    data class Live(val stream: LiveStream) : PlayItem() {
        override val title: String get() = stream.name
    }

    data class Vod(val url: String, override val title: String, val key: String) : PlayItem()
}
