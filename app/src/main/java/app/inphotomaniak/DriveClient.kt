package app.inphotomaniak

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class Account(val driveId: String, val token: String) {
    init {
        require(driveId.matches(Regex("[0-9]{1,18}"))) { "Enter your numeric kDrive ID" }
        require(token.isNotBlank() && token.none { it.isWhitespace() }) { "Enter the complete API token without spaces" }
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
                if(call.isCanceled()) throw IOException("Request cancelled")
                val wait = (nextRequestNanos - System.nanoTime()) / 1_000_000
                if(wait <= 0) break
                try { Thread.sleep(minOf(wait,100)) } catch(e: InterruptedException) { Thread.currentThread().interrupt();throw IOException("Request cancelled",e) }
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
            require(request.method in setOf("GET","HEAD")) { "The app only allows read operations" }
            var response: Response? = null
            for(hop in 0..5) {
                if(!trusted(request.url)) throw IOException("Unrecognized file destination")
                if(apiOrigin(request.url)) pace(chain.call())
                response=chain.proceed(authorized(request))
                if(response.code !in setOf(301,302,303,307,308)) break
                val location=response.header("Location")
                val destination=location?.let { request.url.resolve(it) }
                response.close()
                if(destination==null || !trusted(destination) || hop==5) throw IOException("Invalid file redirect")
                request=request.newBuilder().url(destination).removeHeader("Authorization").build()
            }
            requireNotNull(response)
        }.build()

    // Mutations use a separate client: image/video requests remain strictly GET/HEAD.
    // No redirects or automatic retries for a confirmed trash operation.
    private val trashHttp = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .connectTimeout(20,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).callTimeout(60,TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            require(request.method == "DELETE" && apiOrigin(request.url))
            require(request.url.query == null && request.url.encodedPath.matches(
                Regex("/2/drive/${account.driveId}/files/[0-9]+")))
            pace(chain.call())
            chain.proceed(authorized(request))
        }.build()

    fun trash(entry: RemoteEntry) {
        val id = fileId(entry.path)
        require(!entry.directory && entry.media && id.toLong() > 1) { "Only individual photos and videos can be moved to trash." }
        val request = Request.Builder().url(endpoint("2/drive/${account.driveId}/files/$id")).delete().build()
        trashHttp.newCall(request).execute().use { response ->
            if(response.code == 403) throw IOException("You do not have permission to delete this file. Check your kDrive access and token permissions.")
            checkResponse(response)
            val json = JSONObject(response.body?.string() ?: throw IOException("Deletion was not confirmed. Refresh the library before retrying."))
            if(json.optString("result") != "success") throw IOException("kDrive did not confirm deletion. Refresh the library before retrying.")
        }
    }

    // Only app-owned metadata creation. Existing files are never overwritten.
    private val collectionHttp = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .connectTimeout(20,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).callTimeout(60,TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request=chain.request()
            require(request.method=="POST" && apiOrigin(request.url))
            val folder=request.url.encodedPath.matches(Regex("/3/drive/${account.driveId}/files/[0-9]+/directory"))
            val upload=request.url.encodedPath=="/3/drive/${account.driveId}/upload" &&
                request.url.queryParameter("conflict")=="error" && CollectionJson.isFilename(request.url.queryParameter("file_name").orEmpty())
            require(folder || upload) { "Only collection metadata may be uploaded" }
            pace(chain.call())
            chain.proceed(authorized(request))
        }.build()

    fun collectionFolder(parent: String): String {
        fun existing(): RemoteEntry? {
            val matches=list(parent).filter { it.name=="PhotoManiak" }
            require(matches.size <= 1 && matches.none { !it.directory }) { "PhotoManiak already exists as a file or is ambiguous. Choose another parent folder." }
            return matches.singleOrNull()
        }
        existing()?.let { return it.path }
        val body=JSONObject().put("name","PhotoManiak").put("only_for_me",false).toString().toRequestBody("application/json".toMediaType())
        val request=Request.Builder().url(endpoint("3/drive/${account.driveId}/files/${fileId(parent)}/directory")).post(body).build()
        try {
            collectionHttp.newCall(request).execute().use { response ->
                checkResponse(response)
                require(JSONObject(response.body?.string() ?: "{}").optString("result")=="success") { "Folder creation was not confirmed" }
            }
        } catch(e: Exception) {
            // A concurrent device or a lost response may have created it. Only adopt the exact folder.
            return existing()?.path ?: throw e
        }
        return existing()?.path ?: throw IOException("The new PhotoManiak folder is not visible yet. Try again.")
    }

    fun readCollection(entry: RemoteEntry): String {
        require(!entry.directory && CollectionJson.isFilename(entry.name))
        return http.newCall(Request.Builder().url(url(entry.path)).get().build()).execute().use { response ->
            checkResponse(response)
            val body=response.body ?: throw IOException("Empty collection response")
            require(body.contentLength() <= CollectionJson.MAX_BYTES) { "Collection file is too large" }
            val source=body.source()
            source.request(CollectionJson.MAX_BYTES.toLong()+1)
            require(source.buffer.size <= CollectionJson.MAX_BYTES) { "Collection file is too large" }
            source.readUtf8()
        }
    }

    fun uploadCollection(folder: String, batch: CollectionDb.Batch) {
        require(fileId(folder)!="1" && CollectionJson.isFilename(batch.name))
        CollectionJson.decode(batch.text,account.driveId)
        val bytes=batch.text.toByteArray(Charsets.UTF_8)
        val now=(System.currentTimeMillis()/1000).toString()
        val url=endpoint("3/drive/${account.driveId}/upload").newBuilder()
            .addQueryParameter("directory_id",fileId(folder)).addQueryParameter("file_name",batch.name)
            .addQueryParameter("total_size",bytes.size.toString()).addQueryParameter("conflict","error")
            .addQueryParameter("created_at",now).addQueryParameter("last_modified_at",now).build()
        collectionHttp.newCall(Request.Builder().url(url).post(bytes.toRequestBody("application/octet-stream".toMediaType())).build()).execute().use { response ->
            checkResponse(response)
            if(JSONObject(response.body?.string() ?: "{}").optString("result")!="success") throw IOException("Collection upload was not confirmed. Your changes remain saved on this phone.")
        }
    }

    private fun endpoint(path: String) = base.resolve(path)!!
    fun fileId(path: String): String {
        if(path=="/") return "1"
        val id=path.trimEnd('/').substringAfterLast('/').substringBefore('~')
        require(id.matches(Regex("[0-9]+"))) { "Invalid folder: please select it again" }
        return id
    }
    fun url(path: String): HttpUrl = endpoint("2/drive/${account.driveId}/files/${fileId(path)}/download")
    fun thumbnail(path: String): HttpUrl = endpoint("2/drive/${account.driveId}/files/${fileId(path)}/thumbnail")
        .newBuilder().addQueryParameter("width","384").addQueryParameter("height","384").build()

    fun checkResponse(response: Response) {
        if(!response.isSuccessful) throw IOException(when(response.code) {
            401 -> "Invalid, expired or revoked token. Create a new one in Infomaniak Manager."
            403 -> "API access denied. Check your token scope and account permissions."
            404 -> "Drive, folder or file not found. Check your kDrive ID."
            429 -> "kDrive request limit reached. Wait a minute, then tap Refresh."
            else -> "kDrive API error (${response.code}). Please try again later."
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
                parsePage(it.body?.string() ?: throw IOException("Empty API response"),path)
            }
            page.entries.forEach { result[it.path]=it }
            cursor=page.nextCursor
            if(cursor!=null && !seen.add(cursor)) throw IOException("Invalid API pagination; previous index preserved")
        } while(cursor!=null)
        return result.values.toList()
    }
    data class Page(val entries: List<RemoteEntry>,val nextCursor: String?)
    companion object {
        fun displayPath(path: String): String = java.net.URLDecoder.decode(path.replace("+","%2B"),"UTF-8").replace(Regex("/\\d+~"),"/")
        fun parsePage(text: String,parent: String): Page {
            val json=JSONObject(text)
            if(json.optString("result")!="success") throw IOException("The API did not confirm the folder listing")
            val data=json.optJSONArray("data") ?: throw IOException("Unexpected API listing format; previous index preserved")
            val entries=(0 until data.length()).map { i ->
                val file=data.getJSONObject(i)
                val id=file.getLong("id");require(id>0) { "Invalid file ID" }
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
                ?: throw IOException("Missing API cursor; incomplete listing") else null
            return Page(entries,cursor)
        }
    }
}
