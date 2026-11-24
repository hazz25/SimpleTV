package com.simpletv.app.data

import java.io.IOException

/** Reads M3U / M3U8 channel lists (#EXTINF lines followed by the stream address). */
object M3u {

    class Result(val categories: List<Category>, val streams: List<LiveStream>)

    private val ATTR = Regex("""([\w-]+)="([^"]*)"""")
    private const val NO_GROUP = "Sans catégorie"

    /** Downloads and reads a list. Blocking: call from a background thread. */
    fun load(url: String): Result {
        val result = Xtream.read(url) { input ->
            parse(input.bufferedReader(Charsets.UTF_8).lineSequence())
        }
        if (result.streams.isEmpty()) throw IOException("Aucune chaîne trouvée dans cette liste M3U.")
        return result
    }

    fun parse(lines: Sequence<String>): Result {
        val streams = ArrayList<LiveStream>()
        val groups = LinkedHashSet<String>()
        val seen = HashSet<Int>()

        var name: String? = null
        var logo: String? = null
        var group: String? = null
        var ua: String? = null
        var referrer: String? = null
        var backups: List<String> = emptyList()

        for (raw in lines) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = ATTR.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    name = titleOf(line)
                    logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                    group = attrs["group-title"]?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() }
                    ua = attrs["user-agent"] ?: attrs["http-user-agent"]
                    referrer = attrs["http-referrer"] ?: attrs["referrer"]
                    backups = attrs["backup-urls"]?.split('|')?.map { it.trim() }?.filter { it.startsWith("http") }.orEmpty()
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) ->
                    group = line.substringAfter(':').trim().takeIf { it.isNotEmpty() } ?: group
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val opt = line.substringAfter(':')
                    val value = opt.substringAfter('=', "").trim()
                    when (opt.substringBefore('=').trim().lowercase()) {
                        "http-user-agent" -> ua = value
                        "http-referrer", "http-referer" -> referrer = value
                    }
                }
                line.startsWith("#") -> Unit
                else -> {
                    // A stream address: it closes the entry started by #EXTINF.
                    if (line.startsWith("http", ignoreCase = true) || line.startsWith("rtmp", ignoreCase = true)) {
                        val id = line.hashCode()
                        if (seen.add(id)) {
                            val g = group ?: NO_GROUP
                            groups.add(g)
                            streams.add(
                                LiveStream(
                                    id = id,
                                    num = streams.size + 1,
                                    name = name?.takeIf { it.isNotBlank() } ?: "Chaîne ${streams.size + 1}",
                                    icon = logo,
                                    categoryId = g,
                                    url = line,
                                    userAgent = ua?.takeIf { it.isNotBlank() },
                                    referrer = referrer?.takeIf { it.isNotBlank() },
                                    backups = backups,
                                ),
                            )
                        }
                    }
                    name = null; logo = null; group = null; ua = null; referrer = null; backups = emptyList()
                }
            }
        }
        return Result(groups.map { Category(it, it) }, streams)
    }

    /** The channel name follows the first comma that is not inside quotes. */
    private fun titleOf(line: String): String {
        var inQuotes = false
        for (i in line.indices) {
            when (line[i]) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) return line.substring(i + 1).trim()
            }
        }
        return ""
    }
}
