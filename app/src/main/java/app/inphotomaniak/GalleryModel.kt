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
    val screen: String = "timeline"
)
@OptIn(coil.annotation.ExperimentalCoilApi::class)
class GalleryModel(app: Application): AndroidViewModel(app) {
    private val settings = Settings(app)
    private val db = AssetDb(app)
    private val mutable = MutableStateFlow(GalleryState())
    val state = mutable.asStateFlow()
    var dav: DriveClient? = null; private set
    var images: ImageLoader? = null; private set
    private var scan: Job? = null
    init { viewModelScope.launch { withContext(Dispatchers.IO) {
        val account = settings.account()
        if(account != null) connect(account)
        val rules = settings.rules()
        mutable.value = GalleryState(ready=true,account=account,rules=rules,assets=if(account == null) emptyList() else db.all().filter { rules.accepts(it.entry.path) })
    } } }
    private fun connect(account: Account) {
        dav = DriveClient(account)
        images = ImageLoader.Builder(getApplication()).okHttpClient(dav!!.http)
            .memoryCache { MemoryCache.Builder(getApplication()).maxSizePercent(0.15).build() }
            .diskCache { DiskCache.Builder().directory(File(getApplication<Application>().cacheDir,"images")).maxSizeBytes(256L*1024*1024).build() }.build()
    }
    fun login(id: String,token: String) = operation {
        val account = Account(id.trim(),token.trim())
        val client = DriveClient(account)
        val folders = client.list("/").filter { it.directory }
        // A fresh login must never inherit an unreadable previous account's data.
        settings.clear(); db.replace(emptyList())
        File(getApplication<Application>().cacheDir,"images").deleteRecursively()
        File(getApplication<Application>().cacheDir,"video-thumbs").deleteRecursively()
        settings.save(account); connect(account)
        mutable.update { it.copy(account=account,rules=FolderRules(),assets=emptyList(),folders=folders,path="/",screen="folders") }
    }
    fun browse(path: String) = operation {
        val folders = requireNotNull(dav).list(path).filter { it.directory }.sortedBy { it.name.lowercase() }
        mutable.update { it.copy(folders=folders,path=path,screen="folders") }
    }
    fun screen(name: String) { mutable.update { it.copy(screen=name,error=null) }; if(name=="folders") browse(state.value.path) }
    fun rule(path: String, mode: Int) {
        if(state.value.scanning || state.value.busy) return
        val old = state.value.rules
        val next = FolderRules(old.included - path + if(mode==1) setOf(path) else emptySet(),old.excluded - path + if(mode==2) setOf(path) else emptySet())
        operation { settings.save(next); mutable.update { it.copy(rules=next,assets=db.all().filter { a -> next.accepts(a.entry.path) }) } }
    }
    fun refresh() {
        if(state.value.busy || state.value.scanning || state.value.rules.included.isEmpty()) return
        val client = dav ?: return
        val rules = state.value.rules
        scan = viewModelScope.launch {
            mutable.update { it.copy(scanning=true,error=null,progress="Cerco foto e video…") }
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
                    mutable.update { it.copy(progress="${found.size} elementi · ${visited.size} cartelle") }
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
                    mutable.update { it.copy(progress="Date delle foto · ${i+1}/${assets.size}") }
                }
                db.upsert(changed); publish(assets)
                mutable.update { it.copy(progress=if(failures > 0) "$failures metadati non letti: data provvisoria, riprova con Aggiorna." else "Libreria aggiornata") }
            } } catch(e: CancellationException) { mutable.update { it.copy(progress="Scansione interrotta") }; throw e }
            catch(e: Exception) { mutable.update { it.copy(error=e.message ?: "Connessione non riuscita") } }
            finally { mutable.update { it.copy(scanning=false) } }
        }
    }
    private fun publish(assets: List<Asset>) { mutable.update { it.copy(assets=assets.sortedWith(compareByDescending<Asset> { a -> a.date.millis }.thenBy { a -> a.entry.path })) } }
    fun stop() { scan?.cancel(); dav?.http?.dispatcher?.cancelAll() }
    fun clearError() { mutable.update { it.copy(error=null) } }
    fun logout() {
        if(state.value.scanning || state.value.busy) return
        operation {
            dav?.http?.dispatcher?.cancelAll()
            images?.memoryCache?.clear(); images?.diskCache?.clear(); images?.shutdown(); images=null
            File(getApplication<Application>().cacheDir,"video-thumbs").deleteRecursively()
            settings.clear(); db.replace(emptyList()); dav=null
            mutable.value=GalleryState(ready=true)
        }
    }
    private fun operation(block: suspend () -> Unit) {
        if(state.value.busy) return
        mutable.update { it.copy(busy=true,error=null) }
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { block() } }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(error=e.message ?: "Operazione non riuscita") } }
            finally { mutable.update { it.copy(busy=false) } }
        }
    }
    override fun onCleared() { dav?.http?.dispatcher?.cancelAll(); images?.shutdown(); db.close() }
}
