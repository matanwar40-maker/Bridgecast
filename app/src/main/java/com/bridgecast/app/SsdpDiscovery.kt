package com.bridgecast.app

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.*

data class DlnaDevice(val name: String, val controlUrl: String, val location: String)

/** يكتشف أجهزة DLNA MediaRenderer عبر SSDP. */
class SsdpDiscovery(private val ctx: Context) {

    suspend fun search(timeoutMs: Int = 4000): List<DlnaDevice> = withContext(Dispatchers.IO) {
        val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("bridgecast").apply { setReferenceCounted(false); acquire() }
        val locations = linkedSetOf<String>()
        try {
            DatagramSocket().use { s ->
                s.soTimeout = 1000
                val msg = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\nMX: 2\r\n" +
                    "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n").toByteArray()
                val addr = InetAddress.getByName("239.255.255.250")
                repeat(2) { s.send(DatagramPacket(msg, msg.size, addr, 1900)) }
                val end = System.currentTimeMillis() + timeoutMs
                val buf = ByteArray(2048)
                while (System.currentTimeMillis() < end) {
                    try {
                        val p = DatagramPacket(buf, buf.size)
                        s.receive(p)
                        val text = String(p.data, 0, p.length)
                        Regex("(?im)^location:\\s*(.+)$").find(text)
                            ?.groupValues?.get(1)?.trim()?.let { locations.add(it) }
                    } catch (_: SocketTimeoutException) {
                    }
                }
            }
        } finally {
            lock.release()
        }
        locations.mapNotNull { runCatching { describe(it) }.getOrNull() }
    }

    private fun describe(location: String): DlnaDevice? {
        val conn = URL(location).openConnection().apply { connectTimeout = 3000; readTimeout = 3000 }
        val xml = conn.getInputStream().bufferedReader().use { it.readText() }
        val name = Regex("<friendlyName>(.*?)</friendlyName>").find(xml)?.groupValues?.get(1) ?: return null
        val svc = Regex("<service>(.*?)</service>", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml).map { it.groupValues[1] }.firstOrNull { it.contains("AVTransport") } ?: return null
        val ctrl = Regex("<controlURL>(.*?)</controlURL>").find(svc)?.groupValues?.get(1) ?: return null
        return DlnaDevice(name, URL(URL(location), ctrl).toString(), location)
    }
}
