package app.inphotomaniak

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*

class SlideshowDeviceTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val store=ViewModelStore()
    private lateinit var model: GalleryModel
    private val assets=(0..2).map { index -> Asset(RemoteEntry("/${index+2}~item$index","item$index",false,if(index==1) "video/mp4" else "image/jpeg",1,"fixture"),CaptureDate(1,"2026-11-14","Filename"),true) }
    @Before fun setup() {
        Settings(context).clear();model=GalleryModel(context.applicationContext as Application);store.put("slideshow",model)
        compose.waitUntil(5000) { model.state.value.ready }
    }
    @After fun cleanup() { store.clear();Settings(context).clear() }
    private fun shown(path: String)=compose.onAllNodesWithTag("slide:$path").fetchSemanticsNodes().isNotEmpty()

    @Test fun timerSkipsVideosAndPausesInBackgroundUntilResumed() {
        val owner=object: LifecycleOwner { override val lifecycle=LifecycleRegistry(this) }
        compose.runOnIdle { owner.lifecycle.currentState=Lifecycle.State.RESUMED }
        compose.setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) { PhotoManiakTheme(ThemeMode.DARK) { Slideshow(assets,0,1,model) {} } } }
        compose.waitUntil(5000) { shown(assets[2].entry.path) }
        compose.runOnIdle { owner.lifecycle.currentState=Lifecycle.State.STARTED }
        compose.onNodeWithContentDescription("Resume slideshow").assertIsDisplayed()
        Thread.sleep(1500)
        assertTrue(shown(assets[2].entry.path))
        compose.runOnIdle { owner.lifecycle.currentState=Lifecycle.State.RESUMED }
        compose.onNodeWithContentDescription("Resume slideshow").performClick()
        compose.waitUntil(5000) { shown(assets[0].entry.path) }
    }
    @Test fun randomOptionAndExitReturnToTheDisplayedPhoto() {
        var exited=-1
        compose.setContent { PhotoManiakTheme(ThemeMode.DARK) { Slideshow(assets,0,30,model) { exited=it } } }
        compose.onNodeWithContentDescription("Pause slideshow").performClick()
        compose.onNodeWithContentDescription("Slideshow options").performClick()
        compose.onNodeWithText("Random photos").performClick()
        compose.onNodeWithContentDescription("Next photo").performClick()
        compose.onNodeWithTag("slide:${assets[2].entry.path}").assertExists()
        compose.onNodeWithContentDescription("Slideshow options").performClick()
        compose.onNodeWithText("Exit slideshow").performClick()
        compose.runOnIdle { assertEquals(2,exited) }
    }
    @Test fun viewerStartsSlideshowAndExitKeepsViewerOpen() {
        compose.setContent { PhotoManiakTheme(ThemeMode.DARK) { Viewer(assets,0,model) {} } }
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Slideshow").performClick()
        compose.onNodeWithText("30 seconds per photo").performClick()
        compose.onNodeWithText("Start slideshow").performClick()
        compose.onNodeWithContentDescription("Next photo").performClick()
        compose.onNodeWithContentDescription("Slideshow options").performClick()
        compose.onNodeWithText("Exit slideshow").performClick()
        compose.onNodeWithText("Info").assertIsDisplayed().performClick()
        compose.onNodeWithText("item2").performScrollTo().assertIsDisplayed()
    }
}
