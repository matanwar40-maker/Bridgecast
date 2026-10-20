package com.bridgecast.app

import java.net.HttpURLConnection
import java.net.URL

/** تحكم DLNA عبر SOAP (خدمة AVTransport). */
object DlnaController {
    private const val SVC = "urn:schemas-upnp-org:service:AVTransport:1"

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

    private fun soap(ctrl: String, action: String, args: String = "") {
        val body = """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:$action xmlns:u="$SVC"><InstanceID>0</InstanceID>$args</u:$action></s:Body></s:Envelope>"""
        val c = URL(ctrl).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 5000
            c.readTimeout = 8000
            c.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            c.setRequestProperty("SOAPACTION", "\"$SVC#$action\"")
            c.outputStream.use { it.write(body.toByteArray()) }
            if (c.responseCode !in 200..299) error("DLNA $action failed: HTTP ${c.responseCode}")
        } finally {
            c.disconnect()
        }
    }

    fun play(d: DlnaDevice, url: String, title: String, mime: String) {
        val didl = """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"><item id="0" parentID="-1" restricted="1"><dc:title>${esc(title)}</dc:title><upnp:class>object.item.videoItem</upnp:class><res protocolInfo="http-get:*:$mime:*">${esc(url)}</res></item></DIDL-Lite>"""
        soap(d.controlUrl, "SetAVTransportURI",
            "<CurrentURI>${esc(url)}</CurrentURI><CurrentURIMetaData>${esc(didl)}</CurrentURIMetaData>")
        soap(d.controlUrl, "Play", "<Speed>1</Speed>")
    }

    fun pause(d: DlnaDevice) = soap(d.controlUrl, "Pause")
    fun stop(d: DlnaDevice) = soap(d.controlUrl, "Stop")
}
