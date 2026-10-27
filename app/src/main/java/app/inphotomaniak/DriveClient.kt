package app.inphotomaniak

import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class Account(val driveId: String, val token: String) {
    init {
        require(driveId.matches(Regex("[0-9]{1,18}"))) { "Inserisci l’ID numerico del tuo kDrive" }
        require(token.isNotBlank() && token.none { it.isWhitespace() }) { "Inserisci il token API completo, senza spazi" }
    }
    override fun toString() = "Account(driveId=$driveId, token=[redacted])"
    override fun equals(other: Any?) = other is Account && driveId == other.driveId && token == other.token
    override fun hashCode() = 31 * driveId.hashCode() + token.hashCode()
}

/** REST transport. Bearer credentials are only sent to the API origin, never to download hosts. */
class DriveClient(
    val account: Account,
    private val base: HttpUrl = "https://api.kdrive.infomaniak.com/".toHttpUrl(),
    private val requestIntervalMillis: Long = 1100,
) {
    private var nextRequestNanos = 0L
    private val gate = Any()
    private fun pace(call: Call) {
        if(requestIntervalMillis == 0L) return
        synchronized(gate) {
            while(true) {
                if(call.isCanceled()) throw IOException("Richiesta interrotta")
                val wait = (nextRequestNanos - System.nanoTime()) / 1_000_000
                if(wait <= 0) break
                try { Thread.sleep(minOf(wait,100)) } catch(e: InterruptedException) { Thread.currentThread().interrupt();throw IOException("Richiesta interrotta",e) }
            }
            nextRequestNanos = System.nanoTime() + requestIntervalMillis * 1_000_000
        }
    }
    private fun apiOrigin(url: HttpUrl) = url.scheme==base.scheme && url.host==base.host && url.port==base.port
    private fun trusted(url: HttpUrl) = apiOrigin(url) || (url.scheme=="https" && url.port==443 && url.host.endsWith(".infomaniakusercontent.com"))
    internal fun authorized(request: Request): Request {
        val builder=request.newBuilder().removeHeader("Authorization")
        if(apiOrigin(request.url)) builder.header("Authorization","Bearer ${account.token}")
        return builder.build()
    }
    val http: OkHttpClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(20,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).callTimeout(120,TimeUnit.SECONDS)
        .addInterceptor { chain ->
            var request=chain.request()
            require(request.method in setOf("GET","HEAD")) { "L’app consente solo operazioni di lettura" }
            var response: Response? = null
            for(hop in 0..5) {
                if(!trusted(request.url)) throw IOException("Destinazione del file non riconosciuta")
                if(apiOrigin(request.url)) pace(chain.call())
                response=chain.proceed(authorized(request))
                if(response.code !in setOf(301,302,303,307,308)) break
                val location=response.header("Location")
                val destination=location?.let { request.url.resolve(it) }
                response.close()
                if(destination==null || !trusted(destination) || hop==5) throw IOException("Reindirizzamento del file non valido")
                request=request.newBuilder().url(destination).removeHeader("Authorization").build()
            }
            requireNotNull(response)
        }.build()

    private fun endpoint(path: String) = base.resolve(path)!!
    fun fileId(path: String): String {
        if(path=="/") return "1"
        val id=path.trimEnd('/').substringAfterLast('/').substringBefore('~')
        require(id.matches(Regex("[0-9]+"))) { "Cartella non valida: selezionala nuovamente" }
        return id
    }
    fun url(path: String): HttpUrl = endpoint("2/drive/${account.driveId}/files/${fileId(path)}/download")
    fun thumbnail(path: String): HttpUrl = endpoint("2/drive/${account.driveId}/files/${fileId(path)}/thumbnail")
        .newBuilder().addQueryParameter("width","384").addQueryParameter("height","384").build()

    fun checkResponse(response: Response) {
        if(!response.isSuccessful) throw IOException(when(response.code) {
            401 -> "Token non valido, scaduto o revocato. Generane uno nuovo nel Manager Infomaniak."
            403 -> "L’API ha negato l’accesso. Verifica il permesso drive del token e che l’ID kDrive appartenga a un drive accessibile dal tuo account."
            404 -> "Drive, cartella o file non trovato. Controlla l’ID kDrive."
            429 -> "Limite richieste kDrive raggiunto. Attendi un minuto, poi premi Aggiorna."
            else -> "Errore API kDrive (${response.code}). Riprova più tardi."
        })
    }

    fun list(path: String): List<RemoteEntry> {
        val result=linkedMapOf<String,RemoteEntry>()
        var cursor: String?=null
        val seen=mutableSetOf<String>()
        do {
            val builder=endpoint("3/drive/${account.driveId}/files/${fileId(path)}/files").newBuilder()
                .addQueryParameter("limit","100").addQueryParameter("order_by","name").addQueryParameter("order_for[name]","asc")
            cursor?.let { builder.addQueryParameter("cursor",it) }
            val page=http.newCall(Request.Builder().url(builder.build()).get().build()).execute().use {
                checkResponse(it)
                parsePage(it.body?.string() ?: throw IOException("Risposta API vuota"),path)
            }
            page.entries.forEach { result[it.path]=it }
            cursor=page.nextCursor
            if(cursor!=null && !seen.add(cursor)) throw IOException("Paginazione API non valida; indice precedente conservato")
        } while(cursor!=null)
        return result.values.toList()
    }
    data class Page(val entries: List<RemoteEntry>,val nextCursor: String?)
    companion object {
        fun displayPath(path: String): String = java.net.URLDecoder.decode(path.replace("+","%2B"),"UTF-8").replace(Regex("/\\d+~"),"/")
        fun parsePage(text: String,parent: String): Page {
            val json=JSONObject(text)
            if(json.optString("result")!="success") throw IOException("L’API non ha confermato la lettura della cartella")
            val data=json.optJSONArray("data") ?: throw IOException("Formato elenco API inatteso; indice precedente conservato")
            val entries=(0 until data.length()).map { i ->
                val file=data.getJSONObject(i)
                val id=file.getLong("id");require(id>0) { "Identificativo file non valido" }
                val name=file.getString("name")
                val dir=file.getString("type")=="dir"
                val encoded="https://local.invalid/".toHttpUrl().newBuilder().addPathSegment(name).build().encodedPath.substring(1)
                val path=parent.trimEnd('/')+"/$id~$encoded"+if(dir) "/" else ""
                val modified=file.optLong("last_modified_at",0)*1000
                val mime=file.optString("mime_type").ifBlank {
                    when(file.optString("extension_type")) { "image" -> "image/*";"video" -> "video/*";else -> "" }
                }
                // Size and version augment last-modified for incremental cache invalidation.
                val version=file.optJSONObject("version")?.toString().orEmpty()
                RemoteEntry(path,name,dir,mime,modified,"$modified:${file.optLong("size",0)}:$version")
            }
            val more=json.optBoolean("has_more",false)
            val cursor=if(more) json.optString("cursor").takeIf { it.isNotBlank() && it!="null" }
                ?: throw IOException("Cursore API mancante; elenco incompleto") else null
            return Page(entries,cursor)
        }
    }
}
