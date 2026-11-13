package app.inphotomaniak

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*

class PaletteTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val store=ViewModelStore()
    @After fun cleanup() { store.clear();Settings(context).clear() }

    @Test fun paletteAndCustomColorApplyPersistAndCancelDoesNotChangeThem() {
        Settings(context).clear()
        val model=GalleryModel(context.applicationContext as Application)
        store.put("palette",model)
        compose.waitUntil(5000) { model.state.value.ready }
        var primary=Color.Unspecified
        compose.setContent {
            val state by model.state.collectAsState()
            PhotoManiakTheme(ThemeMode.LIGHT,state.colorPalette,state.customColor) {
                val color=MaterialTheme.colorScheme.primary
                SideEffect { primary=color }
                Surface { SettingsScreen(state,model) }
            }
        }
        compose.onNodeWithText("Blue").performScrollTo().performClick()
        compose.waitUntil(5000) { model.state.value.colorPalette==ColorPalette.BLUE && !model.state.value.busy }
        compose.runOnIdle { assertEquals(seededColors(ColorPalette.BLUE.seed,false).primary,primary) }
        compose.onNodeWithText("Custom").performScrollTo().performClick()
        compose.onNodeWithText("Color (#RRGGBB)").performTextReplacement("#123")
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Color (#RRGGBB)").performTextReplacement("#cc3377")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5000) { model.state.value.colorPalette==ColorPalette.CUSTOM && !model.state.value.busy }
        compose.runOnIdle { assertEquals(seededColors("CC3377",false).primary,primary) }
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { image ->
            context.openFileOutput("palette-preview.png",0).use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        }
        assertEquals(ColorPalette.CUSTOM,Settings(context).colorPalette())
        assertEquals("CC3377",Settings(context).customColor())
        compose.onNodeWithText("Custom").performScrollTo().performClick()
        compose.onNodeWithText("Color (#RRGGBB)").performTextReplacement("#000000")
        compose.onNodeWithText("Cancel").performClick()
        assertEquals("CC3377",Settings(context).customColor())
        val restored=GalleryModel(context.applicationContext as Application)
        store.put("restored",restored)
        compose.waitUntil(5000) { restored.state.value.ready }
        assertEquals(ColorPalette.CUSTOM,restored.state.value.colorPalette)
        assertEquals("CC3377",restored.state.value.customColor)
    }

    @Test fun generatedPalettesKeepTextReadableAcrossHuesAndGrayscale() {
        fun contrast(a: Color,b: Color): Float {
            val x=a.luminance();val y=b.luminance()
            return (maxOf(x,y)+.05f)/(minOf(x,y)+.05f)
        }
        val seeds=(0..35).map { "%06X".format(Color.hsv(it*10f,.9f,.8f).toArgb() and 0xFFFFFF) }+listOf("000000","FFFFFF","808080")
        for(seed in seeds) for(dark in listOf(false,true)) {
            val c=seededColors(seed,dark)
            for((background,text) in listOf(c.primary to c.onPrimary,c.primaryContainer to c.onPrimaryContainer,c.secondary to c.onSecondary,c.tertiary to c.onTertiary,c.background to c.onBackground,c.surface to c.onSurface,c.surfaceVariant to c.onSurfaceVariant)) {
                assertTrue("Contrast for $seed, dark=$dark: ${contrast(background,text)}",contrast(background,text)>=4.5f)
            }
        }
    }

    @Test fun malformedStoredColorsFallBackSafely() {
        context.getSharedPreferences("settings",0).edit().putString("color_palette","future_value").putString("custom_color","invalid").commit()
        assertEquals(ColorPalette.FOREST,Settings(context).colorPalette())
        assertEquals(DEFAULT_CUSTOM_COLOR,Settings(context).customColor())
        assertNull(normalizeColor("#123"))
        assertEquals("ABCDEF",normalizeColor(" #abcdef "))
    }
}
