package com.simpletv.app.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.addCallback
import androidx.annotation.OptIn
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.simpletv.app.R
import com.simpletv.app.data.LiveStream
import com.simpletv.app.data.PlayItem
import com.simpletv.app.data.Prefs
import com.simpletv.app.data.Repo
import com.simpletv.app.data.Xtream
import kotlinx.coroutines.launch

/**
 * Full-screen playback. Live: ▲/▼ (or CH+/CH−) change channel, OK shows what is on.
 * Films and episodes: ◀/▶ skip back 10 s / forward 30 s, OK shows the controls; the next episode follows.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : BaseActivity() {

    private lateinit var view: PlayerView
    private lateinit var overlay: View
    private lateinit var ovTitle: TextView
    private lateinit var ovEpg: TextView
    private lateinit var errorView: TextView

    private var player: ExoPlayer? = null
    private var index = 0
    private var triedOtherFormat = false
    private val handler = Handler(Looper.getMainLooper())
    private val hideOverlay = Runnable { overlay.visibility = View.GONE }

    private val items: List<PlayItem> get() = Repo.queue
    private val current: PlayItem? get() = items.getOrNull(index)
    private val isLive: Boolean get() = current is PlayItem.Live

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = findViewById(R.id.player)
        overlay = findViewById(R.id.overlay)
        ovTitle = findViewById(R.id.ovTitle)
        ovEpg = findViewById(R.id.ovEpg)
        errorView = findViewById(R.id.error)
        index = Repo.queueIndex
        if (items.isEmpty()) {
            finish()
            return
        }
        view.controllerShowTimeoutMs = 4000
        view.setShowSubtitleButton(true)

        onBackPressedDispatcher.addCallback(this) {
            if (!isLive && view.isControllerFullyVisible) view.hideController() else finish()
        }
    }

    // Started in onResume and freed in onPause, so the live screen's preview never overlaps this
    // stream: many subscriptions allow only one connection at a time.
    override fun onResume() {
        super.onResume()
        if (items.isEmpty() || player != null) return
        val p = Players.create(this)
        p.addListener(listener)
        player = p
        view.player = p
        play(index)
    }

    override fun onPause() {
        super.onPause()
        savePosition()
        view.player = null
        player?.release()
        player = null
        handler.removeCallbacks(hideOverlay)
    }

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val p = player ?: return
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                p.seekToDefaultPosition()
                p.prepare()
                return
            }
            val item = current
            if (item is PlayItem.Live && !triedOtherFormat) {
                // Some servers only deliver one of the two formats: try the other one once.
                triedOtherFormat = true
                val other = if (Prefs.liveFormat == "ts") "m3u8" else "ts"
                p.setMediaItem(MediaItem.fromUri(Xtream.liveUrl(Repo.account(), item.stream.id, other)))
                p.prepare()
                return
            }
            errorView.text = "Lecture impossible\n(${error.errorCodeName})" +
                if (item is PlayItem.Live) "\n\n▲ ▼ pour changer de chaîne" else ""
            errorView.visibility = View.VISIBLE
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) errorView.visibility = View.GONE
            if (state == Player.STATE_ENDED) {
                val item = current
                if (item is PlayItem.Vod) {
                    Prefs.savePosition(item.key, -1)
                    if (index + 1 < items.size) play(index + 1) else finish()
                }
            }
        }
    }

    private fun play(i: Int) {
        val p = player ?: return
        index = i
        Repo.queueIndex = i
        triedOtherFormat = false
        errorView.visibility = View.GONE
        when (val item = current ?: return) {
            is PlayItem.Live -> {
                view.useController = false
                Repo.lastLivePlayed = item.stream
                Prefs.addRecent("live", item.stream.id.toString())
                p.setMediaItem(MediaItem.fromUri(Xtream.liveUrl(Repo.account(), item.stream.id, Prefs.liveFormat)))
                showLiveInfo(item.stream)
            }
            is PlayItem.Vod -> {
                view.useController = true
                val start = Prefs.position(item.key).coerceAtLeast(0)
                p.setMediaItem(MediaItem.fromUri(item.url), start)
                showTitle(item.title, if (start > 0) "Reprise à ${formatDuration(start)}" else "")
            }
        }
        p.prepare()
        p.playWhenReady = true
        view.requestFocus()
    }

    private fun savePosition() {
        val item = current as? PlayItem.Vod ?: return
        val p = player ?: return
        val pos = p.currentPosition
        val dur = p.duration
        when {
            dur > 0 && pos > dur * 95 / 100 -> Prefs.savePosition(item.key, -1)
            pos > 30_000 -> Prefs.savePosition(item.key, pos)
        }
    }

    private fun showTitle(title: String, line: String) {
        ovTitle.text = title
        ovEpg.text = line
        ovEpg.visibility = if (line.isEmpty()) View.GONE else View.VISIBLE
        overlay.visibility = View.VISIBLE
        handler.removeCallbacks(hideOverlay)
        handler.postDelayed(hideOverlay, 5000)
    }

    private fun showLiveInfo(s: LiveStream) {
        val number = if (s.num > 0) "${s.num}  " else ""
        showTitle(number + s.name, "")
        lifecycleScope.launch {
            val epg = Repo.shortEpg(s.id)
            val still = (current as? PlayItem.Live)?.stream?.id == s.id
            if (!still || epg.isEmpty()) return@launch
            val now = epg[0]
            val next = epg.getOrNull(1)
            ovEpg.text = "${formatTime(now.start)} – ${formatTime(now.end)}  ${now.title}" +
                (next?.let { "\nEnsuite : ${formatTime(it.start)}  ${it.title}" } ?: "")
            ovEpg.visibility = View.VISIBLE
        }
    }

    private fun zap(delta: Int) {
        if (items.size < 2) return
        play((index + delta + items.size) % items.size)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (isLive) {
                val down = if (Prefs.invertZap) -1 else 1
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        zap(-down); return true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        zap(down); return true
                    }
                    KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                        zap(1); return true
                    }
                    KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                        zap(-1); return true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_INFO -> {
                        (current as? PlayItem.Live)?.let { showLiveInfo(it.stream) }
                        return true
                    }
                }
            } else if (!view.isControllerFullyVisible) {
                val p = player
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        p?.let { it.seekTo((it.currentPosition - 10_000).coerceAtLeast(0)) }
                        view.showController()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        p?.let { it.seekTo(it.currentPosition + 30_000) }
                        view.showController()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
