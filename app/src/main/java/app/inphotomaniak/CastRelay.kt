package app.inphotomaniak

import java.io.*
import java.net.*
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.*

/** One revocable capability URL, bound to the selected LAN interface, never an open proxy. */
internal class CastRelay(address: InetAddress): Closeable {
    private val listener=ServerSocket().apply { reuseAddress=true;bind(InetSocketAddress(address,0)) }
    private val clients=ConcurrentHashMap.newKeySet<Socket>()
    private val workers=ThreadPoolExecutor(0,4,30,TimeUnit.SECONDS,SynchronousQueue(),{ r -> Thread(r,"cast-media").apply { isDaemon=true } })
    @Volatile private var current: Pair<String,CastContent>?=null
    @Volatile private var closed=false
    val port: Int get()=listener.localPort
    init {
        Thread({
            while(!closed) {
                val socket=try { listener.accept() } catch(_: IOException) { break }
                clients.add(socket)
                try { workers.execute { handle(socket) } }
                catch(_: RejectedExecutionException) { clients.remove(socket);socket.close() }
            }
        },"cast-listener").apply { isDaemon=true;start() }
    }
    @Synchronized fun publish(content: CastContent): String {
        check(!closed)
        val secret=ByteArray(32).also { SecureRandom().nextBytes(it) }.let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        val previous=current
        current=secret to content
        previous?.second?.close()
        clients.forEach { runCatching { it.close() } }
        return "http://${listener.inetAddress.hostAddress}:$port/$secret/media"
    }
    private fun handle(socket: Socket) {
        var headersSent=false
        try {
            socket.soTimeout=10_000
            val input=BufferedInputStream(socket.getInputStream())
            fun line(): String {
                val bytes=ByteArrayOutputStream()
                while(bytes.size()<4096) {
                    val b=input.read();if(b<0) throw EOFException()
                    if(b==10) return bytes.toString("US-ASCII").trimEnd('\r')
                    bytes.write(b)
                }
                throw IOException("Header too long")
            }
            val request=line().split(' ')
            if(request.size!=3) { empty(socket,400,"Bad Request");return }
            val headers=mutableMapOf<String,String>()
            var total=0
            while(true) {
                val row=line();if(row.isEmpty()) break
                total+=row.length
                if(total>16384 || headers.size>=40) { empty(socket,431,"Request Header Fields Too Large");return }
                val name=row.substringBefore(':').lowercase()
                if(!row.contains(':') || name in headers) { empty(socket,400,"Bad Request");return }
                headers[name]=row.substringAfter(':').trim()
            }
            val resource=current
            if(resource==null || request[1]!="/${resource.first}/media") { empty(socket,404,"Not Found");return }
            if(request[0]=="OPTIONS") { empty(socket,204,"No Content");return }
            if(request[0] !in setOf("GET","HEAD")) { empty(socket,405,"Method Not Allowed");return }
            val range=headers["range"]
            if(range!=null && !validCastRange(range)) { empty(socket,416,"Range Not Satisfiable");return }
            resource.second.open(range).use { response ->
                if(closed || current !== resource) return
                val output=BufferedOutputStream(socket.getOutputStream())
                val code=response.code
                val text=when(code) { 200 -> "OK";206 -> "Partial Content";416 -> "Range Not Satisfiable";else -> "Bad Gateway" }
                val lines=mutableListOf("HTTP/1.1 $code $text","Connection: close","Cache-Control: no-store","Access-Control-Allow-Origin: *","Access-Control-Expose-Headers: Content-Length, Content-Range, Accept-Ranges","Accept-Ranges: bytes","Content-Type: ${safeHeader(response.mime)}")
                if(response.length>=0) lines.add("Content-Length: ${response.length}")
                response.range?.let { lines.add("Content-Range: ${safeHeader(it)}") }
                headersSent=true
                output.write((lines.joinToString("\r\n")+"\r\n\r\n").toByteArray(Charsets.US_ASCII))
                if(request[0]=="GET") {
                    val buffer=ByteArray(64*1024)
                    while(!closed && current === resource) {
                        val count=response.input.read(buffer);if(count<0) break
                        output.write(buffer,0,count)
                    }
                }
                output.flush()
            }
        } catch(_: Exception) { if(!headersSent) runCatching { empty(socket,502,"Bad Gateway") } }
        finally { clients.remove(socket);runCatching { socket.close() } }
    }
    private fun safeHeader(value: String)=value.replace("\r","").replace("\n","")
    private fun empty(socket: Socket, code: Int, reason: String) {
        socket.getOutputStream().write(("HTTP/1.1 $code $reason\r\nContent-Length: 0\r\nConnection: close\r\nCache-Control: no-store\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, HEAD, OPTIONS\r\nAccess-Control-Allow-Headers: Range\r\nAccess-Control-Allow-Private-Network: true\r\n\r\n").toByteArray(Charsets.US_ASCII))
    }
    @Synchronized override fun close() {
        closed=true;listener.close();current?.second?.close();current=null
        clients.forEach { runCatching { it.close() } };workers.shutdownNow()
    }
}

internal fun validCastRange(value: String): Boolean {
    val match=Regex("bytes=([0-9]{0,19})-([0-9]{0,19})").matchEntire(value) ?: return false
    val a=match.groupValues[1];val b=match.groupValues[2]
    if(a.isEmpty()) return (b.toLongOrNull() ?: 0)>0
    val start=a.toLongOrNull() ?: return false
    return b.isEmpty() || (b.toLongOrNull()?.let { it>=start } == true)
}
internal interface CastContent: Closeable { fun open(range: String?): CastResponse }
internal class CastResponse(val code: Int,val mime: String,val length: Long,val range: String?,val input: InputStream,private val cleanup: ()->Unit={}): Closeable {
    override fun close() { try { input.close() } finally { cleanup() } }
}
internal class CastImage(private val bytes: ByteArray): CastContent {
    override fun open(range: String?): CastResponse {
        var start=0L;var end=bytes.size.toLong()-1
        if(range!=null) {
            require(validCastRange(range))
            val parts=range.removePrefix("bytes=").split('-')
            if(parts[0].isEmpty()) start=(bytes.size-(parts[1].toLong())).coerceAtLeast(0)
            else { start=parts[0].toLong();if(parts[1].isNotEmpty()) end=minOf(end,parts[1].toLong()) }
        }
        if(start> end) return CastResponse(416,"image/jpeg",0,"bytes */${bytes.size}",ByteArrayInputStream(byteArrayOf()))
        val count=(end-start+1).toInt()
        return CastResponse(if(range==null) 200 else 206,"image/jpeg",count.toLong(),if(range==null) null else "bytes $start-$end/${bytes.size}",ByteArrayInputStream(bytes,start.toInt(),count))
    }
    override fun close() = Unit
}
