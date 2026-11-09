package com.example.filetransfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.provider.MediaStore
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveStream
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class TransferService : Service() {
    private var server: ApplicationEngine? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("ft", "File Transfer", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "ft")
            .setContentTitle("File Transfer is ready to receive")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (server == null) {
            server = embeddedServer(CIO, port = Store.PORT, host = "0.0.0.0") { routes() }.start(wait = false)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop(500, 1000)
        server = null
        super.onDestroy()
    }

    private suspend fun ApplicationCall.authorized(): Boolean {
        if (request.headers["X-Token"] == Store.token) return true
        respond(HttpStatusCode.Unauthorized)
        return false
    }

    private fun Application.routes() = routing {
        get("/info") {
            if (!call.authorized()) return@get
            call.respondText(
                JSONObject().put("name", Store.deviceName).put("type", "android").toString(),
                ContentType.Application.Json
            )
        }
        post("/pair") {
            if (!call.authorized()) return@post
            val o = JSONObject(call.receiveText())
            Store.addPeer(Peer(o.getString("name"), o.getString("host"), o.getInt("port"), o.getString("token")))
            Store.log.add(0, "Paired with ${o.getString("name")}")
            call.respondText("ok")
        }
        post("/send") {
            if (!call.authorized()) return@post
            val name = (call.request.queryParameters["name"] ?: "file")
                .substringAfterLast('/').substringAfterLast('\\')
            val ok = withContext(Dispatchers.IO) { save(name, call.receiveStream()) }
            if (ok) {
                Store.log.add(0, "Received: $name")
                call.respondText("ok")
            } else call.respond(HttpStatusCode.InternalServerError)
        }
    }

    /** Saves into Downloads/FileTransfer via MediaStore (no storage permission needed). */
    private fun save(name: String, input: java.io.InputStream): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/FileTransfer")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            contentResolver.openOutputStream(uri)!!.use { input.copyTo(it, 64 * 1024) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            false
        }
    }
}
