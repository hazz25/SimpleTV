package com.simpletv.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Everything the app remembers between launches. Content types: "live", "vod" (films), "series".
 * Favourites, history, hidden categories and resume points belong to the active list, so two
 * lists never mix their channels.
 */
object Prefs {
    private lateinit var sp: SharedPreferences
    private const val SEP = "\u0001"

    /** The list created before multi-list support keeps id "main" and its unprefixed data. */
    private const val LEGACY_ID = "main"

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("simpletv", Context.MODE_PRIVATE)
        migrate()
    }

    // ---- Lists -----------------------------------------------------------------------------

    private fun migrate() {
        if (sp.contains("playlists")) return
        val s = sp.getString("server", null)
        val u = sp.getString("user", null)
        val p = sp.getString("pass", null)
        if (s != null && u != null && p != null) {
            savePlaylists(listOf(Playlist.xtream(LEGACY_ID, hostName(s), Account(s, u, p))))
            sp.edit().putString("activeList", LEGACY_ID).remove("server").remove("user").remove("pass").apply()
        } else {
            savePlaylists(emptyList())
        }
    }

    private var cache: List<Playlist>? = null

    fun playlists(): List<Playlist> {
        cache?.let { return it }
        val arr = try {
            JSONArray(sp.getString("playlists", "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
        val out = ArrayList<Playlist>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                Playlist(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    type = o.optString("type", Playlist.XTREAM),
                    server = o.optString("server"),
                    user = o.optString("user"),
                    pass = o.optString("pass"),
                    url = o.optString("url"),
                ),
            )
        }
        cache = out
        return out
    }

    private fun savePlaylists(list: List<Playlist>) {
        val arr = JSONArray()
        for (p in list) {
            arr.put(
                JSONObject()
                    .put("id", p.id).put("name", p.name).put("type", p.type)
                    .put("server", p.server).put("user", p.user).put("pass", p.pass).put("url", p.url),
            )
        }
        sp.edit().putString("playlists", arr.toString()).apply()
        cache = list
    }

    val active: Playlist?
        get() {
            val all = playlists()
            val id = sp.getString("activeList", null)
            return all.firstOrNull { it.id == id } ?: all.firstOrNull()
        }

    fun setActive(id: String) {
        sp.edit().putString("activeList", id).apply()
    }

    /** Adds a list (or replaces one with the same address) and makes it the active one. */
    fun addPlaylist(p: Playlist): Playlist {
        val all = playlists().toMutableList()
        val same = all.indexOfFirst { it.sameSourceAs(p) }
        val saved = if (same >= 0) p.copy(id = all[same].id) else p
        if (same >= 0) all[same] = saved else all.add(saved)
        savePlaylists(all)
        setActive(saved.id)
        return saved
    }

    fun removePlaylist(id: String) {
        savePlaylists(playlists().filterNot { it.id == id })
        val prefix = "${id}_"
        val e = sp.edit()
        for (k in sp.all.keys) if (k.startsWith(prefix)) e.remove(k)
        if (sp.getString("activeList", null) == id) e.remove("activeList")
        e.apply()
    }

    fun newId(): String = UUID.randomUUID().toString().take(8)

    fun hostName(server: String): String =
        server.substringAfter("://").substringBefore('/').substringBefore(':').ifEmpty { "Ma liste" }

    /** The Xtream account of the active list, or null for an M3U list. */
    val account: Account?
        get() = active?.takeIf { it.isXtream }?.account

    /** Data key for the active list. */
    private fun k(name: String): String {
        val id = active?.id ?: LEGACY_ID
        return if (id == LEGACY_ID) name else "${id}_$name"
    }

    // ---- Settings shared by all lists ------------------------------------------------------

    /** "ts" or "m3u8". */
    var liveFormat: String
        get() = sp.getString("liveFormat", "ts") ?: "ts"
        set(v) = sp.edit().putString("liveFormat", v).apply()

    /** The player name the server last accepted. */
    var userAgent: String?
        get() = sp.getString("userAgent", null)
        set(v) = sp.edit().putString("userAgent", v).apply()

    var invertZap: Boolean
        get() = sp.getBoolean("invertZap", false)
        set(v) = sp.edit().putBoolean("invertZap", v).apply()

    // ---- Per-list data ---------------------------------------------------------------------

    var lastLiveCategory: String?
        get() = sp.getString(k("lastLiveCat"), null)
        set(v) = sp.edit().putString(k("lastLiveCat"), v).apply()

    private fun list(key: String): MutableList<String> =
        (sp.getString(k(key), "") ?: "").split(SEP).filter { it.isNotEmpty() }.toMutableList()

    private fun saveList(key: String, l: List<String>) {
        sp.edit().putString(k(key), l.joinToString(SEP)).apply()
    }

    // Favourites, most recent first.
    fun favorites(type: String): List<String> = list("fav_$type")

    /** Returns true when the item is now a favourite. */
    fun toggleFavorite(type: String, id: String): Boolean {
        val l = list("fav_$type")
        val nowFav = if (l.remove(id)) false else {
            l.add(0, id); true
        }
        saveList("fav_$type", l)
        return nowFav
    }

    // Recently watched, most recent first.
    fun recents(type: String): List<String> = list("recent_$type")

    fun addRecent(type: String, id: String) {
        val l = list("recent_$type")
        l.remove(id)
        l.add(0, id)
        saveList("recent_$type", l.take(60))
    }

    fun clearRecents() {
        sp.edit().remove(k("recent_live")).remove(k("recent_vod")).remove(k("recent_series")).apply()
    }

    // Hidden categories.
    fun hiddenCategories(type: String): Set<String> = list("hidden_$type").toSet()

    fun hideCategory(type: String, id: String) {
        val l = list("hidden_$type")
        if (id !in l) l.add(id)
        saveList("hidden_$type", l)
    }

    fun hiddenCount(): Int = listOf("live", "vod", "series").sumOf { list("hidden_$it").size }

    fun clearHidden() {
        sp.edit().remove(k("hidden_live")).remove(k("hidden_vod")).remove(k("hidden_series")).apply()
    }

    // Resume positions for films and episodes: milliseconds, or -1 once watched to the end.
    fun position(key: String): Long = sp.getLong(k("pos_$key"), 0L)

    fun savePosition(key: String, ms: Long) {
        sp.edit().putLong(k("pos_$key"), ms).apply()
    }
}
