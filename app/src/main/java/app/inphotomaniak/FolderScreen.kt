package app.inphotomaniak

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable internal fun FolderScreen(s: GalleryState,model: GalleryModel) {
    val enabled = !s.busy && !s.scanning
    BackHandler(s.path!="/") { model.browse(parent(s.path)) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal=20.dp,vertical=12.dp)) {
            Text(stringResource(R.string.folder_title),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            Text(stringResource(R.string.folder_help),style=MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={model.browse(parent(s.path))},enabled=s.path!="/" && !s.busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,stringResource(R.string.parent_folder)) }
                Text(DriveClient.displayPath(s.path),Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
            }
            RuleButtons(s.path,s.rules,enabled,model)
        }
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(s.folders.isEmpty() && !s.busy) item { Text(stringResource(R.string.no_subfolders),Modifier.padding(16.dp),color=MaterialTheme.colorScheme.onSurfaceVariant) }
            items(s.folders,key={it.path}) { folder ->
                Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth().clickable(enabled=!s.busy){model.browse(folder.path)}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Folder,null,tint=MaterialTheme.colorScheme.secondary)
                            Text(folder.name,Modifier.weight(1f).padding(horizontal=12.dp),fontWeight=FontWeight.Medium)
                            Icon(Icons.Outlined.ChevronRight,stringResource(R.string.open_folder,folder.name))
                        }
                        RuleButtons(folder.path,s.rules,enabled,model)
                    }
                }
            }
        }
        Button(onClick={model.screen("timeline");model.refresh()},enabled=enabled && s.rules.included.isNotEmpty(),modifier=Modifier.fillMaxWidth().padding(16.dp)) { Text(stringResource(R.string.show_library)) }
    }
}
private fun parent(path: String) = path.trimEnd('/').substringBeforeLast('/',"").ifEmpty { "" } + "/"
@Composable private fun RuleButtons(path: String,rules: FolderRules,enabled: Boolean,model: GalleryModel) {
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        FilterChip(selected=path in rules.included,onClick={model.rule(path,if(path in rules.included) 0 else 1)},enabled=enabled,label={Text(stringResource(R.string.include))})
        FilterChip(selected=path in rules.excluded,onClick={model.rule(path,if(path in rules.excluded) 0 else 2)},enabled=enabled,label={Text(stringResource(R.string.exclude))})
        if(path !in rules.included && path !in rules.excluded) Text(if(rules.excluded(path)) stringResource(R.string.excluded) else if(rules.accepts(path)) stringResource(R.string.included) else "",Modifier.align(Alignment.CenterVertically),style=MaterialTheme.typography.labelSmall)
    }
}
