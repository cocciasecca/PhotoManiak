package app.inphotomaniak

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.luminance
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory

@Composable internal fun CastButton(controller: CastController) {
    val state by controller.state.collectAsStateWithLifecycle()
    var help by remember { mutableStateOf(false) }
    val dark=MaterialTheme.colorScheme.background.luminance()<0.5f
    if(state.ready) key(dark) { AndroidView(factory={ context -> MediaRouteButton(ContextThemeWrapper(context,if(dark) R.style.CastButtonDark else R.style.CastButtonLight)).apply {
        contentDescription=context.getString(R.string.cast)
        CastButtonFactory.setUpMediaRouteButton(context,this)
        setAlwaysVisible(true)
    } },modifier=Modifier.size(48.dp)) }
    else IconButton(onClick={help=true}) { Icon(Icons.Outlined.Cast,stringResource(R.string.cast)) }
    if(help) AlertDialog(onDismissRequest={help=false},title={Text(stringResource(R.string.cast))},
        text={Text(state.unavailable ?: stringResource(R.string.cast_initializing))},confirmButton={TextButton(onClick={help=false}) { Text(stringResource(R.string.close)) }})
}

@Composable internal fun CastControls(controller: CastController) {
    val state by controller.state.collectAsStateWithLifecycle()
    val view=LocalView.current
    DisposableEffect(view,state.connected) {
        val previous=view.keepScreenOn
        if(state.connected) view.keepScreenOn=true
        onDispose { view.keepScreenOn=previous }
    }
    if(!state.connected && state.error==null) return
    Surface(color=MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal=12.dp)) {
            state.error?.let { ErrorCard(it,controller::clearError) }
            if(state.connected) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.casting_to,state.device),style=MaterialTheme.typography.labelMedium)
                        Text(if(state.loading) stringResource(R.string.preparing) else state.title.ifEmpty { stringResource(R.string.cast_choose_media) },style=MaterialTheme.typography.bodySmall,maxLines=1)
                    }
                    if(state.video) IconButton(enabled=!state.loading,onClick=controller::togglePlayback) { Icon(if(state.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,stringResource(if(state.playing) R.string.cast_pause else R.string.cast_play)) }
                    IconButton(onClick=controller::stop) { Icon(Icons.Outlined.StopCircle,stringResource(R.string.cast_stop)) }
                }
                if(state.video && state.duration>0) {
                    var seeking by remember { mutableStateOf<Float?>(null) }
                    Slider(value=seeking ?: (state.position.toFloat()/state.duration).coerceIn(0f,1f),onValueChange={seeking=it},onValueChangeFinished={seeking?.let { controller.seek((it*state.duration).toLong()) };seeking=null})
                }
            }
        }
    }
}
