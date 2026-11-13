package app.inphotomaniak

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable internal fun PaletteSettings(s: GalleryState, model: GalleryModel) {
    var choosing by remember { mutableStateOf(false) }
    Text(stringResource(R.string.color_palette),style=MaterialTheme.typography.titleMedium)
    ColorPalette.entries.chunked(2).forEach { row -> Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        row.forEach { palette -> FilterChip(selected=s.colorPalette==palette,enabled=!s.busy,
            onClick={if(palette==ColorPalette.CUSTOM) choosing=true else model.colorPalette(palette)},
            label={Text(stringResource(palette.labelRes))},leadingIcon={
                if(palette!=ColorPalette.WALLPAPER) Box(Modifier.size(16.dp).clip(CircleShape).background(seedColor(if(palette==ColorPalette.CUSTOM) s.customColor else palette.seed)))
            }) }
    } }
    Text(stringResource(R.string.palette_help),style=MaterialTheme.typography.bodySmall)
    if(choosing) CustomColorDialog(s.customColor,onDismiss={choosing=false}) { model.colorPalette(ColorPalette.CUSTOM,it);choosing=false }
}
@Composable private fun CustomColorDialog(initial: String,onDismiss: ()->Unit,onSave: (String)->Unit) {
    var value by remember { mutableStateOf("#${initial}") }
    val normalized=normalizeColor(value)
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.palette_custom))},text={ Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(44.dp).background(seedColor(normalized ?: initial)))
        Box(Modifier.fillMaxWidth().height(12.dp).background(Brush.horizontalGradient((0..6).map { Color.hsv(it*60f,.75f,.8f) })))
        Slider(value=colorHue(normalized ?: initial),valueRange=0f..360f,onValueChange={ hue -> value="#%06X".format(Color.hsv(hue,.75f,.8f).toArgb() and 0xFFFFFF) })
        OutlinedTextField(value=value,onValueChange={if(it.length<=7) value=it},singleLine=true,label={Text(stringResource(R.string.color_hex))},isError=normalized==null)
        Text(stringResource(R.string.custom_color_help),style=MaterialTheme.typography.bodySmall)
    } },confirmButton={TextButton(enabled=normalized!=null,onClick={normalized?.let(onSave)}) { Text(stringResource(R.string.save)) }},
        dismissButton={TextButton(onClick=onDismiss) { Text(stringResource(R.string.cancel)) }})
}
