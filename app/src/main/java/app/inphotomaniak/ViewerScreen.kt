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
    val pager=rememberPagerState(initialPage=initial) { assets.size }
    var details by remember { mutableStateOf(false) }
    val asset=assets[pager.currentPage]
    val state by model.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf<String?>(null) }
    BackHandler { onClose() }
    if(confirmDelete) DeleteConfirmation(1,onDismiss={confirmDelete=false}) {
        confirmDelete=false
        model.deleteAssets(setOf(asset.entry.path)) { deleted -> if(asset.entry.path in deleted) onClose() }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state=pager,modifier=Modifier.fillMaxSize().safeDrawingPadding().padding(bottom=72.dp),key={assets[it].entry.path}) { index ->
            val item=assets[index]
            if(item.entry.video) { if(index==pager.currentPage) VideoPlayer(item,model) }
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
        Column(Modifier.align(Alignment.BottomCenter).safeDrawingPadding().fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
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
    if(details) AlertDialog(onDismissRequest={details=false},title={Text(asset.entry.name)},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {Text(dayLabel(asset.date.day));Text(stringResource(if(asset.metadataRead) R.string.date_source else R.string.date_source_provisional,dateSourceLabel(asset.date.source)));Text(DriveClient.displayPath(asset.entry.path))}},confirmButton={TextButton(onClick={details=false}){Text(stringResource(R.string.close))}})
}
