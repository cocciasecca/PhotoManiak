package app.inphotomaniak

import okhttp3.Call
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Fixed kDrive file, not a URL supplied by the TV. Authentication stays in DriveClient. */
internal class CastVideo(private val client: DriveClient, private val entry: RemoteEntry,private val mime: String): CastContent {
    private val streamHttp=client.http.newBuilder().callTimeout(0,java.util.concurrent.TimeUnit.MILLISECONDS).build()
    private val calls=ConcurrentHashMap.newKeySet<Call>()
    @Volatile private var closed=false
    override fun open(range: String?): CastResponse {
        check(!closed)
        val request=Request.Builder().url(client.url(entry.path)).header("Accept-Encoding","identity").get()
        range?.let { require(validCastRange(it));request.header("Range",it) }
        val call=streamHttp.newCall(request.build());calls.add(call)
        if(closed) call.cancel()
        try {
            val response=call.execute()
            if(response.code==416) {
                val contentRange=response.header("Content-Range");response.close();calls.remove(call)
                return CastResponse(416,mime,0,contentRange,ByteArrayInputStream(byteArrayOf()))
            }
            if(response.code !in setOf(200,206) || response.body==null) { response.close();throw IOException("Video is unavailable") }
            val body=requireNotNull(response.body)
            return CastResponse(response.code,mime,body.contentLength(),response.header("Content-Range"),body.byteStream()) { response.close();calls.remove(call) }
        } catch(e: Exception) { calls.remove(call);throw e }
    }
    override fun close() { closed=true;calls.forEach { it.cancel() };calls.clear() }
}
