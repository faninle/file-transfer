package com.example.filetransfer

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private var target: Peer? = null

    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val p = target ?: return@registerForActivityResult
        uris.forEach { u ->
            thread {
                val ok = runCatching { Sender.send(this, p, u) }.getOrDefault(false)
                Store.log.add(0, (if (ok) "Sent: " else "Failed: ") + Sender.displayName(this, u))
            }
        }
    }

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { startServer() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else startServer()
        setContent { MaterialTheme { Screen() } }
    }

    private fun startServer() =
        ContextCompat.startForegroundService(this, Intent(this, TransferService::class.java))

    private fun qr(text: String): Bitmap {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 600, 600)
        val bmp = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.RGB_565)
        for (x in 0 until m.width) for (y in 0 until m.height)
            bmp.setPixel(x, y, if (m[x, y]) Color.BLACK else Color.WHITE)
        return bmp
    }

    private fun scan(ip: String?) {
        if (ip == null) { Store.log.add(0, "Not connected to a network"); return }
        GmsBarcodeScanning.getClient(this).startScan().addOnSuccessListener { b ->
            runCatching {
                val o = JSONObject(b.rawValue!!)
                val p = Peer(o.getString("name"), o.getString("host"), o.getInt("port"), o.getString("token"))
                thread {
                    val ok = runCatching { Sender.pair(p, ip) }.getOrDefault(false)
                    if (ok) { Store.addPeer(p); Store.log.add(0, "Paired with ${p.name}") }
                    else Store.log.add(0, "Pairing failed (same network?)")
                }
            }
        }
    }

    @Composable
    private fun Screen() {
        var ip by remember { mutableStateOf(localIp()) }
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("File Transfer", style = MaterialTheme.typography.headlineSmall)
            Text("This device: ${Store.deviceName}  •  ${ip ?: "no network"}:${Store.PORT}")
            if (ip != null) {
                val payload = JSONObject().put("v", 1).put("name", Store.deviceName)
                    .put("host", ip).put("port", Store.PORT).put("token", Store.token).toString()
                val bmp = remember(ip) { qr(payload) }
                Image(bmp.asImageBitmap(), "QR", Modifier.size(220.dp).align(Alignment.CenterHorizontally))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { scan(ip) }) { Text("Scan QR") }
                Button(onClick = { ip = localIp() }) { Text("Refresh") }
            }
            Text("Paired devices", style = MaterialTheme.typography.titleMedium)
            LazyColumn(Modifier.weight(1f)) {
                items(Store.peers.toList()) { p ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("${p.name} (${p.host})")
                        Button(onClick = { target = p; picker.launch(arrayOf("*/*")) }) { Text("Send") }
                    }
                }
                items(Store.log.toList()) { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
