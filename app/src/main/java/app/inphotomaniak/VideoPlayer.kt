package app.inphotomaniak

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
@Composable internal fun VideoPlayer(asset: Asset,model: GalleryModel) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val player=remember(asset.entry.path) {
        val factory=OkHttpDataSource.Factory(model.driveClient!!.http)
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(factory)).build().apply { setMediaItem(MediaItem.fromUri(model.driveClient!!.url(asset.entry.path).toString()));prepare() }
    }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(player,lifecycle) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_STOP) player.pause() }
        val listener=object: androidx.media3.common.Player.Listener { override fun onPlayerError(e: androidx.media3.common.PlaybackException) { error=context.getString(R.string.video_failed) } }
        lifecycle.addObserver(observer);player.addListener(listener)
        onDispose { lifecycle.removeObserver(observer);player.removeListener(listener);player.release() }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory={PlayerView(it).apply { this.player=player }},modifier=Modifier.fillMaxSize())
        error?.let { Text(it,Modifier.align(Alignment.Center).background(Color.Black).padding(24.dp)) }
    }
}
