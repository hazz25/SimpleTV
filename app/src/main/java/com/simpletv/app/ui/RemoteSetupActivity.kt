package com.simpletv.app.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.simpletv.app.R
import com.simpletv.app.data.Prefs
import com.simpletv.app.data.Repo
import com.simpletv.app.data.Setup
import com.simpletv.app.data.SetupServer

/** Shows a QR code; the phone that scans it can send a list to this TV over the home Wi-Fi. */
class RemoteSetupActivity : BaseActivity() {

    private var server: SetupServer? = null
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote)
        status = findViewById(R.id.status)
        val back = findViewById<TextView>(R.id.back)
        back.setOnClickListener { finish() }
        back.requestFocus()
    }

    // The server only runs while this screen is visible.
    override fun onStart() {
        super.onStart()
        val ip = SetupServer.localAddress()
        val qr = findViewById<ImageView>(R.id.qr)
        val address = findViewById<TextView>(R.id.address)
        if (ip == null) {
            address.text = "—"
            status.text = "La TV n'est pas connectée au réseau. Connectez-la au Wi‑Fi ou par câble, puis revenez ici."
            status.setTextColor(ContextCompat.getColor(this, R.color.gold))
            return
        }
        val page = resources.openRawResource(R.raw.setup).bufferedReader().use { it.readText() }
        val s = SetupServer(page, onLocked = {
            runOnUiThread {
                status.text = "Trop de tentatives avec un mauvais code : par sécurité, la connexion est coupée. Revenez en arrière puis rouvrez cet écran pour obtenir un nouveau QR code."
                status.setTextColor(ContextCompat.getColor(this, R.color.gold))
                qr.setImageDrawable(null)
            }
        }) { type, form ->
            runOnUiThread { status.text = "Vérification de la liste…" }
            try {
                val list = Setup.verify(
                    type = type,
                    name = form["name"].orEmpty(),
                    server = form["server"].orEmpty(),
                    user = form["user"].orEmpty(),
                    pass = form["pass"].orEmpty(),
                    url = form["url"].orEmpty(),
                )
                runOnUiThread { done(list.name, list) }
                list
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Le téléphone a envoyé une liste, mais : ${errorText(e)}"
                    status.setTextColor(ContextCompat.getColor(this, R.color.gold))
                }
                throw e
            }
        }
        val port = s.start()
        server = s
        val url = "http://$ip:$port/?t=${s.token}"
        address.text = "$ip:$port/?t=${s.token}"
        qr.setImageBitmap(qrCode(url, 600))
    }

    override fun onStop() {
        server?.stop()
        server = null
        super.onStop()
    }

    private fun done(name: String, list: com.simpletv.app.data.Playlist) {
        Prefs.addPlaylist(list)
        Repo.clear()
        status.text = "✓  Liste « $name » installée !"
        status.setTextColor(ContextCompat.getColor(this, R.color.gold))
        status.postDelayed({
            startActivity(
                Intent(this, HomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
            finish()
        }, 1500)
    }

    private fun qrCode(text: String, size: Int): Bitmap {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 0))
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) pixels[y * size + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565)
    }
}
