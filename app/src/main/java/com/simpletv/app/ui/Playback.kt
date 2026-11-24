package com.simpletv.app.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.PlayerView
import com.simpletv.app.data.Account
import com.simpletv.app.data.Prefs
import com.simpletv.app.data.Xtream

/** One way of asking the server for a stream: an address and the player name sent with it. */
data class Attempt(val url: String, val userAgent: String)

/**
 * IPTV servers differ: some refuse the TS format, some only answer known players, and most
 * keep the previous connection busy for a moment after zapping. This lists every way worth trying,
 * starting with the one that worked last time.
 */
object Streams {
    private const val VLC = "VLC/3.0.21 LibVLC/3.0.21"
    private const val OKHTTP = "okhttp/4.12.0"

    private fun agents(): List<String> {
        val all = listOf(Xtream.UA, VLC, OKHTTP)
        val preferred = Prefs.userAgent
        return if (preferred != null && preferred in all) listOf(preferred) + (all - preferred) else all
    }

    fun live(a: Account, id: Int): List<Attempt> {
        val format = Prefs.liveFormat
        val other = if (format == "ts") "m3u8" else "ts"
        val urls = listOf(
            Xtream.liveUrl(a, id, format),
            Xtream.liveUrl(a, id, other),
            "${a.server}/${a.user}/${a.pass}/$id", // older servers
        )
        return agents().flatMap { ua -> urls.map { Attempt(it, ua) } }
    }

    fun vod(url: String): List<Attempt> = agents().map { Attempt(url, it) }
}

/** Builds players tuned for IPTV streams. */
@OptIn(UnstableApi::class)
object Players {
    fun create(ctx: Context, userAgent: String): ExoPlayer {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
        val dataSource = DefaultDataSource.Factory(ctx, http)
        val extractors = DefaultExtractorsFactory().setTsExtractorFlags(
            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS,
        )
        val renderers = DefaultRenderersFactory(ctx).setEnableDecoderFallback(true)
        return ExoPlayer.Builder(ctx, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource, extractors))
            .build()
    }
}

/**
 * A player that works through a list of [Attempt]s until one plays, then remembers what worked.
 * The first refusal is retried once after a pause, because the server often still counts the
 * channel that was just closed as an open connection.
 */
@OptIn(UnstableApi::class)
class RetryingPlayer(
    private val ctx: Context,
    private val view: PlayerView,
    private val callback: Callback,
) {
    interface Callback {
        fun onReady() {}
        fun onEnded() {}
        fun onFailed(message: String)
    }

    var player: ExoPlayer? = null
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var attempts: List<Attempt> = emptyList()
    private var step = 0
    private var startMs = 0L
    private var live = false
    private var waitedOnce = false
    private var currentAgent: String? = null

    fun play(list: List<Attempt>, isLive: Boolean, startPositionMs: Long = 0L) {
        handler.removeCallbacksAndMessages(null)
        attempts = list
        live = isLive
        step = 0
        startMs = startPositionMs
        waitedOnce = false
        load()
    }

    private fun load() {
        val a = attempts.getOrNull(step) ?: return
        val p = playerFor(a.userAgent)
        val item = MediaItem.fromUri(a.url)
        if (!live && startMs > 0) p.setMediaItem(item, startMs) else p.setMediaItem(item)
        p.prepare()
        p.playWhenReady = true
    }

    private fun playerFor(agent: String): ExoPlayer {
        val existing = player
        if (existing != null && agent == currentAgent) return existing
        existing?.release()
        val p = Players.create(ctx, agent)
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) = handleError(p, error)

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    remember()
                    callback.onReady()
                }
                if (state == Player.STATE_ENDED) callback.onEnded()
            }
        })
        view.player = p
        player = p
        currentAgent = agent
        return p
    }

    private fun remember() {
        val a = attempts.getOrNull(step) ?: return
        Prefs.userAgent = a.userAgent
        if (live) {
            when {
                a.url.endsWith(".ts") -> Prefs.liveFormat = "ts"
                a.url.endsWith(".m3u8") -> Prefs.liveFormat = "m3u8"
            }
        }
    }

    private fun handleError(p: ExoPlayer, error: PlaybackException) {
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            p.seekToDefaultPosition()
            p.prepare()
            return
        }
        // Only network and server errors are worth another try; a file the device cannot decode won't improve.
        val networkProblem = error.errorCode in 2000..2999
        if (networkProblem) {
            if (!live && p.currentPosition > 0) startMs = p.currentPosition
            if (!waitedOnce) {
                waitedOnce = true
                handler.postDelayed({ load() }, 2500)
                return
            }
            if (step + 1 < attempts.size) {
                step++
                load()
                return
            }
        }
        callback.onFailed(describe(error))
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        view.player = null
        player?.release()
        player = null
        currentAgent = null
    }

    companion object {
        fun describe(error: PlaybackException): String {
            var cause: Throwable? = error.cause
            while (cause != null && cause !is HttpDataSource.InvalidResponseCodeException) cause = cause.cause
            val code = (cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
            return when {
                code == 401 || code == 403 ->
                    "Le serveur refuse l'accès (code $code).\nVérifiez que l'abonnement est actif et que personne d'autre ne l'utilise en ce moment."
                code == 404 -> "Ce contenu n'existe plus sur le serveur (code 404)."
                code == 429 || code == 458 || code == 509 ->
                    "Trop de connexions en même temps (code $code).\nFermez l'abonnement sur les autres appareils."
                code != null -> "Le serveur a renvoyé une erreur (code $code)."
                error.errorCode in 2000..2999 -> "Connexion au serveur impossible."
                error.errorCode in 4000..4999 -> "Format vidéo non pris en charge par cet appareil."
                else -> "Lecture impossible (${error.errorCodeName})."
            }
        }
    }
}
