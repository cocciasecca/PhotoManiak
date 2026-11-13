package app.inphotomaniak

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import kotlin.math.abs

const val DEFAULT_CUSTOM_COLOR = "49643B"
enum class ColorPalette(val labelRes: Int, val seed: String) {
    FOREST(R.string.palette_forest,"49643B"), BLUE(R.string.palette_blue,"3565A8"),
    PURPLE(R.string.palette_purple,"8050A0"), ORANGE(R.string.palette_orange,"B96327"),
    WALLPAPER(R.string.palette_wallpaper,"49643B"), CUSTOM(R.string.palette_custom,"49643B")
}
fun normalizeColor(value: String): String? = value.trim().removePrefix("#").uppercase(java.util.Locale.ROOT).takeIf { it.matches(Regex("[0-9A-F]{6}")) }
internal fun seedColor(hex: String) = Color(0xFF000000L or (normalizeColor(hex) ?: DEFAULT_CUSTOM_COLOR).toLong(16))
internal fun colorHue(hex: String): Float {
    val c=seedColor(hex);val max=maxOf(c.red,c.green,c.blue);val min=minOf(c.red,c.green,c.blue);val delta=max-min
    if(delta==0f) return 0f
    return ((when(max) { c.red -> (c.green-c.blue)/delta; c.green -> (c.blue-c.red)/delta+2; else -> (c.red-c.green)/delta+4 }*60+360)%360)
}
private fun foreground(color: Color): Color = if(color.luminance()>0.179f) Color.Black else Color.White
internal fun seededColors(hex: String,dark: Boolean): ColorScheme {
    val seed=seedColor(hex);val max=maxOf(seed.red,seed.green,seed.blue);val min=minOf(seed.red,seed.green,seed.blue)
    val light=(max+min)/2;val delta=max-min
    val saturation=if(delta==0f) 0f else (delta/(1-abs(2*light-1))).coerceIn(0f,0.75f)
    val hue=colorHue(hex)
    fun tone(l: Float,s: Float=saturation,h: Float=hue)=Color.hsl(h,s,l)
    val primary=tone(if(dark) .78f else .34f)
    val secondary=tone(if(dark) .78f else .34f,saturation*.65f,(hue+35)%360)
    val tertiary=tone(if(dark) .78f else .34f,saturation*.65f,(hue+70)%360)
    val container=tone(if(dark) .23f else .88f)
    val secondaryContainer=tone(if(dark) .23f else .88f,saturation*.65f,(hue+35)%360)
    val tertiaryContainer=tone(if(dark) .23f else .88f,saturation*.65f,(hue+70)%360)
    val neutral=saturation*.15f
    val background=tone(if(dark) .065f else .98f,neutral)
    val surface=tone(if(dark) .09f else .98f,neutral)
    val text=tone(if(dark) .94f else .10f,neutral)
    val variant=tone(if(dark) .18f else .90f,neutral)
    val base=if(dark) darkColorScheme() else lightColorScheme()
    return base.copy(primary=primary,onPrimary=foreground(primary),primaryContainer=container,onPrimaryContainer=foreground(container),
        secondary=secondary,onSecondary=foreground(secondary),secondaryContainer=secondaryContainer,onSecondaryContainer=foreground(secondaryContainer),
        tertiary=tertiary,onTertiary=foreground(tertiary),tertiaryContainer=tertiaryContainer,onTertiaryContainer=foreground(tertiaryContainer),
        background=background,onBackground=text,surface=surface,onSurface=text,surfaceVariant=variant,onSurfaceVariant=tone(if(dark) .80f else .28f,neutral),
        outline=tone(if(dark) .6f else .45f,neutral),outlineVariant=tone(if(dark) .3f else .75f,neutral),surfaceTint=primary,
        inverseSurface=text,inverseOnSurface=background,inversePrimary=tone(if(dark) .34f else .78f),
        surfaceContainerLowest=tone(if(dark) .04f else 1f,neutral),surfaceContainerLow=tone(if(dark) .09f else .96f,neutral),
        surfaceContainer=tone(if(dark) .12f else .94f,neutral),surfaceContainerHigh=tone(if(dark) .16f else .92f,neutral),surfaceContainerHighest=tone(if(dark) .20f else .90f,neutral))
}
@Composable internal fun paletteColors(dark: Boolean,palette: ColorPalette,customColor: String): ColorScheme = when {
    palette==ColorPalette.WALLPAPER && Build.VERSION.SDK_INT>=31 -> if(dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    palette==ColorPalette.FOREST || palette==ColorPalette.WALLPAPER -> if(dark) DarkColors else LightColors
    else -> seededColors(if(palette==ColorPalette.CUSTOM) customColor else palette.seed,dark)
}
