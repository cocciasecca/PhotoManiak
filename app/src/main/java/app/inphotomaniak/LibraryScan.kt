package app.inphotomaniak

import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketException
import java.net.UnknownHostException

internal data class ScanCheckpoint(
    val key: String, val full: Boolean, val pending: List<String>,
    val visited: Set<String> = emptySet(), val cursor: String? = null,
    val cursors: Set<String> = emptySet(), val metadata: Boolean = false,
    val remaining: List<String> = emptyList(), val total: Int = 0, val failures: Int = 0
) {
    fun json(): String = JSONObject().put("key",key).put("full",full)
        .put("pending",JSONArray(pending)).put("visited",JSONArray(visited.toList()))
        .put("cursor",cursor ?: JSONObject.NULL).put("cursors",JSONArray(cursors.toList()))
        .put("metadata",metadata).put("remaining",JSONArray(remaining))
        .put("total",total).put("failures",failures).toString()
    companion object {
        fun parse(text: String): ScanCheckpoint {
            val j=JSONObject(text)
            fun list(key: String): List<String> = j.getJSONArray(key).let { a -> (0 until a.length()).map { a.getString(it) } }
            return ScanCheckpoint(j.getString("key"),j.getBoolean("full"),list("pending"),list("visited").toSet(),
                if(j.isNull("cursor")) null else j.getString("cursor"),list("cursors").toSet(),
                j.getBoolean("metadata"),list("remaining"),j.getInt("total"),j.getInt("failures"))
        }
        fun key(drive: String, rules: FolderRules): String = JSONObject().put("drive",drive)
            .put("included",JSONArray(rules.included.sorted())).put("excluded",JSONArray(rules.excluded.sorted())).toString()
    }
}

internal interface ScanStore {
    fun checkpoint(): ScanCheckpoint?
    fun staged(): List<RemoteEntry>
    fun indexed(): List<Asset>
    // Checkpoint, discovered entries and index changes must commit together.
    fun saveScan(state: ScanCheckpoint, entries: List<RemoteEntry> = emptyList(), reset: Boolean = false,
                 replacement: List<Asset>? = null, updated: Asset? = null)
    fun clearScan()
}

internal fun transientScanError(e: Exception): Boolean = when(e) {
    is DriveHttpException -> e.status == 408 || e.status == 429 || e.status in 500..599
    is UnknownHostException, is ConnectException, is SocketException, is InterruptedIOException -> true
    else -> false
}

/** Each API page and each metadata result is a durable checkpoint. */
internal class LibraryScan(
    private val store: ScanStore, private val drive: String, private val rules: FolderRules,
    private val listPage: (String,String?) -> DriveClient.Page,
    private val readTags: (RemoteEntry) -> Map<String,String>,
    private val publish: (List<Asset>) -> Unit,
    private val progress: (String,Int,Int) -> Unit,
    private val retryDelay: suspend (Long) -> Unit = { delay(it) }
) {
    private suspend fun <T> request(block: () -> T): T {
        var attempts=0
        while(true) {
            currentCoroutineContext().ensureActive()
            try { return block().also { currentCoroutineContext().ensureActive() } }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) {
                currentCoroutineContext().ensureActive()
                if(!transientScanError(e)) throw e
                progress("waiting",0,0)
                retryDelay(minOf(30_000L, 2_000L shl minOf(attempts++,4)))
            }
        }
    }
    suspend fun run(full: Boolean = false, recheckFolders: Boolean = false, onStarted: () -> Unit = {}): Int {
        val key=ScanCheckpoint.key(drive,rules)
        // Play and cold startup can rediscover new files; foreground resume keeps the saved cursor.
        // Only full refresh invalidates dates already read for unchanged files.
        var job=(if(full || recheckFolders) null else store.checkpoint()?.takeIf { it.key==key })
            ?: ScanCheckpoint(key,full,rules.included.sorted()).also { store.saveScan(it,reset=true) }
        onStarted()
        val found=store.staged().associateByTo(linkedMapOf()) { it.path }
        while(job.pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val path=job.pending.first()
            if(rules.excluded(path) || path.trimEnd('/') in job.visited) {
                job=job.copy(pending=job.pending.drop(1),cursor=null,cursors=emptySet())
                store.saveScan(job);continue
            }
            val page=request { listPage(path,job.cursor) }
            if(page.nextCursor!=null && page.nextCursor in job.cursors) throw IOException("Invalid API pagination; previous index preserved")
            val entries=page.entries.filter { !rules.excluded(it.path) }
            val children=entries.filter { it.directory && it.path.trimEnd('/') !in job.visited }.map { it.path }
            val media=entries.filter { !it.directory && it.media && rules.accepts(it.path) }
            val pending=(job.pending + children).distinct()
            job=if(page.nextCursor==null) job.copy(pending=pending.drop(1),visited=job.visited+path.trimEnd('/'),cursor=null,cursors=emptySet())
                else job.copy(pending=pending,cursor=page.nextCursor,cursors=job.cursors+page.nextCursor)
            store.saveScan(job,entries=media)
            media.forEach { found[it.path]=it }
            progress("discovery",found.size,job.visited.size)
        }
        val assets: MutableList<Asset>
        if(!job.metadata) {
            val previous=store.indexed().associateBy { it.entry.path }
            assets=found.values.map { entry ->
                previous[entry.path]?.takeIf { it.entry.etag==entry.etag && it.entry.modified==entry.modified && (entry.etag.isNotBlank() || entry.modified>0) }
                    ?.let { it.copy(entry=entry,metadataRead=it.metadataRead && !job.full) }
                    ?: Asset(entry,Dates.resolve(emptyMap(),entry.name,entry.modified),false)
            }.toMutableList()
            val remaining=assets.filter { !it.metadataRead }.map { it.entry.path }
            job=job.copy(metadata=true,remaining=remaining,total=remaining.size)
            store.saveScan(job,replacement=assets)
        } else assets=store.indexed().toMutableList()
        publish(assets.toList())
        val positions=assets.indices.associateBy { assets[it].entry.path }
        if(job.remaining.isNotEmpty()) progress("dates",job.total-job.remaining.size,job.total)
        while(job.remaining.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val index=positions[job.remaining.first()]
            var updated: Asset?=null
            var failures=job.failures
            if(index!=null) {
                val entry=assets[index].entry
                try {
                    val tags=request { readTags(entry) }
                    updated=Asset(entry,Dates.resolve(tags,entry.name,entry.modified),true)
                } catch(e: CancellationException) { throw e }
                catch(e: Exception) {
                    currentCoroutineContext().ensureActive()
                    if(e is DriveHttpException && e.status in setOf(401,403)) throw e
                    failures++
                }
            }
            job=job.copy(remaining=job.remaining.drop(1),failures=failures)
            store.saveScan(job,updated=updated)
            if(updated!=null && index!=null) assets[index]=updated
            val done=job.total-job.remaining.size
            if(done % 25 == 0 || job.remaining.isEmpty()) publish(assets.toList())
            progress("dates",done,job.total)
        }
        store.clearScan()
        return job.failures
    }
}
