package app.inphotomaniak

import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import androidx.exifinterface.media.ExifInterface
import okhttp3.Request
import java.io.FilterInputStream
import java.io.InputStream
import java.io.IOException
import org.xmlpull.v1.XmlPullParser
import android.util.Xml

/** Bounded remote reads: never downloads an entire video to inspect its date. */
class RangeSource(private val dav: DriveClient, private val path: String): MediaDataSource() {
    private var length = -1L
    private var resolvedUrl: okhttp3.HttpUrl? = null
    private var transferred = 0L
    override fun getSize(): Long {
        if (length < 0) dav.http.newCall(Request.Builder().url(dav.url(path)).header("Range","bytes=0-0").get().build()).execute().use {
            dav.checkResponse(it)
            resolvedUrl = it.request.url
            length = it.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                ?: if(it.code==200) it.header("Content-Length")?.toLongOrNull() ?: -1L else -1L
        }
        return length
    }
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (size == 0) return 0
        if (getSize() >= 0 && position >= length) return -1
        val wanted = minOf(size, 256 * 1024)
        if (transferred + wanted > 24L * 1024 * 1024) throw IOException("Limite lettura metadati video")
        return dav.http.newCall(Request.Builder().url(resolvedUrl ?: dav.url(path)).header("Range", "bytes=$position-${position + wanted - 1}").build()).execute().use {
            dav.checkResponse(it)
            if (position > 0 && it.code != 206) throw IOException("Il server non supporta le letture parziali")
            val stream = it.body?.byteStream() ?: return@use -1
            var total = 0
            while (total < wanted) { val n = stream.read(buffer, offset + total, wanted - total); if(n < 0) break; total += n }
            transferred += total
            if(total == 0) -1 else total
        }
    }
    override fun close() = Unit
}
class LimitedStream(input: InputStream, private var remaining: Long): FilterInputStream(input) {
    override fun read(): Int { if(remaining <= 0) throw IOException("Limite metadati raggiunto"); val n = super.read(); if(n >= 0) remaining--; return n }
    override fun read(b: ByteArray, off: Int, len: Int): Int { if(len == 0) return 0; if(remaining <= 0) throw IOException("Limite metadati raggiunto"); val n = `in`.read(b,off,minOf(len.toLong(),remaining).toInt()); if(n > 0) remaining -= n; return n }
    override fun skip(n: Long): Long { val skipped = `in`.skip(minOf(n,remaining)); remaining -= skipped; return skipped }
}
object MediaReader {
    fun tags(dav: DriveClient, entry: RemoteEntry): Map<String,String> {
        if (entry.video) {
            val retriever = MediaMetadataRetriever()
            try {
                RangeSource(dav,entry.path).use { source ->
                    retriever.setDataSource(source)
                    return retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)?.let { mapOf("CreationDate" to it) } ?: emptyMap()
                }
            } finally { retriever.release() }
        }
        val req = Request.Builder().url(dav.url(entry.path)).header("Range","bytes=0-16777215").build()
        return dav.http.newCall(req).execute().use { response ->
            dav.checkResponse(response)
            val stream = response.body?.byteStream() ?: throw IOException("File vuoto")
            val exif = ExifInterface(LimitedStream(stream,16L*1024*1024))
            val tags = mutableMapOf<String,String>()
            fun add(key: String, dateTag: String, offsetTag: String, subsecTag: String, subsecKey: String) {
                val value = exif.getAttribute(dateTag) ?: return
                val offset = exif.getAttribute(offsetTag).orEmpty()
                tags[key] = value + offset
                val subsec = exif.getAttribute(subsecTag)?.filter { it.isDigit() }?.take(9)
                if(!subsec.isNullOrEmpty()) tags[subsecKey] = "$value.$subsec$offset"
            }
            add("DateTimeOriginal",ExifInterface.TAG_DATETIME_ORIGINAL,ExifInterface.TAG_OFFSET_TIME_ORIGINAL,ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,"SubSecDateTimeOriginal")
            add("CreateDate",ExifInterface.TAG_DATETIME_DIGITIZED,ExifInterface.TAG_OFFSET_TIME_DIGITIZED,ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,"SubSecCreateDate")
            val gps = exif.gpsDateTime ?: -1L
            if(gps > 0) tags["GPSDateTime"] = java.time.Instant.ofEpochMilli(gps).toString()
            exif.getAttribute(ExifInterface.TAG_XMP)?.let { xmp ->
                // XmlPullParser does not fetch external entities. No remote sidecar fetching.
                runCatching {
                    val parser = Xml.newPullParser(); parser.setInput(xmp.reader())
                    while(parser.eventType != XmlPullParser.END_DOCUMENT) {
                        if(parser.eventType == XmlPullParser.START_TAG) {
                            for(i in 0 until parser.attributeCount) {
                                val key = parser.getAttributeName(i).substringAfter(':')
                                if(key in Dates.priority) tags.putIfAbsent(key,parser.getAttributeValue(i))
                            }
                            val key = parser.name.substringAfter(':')
                            if(key in Dates.priority) tags.putIfAbsent(key,parser.nextText())
                        }
                        parser.next()
                    }
                }
            }
            tags
        }
    }
}
