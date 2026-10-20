package com.bridgecast.app

import android.content.Context
import android.net.Uri
import fi.iki.elonen.NanoHTTPD
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** خادم HTTP محلي لبث الملفات: Range + CORS + ترويسات DLNA. */
class LocalServer(private val ctx: Context, port: Int = 8686) : NanoHTTPD(port) {
    private val files = ConcurrentHashMap<String, Uri>()

    fun register(uri: Uri): String {
        val id = UUID.randomUUID().toString().take(8)
        files[id] = uri
        return "http://${localIp()}:$listeningPort/f/$id"
    }

    private fun cors(r: Response): Response = r.apply {
        addHeader("Access-Control-Allow-Origin", "*")
        addHeader("Access-Control-Allow-Headers", "Range, Content-Type")
        addHeader("Access-Control-Expose-Headers", "Content-Range, Content-Length, Accept-Ranges")
    }

    private fun text(status: Response.IStatus, msg: String) =
        cors(newFixedLengthResponse(status, "text/plain", msg))

    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.OPTIONS) return text(Response.Status.OK, "")
        val uri = files[session.uri.removePrefix("/f/")] ?: return text(Response.Status.NOT_FOUND, "not found")
        val afd = ctx.contentResolver.openAssetFileDescriptor(uri, "r")
            ?: return text(Response.Status.NOT_FOUND, "cannot open")
        val total = afd.length
        val mime = ctx.contentResolver.getType(uri) ?: "video/mp4"

        var start = 0L
        var end = total - 1
        val range = session.headers["range"]
        val partial = range != null && range.startsWith("bytes=")
        if (partial) {
            val p = range!!.removePrefix("bytes=").split("-")
            start = p[0].toLongOrNull() ?: 0L
            end = p.getOrNull(1)?.toLongOrNull()?.coerceAtMost(total - 1) ?: (total - 1)
        }
        if (start >= total) {
            afd.close()
            return text(Response.Status.RANGE_NOT_SATISFIABLE, "").also {
                it.addHeader("Content-Range", "bytes */$total")
            }
        }
        val len = end - start + 1
        val stream = afd.createInputStream().also { it.skip(start) }
        val status = if (partial) Response.Status.PARTIAL_CONTENT else Response.Status.OK
        return cors(newFixedLengthResponse(status, mime, stream, len)).apply {
            addHeader("Accept-Ranges", "bytes")
            if (partial) addHeader("Content-Range", "bytes $start-$end/$total")
            addHeader("transferMode.dlna.org", "Streaming")
            addHeader("contentFeatures.dlna.org",
                "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000")
        }
    }

    private fun localIp(): String =
        NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && it is Inet4Address && it.isSiteLocalAddress }
            ?.hostAddress ?: "127.0.0.1"
}
