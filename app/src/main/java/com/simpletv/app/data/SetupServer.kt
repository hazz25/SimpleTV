package com.simpletv.app.data

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * A tiny web server that runs on the TV only while the QR code screen is open. The phone opens its
 * page over the home Wi-Fi and sends the list; nothing goes through the Internet. Requests must carry
 * the secret token from the QR code and come from the local network.
 */
class SetupServer(
    private val page: String,
    private val onList: suspend (type: String, fields: Map<String, String>) -> Playlist,
) {
    val token: String = randomToken()
    private var socket: ServerSocket? = null
    private val workers = Executors.newFixedThreadPool(4)

    /** Starts listening and returns the port. */
    fun start(): Int {
        val s = try {
            ServerSocket(8765)
        } catch (e: IOException) {
            ServerSocket(0)
        }
        socket = s
        thread(name = "setup-server", isDaemon = true) {
            while (!s.isClosed) {
                val client = try {
                    s.accept()
                } catch (e: IOException) {
                    break
                }
                workers.execute { handle(client) }
            }
        }
        return s.localPort
    }

    fun stop() {
        try {
            socket?.close()
        } catch (e: IOException) { /* already closed */ }
        workers.shutdownNow()
    }

    private fun handle(c: Socket) {
        c.use {
            try {
                c.soTimeout = 15_000
                if (!isLocal(c.inetAddress)) return
                val input = BufferedInputStream(c.getInputStream())
                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(' ')
                if (parts.size < 2) return
                val method = parts[0]
                val target = parts[1]
                var length = 0
                while (true) {
                    val line = readLine(input) ?: return
                    if (line.isEmpty()) break
                    val colon = line.indexOf(':')
                    if (colon > 0 && line.substring(0, colon).trim().equals("Content-Length", true)) {
                        length = line.substring(colon + 1).trim().toIntOrNull() ?: 0
                    }
                }
                val path = target.substringBefore('?')
                val query = parseForm(target.substringAfter('?', ""))
                if (query["t"] != token) {
                    respond(c, 403, "text/plain", "Lien expiré : scannez à nouveau le QR code affiché sur la TV.")
                    return
                }
                when {
                    method == "GET" && path == "/" -> respond(c, 200, "text/html", page.replace("__TOKEN__", token))
                    method == "POST" && path == "/add" && length in 1..8192 -> {
                        val body = ByteArray(length)
                        var read = 0
                        while (read < length) {
                            val n = input.read(body, read, length - read)
                            if (n < 0) break
                            read += n
                        }
                        val form = parseForm(String(body, 0, read, Charsets.UTF_8))
                        val result = JSONObject()
                        try {
                            val list = runBlocking {
                                withTimeout(60_000) { onList(form["type"].orEmpty(), form) }
                            }
                            result.put("ok", true).put("name", list.name)
                        } catch (e: Exception) {
                            result.put("ok", false).put("error", e.message ?: "Erreur inconnue")
                        }
                        respond(c, 200, "application/json", result.toString())
                    }
                    else -> respond(c, 404, "text/plain", "Introuvable")
                }
            } catch (e: Exception) {
                // A broken connection from the phone: nothing to do.
            }
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            if (sb.length > 4096) return null
            sb.append(b.toChar())
        }
    }

    private fun respond(c: Socket, code: Int, type: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val status = when (code) {
            200 -> "OK"
            403 -> "Forbidden"
            else -> "Not Found"
        }
        val head = "HTTP/1.1 $code $status\r\n" +
            "Content-Type: $type; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        val out = c.getOutputStream()
        out.write(head.toByteArray(Charsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    companion object {
        /** The TV's address on the home network, or null when it is not connected. */
        fun localAddress(): String? {
            val all = try {
                NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            } catch (e: Exception) {
                emptyList()
            }
            for (ni in all) {
                if (!ni.isUp || ni.isLoopback) continue
                for (addr in ni.inetAddresses.toList()) {
                    if (addr is Inet4Address && addr.isSiteLocalAddress) return addr.hostAddress
                }
            }
            return null
        }

        private fun isLocal(a: InetAddress): Boolean = when (a) {
            is Inet4Address -> a.isSiteLocalAddress || a.isLoopbackAddress || a.isLinkLocalAddress
            is Inet6Address -> a.isLoopbackAddress || a.isLinkLocalAddress || a.isSiteLocalAddress ||
                (a.address[0].toInt() and 0xFE) == 0xFC || a.isIPv4CompatibleAddress
            else -> false
        }

        private fun parseForm(s: String): Map<String, String> =
            s.split('&').filter { it.isNotEmpty() }.associate { pair ->
                val k = pair.substringBefore('=')
                val v = pair.substringAfter('=', "")
                URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
            }

        private fun randomToken(): String {
            val chars = "abcdefghijkmnpqrstuvwxyz23456789"
            val rnd = SecureRandom()
            return (1..10).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
        }
    }
}
