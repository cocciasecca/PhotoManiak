package app.inphotomaniak

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import java.util.Locale

class MainActivity: ComponentActivity() {
    override fun attachBaseContext(base: Context) {
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.ENGLISH) }
        super.attachBaseContext(base.createConfigurationContext(config))
    }
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
