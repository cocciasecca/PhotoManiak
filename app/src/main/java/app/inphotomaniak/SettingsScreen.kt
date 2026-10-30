package app.inphotomaniak

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable internal fun SettingsScreen(s: GalleryState,model: GalleryModel) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Text(stringResource(R.string.settings),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
        Text(stringResource(R.string.thumbnail_size),style=MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            ThumbnailSize.entries.forEach { size ->
                FilterChip(selected=s.thumbnailSize==size,onClick={model.thumbnailSize(size)},enabled=!s.busy,label={Text(stringResource(size.labelRes))})
            }
        }
        HorizontalDivider()
        Text(stringResource(R.string.signed_in_token));Text(stringResource(R.string.connected_drive,s.account?.driveId.orEmpty()),color=MaterialTheme.colorScheme.primary)
        HorizontalDivider()
        Text(stringResource(R.string.folder_count,s.rules.included.size,s.rules.excluded.size))
        Text(stringResource(R.string.cache_description))
        Text(stringResource(R.string.metadata_description),style=MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.app_about,BuildConfig.VERSION_NAME),style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={confirm=true},enabled=!s.busy && !s.scanning) { Text(stringResource(R.string.sign_out_clear)) }
    }
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text(stringResource(R.string.sign_out_title))},text={Text(stringResource(R.string.sign_out_description))},confirmButton={TextButton(onClick={confirm=false;model.logout()}) { Text(stringResource(R.string.sign_out)) }},dismissButton={TextButton(onClick={confirm=false}) { Text(stringResource(R.string.cancel)) }})
}
