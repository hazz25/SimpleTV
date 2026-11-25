package com.simpletv.english

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Shows the web game and lends it Android's English voices and speech recognition. */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null
    private var waitingForMic: Pair<String, String>? = null

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.webViewClient = object : WebViewClient() {
            // The game never leaves its own pages.
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = !url.startsWith("file:///android_asset/")
        }
        web.webChromeClient = WebChromeClient()
        web.addJavascriptInterface(Bridge(), "AndroidVoice")

        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) {}
                override fun onDone(utteranceId: String) = js("window.__voiceDone('$utteranceId')")
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String) = js("window.__voiceDone('$utteranceId')")
            })
        }
        web.loadUrl("file:///android_asset/index.html")
    }

    private fun js(code: String) {
        runOnUiThread { web.evaluateJavascript(code, null) }
    }

    private fun sendResult(id: String, results: List<String>?, error: String?) {
        val o = JSONObject()
        if (error != null) o.put("error", error) else o.put("results", JSONArray(results ?: emptyList<String>()))
        js("window.__voiceResult('$id', ${JSONObject.quote(o.toString())})")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        web.evaluateJavascript("window.goBack ? window.goBack() : false") { handled ->
            if (handled != "true") finish()
        }
    }

    override fun onPause() {
        tts?.stop()
        recognizer?.cancel()
        super.onPause()
    }

    override fun onDestroy() {
        tts?.shutdown()
        recognizer?.destroy()
        web.destroy()
        super.onDestroy()
    }

    // ---- Speech recognition --------------------------------------------------------------------

    private fun startListening(id: String, lang: String) {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            waitingForMic = id to lang
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            sendResult(id, null, "unsupported")
            return
        }
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = r
        var answered = false
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                if (answered) return
                answered = true
                sendResult(id, results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty(), null)
            }

            override fun onError(error: Int) {
                if (answered) return
                answered = true
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> sendResult(id, emptyList(), null)
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> sendResult(id, null, "not-allowed")
                    else -> sendResult(id, null, "error-$error")
                }
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        r.startListening(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        val waiting = waitingForMic ?: return
        waitingForMic = null
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startListening(waiting.first, waiting.second)
        else sendResult(waiting.first, null, "not-allowed")
    }

    // ---- Calls from the game -------------------------------------------------------------------

    inner class Bridge {
        @JavascriptInterface
        fun speak(id: String, text: String, rate: Float, pitch: Float, lang: String) {
            val t = tts
            if (t == null || !ttsReady) {
                js("window.__voiceDone('$id')")
                return
            }
            t.language = Locale.forLanguageTag(lang)
            t.setSpeechRate(rate)
            t.setPitch(pitch)
            val params = Bundle()
            if (Build.VERSION.SDK_INT >= 21) t.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
        }

        @JavascriptInterface
        fun stop() {
            tts?.stop()
        }

        @JavascriptInterface
        fun canListen(): Boolean = SpeechRecognizer.isRecognitionAvailable(this@MainActivity)

        @JavascriptInterface
        fun listen(id: String, lang: String) {
            runOnUiThread { startListening(id, lang) }
        }
    }
}
