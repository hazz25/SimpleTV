package com.simpletv.app.data

data class Account(val server: String, val user: String, val pass: String)

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
