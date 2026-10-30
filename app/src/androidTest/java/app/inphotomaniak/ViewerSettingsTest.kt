package app.inphotomaniak

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
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
    private fun library() = GalleryState(ready=true,account=Account("123","fixture-not-used"),assets=(0 until 200).map { i ->
        Asset(RemoteEntry("/${i+2}~photo$i.jpg","photo$i.jpg",false,"image/jpeg",1,"fixture"),CaptureDate(1,"2024-01-01","EXIF"),true)
    })

    @Test fun returnFromViewerPreservesTimelinePositionAfterRecreation() {
        val restoration=StateRestorationTester(compose)
        val state=library()
        restoration.setContent { MaterialTheme(colorScheme=darkColorScheme()) { GalleryContent(state,model) } }
        val grid=compose.onNodeWithTag("timeline_grid")
        grid.performScrollToIndex(100)
        val target=compose.onNodeWithTag("media:/102~photo100.jpg")
        target.assertIsDisplayed()
        val before=target.fetchSemanticsNode().boundsInRoot.top
        target.performClick()
        compose.onNodeWithContentDescription("Back to timeline").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription("Back to timeline").performClick()
        target.assertIsDisplayed()
        assertEquals(before,target.fetchSemanticsNode().boundsInRoot.top,1f)
    }

    @Test fun selectionRequiresExplicitConfirmationAndCancelKeepsFiles() {
        val state=library()
        compose.setContent { MaterialTheme(colorScheme=darkColorScheme()) { GalleryContent(state,model) } }
        compose.onNodeWithContentDescription("Select photos").performClick()
        compose.onNodeWithText("0 selected").assertIsDisplayed()
        compose.onNodeWithText("Delete").assertIsNotEnabled()
        compose.onNodeWithTag("media:/2~photo0.jpg").performClick()
        compose.onNodeWithTag("media:/3~photo1.jpg").performClick()
        compose.onNodeWithText("2 selected").assertIsDisplayed()
        compose.onNodeWithText("Share").assertIsEnabled()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Move 2 item(s) to trash?").assertIsDisplayed()
        compose.onNodeWithText("Move to trash").assertIsDisplayed()
        compose.onAllNodesWithText("Cancel").onLast().performClick()
        compose.onNodeWithText("Move to trash").assertDoesNotExist()
        compose.onNodeWithTag("media:/2~photo0.jpg").assertIsSelected()
        compose.onNodeWithTag("media:/3~photo1.jpg").assertIsSelected()
    }

}
