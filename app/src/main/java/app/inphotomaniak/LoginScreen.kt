package app.inphotomaniak

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable internal fun Login(s: GalleryState,model: GalleryModel) {
    var id by rememberSaveable { mutableStateOf("") }
    // Token deliberately excluded from saved instance state.
    var token by remember { mutableStateOf("") }
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Spacer(Modifier.height(28.dp))
        Icon(Icons.Outlined.FilterVintage,null,Modifier.size(58.dp),tint=MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.login_title),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
        Text(stringResource(R.string.login_intro),color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(id,{id=it},Modifier.fillMaxWidth(),label={Text(stringResource(R.string.drive_id))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,enabled=!s.busy)
        OutlinedTextField(token,{token=it},Modifier.fillMaxWidth(),label={Text(stringResource(R.string.api_token))},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),singleLine=true,enabled=!s.busy)
        Text(stringResource(R.string.login_help),style=MaterialTheme.typography.bodySmall)
        s.error?.let { ErrorCard(it,model::clearError) }
        Button(onClick={model.login(id,token)},Modifier.fillMaxWidth().height(54.dp),enabled=!s.busy && id.isNotBlank() && token.isNotBlank()) {
            if(s.busy) CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp) else Text(stringResource(R.string.connect_drive))
        }
        TextButton(onClick={uri.openUri("https://manager.infomaniak.com/v3/ng/accounts/token/list")}) { Text(stringResource(R.string.create_token)) }
        Text(stringResource(R.string.login_privacy),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
