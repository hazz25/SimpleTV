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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.simpletv.app.R
import com.simpletv.app.data.LiveStream
import com.simpletv.app.data.PlayItem
import com.simpletv.app.data.Prefs
import com.simpletv.app.data.Repo
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

    private lateinit var playback: RetryingPlayer
    private val player: ExoPlayer? get() = if (::playback.isInitialized) playback.player else null
    private var active = false
    private var index = 0
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
        playback = RetryingPlayer(this, view, object : RetryingPlayer.Callback {
            override fun onReady() {
                errorView.visibility = View.GONE
            }

            override fun onEnded() {
                val item = current
                if (item is PlayItem.Vod) {
                    Prefs.savePosition(item.key, -1)
                    if (index + 1 < items.size) play(index + 1) else finish()
                }
            }

            override fun onFailed(message: String) {
                errorView.text = message + if (isLive) "\n\n▲ ▼ pour changer de chaîne" else ""
                errorView.visibility = View.VISIBLE
            }
        })

        onBackPressedDispatcher.addCallback(this) {
            if (!isLive && view.isControllerFullyVisible) view.hideController() else finish()
        }
    }

    // Started in onResume and freed in onPause, so the live screen's preview never overlaps this
    // stream: many subscriptions allow only one connection at a time.
    override fun onResume() {
        super.onResume()
        if (items.isEmpty() || active) return
        active = true
        play(index)
    }

    override fun onPause() {
        super.onPause()
        if (!active) return
        savePosition()
        playback.release()
        active = false
        handler.removeCallbacks(hideOverlay)
    }

    private fun play(i: Int) {
        index = i
        Repo.queueIndex = i
        errorView.visibility = View.GONE
        val account = try {
            Repo.account()
        } catch (e: Exception) {
            onError(errorText(e)); return
        }
        when (val item = current ?: return) {
            is PlayItem.Live -> {
                view.useController = false
                Repo.lastLivePlayed = item.stream
                Prefs.addRecent("live", item.stream.id.toString())
                playback.play(Streams.live(account, item.stream.id), isLive = true)
                showLiveInfo(item.stream)
            }
            is PlayItem.Vod -> {
                view.useController = true
                val start = Prefs.position(item.key).coerceAtLeast(0)
                playback.play(Streams.vod(item.url), isLive = false, startPositionMs = start)
                showTitle(item.title, if (start > 0) "Reprise à ${formatDuration(start)}" else "")
            }
        }
        view.requestFocus()
    }

    private fun onError(message: String) {
        errorView.text = message
        errorView.visibility = View.VISIBLE
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
