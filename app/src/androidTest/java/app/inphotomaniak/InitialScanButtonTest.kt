package app.inphotomaniak

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class InitialScanButtonTest {
    @get:Rule val compose=createComposeRule()
    @Test fun initialScanOffersPauseAndPlayThenSwitchesToRefresh() {
        var state by mutableStateOf(GalleryState(rules=FolderRules(setOf("/")),scanning=true))
        var pauses=0;var resumes=0
        compose.setContent { PhotoManiakTheme(ThemeMode.LIGHT) { LibraryScanButton(state,{pauses++},{resumes++}) } }
        compose.onNodeWithContentDescription("Refresh library").assertDoesNotExist()
        compose.onNodeWithContentDescription("Pause initial scan").performClick()
        compose.runOnIdle { assertEquals(1,pauses);state=state.copy(scanning=false) }
        compose.onNodeWithContentDescription("Refresh library").assertDoesNotExist()
        compose.onNodeWithContentDescription("Resume initial scan").performClick()
        compose.runOnIdle { assertEquals(1,resumes);state=state.copy(initialScanComplete=true) }
        compose.onNodeWithContentDescription("Resume initial scan").assertDoesNotExist()
        compose.onNodeWithContentDescription("Refresh library").performClick()
        compose.runOnIdle { assertEquals(2,resumes) }
    }
}
