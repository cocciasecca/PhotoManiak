package app.inphotomaniak

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun GalleryApp(model: GalleryModel = viewModel()) {
    val s by model.state.collectAsStateWithLifecycle()
    GalleryContent(s,model)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun GalleryContent(s: GalleryState,model: GalleryModel) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    if(!s.ready) { Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator() }; return }
    if(s.account==null) { Login(s,model); return }
    val grid = rememberLazyGridState()
    val current = selected
    // Freeze the ordering during a viewing session while indexing updates the timeline.
    val viewerAssets = remember(current) { s.assets }
    if(current != null && viewerAssets.any { it.entry.path==current }) {
        Viewer(viewerAssets,viewerAssets.indexOfFirst { it.entry.path==current },model) { selected=null }; return
    }
    Scaffold(
        topBar={ TopAppBar(title={ Column { Text(stringResource(R.string.app_name),fontWeight=FontWeight.Bold); Text(stringResource(R.string.app_tagline),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary) } }, actions={
            if(s.scanning) IconButton(onClick=model::stop) { Icon(Icons.Outlined.Stop,stringResource(R.string.stop_scan)) }
            else IconButton(onClick=model::refresh,enabled=s.rules.included.isNotEmpty() && !s.busy) { Icon(Icons.Outlined.Refresh,stringResource(R.string.refresh_library)) }
        }) },
        bottomBar={ NavigationBar {
            listOf(Triple("timeline",stringResource(R.string.photos),Icons.Outlined.PhotoLibrary),Triple("folders",stringResource(R.string.folders),Icons.Outlined.Folder),Triple("settings",stringResource(R.string.settings),Icons.Outlined.Settings)).forEach { (key,label,icon) ->
                NavigationBarItem(selected=s.screen==key,onClick={model.screen(key)},icon={Icon(icon,label)},label={Text(label)})
            }
        } }
    ) { padding -> Column(Modifier.padding(padding).fillMaxSize()) {
        if(s.busy || s.scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(s.progress.isNotBlank()) Text(s.progress,Modifier.padding(horizontal=20.dp,vertical=6.dp),style=MaterialTheme.typography.labelMedium)
        s.error?.let { ErrorCard(it,model::clearError) }
        when(s.screen) {
            "folders" -> FolderScreen(s,model)
            "settings" -> SettingsScreen(s,model)
            else -> Timeline(s,model,grid) { selected=it.entry.path }
        }
    } }
}
