package com.bridgecast.app

import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var server: LocalServer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { CastContext.getSharedInstance(this) }
        server = LocalServer(applicationContext).also { it.start() }
        setContent { MaterialTheme { Screen() } }
    }

    override fun onDestroy() {
        server.stop()
        super.onDestroy()
    }

    private fun guessMime(url: String) = when {
        url.contains(".m3u8") -> "application/x-mpegURL"
        url.contains(".mpd") -> "application/dash+xml"
        url.contains(".webm") -> "video/webm"
        url.contains(".mkv") -> "video/x-matroska"
        else -> "video/mp4"
    }

    @Composable
    private fun Screen() {
        val scope = rememberCoroutineScope()
        var url by remember { mutableStateOf("") }
        var devices by remember { mutableStateOf(listOf<DlnaDevice>()) }
        var selected by remember { mutableStateOf<DlnaDevice?>(null) }
        var status by remember { mutableStateOf("") }

        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                url = server.register(uri)
                status = "تم تجهيز الملف المحلي"
            }
        }

        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bridgecast", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                AndroidView(factory = { c ->
                    MediaRouteButton(c).also { CastButtonFactory.setUpMediaRouteButton(c, it) }
                })
            }
            OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text("رابط الفيديو (mp4 / m3u8 / mpd)") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { picker.launch(arrayOf("video/*", "audio/*")) }) { Text("ملف محلي") }
                Button(onClick = {
                    scope.launch {
                        status = "جارٍ البحث..."
                        devices = runCatching { SsdpDiscovery(applicationContext).search() }
                            .getOrDefault(emptyList())
                        status = "تم العثور على ${devices.size} جهاز DLNA"
                    }
                }) { Text("بحث DLNA") }
            }
            LazyColumn(Modifier.weight(1f)) {
                items(devices) { d ->
                    ListItem(
                        headlineContent = { Text(d.name) },
                        trailingContent = { if (selected == d) Text("✓") },
                        modifier = Modifier.clickable { selected = if (selected == d) null else d }
                    )
                }
            }
            Text(status)
            Button(
                enabled = url.isNotBlank(), modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        val mime = guessMime(url)
                        val d = selected
                        status = try {
                            if (d != null) {
                                withContext(Dispatchers.IO) { DlnaController.play(d, url, "Bridgecast", mime) }
                                "يُبث إلى ${d.name}"
                            } else if (ChromecastController.play(applicationContext, url, "Bridgecast", mime)) {
                                "يُبث إلى Chromecast"
                            } else "اختر جهاز DLNA أو اتصل بـ Chromecast من الأيقونة"
                        } catch (e: Exception) {
                            "خطأ: ${e.message}"
                        }
                    }
                }
            ) { Text("بث") }
        }
    }
}
