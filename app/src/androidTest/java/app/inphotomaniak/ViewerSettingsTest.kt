package app.inphotomaniak

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*

class ViewerSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = ViewModelStore()
    private lateinit var model: GalleryModel

    @Before fun setup() {
        Settings(context).clear()
        model=GalleryModel(context.applicationContext as Application)
        store.put("gallery",model)
        compose.waitUntil(5000) { model.state.value.ready }
    }
    @After fun cleanup() { store.clear();Settings(context).clear() }

    @Test fun settingsSelectionUpdatesAndPersists() {
        compose.setContent {
            val state by model.state.collectAsState()
            MaterialTheme(colorScheme=darkColorScheme()) { SettingsScreen(state,model) }
        }
        compose.onNodeWithText("Medium").assertIsSelected()
        compose.onNodeWithText("Large").performClick()
        compose.waitUntil(5000) { model.state.value.thumbnailSize==ThumbnailSize.LARGE && !model.state.value.busy }
        compose.onNodeWithText("Large").assertIsSelected()
        assertEquals(ThumbnailSize.LARGE,Settings(context).thumbnailSize())
        compose.onNodeWithText("Small").performClick()
        compose.waitUntil(5000) { model.state.value.thumbnailSize==ThumbnailSize.SMALL && !model.state.value.busy }
        compose.onNodeWithText("Small").assertIsSelected()
    }

    @Test fun viewerHasBottomActionsNoCounterAndEnglishLegacyInfo() {
        val asset=Asset(RemoteEntry("/2~photo.jpg","photo.jpg",false,"image/jpeg",1,"test"),CaptureDate(1,"2026-10-27","Nome file"),true)
        var closed=false
        compose.setContent { MaterialTheme(colorScheme=darkColorScheme()) { Viewer(listOf(asset),0,model) {closed=true} } }
        compose.onNodeWithText("Share").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("Info").assertIsDisplayed().performClick()
        compose.onNodeWithText("Date source: Filename").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("1 / 1").assertDoesNotExist()
        val back=compose.onNodeWithContentDescription("Back to timeline")
        val share=compose.onNodeWithText("Share")
        assertTrue(share.fetchSemanticsNode().boundsInRoot.top > back.fetchSemanticsNode().boundsInRoot.bottom)
        back.performClick()
        assertTrue(closed)
    }
}
