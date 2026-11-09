package com.example.filetransfer

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID

/** A paired device. [token] is the secret of that device (used when WE send to it). */
data class Peer(val name: String, val host: String, val port: Int, val token: String)

object Store {
    const val PORT = 8765
    private lateinit var ctx: Context
    val peers = mutableStateListOf<Peer>()
    val log = mutableStateListOf<String>()

    private val prefs get() = ctx.getSharedPreferences("ft", Context.MODE_PRIVATE)

    val deviceName: String get() = android.os.Build.MODEL

    /** Our own secret; incoming requests must present it in the X-Token header. */
    val token: String
        get() = prefs.getString("token", null) ?: UUID.randomUUID().toString().replace("-", "")
            .also { prefs.edit().putString("token", it).apply() }

    fun init(c: Context) {
        ctx = c.applicationContext
        peers.clear()
        val arr = JSONArray(prefs.getString("peers", "[]"))
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            peers.add(Peer(o.getString("name"), o.getString("host"), o.getInt("port"), o.getString("token")))
        }
    }

    @Synchronized
    fun addPeer(p: Peer) {
        peers.removeAll { it.token == p.token }
        peers.add(p)
        val arr = JSONArray()
        peers.forEach {
            arr.put(JSONObject().put("name", it.name).put("host", it.host).put("port", it.port).put("token", it.token))
        }
        prefs.edit().putString("peers", arr.toString()).apply()
    }
}

fun localIp(): String? =
    NetworkInterface.getNetworkInterfaces().toList()
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }
        ?.hostAddress
