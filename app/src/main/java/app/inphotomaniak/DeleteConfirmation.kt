package app.inphotomaniak

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable internal fun DeleteConfirmation(count: Int,onDismiss: () -> Unit,onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest=onDismiss,
        title={Text(stringResource(R.string.delete_title,count))},
        text={Text(stringResource(R.string.delete_description))},
        confirmButton={TextButton(onClick=onConfirm) { Text(stringResource(R.string.move_to_trash)) }},
        dismissButton={TextButton(onClick=onDismiss) { Text(stringResource(R.string.cancel)) }}
    )
}
