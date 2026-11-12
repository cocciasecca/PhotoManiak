package app.inphotomaniak

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File

data class GalleryState(
    val ready: Boolean = false, val account: Account? = null, val rules: FolderRules = FolderRules(),
    val assets: List<Asset> = emptyList(), val folders: List<RemoteEntry> = emptyList(), val path: String = "/",
    val busy: Boolean = false, val scanning: Boolean = false, val progress: String = "", val error: String? = null,
    val collections: Collections = Collections(), val collectionView: String = "all", val albumId: String? = null,
    val syncFolder: String? = null, val syncing: Boolean = false, val syncError: String? = null,
    val pendingEdits: Int = 0, val lastSync: Long = 0,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val screen: String = "timeline", val thumbnailSize: ThumbnailSize = ThumbnailSize.MEDIUM
)
@OptIn(coil.annotation.ExperimentalCoilApi::class)
class GalleryModel(app: Application): AndroidViewModel(app) {
    private val settings = Settings(app)
    private val db = AssetDb(app)
    private val collectionDb = CollectionDb(app)
    private var syncJob: Job? = null
    private var syncDelay: Job? = null
    private val mutable = MutableStateFlow(GalleryState())
    val state = mutable.asStateFlow()
    var driveClient: DriveClient? = null; private set
    var images: ImageLoader? = null; private set
    private var scan: Job? = null
    init { viewModelScope.launch { withContext(Dispatchers.IO) {
        val account = settings.account()
        if(account != null) connect(account)
        val rules = settings.rules()
        mutable.value = GalleryState(ready=true,account=account,rules=rules,thumbnailSize=settings.thumbnailSize(),themeMode=settings.themeMode(),collections=if(account==null) Collections() else collectionDb.snapshot(),syncFolder=settings.syncFolder(),pendingEdits=collectionDb.pendingCount(),lastSync=settings.lastCollectionSync(),assets=if(account == null) emptyList() else db.all().filter { rules.accepts(it.entry.path) })
    }; syncCollections() } }
    private fun connect(account: Account) {
        driveClient = DriveClient(account)
        images = ImageLoader.Builder(getApplication()).okHttpClient(driveClient!!.http)
            .memoryCache { MemoryCache.Builder(getApplication()).maxSizePercent(0.15).build() }
            .diskCache { DiskCache.Builder().directory(File(getApplication<Application>().cacheDir,"images")).maxSizeBytes(256L*1024*1024).build() }.build()
    }
    fun login(id: String,token: String) = operation {
        val account = Account(id.trim(),token.trim())
        val client = DriveClient(account)
        val folders = client.list("/").filter { it.directory }
        // A fresh login must never inherit an unreadable previous account's data.
        settings.clear(); settings.save(state.value.thumbnailSize); settings.save(state.value.themeMode); db.replace(emptyList()); collectionDb.clear()
        File(getApplication<Application>().cacheDir,"images").deleteRecursively()
        File(getApplication<Application>().cacheDir,"video-thumbs").deleteRecursively()
        settings.save(account); connect(account)
        mutable.update { it.copy(account=account,rules=FolderRules(),assets=emptyList(),collections=Collections(),syncFolder=null,pendingEdits=0,lastSync=0,syncError=null,collectionView="all",albumId=null,folders=folders,path="/",screen="folders") }
    }
    fun collectionView(view: String, album: String? = null) {
        mutable.update { it.copy(collectionView=view,albumId=album,screen="timeline") }
    }
    private fun publishCollections() = synchronized(collectionDb) {
        val collections=collectionDb.snapshot()
        val pending=collectionDb.pendingCount()
        mutable.update { it.copy(collections=collections,pendingEdits=pending) }
    }
    private fun changeCollection(block: ()->Unit) = operation {
        block(); publishCollections()
        withContext(Dispatchers.Main) { scheduleSync() }
    }
    fun favorite(assets: List<Asset>, value: Boolean) = changeCollection {
        collectionDb.edit("favorite",assets.map { it.entry.collectionId() },value.toString())
    }
    fun archive(assets: List<Asset>, value: Boolean) = changeCollection {
        collectionDb.edit("archive",assets.map { it.entry.collectionId() },value.toString())
    }
    fun createAlbum(name: String, assets: List<Asset> = emptyList()) = changeCollection {
        val id=java.util.UUID.randomUUID().toString()
        collectionDb.edit("albumName",listOf(id),name.trim())
        collectionDb.members(id,assets.map { it.entry.collectionId() },true)
    }
    fun renameAlbum(id: String, name: String) = changeCollection { collectionDb.edit("albumName",listOf(id),name.trim()) }
    fun deleteAlbum(id: String) = changeCollection {
        collectionDb.edit("albumDeleted",listOf(id),"true")
        mutable.update { if(it.albumId==id) it.copy(albumId=null,collectionView="all",screen="collections") else it }
    }
    fun albumMembers(id: String, assets: List<Asset>, added: Boolean) = changeCollection {
        collectionDb.members(id,assets.map { it.entry.collectionId() },added)
    }
    fun enableCollectionSync(parent: String) {
        if(state.value.syncing) return
        operation {
            val folder=requireNotNull(driveClient).collectionFolder(parent)
            if(state.value.syncFolder?.let { requireNotNull(driveClient).fileId(it) } != requireNotNull(driveClient).fileId(folder)) collectionDb.resetForLocation()
            publishCollections()
            settings.syncFolder(folder)
            mutable.update { it.copy(syncFolder=folder,syncError=null) }
            withContext(Dispatchers.Main) { syncCollections() }
        }
    }
    fun disableCollectionSync() {
        if(state.value.syncing) return
        syncDelay?.cancel()
        operation { settings.syncFolder(null);mutable.update { it.copy(syncFolder=null,syncError=null) } }
    }
    private fun scheduleSync() {
        syncDelay?.cancel()
        syncDelay=viewModelScope.launch { delay(2000);syncCollections() }
    }
    fun syncCollections() {
        if(state.value.syncing) return
        val folder=state.value.syncFolder ?: return
        val client=driveClient ?: return
        mutable.update { it.copy(syncing=true,syncError=null) }
        syncJob=viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val context=currentCoroutineContext()
                    CollectionSync(client,collectionDb).run(folder) { context.ensureActive() }
                    val time=System.currentTimeMillis();settings.lastCollectionSync(time)
                    publishCollections()
                    mutable.update { it.copy(lastSync=time) }
                }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) {
                withContext(Dispatchers.IO) { publishCollections() }
                mutable.update { it.copy(syncError=e.message ?: "Collection sync failed. Local changes are saved.") }
            } finally {
                mutable.update { it.copy(syncing=false) }
                if(state.value.pendingEdits>0 && state.value.syncError==null) scheduleSync()
            }
        }
    }
    fun themeMode(mode: ThemeMode) = operation {
        settings.save(mode)
        mutable.update { it.copy(themeMode=mode) }
    }
    fun thumbnailSize(size: ThumbnailSize) = operation {
        settings.save(size)
        mutable.update { it.copy(thumbnailSize=size) }
    }
    fun browse(path: String) = operation {
        val folders = requireNotNull(driveClient).list(path).filter { it.directory }.sortedBy { it.name.lowercase() }
        mutable.update { it.copy(folders=folders,path=path,screen="folders") }
    }
    fun screen(name: String) { mutable.update { it.copy(screen=name,error=null) }; if(name=="folders") browse(state.value.path) }
    fun rule(path: String, mode: Int) {
        if(state.value.scanning || state.value.busy) return
        val old = state.value.rules
        val next = FolderRules(old.included - path + if(mode==1) setOf(path) else emptySet(),old.excluded - path + if(mode==2) setOf(path) else emptySet())
        operation { settings.save(next); mutable.update { it.copy(rules=next,assets=db.all().filter { a -> next.accepts(a.entry.path) }) } }
    }
    fun deleteAssets(paths: Set<String>, onFinished: (Set<String>) -> Unit = {}) {
        if(state.value.busy || state.value.scanning || paths.isEmpty()) return
        val client = driveClient ?: return
        val entries = state.value.assets.filter { it.entry.path in paths }.map { it.entry }
        if(entries.isEmpty()) return
        operation {
            val deleted = linkedSetOf<String>()
            try {
                for(entry in entries) {
                    currentCoroutineContext().ensureActive()
                    // Never remove a local record until kDrive confirms the trash operation.
                    client.trash(entry)
                    db.remove(entry.path)
                    deleted.add(entry.path)
                    mutable.update { it.copy(assets=it.assets.filterNot { a -> a.entry.path == entry.path },
                        progress=getApplication<Application>().getString(R.string.delete_progress,deleted.size,entries.size)) }
                }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) {
                mutable.update { it.copy(error=getApplication<Application>().getString(
                    R.string.delete_failed,deleted.size,entries.size,e.message.orEmpty())) }
            } finally {
                withContext(NonCancellable + Dispatchers.Main) { onFinished(deleted) }
            }
        }
    }
    fun refresh() {
        syncCollections()
        if(state.value.busy || state.value.scanning || state.value.rules.included.isEmpty()) return
        val client = driveClient ?: return
        val rules = state.value.rules
        scan = viewModelScope.launch {
            mutable.update { it.copy(scanning=true,error=null,progress=getApplication<Application>().getString(R.string.scan_searching)) }
            try { withContext(Dispatchers.IO) {
                val previous = db.all().associateBy { it.entry.path }
                val found = linkedMapOf<String,RemoteEntry>()
                val pending = ArrayDeque(rules.included.sorted())
                val visited = mutableSetOf<String>()
                while(pending.isNotEmpty()) {
                    ensureActive()
                    val path = pending.removeFirst()
                    if(!visited.add(path.trimEnd('/')) || rules.excluded(path)) continue
                    client.list(path).forEach { entry ->
                        if(!rules.excluded(entry.path)) {
                            if(entry.directory) pending.add(entry.path)
                            else if(entry.media && rules.accepts(entry.path)) found[entry.path] = entry
                        }
                    }
                    mutable.update { it.copy(progress=getApplication<Application>().getString(R.string.scan_discovery,found.size,visited.size)) }
                }
                val assets = found.values.map { entry ->
                    previous[entry.path]?.takeIf { it.entry.etag==entry.etag && it.entry.modified==entry.modified && (entry.etag.isNotBlank() || entry.modified > 0) }
                        ?: Asset(entry,Dates.resolve(emptyMap(),entry.name,entry.modified),false)
                }.toMutableList()
                // Only replace after every selected folder has been successfully listed.
                db.replace(assets); publish(assets)
                var failures = 0
                val changed = mutableListOf<Asset>()
                assets.indices.forEach { i ->
                    ensureActive()
                    if(!assets[i].metadataRead) {
                        val entry = assets[i].entry
                        try {
                            val tags = MediaReader.tags(client,entry)
                            assets[i] = Asset(entry,Dates.resolve(tags,entry.name,entry.modified),true)
                            changed.add(assets[i])
                        } catch(e: Exception) { ensureActive(); failures++ }
                    }
                    if(i % 25 == 0 && changed.isNotEmpty()) { db.upsert(changed); changed.clear(); publish(assets) }
                    mutable.update { it.copy(progress=getApplication<Application>().getString(R.string.scan_dates,i+1,assets.size)) }
                }
                db.upsert(changed); publish(assets)
                mutable.update { it.copy(progress=if(failures > 0) getApplication<Application>().getString(R.string.scan_provisional,failures) else getApplication<Application>().getString(R.string.library_updated)) }
            } } catch(e: CancellationException) { mutable.update { it.copy(progress=getApplication<Application>().getString(R.string.scan_stopped)) }; throw e }
            catch(e: Exception) { mutable.update { it.copy(error=e.message ?: getApplication<Application>().getString(R.string.connection_failed)) } }
            finally { mutable.update { it.copy(scanning=false) } }
        }
    }
    private fun publish(assets: List<Asset>) { mutable.update { it.copy(assets=assets.sortedWith(compareByDescending<Asset> { a -> a.date.millis }.thenBy { a -> a.entry.path })) } }
    fun stop() { scan?.cancel(); driveClient?.http?.dispatcher?.cancelAll() }
    fun clearError() { mutable.update { it.copy(error=null) } }
    fun logout() {
        if(state.value.scanning || state.value.busy || state.value.syncing) return
        syncDelay?.cancel()
        operation {
            driveClient?.http?.dispatcher?.cancelAll()
            images?.memoryCache?.clear(); images?.diskCache?.clear(); images?.shutdown(); images=null
            File(getApplication<Application>().cacheDir,"video-thumbs").deleteRecursively()
            File(getApplication<Application>().cacheDir,"shared").deleteRecursively()
            settings.clear(); db.replace(emptyList()); collectionDb.clear(); driveClient=null
            mutable.value=GalleryState(ready=true)
        }
    }
    private fun operation(block: suspend () -> Unit) {
        if(state.value.busy) return
        mutable.update { it.copy(busy=true,error=null) }
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { block() } }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(error=e.message ?: getApplication<Application>().getString(R.string.operation_failed)) } }
            finally { mutable.update { it.copy(busy=false) } }
        }
    }
    override fun onCleared() { driveClient?.http?.dispatcher?.cancelAll(); images?.shutdown(); db.close(); collectionDb.close() }
}
