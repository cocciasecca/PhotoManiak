package app.inphotomaniak

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.Locale

/** Shares a local original with a temporary read grant, never a token or authenticated URL. */
object ShareFiles {
    suspend fun prepare(context: Context, client: DriveClient, entry: RemoteEntry): Intent = withContext(Dispatchers.IO) {
        val root = File(context.cacheDir, "shared").apply { mkdirs() }
        // Keep recent copies available to receiving apps. Remove expired copies on the next share.
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        root.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
        val directory = File(root, UUID.randomUUID().toString())
        check(directory.mkdirs()) { "Could not create a temporary sharing folder." }
        val name = entry.name.replace(Regex("[\\\\/\\p{Cntrl}]"), "_").take(180).takeUnless { it.isBlank() || it == "." || it == ".." } ?: "media"
        val file = File(directory, name)
        val call = client.http.newCall(Request.Builder().url(client.url(entry.path)).get().build())
        val cancellation = launch(start=CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { call.cancel() }
        }
        try {
            call.execute().use { response ->
                client.checkResponse(response)
                val body = response.body ?: throw IOException("The file is empty.")
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var written = 0L
                        while(true) {
                            ensureActive()
                            val count = input.read(buffer)
                            if(count < 0) break
                            output.write(buffer,0,count)
                            written += count
                        }
                        if(written == 0L || (body.contentLength() >= 0 && written != body.contentLength())) {
                            throw IOException("The download was incomplete. Please try again.")
                        }
                    }
                }
            }
            ensureActive()
            sendIntent(context,file,entry.mime)
        } catch(e: Exception) {
            directory.deleteRecursively()
            throw e
        } finally { cancellation.cancel() }
    }

    internal fun sendIntent(context: Context, file: File, mime: String): Intent {
        val uri = FileProvider.getUriForFile(context,"${context.packageName}.files",file)
        val inferred = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase(Locale.ROOT))
        return Intent(Intent.ACTION_SEND).apply {
            type = mime.takeIf { it.contains('/') && !it.endsWith("/*") } ?: inferred ?: mime.ifBlank { "application/octet-stream" }
            putExtra(Intent.EXTRA_STREAM,uri)
            clipData = ClipData.newUri(context.contentResolver,file.name,uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
