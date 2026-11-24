package com.simpletv.app.data

import org.json.JSONArray

/** A public-domain film from the built-in free list (movies.json). */
data class FreeFilm(
    val id: String,
    val title: String,
    val year: String,
    val plot: String,
    val duration: String,
    val poster: String?,
    val url: String,
    val category: String,
) {
    companion object {
        /** Blocking: call from a background thread. */
        fun load(url: String): List<FreeFilm> {
            val text = Xtream.read(url) { it.readBytes().toString(Charsets.UTF_8) }
            val arr = JSONArray(text)
            val out = ArrayList<FreeFilm>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val u = o.optString("url")
                if (u.isBlank()) continue
                out.add(
                    FreeFilm(
                        id = o.optString("id"),
                        title = o.optString("title"),
                        year = o.optString("year"),
                        plot = o.optString("plot"),
                        duration = o.optString("duration"),
                        poster = o.optString("poster").ifBlank { null },
                        url = u,
                        category = o.optString("category").ifBlank { "Films" },
                    ),
                )
            }
            return out
        }
    }
}
