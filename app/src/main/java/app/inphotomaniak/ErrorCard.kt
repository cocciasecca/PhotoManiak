package app.inphotomaniak

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable internal fun ErrorCard(message: String,dismiss: () -> Unit) {
    Surface(color=MaterialTheme.colorScheme.errorContainer,shape=RoundedCornerShape(16.dp),modifier=Modifier.padding(12.dp).fillMaxWidth()) {
        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) { Text(message,Modifier.weight(1f),color=MaterialTheme.colorScheme.onErrorContainer); IconButton(onClick=dismiss) { Icon(Icons.Outlined.Close,stringResource(R.string.dismiss_message)) } }
    }
}
