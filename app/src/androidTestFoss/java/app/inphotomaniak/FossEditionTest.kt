package app.inphotomaniak

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*

class FossEditionTest {
    @get:Rule val compose=createComposeRule()
    @Test fun galleryAndViewerHaveNoCastActionsAndSdkIsAbsent() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        Settings(context).clear()
        val store=ViewModelStore()
        val model=GalleryModel(context.applicationContext as Application)
        store.put("foss",model)
        try {
            compose.waitUntil(5000) { model.state.value.ready }
            assertEquals("foss",BuildConfig.FLAVOR)
            assertThrows(ClassNotFoundException::class.java) { Class.forName("com.google.android.gms.cast.framework.CastContext") }
            assertThrows(ClassNotFoundException::class.java) { Class.forName("app.inphotomaniak.CastRelay") }
            val asset=Asset(RemoteEntry("/2~photo.jpg","photo.jpg",false,"image/jpeg",1,"test"),CaptureDate(1,"2026-11-14","Filename"),true)
            val viewer=mutableStateOf(false)
            compose.setContent { PhotoManiakTheme(ThemeMode.LIGHT) {
                if(viewer.value) Viewer(listOf(asset),0,model) {}
                else GalleryContent(GalleryState(ready=true,account=Account("123","fixture"),assets=listOf(asset)),model)
            } }
            compose.onNodeWithContentDescription("Cast").assertDoesNotExist()
            compose.runOnIdle { viewer.value=true }
            compose.onNodeWithText("Share").assertIsDisplayed()
            compose.onNodeWithText("Info").assertIsDisplayed()
            compose.onNodeWithContentDescription("Favorite").assertIsDisplayed()
            compose.onNodeWithContentDescription("More options").performClick()
            compose.onNodeWithText("Cast").assertDoesNotExist()
            compose.onNodeWithText("Add to album").assertIsDisplayed()
            compose.onNodeWithText("Archive").assertIsDisplayed()
        } finally { store.clear();Settings(context).clear() }
    }
}
