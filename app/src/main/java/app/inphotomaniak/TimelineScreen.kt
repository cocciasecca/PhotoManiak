package app.inphotomaniak

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import android.content.Intent
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun Timeline(s: GalleryState,model: GalleryModel,grid: LazyGridState,onOpen: (Asset)->Unit) {
    if(s.assets.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(32.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.PhotoLibrary,null,Modifier.size(64.dp),tint=MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(20.dp));Text(stringResource(R.string.empty_title),style=MaterialTheme.typography.headlineSmall)
            Text(if(s.scanning) stringResource(R.string.exploring_folders) else if(s.rules.included.isEmpty()) stringResource(R.string.empty_choose_folders) else stringResource(R.string.empty_refresh),Modifier.padding(vertical=12.dp))
            if(s.rules.included.isEmpty()) Button(onClick={model.screen("folders")}) { Text(stringResource(R.string.choose_folders)) }
        };return
    }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedPaths by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val selectedSet = selectedPaths.toSet()
    val selectedAssets = s.assets.filter { it.entry.path in selectedSet }
    val locked = s.busy || sharing
    BackHandler(selecting && !locked) { selecting=false;selectedPaths=arrayListOf() }
    LaunchedEffect(s.assets) { selectedPaths=ArrayList(selectedPaths.filter { path -> s.assets.any { it.entry.path==path } }) }
    fun toggle(path: String) {
        selecting=true
        selectedPaths=ArrayList(if(path in selectedPaths) selectedPaths-path else selectedPaths+path)
    }
    val scope = rememberCoroutineScope()
    val groups = remember(s.assets) { s.assets.groupBy { it.date.day }.toSortedMap(reverseOrder()) }
    val months = remember(groups) {
        var index=1
        buildList { var previous="";groups.forEach { (day,items) -> val month=day.take(7);if(month!=previous) {add(month to index);previous=month};index+=1+items.size } }
    }
    val noDate = stringResource(R.string.no_date)
    var showMonths by remember { mutableStateOf(false) }
    if(confirmDelete) DeleteConfirmation(selectedAssets.size,onDismiss={confirmDelete=false}) {
        confirmDelete=false
        model.deleteAssets(selectedAssets.map { it.entry.path }.toSet()) { deleted -> selectedPaths=ArrayList(selectedPaths.filterNot { it in deleted }) }
    }
    Column {
        shareError?.let { ErrorCard(it) { shareError=null } }
        if(selecting) {
            Text(stringResource(R.string.selected_count,selectedAssets.size),Modifier.padding(horizontal=20.dp,vertical=8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
                TextButton(enabled=!locked,onClick={selecting=false;selectedPaths=arrayListOf()}) { Text(stringResource(R.string.cancel)) }
                TextButton(enabled=!locked && selectedAssets.isNotEmpty(),onClick={
                    val entries=selectedAssets.map { it.entry }
                    sharing=true;shareError=null
                    scope.launch {
                        try { context.startActivity(Intent.createChooser(ShareFiles.prepareMany(context,requireNotNull(model.driveClient),entries),context.getString(R.string.share))) }
                        catch(e: CancellationException) { throw e }
                        catch(e: Exception) { shareError=e.message ?: context.getString(R.string.share_failed) }
                        finally { sharing=false }
                    }
                }) { Text(stringResource(if(sharing) R.string.preparing else R.string.share)) }
                TextButton(enabled=!locked && !s.scanning && selectedAssets.isNotEmpty(),onClick={confirmDelete=true}) { Text(stringResource(R.string.delete)) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(stringResource(R.string.timeline_title),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            if(!selecting) IconButton(enabled=!locked,onClick={selecting=true}) {
                Icon(Icons.Outlined.Checklist,stringResource(R.string.select_photos))
            }
            Box {
                TextButton(onClick={showMonths=true}) {
                    Icon(Icons.Outlined.CalendarMonth,null)
                    Text(stringResource(R.string.jump_to))
                }
                DropdownMenu(expanded=showMonths,onDismissRequest={showMonths=false}) {
                    months.forEach { (month,index) ->
                        DropdownMenuItem(
                            text={Text(month.ifEmpty { noDate })},
                            onClick={showMonths=false;scope.launch { grid.scrollToItem(index) }}
                        )
                    }
                }
            }
        }
        LazyVerticalGrid(modifier=Modifier.testTag("timeline_grid"),columns=GridCells.Adaptive(s.thumbnailSize.minWidth.dp),state=grid,contentPadding=PaddingValues(3.dp),horizontalArrangement=Arrangement.spacedBy(3.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            item(span={GridItemSpan(maxLineSpan)},key="count") { Text(stringResource(R.string.memory_count,s.assets.size),Modifier.padding(horizontal=17.dp,vertical=8.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            groups.forEach { (day,assets) ->
                item(span={GridItemSpan(maxLineSpan)},key="day:$day") { Text(dayLabel(day),Modifier.padding(start=17.dp,top=22.dp,bottom=10.dp),style=MaterialTheme.typography.titleSmall) }
                items(assets,key={it.entry.path}) { asset ->
                    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant).testTag("media:${asset.entry.path}").semantics { selected=asset.entry.path in selectedPaths }.combinedClickable(
                        enabled=!locked,
                        onClick={if(selecting) toggle(asset.entry.path) else onOpen(asset)},
                        onLongClick={toggle(asset.entry.path)}
                    )) {
                        Photo(asset,model,Modifier.fillMaxSize(),ContentScale.Crop)
                        if(asset.entry.path in selectedPaths) {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha=.25f)))
                            Icon(Icons.Outlined.CheckCircle,stringResource(R.string.selected),Modifier.align(Alignment.TopStart).padding(6.dp),tint=MaterialTheme.colorScheme.primary)
                        }
                        if(asset.entry.video) Icon(Icons.Outlined.PlayCircle,stringResource(R.string.video),Modifier.align(Alignment.BottomEnd).padding(6.dp),tint=Color.White)
                        if(!asset.metadataRead) Icon(Icons.Outlined.Schedule,stringResource(R.string.provisional_date),Modifier.align(Alignment.TopEnd).padding(6.dp).size(16.dp),tint=Color.White)
                    }
                }
            }
        }
    }
}
