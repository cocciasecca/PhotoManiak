package app.inphotomaniak

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState

@Composable internal fun UpdateDownloadPreference(updates: AppUpdates) {
    val state by updates.state.collectAsStateWithLifecycle()
    if (state.autoDownload == null) AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.update_download_prompt)) },
        text = { Text(stringResource(R.string.update_download_choice)) },
        confirmButton = {
            TextButton(onClick = { updates.autoDownload(true) }) { Text(stringResource(R.string.update_download_enable)) }
        },
        dismissButton = {
            TextButton(onClick = { updates.autoDownload(false) }) { Text(stringResource(R.string.update_notify_only)) }
        }
    )
}

@Composable internal fun UpdateNotice(updates: AppUpdates) {
    val state by updates.state.collectAsStateWithLifecycle()
    val update = state.available ?: return
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycleState == Lifecycle.State.RESUMED
    val canInstall = resumed && context.packageManager.canRequestPackageInstalls()
    val apk = state.apk

    LaunchedEffect(resumed, state.autoDownload, state.showNotice, state.checking, state.downloading, update) {
        if (resumed && !state.checking) updates.downloadAutomatically(update)
    }
    LaunchedEffect(canInstall, apk) {
        if (canInstall && apk != null) {
            if (runCatching { context.startActivity(updateInstaller(context, apk)) }.isSuccess) updates.dismiss()
            else updates.installerFailed()
        }
    }

    if (!state.showNotice || state.autoDownload == null) return
    AlertDialog(
        onDismissRequest = updates::dismiss,
        title = { Text(stringResource(R.string.update_available, update.version)) },
        text = { Column {
            Text(when {
                state.downloading -> stringResource(R.string.update_downloading, update.edition)
                apk != null -> stringResource(R.string.update_install_permission)
                else -> stringResource(R.string.update_description, update.edition)
            })
            if (state.message == R.string.update_browser_failed || state.message == R.string.update_apk_failed) {
                state.message?.let { Text(stringResource(it)) }
            }
        } },
        confirmButton = {
            if (apk != null && !canInstall) {
                TextButton(onClick = {
                    val intent = Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.fromParts("package", context.packageName, null))
                    if (runCatching { context.startActivity(intent) }.isFailure) updates.installerFailed()
                }) { Text(stringResource(R.string.update_allow_install)) }
            } else if (!state.downloading && apk == null) {
                TextButton(onClick = {
                    if (runCatching { uri.openUri(update.downloadUrl) }.isSuccess) updates.dismiss()
                    else updates.downloadFailed()
                }) { Text(stringResource(R.string.download_update)) }
            }
        },
        dismissButton = { TextButton(onClick = updates::dismiss) { Text(stringResource(R.string.update_later)) } }
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
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Text(stringResource(R.string.auto_download_updates),Modifier.weight(1f).padding(end=12.dp))
        Switch(checked=state.autoDownload==true,onCheckedChange=updates::autoDownload)
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
