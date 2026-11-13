package app.inphotomaniak

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

internal val DarkColors = darkColorScheme(
    primary=Color(0xFFC4D9AA), onPrimary=Color(0xFF24351D),
    primaryContainer=Color(0xFF3A5031), onPrimaryContainer=Color(0xFFE0F5C6),
    secondary=Color(0xFFE9CCA0), onSecondary=Color(0xFF3F2E13),
    secondaryContainer=Color(0xFF574326), onSecondaryContainer=Color(0xFFFFDEAD),
    background=Color(0xFF101713), onBackground=Color(0xFFF0F2E9),
    surface=Color(0xFF17211B), onSurface=Color(0xFFF0F2E9),
    surfaceVariant=Color(0xFF28362D), onSurfaceVariant=Color(0xFFC4CEC3),
    surfaceContainerLowest=Color(0xFF0C130F), surfaceContainerLow=Color(0xFF17211B),
    surfaceContainer=Color(0xFF1C261F), surfaceContainerHigh=Color(0xFF263129),
    surfaceContainerHighest=Color(0xFF313C34), surfaceTint=Color(0xFFC4D9AA)
)
internal val LightColors = lightColorScheme(
    primary=Color(0xFF49643B), onPrimary=Color.White,
    primaryContainer=Color(0xFFD0E9B9), onPrimaryContainer=Color(0xFF14250C),
    secondary=Color(0xFF745A32), onSecondary=Color.White,
    secondaryContainer=Color(0xFFFFDEAD), onSecondaryContainer=Color(0xFF281800),
    background=Color(0xFFF7FAF2), onBackground=Color(0xFF182019),
    surface=Color(0xFFF7FAF2), onSurface=Color(0xFF182019),
    surfaceVariant=Color(0xFFE0E8DC), onSurfaceVariant=Color(0xFF424C40),
    surfaceContainerLowest=Color.White, surfaceContainerLow=Color(0xFFF1F5EB),
    surfaceContainer=Color(0xFFEBEFE5), surfaceContainerHigh=Color(0xFFE5E9DF),
    surfaceContainerHighest=Color(0xFFDFE4DA), surfaceTint=Color(0xFF49643B)
)

internal fun Context.activity(): ComponentActivity? = when(this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
internal fun ComponentActivity.themeSystemBars(dark: Boolean) {
    val transparent=android.graphics.Color.TRANSPARENT
    val style=if(dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent,transparent)
    enableEdgeToEdge(statusBarStyle=style,navigationBarStyle=style)
}

@Composable internal fun PhotoManiakTheme(mode: ThemeMode,palette: ColorPalette = ColorPalette.FOREST,customColor: String = DEFAULT_CUSTOM_COLOR,content: @Composable () -> Unit) {
    val dark=mode.isDark(isSystemInDarkTheme())
    val activity=LocalContext.current.activity()
    DisposableEffect(activity,dark) {
        activity?.themeSystemBars(dark)
        onDispose { }
    }
    MaterialTheme(colorScheme=paletteColors(dark,palette,customColor),content=content)
}
