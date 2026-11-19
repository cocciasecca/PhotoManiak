package app.inphotomaniak

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

class UpdateUiTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get()=context.getSharedPreferences("app_updates",Context.MODE_PRIVATE)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val controllers=mutableListOf<AppUpdates>()
    private fun controller()=AppUpdates(context,scope).also { controllers.add(it) }
    @Before fun setup() { prefs.edit().clear().commit() }
    @After fun cleanup() { controllers.forEach { it.close() };scope.cancel();prefs.edit().clear().commit() }
    @Test fun updateNoticeCanBeDismissedAndStaysDismissedAfterRecreation() {
        prefs.edit().putString("available_version","99.0.0").putString("available_edition",context.getString(R.string.edition_name)).commit()
        val updates=controller()
        compose.setContent { PhotoManiakTheme(ThemeMode.LIGHT) { UpdateNotice(updates) } }
        compose.onNodeWithText("PhotoManiak 99.0.0 is available").assertIsDisplayed()
        compose.onNodeWithText("Download update").assertIsDisplayed()
        compose.onNodeWithText("Later").performClick()
        compose.onNodeWithText("PhotoManiak 99.0.0 is available").assertDoesNotExist()
        assertFalse(controller().state.value.showNotice)
        assertNotNull(updates.state.value.available)
    }
    @Test fun automaticChecksCanBeDisabledAndPreferencePersists() {
        val updates=controller()
        compose.setContent { PhotoManiakTheme(ThemeMode.LIGHT) { Column { UpdateSettings(updates) } } }
        compose.onNode(isToggleable()).performClick()
        assertFalse(updates.state.value.automatic)
        assertFalse(controller().state.value.automatic)
        compose.onNodeWithText("Check for updates").assertIsEnabled()
    }
    @Test fun anotherEditionDoesNotReuseAnUnverifiedCachedDownload() {
        val other=if(context.getString(R.string.edition_name)=="FOSS") "nonFOSS" else "FOSS"
        prefs.edit().putString("available_version","99.0.0").putString("available_edition",other).commit()
        assertNull(controller().state.value.available)
    }
}
