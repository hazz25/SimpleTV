package com.simpletv.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Checks a list typed on the TV or sent from a phone, and returns it ready to save. */
object Setup {

    suspend fun verify(
        type: String,
        name: String = "",
        server: String = "",
        user: String = "",
        pass: String = "",
        url: String = "",
    ): Playlist = withContext(Dispatchers.IO) {
        val label = name.trim()
        when (type) {
            "free" -> {
                val list = Prefs.playlists().firstOrNull { it.isFree } ?: Playlist.free(Prefs.newId())
                M3u.load("${list.url}/live.m3u")
                list
            }
            "m3u" -> {
                val u = url.trim()
                // A subscription link (…get.php?username=…) works better as Xtream.
                val asXtream = Xtream.parseLink(u)
                if (asXtream != null) {
                    xtream(asXtream, label)
                } else {
                    if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) {
                        throw IOException("L'adresse doit commencer par http:// ou https://")
                    }
                    M3u.load(u)
                    Playlist.m3u(Prefs.newId(), label.ifEmpty { Prefs.hostName(u) }, u)
                }
            }
            "xtream" -> {
                val parsed = Xtream.parseLink(server)
                val account = parsed ?: run {
                    if (server.isBlank() || user.isBlank() || pass.isBlank()) throw IOException("Remplissez les trois champs.")
                    Account(Xtream.normalizeServer(server), user.trim(), pass.trim())
                }
                xtream(account, label)
            }
            else -> throw IOException("Type de liste inconnu")
        }
    }

    private fun xtream(a: Account, label: String): Playlist {
        Xtream.login(a)
        return Playlist.xtream(Prefs.newId(), label.ifEmpty { Prefs.hostName(a.server) }, a)
    }
}
