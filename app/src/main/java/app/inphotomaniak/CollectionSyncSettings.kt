package app.inphotomaniak

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.text.DateFormat
import java.util.Date

@Composable internal fun CollectionSyncSettings(s: GalleryState, model: GalleryModel) {
    var choose by remember { mutableStateOf(false) }
    Text(stringResource(R.string.collection_sync),style=MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.collection_sync_help),style=MaterialTheme.typography.bodySmall)
    Text(if(s.syncFolder==null) stringResource(R.string.collections_local) else DriveClient.displayPath(s.syncFolder))
    if(s.pendingEdits>0) Text(stringResource(R.string.collection_pending,s.pendingEdits))
    if(s.syncing) Text(stringResource(R.string.collections_syncing))
    else if(s.lastSync>0 && s.syncFolder!=null) Text(stringResource(R.string.collection_last_sync,DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(s.lastSync))),style=MaterialTheme.typography.bodySmall)
    s.syncError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled=!s.busy && !s.syncing,onClick={choose=true}) { Text(stringResource(R.string.choose_sync_folder)) }
        if(s.syncFolder!=null) OutlinedButton(enabled=!s.busy && !s.syncing,onClick=model::syncCollections) { Text(stringResource(R.string.sync_now)) }
    }
    if(s.syncFolder!=null) TextButton(enabled=!s.busy && !s.syncing,onClick=model::disableCollectionSync) { Text(stringResource(R.string.disable_sync)) }
    if(choose) SyncFolderDialog(model,onDismiss={choose=false}) { model.enableCollectionSync(it);choose=false }
}

@Composable private fun SyncFolderDialog(model: GalleryModel, onDismiss: ()->Unit, onChoose: (String)->Unit) {
    var path by remember { mutableStateOf("/") }
    var folders by remember { mutableStateOf<List<RemoteEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(path,retry) {
        loading=true;error=null;folders=emptyList()
        try { folders=withContext(Dispatchers.IO) { requireNotNull(model.driveClient).list(path).filter { it.directory } } }
        catch(e: CancellationException) { throw e }
        catch(e: Exception) { error=e.message ?: "Could not list folders" }
        finally { loading=false }
    }
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.choose_sync_folder))},text={ Column {
        Text(stringResource(R.string.sync_folder_help))
        Text(DriveClient.displayPath(path),Modifier.padding(vertical=8.dp))
        if(path!="/") TextButton(enabled=!loading,onClick={path=path.trimEnd('/').substringBeforeLast('/',"")+"/"}) { Text(stringResource(R.string.parent_folder)) }
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it,color=MaterialTheme.colorScheme.error);TextButton(onClick={retry++}) { Text(stringResource(R.string.retry)) } }
        LazyColumn(Modifier.heightIn(max=280.dp)) { items(folders,key={it.path}) { folder ->
            ListItem(headlineContent={Text(folder.name)},modifier=Modifier.clickable(enabled=!loading) { path=folder.path })
        } }
    } },confirmButton={TextButton(enabled=!loading && error==null,onClick={onChoose(path)}) { Text(stringResource(R.string.use_folder)) }},
        dismissButton={TextButton(onClick=onDismiss) { Text(stringResource(R.string.cancel)) }})
}
