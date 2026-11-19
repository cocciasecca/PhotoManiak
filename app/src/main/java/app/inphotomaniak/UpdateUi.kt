package app.inphotomaniak

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable internal fun UpdateNotice(updates: AppUpdates) {
    val state by updates.state.collectAsStateWithLifecycle()
    val update=state.available ?: return
    val uri=LocalUriHandler.current
    if(state.showNotice) AlertDialog(
        onDismissRequest=updates::dismiss,
        title={Text(stringResource(R.string.update_available,update.version))},
        text={Column {
            Text(stringResource(R.string.update_description,update.edition))
            if(state.message==R.string.update_browser_failed) Text(stringResource(R.string.update_browser_failed))
        }},
        confirmButton={TextButton(onClick={
            if(runCatching { uri.openUri(update.downloadUrl) }.isSuccess) updates.dismiss() else updates.downloadFailed()
        }) { Text(stringResource(R.string.download_update)) }},
        dismissButton={TextButton(onClick=updates::dismiss) { Text(stringResource(R.string.update_later)) }}
    )
}

@Composable internal fun UpdateSettings(updates: AppUpdates) {
    val state by updates.state.collectAsStateWithLifecycle()
    val uri=LocalUriHandler.current
    Text(stringResource(R.string.updates),style=MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Text(stringResource(R.string.automatic_updates),Modifier.weight(1f).padding(end=12.dp))
        Switch(checked=state.automatic,onCheckedChange=updates::automatic)
    }
    Text(stringResource(R.string.updates_privacy),style=MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick={updates.check()},enabled=!state.checking) {
        Text(stringResource(if(state.checking) R.string.checking_updates else R.string.check_updates))
    }
    state.available?.let { update ->
        Text(stringResource(R.string.update_available,update.version))
        TextButton(onClick={ if(runCatching { uri.openUri(update.downloadUrl) }.isFailure) updates.downloadFailed() }) {
            Text(stringResource(R.string.download_update_edition,update.edition))
        }
    }
    state.message?.let { Text(stringResource(it),style=MaterialTheme.typography.bodySmall) }
}
