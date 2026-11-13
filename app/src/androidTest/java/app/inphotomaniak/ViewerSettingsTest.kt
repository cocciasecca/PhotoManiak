package app.inphotomaniak

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
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
        CollectionDb(context).use { it.clear() }
        model=GalleryModel(context.applicationContext as Application)
        store.put("gallery",model)
        compose.waitUntil(5000) { model.state.value.ready }
    }
    @After fun cleanup() { store.clear();Settings(context).clear();CollectionDb(context).use { it.clear() } }

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

    @Test fun favoriteAndArchiveControlsPersistWithoutNetwork() {
        val asset=library().assets.first()
        compose.setContent { PhotoManiakTheme(ThemeMode.LIGHT) { Viewer(listOf(asset),0,model) {} } }
        compose.onNodeWithContentDescription("Favorite").performClick()
        compose.waitUntil(5000) { !model.state.value.busy && "2" in model.state.value.collections.favorites }
        compose.onNodeWithContentDescription("Remove favorite").assertIsDisplayed()
        compose.onNodeWithContentDescription("Archive").performClick()
        compose.waitUntil(5000) { !model.state.value.busy && "2" in model.state.value.collections.archived }
        compose.onNodeWithContentDescription("Unarchive").assertIsDisplayed()
        CollectionDb(context).use { assertEquals(setOf("2"),it.snapshot().favorites);assertEquals(setOf("2"),it.snapshot().archived) }
    }

    @Test fun virtualAlbumCanBeCreatedFromViewerThenRenamedAndDeletedWithoutRemovingMedia() {
        val asset=library().assets.first()
        val showCollections=mutableStateOf(false)
        compose.setContent {
            val state by model.state.collectAsState()
            PhotoManiakTheme(ThemeMode.LIGHT) {
                if(showCollections.value) CollectionsScreen(state.copy(assets=listOf(asset)),model)
                else Viewer(listOf(asset),0,model) {}
            }
        }
        compose.onNodeWithContentDescription("Add to album").performClick()
        compose.onNodeWithText("New album").performClick()
        compose.onNodeWithText("Album name").performTextInput("Holiday")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5000) { !model.state.value.busy && model.state.value.collections.albums.size==1 }
        assertEquals(setOf("2"),model.state.value.collections.albums.single().members)
        compose.runOnIdle { showCollections.value=true }
        compose.onNodeWithText("Holiday").assertIsDisplayed()
        compose.onNodeWithContentDescription("Rename album").performClick()
        compose.onNodeWithText("Album name").performTextReplacement("Summer")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5000) { !model.state.value.busy && model.state.value.collections.albums.single().name=="Summer" }
        compose.onNodeWithContentDescription("Delete album").performClick()
        compose.onNodeWithText("Only the virtual album will be deleted. The original photos and videos stay on kDrive.").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1,model.state.value.collections.albums.size)
        compose.onNodeWithContentDescription("Delete album").performClick()
        compose.onNodeWithText("Delete album").performClick()
        compose.waitUntil(5000) { !model.state.value.busy && model.state.value.collections.albums.isEmpty() }
    }

    @Test fun castWithoutGooglePlayServicesExplainsRequirementWithoutBreakingViewer() {
        if(com.google.android.gms.common.GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)==com.google.android.gms.common.ConnectionResult.SUCCESS) return
        val asset=library().assets.first()
        compose.setContent { PhotoManiakTheme(ThemeMode.LIGHT) { Viewer(listOf(asset),0,model) {} } }
        compose.onNodeWithContentDescription("Cast").performClick()
        compose.onNodeWithText("Google Cast requires Google Play services on this phone. The rest of PhotoManiak works without them.").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Share").assertIsDisplayed()
        compose.onNodeWithText("Info").assertIsDisplayed()
    }

    @Test fun appearanceFollowsSystemUnlessExplicitlyOverridden() {
        val night=mutableStateOf(false)
        var background=Color.Unspecified
        compose.setContent {
            val state by model.state.collectAsState()
            val config=Configuration(LocalConfiguration.current).apply {
                uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if(night.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                PhotoManiakTheme(state.themeMode) {
                    val colors=MaterialTheme.colorScheme
                    SideEffect { background=colors.background }
                    SettingsScreen(state,model)
                }
            }
        }
        compose.onNodeWithText("System").assertIsSelected()
        compose.runOnIdle { assertEquals(LightColors.background,background);night.value=true }
        compose.runOnIdle { assertEquals(DarkColors.background,background) }
        compose.onNodeWithText("Light").performClick()
        compose.waitUntil(5000) { model.state.value.themeMode==ThemeMode.LIGHT && !model.state.value.busy }
        compose.runOnIdle { assertEquals(LightColors.background,background) }
        assertEquals(ThemeMode.LIGHT,Settings(context).themeMode())
        compose.onNodeWithText("Dark").performClick()
        compose.waitUntil(5000) { model.state.value.themeMode==ThemeMode.DARK && !model.state.value.busy }
        compose.runOnIdle { night.value=false }
        compose.runOnIdle { assertEquals(DarkColors.background,background) }
        compose.onNodeWithText("System").performClick()
        compose.waitUntil(5000) { model.state.value.themeMode==ThemeMode.SYSTEM && !model.state.value.busy }
        compose.runOnIdle { assertEquals(LightColors.background,background) }
    }

}
