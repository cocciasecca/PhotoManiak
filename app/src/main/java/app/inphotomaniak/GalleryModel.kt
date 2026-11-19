package app.inphotomaniak

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
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
    val initialScanComplete: Boolean = false,
    val busy: Boolean = false, val scanning: Boolean = false, val progress: String = "", val error: String? = null,
    val collections: Collections = Collections(), val collectionView: String = "all", val albumId: String? = null,
    val syncFolder: String? = null, val syncing: Boolean = false, val syncError: String? = null,
    val pendingEdits: Int = 0, val lastSync: Long = 0,
    val colorPalette: ColorPalette = ColorPalette.FOREST, val customColor: String = DEFAULT_CUSTOM_COLOR,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val screen: String = "timeline", val thumbnailSize: ThumbnailSize = ThumbnailSize.MEDIUM
)
@OptIn(coil.annotation.ExperimentalCoilApi::class)
class GalleryModel @JvmOverloads constructor(
    app: Application,
    private val scanLifecycle: Lifecycle = ProcessLifecycleOwner.get().lifecycle,
    private val scanClientFactory: (Account) -> DriveClient = { DriveClient(it) }
): AndroidViewModel(app), DefaultLifecycleObserver {
    internal val casting = CastController(app,viewModelScope)
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
    private var scanClient: DriveClient? = null
    private var foreground = scanLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    private var resumeScan = false
    private var forceFullScan = false
    private var recheckScanFolders = false
    private var manualStop = false
    @Volatile private var cleared = false
    init { viewModelScope.launch { if(!cleared) scanLifecycle.addObserver(this@GalleryModel) } }
    init { viewModelScope.launch { withContext(Dispatchers.IO) {
        val account = settings.account()
        if(account != null) connect(account)
        val rules = settings.rules()
        val indexed=if(account==null) emptyList() else db.all().filter { rules.accepts(it.entry.path) }
        // Older versions had no completion flag. Reuse a fully indexed library on upgrade.
        val initialComplete=account!=null && settings.initialScanComplete(indexed.isNotEmpty() && indexed.all { it.metadataRead } && db.checkpoint()==null)
        settings.saveInitialScanComplete(initialComplete)
        mutable.value = GalleryState(ready=true,account=account,rules=rules,initialScanComplete=initialComplete,thumbnailSize=settings.thumbnailSize(),themeMode=settings.themeMode(),colorPalette=settings.colorPalette(),customColor=settings.customColor(),collections=if(account==null) Collections() else collectionDb.snapshot(),syncFolder=settings.syncFolder(),pendingEdits=collectionDb.pendingCount(),lastSync=settings.lastCollectionSync(),assets=indexed)
    }; manualStop=settings.scanStopped();resumeScan=!manualStop && state.value.account!=null && state.value.rules.included.isNotEmpty()
        if(manualStop) mutable.update { it.copy(progress=getApplication<Application>().getString(if(state.value.initialScanComplete) R.string.scan_stopped else R.string.initial_scan_paused)) }
        syncCollections();startScanIfNeeded()
    } }
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
        settings.clear(); settings.save(state.value.thumbnailSize); settings.save(state.value.themeMode); settings.save(state.value.colorPalette,state.value.customColor); db.replace(emptyList()); db.clearScan(); collectionDb.clear()
        File(getApplication<Application>().cacheDir,"images").deleteRecursively()
        File(getApplication<Application>().cacheDir,"video-thumbs").deleteRecursively()
        manualStop=false;resumeScan=false;forceFullScan=false;recheckScanFolders=false
        settings.save(account); connect(account)
        mutable.update { it.copy(account=account,initialScanComplete=false,rules=FolderRules(),assets=emptyList(),collections=Collections(),syncFolder=null,pendingEdits=0,lastSync=0,syncError=null,collectionView="all",albumId=null,folders=folders,path="/",screen="folders") }
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
    fun colorPalette(palette: ColorPalette, customColor: String = state.value.customColor) = operation {
        val hex=requireNotNull(normalizeColor(customColor))
        settings.save(palette,hex)
        mutable.update { it.copy(colorPalette=palette,customColor=hex) }
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
                    db.clearScan() // A paused discovery must not reintroduce a file just trashed.
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
    fun refresh() = refreshLibrary(full = false)
    fun fullRefresh() { if(state.value.initialScanComplete) refreshLibrary(full = true) }

    override fun onStart(owner: LifecycleOwner) {
        foreground=true
        startScanIfNeeded()
    }
    override fun onStop(owner: LifecycleOwner) {
        foreground=false
        pauseScan()
    }
    private fun pauseScan() {
        scan?.cancel()
        // This client belongs only to the scanner: do not cancel photos or Cast.
        scanClient?.http?.dispatcher?.cancelAll()
    }
    private fun refreshLibrary(full: Boolean) {
        if(state.value.busy || state.value.scanning || state.value.rules.included.isEmpty()) return
        manualStop=false;settings.scanStopped(false)
        resumeScan=true;forceFullScan=full
        recheckScanFolders=!state.value.initialScanComplete
        syncCollections();startScanIfNeeded()
    }
    private fun startScanIfNeeded() {
        if(cleared || !foreground || !resumeScan || manualStop || !state.value.ready || state.value.busy || state.value.scanning) return
        val account=state.value.account ?: return
        val rules=state.value.rules
        if(rules.included.isEmpty()) return
        val client=scanClientFactory(account)
        scanClient=client
        val full=forceFullScan
        val recheck=recheckScanFolders
        mutable.update { it.copy(scanning=true,error=null,progress=getApplication<Application>().getString(R.string.scan_searching)) }
        scan=viewModelScope.launch {
            var completed=false
            try {
                val failures=withContext(Dispatchers.IO) {
                    val result=LibraryScan(db,account.driveId,rules,client::listPage,
                        { MediaReader.tags(client,it) },::publish,
                        { phase,done,total ->
                            val app=getApplication<Application>()
                            val message=when(phase) {
                                "waiting" -> app.getString(R.string.scan_waiting)
                                "discovery" -> app.getString(R.string.scan_discovery,done,total)
                                else -> app.getString(R.string.scan_dates,done,total)
                            }
                            mutable.update { it.copy(progress=message) }
                        }).run(full,recheck) { recheckScanFolders=false;forceFullScan=false }
                    settings.saveInitialScanComplete(true)
                    mutable.update { it.copy(initialScanComplete=true) }
                    completed=true
                    result
                }
                resumeScan=false;forceFullScan=false
                mutable.update { it.copy(progress=if(failures>0) getApplication<Application>().getString(R.string.scan_provisional,failures) else getApplication<Application>().getString(R.string.library_updated)) }
            } catch(e: CancellationException) {
                if(completed) { resumeScan=false;forceFullScan=false }
                // A completed checkpoint records full-refresh mode, so resuming must not reset it.
                withContext(NonCancellable + Dispatchers.IO) {
                    val saved=runCatching { db.checkpoint() }.getOrNull()
                    if(saved?.key==ScanCheckpoint.key(account.driveId,rules) && (!full || saved.full)) forceFullScan=false
                }
                mutable.update { it.copy(progress=getApplication<Application>().getString(if(!manualStop) R.string.scan_paused else if(state.value.initialScanComplete) R.string.scan_stopped else R.string.initial_scan_paused)) }
                throw e
            } catch(e: Exception) {
                resumeScan=false;forceFullScan=false
                mutable.update { it.copy(error=e.message ?: getApplication<Application>().getString(R.string.connection_failed)) }
            } finally {
                scanClient=null
                mutable.update { it.copy(scanning=false) }
                // Handles a quick return while cancellation of the old scan was still completing.
                startScanIfNeeded()
            }
        }
    }
    private fun publish(assets: List<Asset>) { mutable.update { it.copy(assets=assets.sortedWith(compareByDescending<Asset> { a -> a.date.millis }.thenBy { a -> a.entry.path })) } }
    fun stop() {
        manualStop=true;settings.scanStopped(true);resumeScan=false
        pauseScan()
        mutable.update { it.copy(progress=getApplication<Application>().getString(if(state.value.initialScanComplete) R.string.scan_stopped else R.string.initial_scan_paused)) }
    }
    fun clearError() { mutable.update { it.copy(error=null) } }
    fun logout() {
        if(state.value.scanning || state.value.busy || state.value.syncing) return
        resumeScan=false;forceFullScan=false;manualStop=false;recheckScanFolders=false
        syncDelay?.cancel()
        casting.stop()
        operation {
            driveClient?.http?.dispatcher?.cancelAll()
            images?.memoryCache?.clear(); images?.diskCache?.clear(); images?.shutdown(); images=null
            File(getApplication<Application>().cacheDir,"video-thumbs").deleteRecursively()
            File(getApplication<Application>().cacheDir,"shared").deleteRecursively()
            settings.clear(); db.replace(emptyList()); db.clearScan(); collectionDb.clear(); driveClient=null
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
            finally { mutable.update { it.copy(busy=false) };startScanIfNeeded() }
        }
    }
    override fun onCleared() {
        cleared=true;resumeScan=false
        if(Looper.myLooper()==Looper.getMainLooper()) scanLifecycle.removeObserver(this)
        else Handler(Looper.getMainLooper()).post { scanLifecycle.removeObserver(this) }
        pauseScan();casting.close();driveClient?.http?.dispatcher?.cancelAll();images?.shutdown()
        val running=scan
        if(running!=null && !running.isCompleted) running.invokeOnCompletion { db.close() } else db.close()
        collectionDb.close()
    }
}
