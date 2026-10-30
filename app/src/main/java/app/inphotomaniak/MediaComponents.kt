package app.inphotomaniak

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable internal fun dayLabel(day: String): String {
    val unknownDate = stringResource(R.string.unknown_date)
    return runCatching { LocalDate.parse(day).format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy",Locale.ENGLISH)) }.getOrDefault(unknownDate)
}
@Composable internal fun Photo(asset: Asset,model: GalleryModel,modifier: Modifier,scale: ContentScale) {
    val context=LocalContext.current
    var failed by remember(asset.entry.path) { mutableStateOf(false) }
    val loader=model.images ?: return
    val thumbnail = scale == ContentScale.Crop
    val imageUrl = if(thumbnail) model.driveClient!!.thumbnail(asset.entry.path) else model.driveClient!!.url(asset.entry.path)
    val cacheKey = "${model.driveClient!!.account.driveId}:${asset.entry.path}:${asset.entry.etag}:${asset.entry.modified}:$thumbnail"
    Box(modifier,contentAlignment=Alignment.Center) {
        Icon(Icons.Outlined.Image,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
        AsyncImage(model=ImageRequest.Builder(context).data(imageUrl.toString()).memoryCacheKey(cacheKey).diskCacheKey(cacheKey).crossfade(true).build(),imageLoader=loader,contentDescription=asset.entry.name,modifier=Modifier.fillMaxSize(),contentScale=scale,onError={failed=true})
        if(failed) Text(stringResource(R.string.preview_unavailable),Modifier.background(Color.Black.copy(alpha=.6f)).padding(4.dp),style=MaterialTheme.typography.labelSmall)
    }
}

@Composable internal fun dateSourceLabel(source: String): String = when(source) {
    "Nome file", "Filename" -> stringResource(R.string.source_filename)
    "Modifica remota", "Remote modification" -> stringResource(R.string.source_remote)
    "Data sconosciuta", "Unknown date" -> stringResource(R.string.unknown_date)
    else -> source
}
