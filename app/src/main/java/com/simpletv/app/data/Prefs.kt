package com.simpletv.app.data

import android.content.Context
import android.content.SharedPreferences

/** Everything the app remembers between launches. Types: "live", "vod" (films), "series". */
object Prefs {
    private lateinit var sp: SharedPreferences
    private const val SEP = "\u0001"

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("simpletv", Context.MODE_PRIVATE)
    }

    var account: Account?
        get() {
            val s = sp.getString("server", null) ?: return null
            val u = sp.getString("user", null) ?: return null
            val p = sp.getString("pass", null) ?: return null
            return Account(s, u, p)
        }
        set(v) {
            val e = sp.edit()
            if (v == null) {
                e.remove("server").remove("user").remove("pass")
            } else {
                e.putString("server", v.server).putString("user", v.user).putString("pass", v.pass)
            }
            e.apply()
        }

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

    var lastLiveCategory: String?
        get() = sp.getString("lastLiveCat", null)
        set(v) = sp.edit().putString("lastLiveCat", v).apply()

    private fun list(key: String): MutableList<String> =
        (sp.getString(key, "") ?: "").split(SEP).filter { it.isNotEmpty() }.toMutableList()

    private fun saveList(key: String, l: List<String>) {
        sp.edit().putString(key, l.joinToString(SEP)).apply()
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
        sp.edit().remove("recent_live").remove("recent_vod").remove("recent_series").apply()
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
        sp.edit().remove("hidden_live").remove("hidden_vod").remove("hidden_series").apply()
    }

    // Resume positions for films and episodes: milliseconds, or -1 once watched to the end.
    fun position(key: String): Long = sp.getLong("pos_$key", 0L)

    fun savePosition(key: String, ms: Long) {
        sp.edit().putLong("pos_$key", ms).apply()
    }
}
