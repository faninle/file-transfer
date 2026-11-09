package com.example.filetransfer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Sender {
    fun displayName(ctx: Context, uri: Uri): String =
        ctx.contentResolver.query(uri, null, null, null, null)?.use {
            val i = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && it.moveToFirst()) it.getString(i) else null
        } ?: "file"

    private fun size(ctx: Context, uri: Uri): Long =
        ctx.contentResolver.query(uri, null, null, null, null)?.use {
            val i = it.getColumnIndex(OpenableColumns.SIZE)
            if (i >= 0 && it.moveToFirst() && !it.isNull(i)) it.getLong(i) else -1L
        } ?: -1L

    /** Streams the file to the peer (no full-file buffering in memory). */
    fun send(ctx: Context, peer: Peer, uri: Uri): Boolean {
        val name = URLEncoder.encode(displayName(ctx, uri), "UTF-8")
        val conn = URL("http://${peer.host}:${peer.port}/send?name=$name").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 5000
            conn.setRequestProperty("X-Token", peer.token)
            val len = size(ctx, uri)
            if (len >= 0) conn.setFixedLengthStreamingMode(len) else conn.setChunkedStreamingMode(64 * 1024)
            ctx.contentResolver.openInputStream(uri)!!.use { input ->
                conn.outputStream.use { input.copyTo(it, 64 * 1024) }
            }
            return conn.responseCode == 200
        } finally {
            conn.disconnect()
        }
    }

    /** Tells the scanned peer who we are, so it can send files back to us. */
    fun pair(peer: Peer, myHost: String): Boolean {
        val conn = URL("http://${peer.host}:${peer.port}/pair").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 5000
            conn.setRequestProperty("X-Token", peer.token)
            val body = JSONObject().put("name", Store.deviceName).put("host", myHost)
                .put("port", Store.PORT).put("token", Store.token).toString()
            conn.outputStream.use { it.write(body.toByteArray()) }
            return conn.responseCode == 200
        } finally {
            conn.disconnect()
        }
    }
}
