package app.inphotomaniak

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun Viewer(assets: List<Asset>,initial: Int,model: GalleryModel,onClose: ()->Unit) {
    // Keep photo/video controls readable against the black viewer in every app theme.
    val activity=LocalContext.current.activity()
    val parentDark=MaterialTheme.colorScheme.background.luminance() < 0.5f
    DisposableEffect(activity,parentDark) {
        activity?.themeSystemBars(true)
        onDispose { activity?.themeSystemBars(parentDark) }
    }
    val state by model.state.collectAsStateWithLifecycle()
    MaterialTheme(colorScheme=paletteColors(true,state.colorPalette,state.customColor)) { ViewerContent(assets,initial,model,onClose) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ViewerContent(assets: List<Asset>,initial: Int,model: GalleryModel,onClose: ()->Unit) {
    val pager=rememberPagerState(initialPage=initial) { assets.size }
    var addToAlbum by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val asset=assets[pager.currentPage]
    val state by model.state.collectAsStateWithLifecycle()
    val casting by model.casting.state.collectAsStateWithLifecycle()
    LaunchedEffect(casting.connected,asset.entry.path) {
        if(casting.connected) model.driveClient?.let { client -> model.images?.let { model.casting.show(asset.entry,client,it) } }
    }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf<String?>(null) }
    BackHandler { onClose() }
    if(confirmDelete) DeleteConfirmation(1,onDismiss={confirmDelete=false}) {
        confirmDelete=false
        model.deleteAssets(setOf(asset.entry.path)) { deleted -> if(asset.entry.path in deleted) { if(casting.connected) model.casting.stop();onClose() } }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state=pager,modifier=Modifier.fillMaxSize().safeDrawingPadding().padding(bottom=if(casting.connected) 240.dp else 80.dp),key={assets[it].entry.path}) { index ->
            val item=assets[index]
            if(item.entry.video) { if(index==pager.currentPage && !casting.connected) VideoPlayer(item,model) else if(casting.connected) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Icon(Icons.Outlined.CastConnected,stringResource(R.string.cast),Modifier.size(64.dp)) } }
            else {
                var zoom by remember(item.entry.path) { mutableFloatStateOf(1f) }
                var x by remember(item.entry.path) { mutableFloatStateOf(0f) };var y by remember(item.entry.path) { mutableFloatStateOf(0f) }
                var dimensions by remember { mutableStateOf(IntSize.Zero) }
                val transform=rememberTransformableState { change,pan,_ ->
                    zoom=(zoom*change).coerceIn(1f,5f)
                    x=if(zoom==1f) 0f else (x+pan.x).coerceIn(-dimensions.width*(zoom-1)/2,dimensions.width*(zoom-1)/2)
                    y=if(zoom==1f) 0f else (y+pan.y).coerceIn(-dimensions.height*(zoom-1)/2,dimensions.height*(zoom-1)/2)
                }
                Photo(item,model,Modifier.fillMaxSize().onSizeChanged { dimensions=it }.transformable(transform,canPan={zoom>1f}).graphicsLayer(scaleX=zoom,scaleY=zoom,translationX=x,translationY=y),ContentScale.Fit)
            }
        }
        IconButton(onClick=onClose,modifier=Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(8.dp).background(Color.Black.copy(alpha=.45f),RoundedCornerShape(50))) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack,stringResource(R.string.back_to_timeline),tint=Color.White)
        }
        Row(Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(8.dp).background(Color.Black.copy(alpha=.45f),RoundedCornerShape(50))) {
            val favorite=asset.entry.collectionId() in state.collections.favorites
            val archived=asset.entry.collectionId() in state.collections.archived
            IconToggleButton(checked=favorite,enabled=!state.busy && !sharing,onCheckedChange={model.favorite(listOf(asset),it)}) {
                Icon(if(favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,stringResource(if(favorite) R.string.unfavorite else R.string.favorite),tint=Color.White)
            }
            Box {
                IconButton(onClick={menu=true}) { Icon(Icons.Outlined.MoreVert,stringResource(R.string.more_options),tint=Color.White) }
                DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                    CastMenuItem(model.casting) { menu=false }
                    DropdownMenuItem(text={Text(stringResource(R.string.add_to_album))},leadingIcon={Icon(Icons.Outlined.AddPhotoAlternate,null)},enabled=!state.busy && !sharing,onClick={menu=false;addToAlbum=true})
                    DropdownMenuItem(text={Text(stringResource(if(archived) R.string.unarchive else R.string.archive))},leadingIcon={Icon(if(archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,null)},enabled=!state.busy && !sharing,onClick={menu=false;model.archive(listOf(asset),!archived)})
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).safeDrawingPadding().fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
            CastControls(model.casting)
            state.error?.let { ErrorCard(it,model::clearError) }
            shareError?.let { ErrorCard(it) { shareError=null } }
            Row(Modifier.padding(horizontal=24.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(enabled=!sharing && !state.busy,onClick={
                    val selectedAsset=asset
                    sharing=true;shareError=null
                    scope.launch {
                        try {
                            val intent=ShareFiles.prepare(context,requireNotNull(model.driveClient),selectedAsset.entry)
                            context.startActivity(Intent.createChooser(intent,context.getString(R.string.share)))
                        } catch(e: CancellationException) { throw e }
                        catch(e: Exception) { shareError=e.message ?: context.getString(R.string.share_failed) }
                        finally { sharing=false }
                    }
                }) {
                    if(sharing) CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)
                    else Icon(Icons.Outlined.Share,null)
                    Spacer(Modifier.width(8.dp));Text(if(sharing) stringResource(R.string.preparing) else context.getString(R.string.share))
                }
                TextButton(enabled=!sharing && !state.busy && !state.scanning,onClick={model.clearError();confirmDelete=true}) {
                    Icon(Icons.Outlined.Delete,null);Spacer(Modifier.width(8.dp));Text(stringResource(R.string.delete))
                }
                TextButton(onClick={details=true}) {
                    Icon(Icons.Outlined.Info,null);Spacer(Modifier.width(8.dp));Text(stringResource(R.string.info))
                }
            }
        }
    }
    if(addToAlbum) AddToAlbumDialog(state,model,listOf(asset)) { addToAlbum=false }
    if(details) PhotoInfoSheet(asset,model) { details=false }
}
