package com.simpletv.app.data

import android.net.Uri
import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import org.json.JSONArray
import org.json.JSONObject
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** The Xtream Codes API. Every function here blocks: call it from a background thread. */
object Xtream {
    const val UA = "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 SimpleTV/1.0"

    // ---- Addresses -------------------------------------------------------------------------

    /** Cleans what the user typed: adds http://, drops /player_api.php, /get.php, queries and trailing slashes. */
    fun normalizeServer(raw: String): String {
        var s = raw.trim()
        if (!s.startsWith("http://", ignoreCase = true) && !s.startsWith("https://", ignoreCase = true)) {
            s = "http://$s"
        }
        s = s.substringBefore('?')
        for (suffix in listOf("/player_api.php", "/get.php", "/xmltv.php")) {
            val i = s.indexOf(suffix, ignoreCase = true)
            if (i >= 0) s = s.substring(0, i)
        }
        return s.trimEnd('/')
    }

    /** Reads a full M3U/Xtream link (…/get.php?username=X&password=Y…) into an account, or null. */
    fun parseLink(raw: String): Account? {
        val text = raw.trim()
        if (!text.contains("username=", ignoreCase = true)) return null
        return try {
            val withScheme = if (text.startsWith("http", ignoreCase = true)) text else "http://$text"
            val uri = Uri.parse(withScheme)
            val u = uri.getQueryParameter("username")
            val p = uri.getQueryParameter("password")
            if (u.isNullOrBlank() || p.isNullOrBlank()) null else Account(normalizeServer(withScheme), u, p)
        } catch (e: Exception) {
            null
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun api(a: Account, action: String?, extra: String = ""): String =
        "${a.server}/player_api.php?username=${enc(a.user)}&password=${enc(a.pass)}" +
            (if (action != null) "&action=$action" else "") + extra

    fun liveUrl(a: Account, id: Int, ext: String) = "${a.server}/live/${a.user}/${a.pass}/$id.$ext"
    fun movieUrl(a: Account, id: Int, ext: String) = "${a.server}/movie/${a.user}/${a.pass}/$id.$ext"
    fun episodeUrl(a: Account, id: String, ext: String) = "${a.server}/series/${a.user}/${a.pass}/$id.$ext"

    // ---- HTTP ------------------------------------------------------------------------------

    fun open(url: String): HttpURLConnection {
        var current = url
        repeat(6) {
            val c = URL(current).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 90_000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", UA)
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                if (loc.isNullOrBlank()) throw IOException("Redirection invalide du serveur")
                current = URL(URL(current), loc).toString()
                return@repeat
            }
            if (code == 401 || code == 403) {
                c.disconnect()
                throw IOException("Accès refusé par le serveur ($code). Vérifiez vos identifiants.")
            }
            if (code != 200) {
                c.disconnect()
                throw IOException("Le serveur a répondu avec l'erreur $code")
            }
            return c
        }
        throw IOException("Trop de redirections")
    }

    fun <T> read(url: String, block: (InputStream) -> T): T {
        val c = open(url)
        try {
            return c.inputStream.buffered().use(block)
        } finally {
            c.disconnect()
        }
    }

    private fun text(url: String): String = read(url) { it.readBytes().toString(Charsets.UTF_8) }

    // ---- Account ---------------------------------------------------------------------------

    fun login(a: Account): UserInfo {
        val body = text(api(a, null))
        val root = try {
            JSONObject(body)
        } catch (e: Exception) {
            throw IOException("Réponse inattendue : est-ce bien un serveur Xtream ?")
        }
        val ui = root.optJSONObject("user_info") ?: throw IOException("Identifiants incorrects")
        val auth = ui.optString("auth")
        if (auth != "1" && !auth.equals("true", ignoreCase = true)) throw IOException("Identifiants incorrects")
        return UserInfo(
            status = ui.str("status"),
            expDate = ui.str("exp_date").toLongOrNull(),
            maxConnections = ui.str("max_connections"),
            activeConnections = ui.str("active_cons"),
        )
    }

    // ---- Lists (streamed, because film lists can hold tens of thousands of entries) -------

    fun categories(a: Account, action: String): List<Category> {
        val out = ArrayList<Category>()
        readObjects(api(a, action), setOf("category_id", "category_name")) { m ->
            val id = m["category_id"] ?: return@readObjects
            out.add(Category(id, m["category_name"]?.trim().orEmpty().ifEmpty { "Sans nom" }))
        }
        return out
    }

    fun liveStreams(a: Account): List<LiveStream> {
        val out = ArrayList<LiveStream>()
        val keys = setOf("num", "name", "stream_id", "stream_icon", "category_id")
        readObjects(api(a, "get_live_streams"), keys) { m ->
            val id = m["stream_id"]?.toIntOrNull() ?: return@readObjects
            out.add(
                LiveStream(
                    id = id,
                    num = m["num"]?.toIntOrNull() ?: 0,
                    name = m["name"]?.trim().orEmpty(),
                    icon = m["stream_icon"]?.takeIf { it.isNotBlank() },
                    categoryId = m["category_id"].orEmpty(),
                ),
            )
        }
        return out
    }

    fun movies(a: Account): List<Media> {
        val out = ArrayList<Media>()
        val keys = setOf("name", "stream_id", "stream_icon", "category_id", "rating", "added", "container_extension")
        readObjects(api(a, "get_vod_streams"), keys) { m ->
            val id = m["stream_id"]?.toIntOrNull() ?: return@readObjects
            out.add(
                Media(
                    id = id,
                    name = m["name"]?.trim().orEmpty(),
                    image = m["stream_icon"]?.takeIf { it.isNotBlank() },
                    categoryId = m["category_id"].orEmpty(),
                    rating = m["rating"]?.takeIf { it.isNotBlank() && it != "0" },
                    added = m["added"]?.toLongOrNull() ?: 0L,
                    ext = m["container_extension"]?.takeIf { it.isNotBlank() } ?: "mp4",
                ),
            )
        }
        return out
    }

    fun series(a: Account): List<Media> {
        val out = ArrayList<Media>()
        val keys = setOf("name", "series_id", "cover", "category_id", "rating", "last_modified")
        readObjects(api(a, "get_series"), keys) { m ->
            val id = m["series_id"]?.toIntOrNull() ?: return@readObjects
            out.add(
                Media(
                    id = id,
                    name = m["name"]?.trim().orEmpty(),
                    image = m["cover"]?.takeIf { it.isNotBlank() },
                    categoryId = m["category_id"].orEmpty(),
                    rating = m["rating"]?.takeIf { it.isNotBlank() && it != "0" },
                    added = m["last_modified"]?.toLongOrNull() ?: 0L,
                    ext = "",
                ),
            )
        }
        return out
    }

    private fun readObjects(url: String, keys: Set<String>, onItem: (Map<String, String?>) -> Unit) {
        read(url) { input ->
            val r = JsonReader(InputStreamReader(input, Charsets.UTF_8))
            r.isLenient = true
            try {
                when (r.peek()) {
                    JsonToken.BEGIN_ARRAY -> {
                        r.beginArray()
                        while (r.hasNext()) readOne(r, keys, onItem)
                        r.endArray()
                    }
                    JsonToken.BEGIN_OBJECT -> {
                        // Some servers send {"1": {...}, "2": {...}} instead of a list.
                        r.beginObject()
                        while (r.hasNext()) {
                            r.nextName()
                            readOne(r, keys, onItem)
                        }
                        r.endObject()
                    }
                    else -> r.skipValue()
                }
            } catch (e: EOFException) {
                // Empty answer: nothing in this list.
            }
        }
    }

    private fun readOne(r: JsonReader, keys: Set<String>, onItem: (Map<String, String?>) -> Unit) {
        if (r.peek() != JsonToken.BEGIN_OBJECT) {
            r.skipValue()
            return
        }
        val m = HashMap<String, String?>(keys.size * 2)
        r.beginObject()
        while (r.hasNext()) {
            val name = r.nextName()
            if (name in keys) m[name] = r.anyString() else r.skipValue()
        }
        r.endObject()
        onItem(m)
    }

    private fun JsonReader.anyString(): String? = when (peek()) {
        JsonToken.NULL -> {
            nextNull(); null
        }
        JsonToken.STRING, JsonToken.NUMBER -> nextString()
        JsonToken.BOOLEAN -> nextBoolean().toString()
        else -> {
            skipValue(); null
        }
    }

    // ---- Details ---------------------------------------------------------------------------

    fun shortEpg(a: Account, streamId: Int): List<EpgEntry> {
        val root = JSONObject(text(api(a, "get_short_epg", "&stream_id=$streamId&limit=4")))
        val arr = root.optJSONArray("epg_listings") ?: return emptyList()
        val out = ArrayList<EpgEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                EpgEntry(
                    title = b64(o.str("title")),
                    start = o.str("start_timestamp").toLongOrNull() ?: 0L,
                    end = o.str("stop_timestamp").toLongOrNull() ?: 0L,
                ),
            )
        }
        return out
    }

    fun movieInfo(a: Account, id: Int): MovieInfo {
        val root = JSONObject(text(api(a, "get_vod_info", "&vod_id=$id")))
        val info = root.optJSONObject("info") ?: JSONObject()
        val data = root.optJSONObject("movie_data")
        return MovieInfo(
            plot = info.str("plot").ifEmpty { info.str("description") },
            genre = info.str("genre"),
            year = info.str("releasedate").take(4).ifEmpty { info.str("year") },
            duration = info.str("duration"),
            rating = info.str("rating"),
            cast = info.str("cast").ifEmpty { info.str("actors") },
            director = info.str("director"),
            poster = info.str("movie_image").ifEmpty { info.str("cover_big") }.ifEmpty { null },
            backdrop = firstImage(info.opt("backdrop_path")),
            ext = data?.str("container_extension")?.ifEmpty { null },
        )
    }

    fun seriesInfo(a: Account, id: Int): SeriesInfo {
        val root = JSONObject(text(api(a, "get_series_info", "&series_id=$id")))
        val info = root.optJSONObject("info") ?: JSONObject()

        val seasonNames = HashMap<Int, String>()
        root.optJSONArray("seasons")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                val n = s.str("season_number").toIntOrNull() ?: continue
                val name = s.str("name")
                if (name.isNotEmpty()) seasonNames[n] = name
            }
        }

        val bySeason = sortedMapOf<Int, MutableList<Episode>>()
        fun addEpisodes(arr: JSONArray, fallbackSeason: Int) {
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val epId = e.str("id")
                if (epId.isEmpty()) continue
                val season = e.str("season").toIntOrNull() ?: fallbackSeason
                val epInfo = e.optJSONObject("info")
                bySeason.getOrPut(season) { ArrayList() }.add(
                    Episode(
                        id = epId,
                        num = e.str("episode_num").toIntOrNull() ?: (i + 1),
                        title = e.str("title"),
                        ext = e.str("container_extension").ifEmpty { "mp4" },
                        plot = epInfo?.str("plot").orEmpty(),
                        image = epInfo?.str("movie_image")?.ifEmpty { null },
                        duration = epInfo?.str("duration").orEmpty(),
                    ),
                )
            }
        }
        when (val eps = root.opt("episodes")) {
            is JSONObject -> {
                val names = eps.keys()
                while (names.hasNext()) {
                    val k = names.next()
                    eps.optJSONArray(k)?.let { arr -> addEpisodes(arr, k.toIntOrNull() ?: 1) }
                }
            }
            is JSONArray -> for (i in 0 until eps.length()) {
                eps.optJSONArray(i)?.let { arr -> addEpisodes(arr, i + 1) }
            }
        }

        val seasons = bySeason.map { (n, list) ->
            Season(n, seasonNames[n] ?: "Saison $n", list.sortedBy { it.num })
        }
        return SeriesInfo(
            plot = info.str("plot"),
            genre = info.str("genre"),
            year = info.str("releaseDate").ifEmpty { info.str("releasedate") }.take(4),
            rating = info.str("rating"),
            cast = info.str("cast"),
            cover = info.str("cover").ifEmpty { null },
            backdrop = firstImage(info.opt("backdrop_path")),
            seasons = seasons,
        )
    }

    // ---- Helpers ---------------------------------------------------------------------------

    private fun JSONObject.str(key: String): String {
        val v = optString(key, "")
        return if (v == "null") "" else v.trim()
    }

    private fun firstImage(v: Any?): String? = when (v) {
        is JSONArray -> v.optString(0, "").takeIf { it.isNotBlank() && it != "null" }
        is String -> v.takeIf { it.isNotBlank() && it != "null" }
        else -> null
    }

    private fun b64(s: String): String = try {
        String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8).trim()
    } catch (e: IllegalArgumentException) {
        s
    }
}
