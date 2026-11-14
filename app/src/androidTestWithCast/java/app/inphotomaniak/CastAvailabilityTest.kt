package app.inphotomaniak

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*

class CastAvailabilityTest {
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

    @Test fun castWithoutGooglePlayServicesExplainsRequirementWithoutBreakingViewer() {
        if(com.google.android.gms.common.GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)==com.google.android.gms.common.ConnectionResult.SUCCESS) return
        val asset=Asset(RemoteEntry("/2~photo.jpg","photo.jpg",false,"image/jpeg",1,"test"),CaptureDate(1,"2026-11-14","Filename"),true)
        compose.setContent { PhotoManiakTheme(ThemeMode.LIGHT) { Viewer(listOf(asset),0,model) {} } }
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Cast").performClick()
        compose.onNodeWithText("Google Cast requires Google Play services on this phone. The rest of PhotoManiak works without them.").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Share").assertIsDisplayed()
        compose.onNodeWithText("Info").assertIsDisplayed()
    }

}
