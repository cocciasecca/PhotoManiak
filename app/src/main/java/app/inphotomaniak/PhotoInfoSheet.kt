package app.inphotomaniak

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun PhotoInfoSheet(asset: Asset,model: GalleryModel,onDismiss: ()->Unit) {
    var tags by remember(asset.entry) { mutableStateOf<Map<String,String>?>(null) }
    var failed by remember(asset.entry) { mutableStateOf(false) }
    LaunchedEffect(asset.entry) {
        try { tags=withContext(Dispatchers.IO) { MediaReader.tags(requireNotNull(model.driveClient),asset.entry) } }
        catch(e: CancellationException) { throw e }
        catch(e: Exception) { failed=true }
    }
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=false)) {
        SelectionContainer {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.info),style=MaterialTheme.typography.headlineSmall)
                Text(asset.entry.name,style=MaterialTheme.typography.titleMedium)
                Text(dayLabel(asset.date.day))
                Text(stringResource(if(asset.metadataRead) R.string.date_source else R.string.date_source_provisional,dateSourceLabel(asset.date.source)))
                Text(DriveClient.displayPath(asset.entry.path))
                Text(asset.entry.mime)
                HorizontalDivider()
                Text(stringResource(R.string.photo_location),style=MaterialTheme.typography.titleMedium)
                when {
                    failed -> Text(stringResource(R.string.metadata_failed))
                    tags==null -> Text(stringResource(R.string.metadata_loading))
                    else -> {
                        val latitude=tags?.get("Latitude")
                        val longitude=tags?.get("Longitude")
                        Text(if(latitude!=null && longitude!=null) "$latitude, $longitude" else stringResource(R.string.location_missing))
                        val camera=listOfNotNull(tags?.get("Make"),tags?.get("Camera")).joinToString(" ")
                        if(camera.isNotBlank()) { Text(stringResource(R.string.photo_camera),style=MaterialTheme.typography.titleMedium);Text(camera) }
                        val width=tags?.get("Width");val height=tags?.get("Height")
                        if(width!=null && height!=null) { Text(stringResource(R.string.photo_dimensions),style=MaterialTheme.typography.titleMedium);Text("$width × $height") }
                    }
                }
            }
        }
    }
}
