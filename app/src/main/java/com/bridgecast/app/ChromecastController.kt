package com.bridgecast.app

import android.content.Context
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext

/** يجب استدعاؤه من الخيط الرئيسي. الاتصال بالجهاز يتم عبر MediaRouteButton. */
object ChromecastController {
    fun play(ctx: Context, url: String, title: String, mime: String): Boolean {
        val client = runCatching {
            CastContext.getSharedInstance(ctx).sessionManager.currentCastSession?.remoteMediaClient
        }.getOrNull() ?: return false
        val meta = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title)
        }
        val info = MediaInfo.Builder(url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(mime)
            .setMetadata(meta)
            .build()
        client.load(MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true).build())
        return true
    }
}
