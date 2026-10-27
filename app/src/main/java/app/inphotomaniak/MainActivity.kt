package app.inphotomaniak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFFC4D9AA), onPrimary=Color(0xFF24351D), secondary=Color(0xFFE9CCA0), background=Color(0xFF101713), surface=Color(0xFF17211B), surfaceVariant=Color(0xFF28362D), onSurface=Color(0xFFF0F2E9))) {
                Surface(Modifier.fillMaxSize()) { GalleryApp() }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun GalleryApp(model: GalleryModel = viewModel()) {
    val s by model.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    if(!s.ready) { Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator() }; return }
    if(s.account==null) { Login(s,model); return }
    val current = selected
    // Freeze the ordering during a viewing session while indexing updates the timeline.
    val viewerAssets = remember(current) { s.assets }
    if(current != null && viewerAssets.any { it.entry.path==current }) {
        Viewer(viewerAssets,viewerAssets.indexOfFirst { it.entry.path==current },model) { selected=null }; return
    }
    Scaffold(
        topBar={ TopAppBar(title={ Column { Text("inPho(to)Maniak",fontWeight=FontWeight.Bold); Text("Il tuo archivio, da riscoprire",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary) } }, actions={
            if(s.scanning) IconButton(onClick=model::stop) { Icon(Icons.Outlined.Stop,"Interrompi scansione") }
            else IconButton(onClick=model::refresh,enabled=s.rules.included.isNotEmpty() && !s.busy) { Icon(Icons.Outlined.Refresh,"Aggiorna libreria") }
        }) },
        bottomBar={ NavigationBar {
            listOf(Triple("timeline","Foto",Icons.Outlined.PhotoLibrary),Triple("folders","Cartelle",Icons.Outlined.Folder),Triple("settings","Account",Icons.Outlined.PersonOutline)).forEach { (key,label,icon) ->
                NavigationBarItem(selected=s.screen==key,onClick={model.screen(key)},icon={Icon(icon,label)},label={Text(label)})
            }
        } }
    ) { padding -> Column(Modifier.padding(padding).fillMaxSize()) {
        if(s.busy || s.scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(s.progress.isNotBlank()) Text(s.progress,Modifier.padding(horizontal=20.dp,vertical=6.dp),style=MaterialTheme.typography.labelMedium)
        s.error?.let { ErrorCard(it,model::clearError) }
        when(s.screen) {
            "folders" -> FolderScreen(s,model)
            "settings" -> SettingsScreen(s,model)
            else -> Timeline(s,model) { selected=it.entry.path }
        }
    } }
}
@Composable private fun Login(s: GalleryState,model: GalleryModel) {
    var id by rememberSaveable { mutableStateOf("") }
    // Token deliberately excluded from saved instance state.
    var token by remember { mutableStateOf("") }
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Spacer(Modifier.height(28.dp))
        Icon(Icons.Outlined.FilterVintage,null,Modifier.size(58.dp),tint=MaterialTheme.colorScheme.primary)
        Text("I tuoi ricordi.\nIl tuo kDrive.",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
        Text("Una nuova prospettiva sulle tue foto e sui tuoi video. Scegli le cartelle e ritrova ogni momento nella sua data.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(id,{id=it},Modifier.fillMaxWidth(),label={Text("ID kDrive")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,enabled=!s.busy)
        OutlinedTextField(token,{token=it},Modifier.fillMaxWidth(),label={Text("Token API Infomaniak")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),singleLine=true,enabled=!s.busy)
        Text("Trovi l’ID nell’indirizzo di kDrive, dopo /drive/. Genera un token con il permesso drive e incollalo qui. La password dell’account non serve.",style=MaterialTheme.typography.bodySmall)
        s.error?.let { ErrorCard(it,model::clearError) }
        Button(onClick={model.login(id,token)},Modifier.fillMaxWidth().height(54.dp),enabled=!s.busy && id.isNotBlank() && token.isNotBlank()) {
            if(s.busy) CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp) else Text("Collega il tuo kDrive")
        }
        TextButton(onClick={uri.openUri("https://manager.infomaniak.com/v3/ng/accounts/token/list")}) { Text("Genera il token su Infomaniak") }
        Text("I file restano nel tuo cloud. L’app legge foto e video e conserva una cache privata sul dispositivo.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun ErrorCard(message: String,dismiss: () -> Unit) {
    Surface(color=MaterialTheme.colorScheme.errorContainer,shape=RoundedCornerShape(16.dp),modifier=Modifier.padding(12.dp).fillMaxWidth()) {
        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) { Text(message,Modifier.weight(1f),color=MaterialTheme.colorScheme.onErrorContainer); IconButton(onClick=dismiss) { Icon(Icons.Outlined.Close,"Chiudi messaggio") } }
    }
}
@Composable private fun FolderScreen(s: GalleryState,model: GalleryModel) {
    val enabled = !s.busy && !s.scanning
    BackHandler(s.path!="/") { model.browse(parent(s.path)) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal=20.dp,vertical=12.dp)) {
            Text("Scegli cosa ritrovare",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            Text("Le cartelle incluse comprendono tutti i livelli sottostanti. Puoi escludere singole sottocartelle.",style=MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={model.browse(parent(s.path))},enabled=s.path!="/" && !s.busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Cartella superiore") }
                Text(DriveClient.displayPath(s.path),Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
            }
            RuleButtons(s.path,s.rules,enabled,model)
        }
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(s.folders.isEmpty() && !s.busy) item { Text("Nessuna sottocartella",Modifier.padding(16.dp),color=MaterialTheme.colorScheme.onSurfaceVariant) }
            items(s.folders,key={it.path}) { folder ->
                Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth().clickable(enabled=!s.busy){model.browse(folder.path)}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Folder,null,tint=MaterialTheme.colorScheme.secondary)
                            Text(folder.name,Modifier.weight(1f).padding(horizontal=12.dp),fontWeight=FontWeight.Medium)
                            Icon(Icons.Outlined.ChevronRight,"Apri ${folder.name}")
                        }
                        RuleButtons(folder.path,s.rules,enabled,model)
                    }
                }
            }
        }
        Button(onClick={model.screen("timeline");model.refresh()},enabled=enabled && s.rules.included.isNotEmpty(),modifier=Modifier.fillMaxWidth().padding(16.dp)) { Text("Mostra la mia libreria") }
    }
}
private fun parent(path: String) = path.trimEnd('/').substringBeforeLast('/',"").ifEmpty { "" } + "/"
@Composable private fun RuleButtons(path: String,rules: FolderRules,enabled: Boolean,model: GalleryModel) {
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        FilterChip(selected=path in rules.included,onClick={model.rule(path,if(path in rules.included) 0 else 1)},enabled=enabled,label={Text("Includi")})
        FilterChip(selected=path in rules.excluded,onClick={model.rule(path,if(path in rules.excluded) 0 else 2)},enabled=enabled,label={Text("Escludi")})
        if(path !in rules.included && path !in rules.excluded) Text(if(rules.excluded(path)) "Esclusa" else if(rules.accepts(path)) "Inclusa" else "",Modifier.align(Alignment.CenterVertically),style=MaterialTheme.typography.labelSmall)
    }
}
@Composable private fun Timeline(s: GalleryState,model: GalleryModel,onOpen: (Asset)->Unit) {
    if(s.assets.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(32.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.PhotoLibrary,null,Modifier.size(64.dp),tint=MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(20.dp));Text("Spazio ai tuoi ricordi",style=MaterialTheme.typography.headlineSmall)
            Text(if(s.scanning) "Sto esplorando le cartelle che hai scelto…" else if(s.rules.included.isEmpty()) "Scegli le cartelle kDrive da riunire nella tua timeline." else "Premi Aggiorna per cercare foto e video nelle cartelle selezionate.",Modifier.padding(vertical=12.dp))
            if(s.rules.included.isEmpty()) Button(onClick={model.screen("folders")}) { Text("Scegli le cartelle") }
        };return
    }
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val groups = remember(s.assets) { s.assets.groupBy { it.date.day }.toSortedMap(reverseOrder()) }
    val months = remember(groups) {
        var index=1
        buildList { var previous="";groups.forEach { (day,items) -> val month=day.take(7);if(month!=previous) {add(month to index);previous=month};index+=1+items.size } }
    }
    var showMonths by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("La tua timeline",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Box {
                TextButton(onClick={showMonths=true}) {
                    Icon(Icons.Outlined.CalendarMonth,null)
                    Text("Vai a")
                }
                DropdownMenu(expanded=showMonths,onDismissRequest={showMonths=false}) {
                    months.forEach { (month,index) ->
                        DropdownMenuItem(
                            text={Text(month.ifEmpty { "Senza data" })},
                            onClick={showMonths=false;scope.launch { grid.scrollToItem(index) }}
                        )
                    }
                }
            }
        }
        LazyVerticalGrid(columns=GridCells.Adaptive(112.dp),state=grid,contentPadding=PaddingValues(3.dp),horizontalArrangement=Arrangement.spacedBy(3.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            item(span={GridItemSpan(maxLineSpan)},key="count") { Text("${s.assets.size} ricordi · dal tuo kDrive",Modifier.padding(horizontal=17.dp,vertical=8.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            groups.forEach { (day,assets) ->
                item(span={GridItemSpan(maxLineSpan)},key="day:$day") { Text(dayLabel(day),Modifier.padding(start=17.dp,top=22.dp,bottom=10.dp),style=MaterialTheme.typography.titleSmall) }
                items(assets,key={it.entry.path}) { asset ->
                    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { onOpen(asset) }) {
                        Photo(asset,model,Modifier.fillMaxSize(),ContentScale.Crop)
                        if(asset.entry.video) Icon(Icons.Outlined.PlayCircle,"Video",Modifier.align(Alignment.BottomEnd).padding(6.dp),tint=Color.White)
                        if(!asset.metadataRead) Icon(Icons.Outlined.Schedule,"Data provvisoria",Modifier.align(Alignment.TopEnd).padding(6.dp).size(16.dp),tint=Color.White)
                    }
                }
            }
        }
    }
}
private fun dayLabel(day: String) = runCatching { LocalDate.parse(day).format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy",Locale.getDefault())) }.getOrDefault("Data sconosciuta")
@Composable private fun Photo(asset: Asset,model: GalleryModel,modifier: Modifier,scale: ContentScale) {
    val context=LocalContext.current
    var failed by remember(asset.entry.path) { mutableStateOf(false) }
    val loader=model.images ?: return
    val thumbnail = scale == ContentScale.Crop
    val imageUrl = if(thumbnail) model.dav!!.thumbnail(asset.entry.path) else model.dav!!.url(asset.entry.path)
    val cacheKey = "${model.dav!!.account.driveId}:${asset.entry.path}:${asset.entry.etag}:${asset.entry.modified}:$thumbnail"
    Box(modifier,contentAlignment=Alignment.Center) {
        Icon(Icons.Outlined.Image,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
        AsyncImage(model=ImageRequest.Builder(context).data(imageUrl.toString()).memoryCacheKey(cacheKey).diskCacheKey(cacheKey).crossfade(true).build(),imageLoader=loader,contentDescription=asset.entry.name,modifier=Modifier.fillMaxSize(),contentScale=scale,onError={failed=true})
        if(failed) Text("Anteprima non disponibile",Modifier.background(Color.Black.copy(alpha=.6f)).padding(4.dp),style=MaterialTheme.typography.labelSmall)
    }
}
@Composable private fun SettingsScreen(s: GalleryState,model: GalleryModel) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Text("Il tuo spazio",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
        Text("Accesso con token personale");Text("kDrive ${s.account?.driveId}",color=MaterialTheme.colorScheme.primary)
        HorizontalDivider()
        Text("${s.rules.included.size} cartelle incluse · ${s.rules.excluded.size} escluse")
        Text("Cache immagini e miniature fino a 256 MB. L’indice delle date resta sul dispositivo.")
        Text("La prima scansione legge i metadati via rete. Le date provvisorie vengono riprovate al prossimo aggiornamento. La lettura EXIF/XMP segue le priorità di Immich per i tag supportati; alcuni formati proprietari possono richiedere il fallback.",style=MaterialTheme.typography.bodyMedium)
        Text("inPho(to)Maniak · 0.2.0\nApp indipendente, non affiliata a Infomaniak.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={confirm=true},enabled=!s.busy && !s.scanning) { Text("Scollega account e svuota dati locali") }
    }
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("Scollegare questo account?")},text={Text("Credenziali, selezione delle cartelle, indice e cache verranno rimossi da questo dispositivo. Potrai collegare un altro account.")},confirmButton={TextButton(onClick={confirm=false;model.logout()}) { Text("Scollega") }},dismissButton={TextButton(onClick={confirm=false}) { Text("Annulla") }})
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun Viewer(assets: List<Asset>,initial: Int,model: GalleryModel,onClose: ()->Unit) {
    val pager=rememberPagerState(initialPage=initial) { assets.size }
    var details by remember { mutableStateOf(false) }
    val asset=assets[pager.currentPage]
    BackHandler { onClose() }
    Scaffold(containerColor=Color.Black,topBar={TopAppBar(title={Text("${pager.currentPage+1} / ${assets.size}")},navigationIcon={IconButton(onClick=onClose){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Torna alla timeline")}},actions={IconButton(onClick={details=true}){Icon(Icons.Outlined.Info,"Dettagli")}})}) { padding ->
        HorizontalPager(state=pager,modifier=Modifier.padding(padding).fillMaxSize(),key={assets[it].entry.path}) { index ->
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
    }
    if(details) AlertDialog(onDismissRequest={details=false},title={Text(asset.entry.name)},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {Text(dayLabel(asset.date.day));Text("Data da: ${asset.date.source}${if(!asset.metadataRead) " (provvisoria)" else ""}");Text(DriveClient.displayPath(asset.entry.path))}},confirmButton={TextButton(onClick={details=false}){Text("Chiudi")}})
}
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
@Composable private fun VideoPlayer(asset: Asset,model: GalleryModel) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val player=remember(asset.entry.path) {
        val factory=OkHttpDataSource.Factory(model.dav!!.http)
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(factory)).build().apply { setMediaItem(MediaItem.fromUri(model.dav!!.url(asset.entry.path).toString()));prepare() }
    }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(player,lifecycle) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_STOP) player.pause() }
        val listener=object: androidx.media3.common.Player.Listener { override fun onPlayerError(e: androidx.media3.common.PlaybackException) { error="Video non riproducibile: verifica la connessione o il formato del file." } }
        lifecycle.addObserver(observer);player.addListener(listener)
        onDispose { lifecycle.removeObserver(observer);player.removeListener(listener);player.release() }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory={PlayerView(it).apply { this.player=player }},modifier=Modifier.fillMaxSize())
        error?.let { Text(it,Modifier.align(Alignment.Center).background(Color.Black).padding(24.dp)) }
    }
}
