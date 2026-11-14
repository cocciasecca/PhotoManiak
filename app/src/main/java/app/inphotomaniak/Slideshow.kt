package app.inphotomaniak

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.random.Random

internal fun nextSlide(current: Int,count: Int,random: Boolean): Int {
    if(count<=1) return 0
    if(!random) return (current+1)%count
    // Pick a different photo without allocating/shuffling a potentially huge library.
    val picked=Random.nextInt(count-1)
    return if(picked>=current) picked+1 else picked
}

@Composable internal fun SlideshowSetup(initialSeconds: Int,onDismiss: ()->Unit,onStart: (Int)->Unit) {
    var seconds by rememberSaveable { mutableIntStateOf(initialSeconds) }
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.slideshow))},text={
        Column {
            Text(stringResource(R.string.slideshow_help))
            listOf(3,5,10,30).forEach { value ->
                TextButton(onClick={seconds=value}) {
                    RadioButton(selected=seconds==value,onClick=null)
                    Text(stringResource(R.string.slide_interval,value))
                }
            }
        }
    },confirmButton={TextButton(onClick={onStart(seconds)}) { Text(stringResource(R.string.start_slideshow)) }},dismissButton={TextButton(onClick=onDismiss) { Text(stringResource(R.string.cancel)) }})
}

@Composable internal fun Slideshow(assets: List<Asset>,initial: Int,seconds: Int,model: GalleryModel,onExit: (Int)->Unit) {
    val indices=remember(assets) { assets.indices.filter { !assets[it].entry.video } }
    if(indices.isEmpty()) { LaunchedEffect(Unit) { onExit(initial) };return }
    var current by rememberSaveable { mutableIntStateOf(indices.indexOfFirst { it>=initial }.takeIf { it>=0 } ?: 0) }
    var playing by rememberSaveable { mutableStateOf(true) }
    var random by rememberSaveable { mutableStateOf(false) }
    var options by remember { mutableStateOf(false) }
    val index=indices[current.coerceIn(indices.indices)]
    val asset=assets[index]
    var settled by remember(asset.entry.path) { mutableStateOf(false) }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val view=LocalView.current
    val casting by model.casting.state.collectAsStateWithLifecycle()
    DisposableEffect(view) {
        val previous=view.keepScreenOn;view.keepScreenOn=true
        onDispose { view.keepScreenOn=previous }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { playing=false }
    LaunchedEffect(casting.connected,asset.entry.path) {
        if(casting.connected) model.driveClient?.let { client -> model.images?.let { model.casting.show(asset.entry,client,it) } }
    }
    LaunchedEffect(current,playing,random,options,settled,casting.loading,casting.error) {
        if(playing && !options && settled && !casting.loading && casting.error==null && indices.size>1) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                delay(seconds*1000L)
                current=nextSlide(current,indices.size,random)
            }
        }
    }
    BackHandler { onExit(index) }
    Box(Modifier.fillMaxSize().background(Color.Black).testTag("slide:${asset.entry.path}")) {
        key(asset.entry.path) { Photo(asset,model,Modifier.fillMaxSize(),ContentScale.Fit) { settled=true } }
        if(!settled) CircularProgressIndicator(Modifier.align(Alignment.Center))
        Column(Modifier.align(Alignment.BottomCenter).safeDrawingPadding().background(Color.Black.copy(alpha=.65f))) {
            casting.error?.let { ErrorCard(it,model.casting::clearError) }
            Row(Modifier.fillMaxWidth().padding(8.dp),horizontalArrangement=Arrangement.Center) {
                IconButton(onClick={playing=!playing}) { Icon(if(playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,stringResource(if(playing) R.string.pause_slideshow else R.string.resume_slideshow),tint=Color.White) }
                IconButton(enabled=indices.size>1,onClick={current=nextSlide(current,indices.size,random)}) { Icon(Icons.Outlined.SkipNext,stringResource(R.string.next_photo),tint=Color.White) }
                Box {
                    IconButton(onClick={options=true}) { Icon(Icons.Outlined.MoreVert,stringResource(R.string.slideshow_options),tint=Color.White) }
                    DropdownMenu(expanded=options,onDismissRequest={options=false}) {
                        DropdownMenuItem(text={Text(stringResource(R.string.random_photos))},leadingIcon={Icon(if(random) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,null)},onClick={random=!random;options=false})
                        DropdownMenuItem(text={Text(stringResource(R.string.exit_slideshow))},leadingIcon={Icon(Icons.Outlined.Close,null)},onClick={onExit(index)})
                    }
                }
            }
        }
    }
}
