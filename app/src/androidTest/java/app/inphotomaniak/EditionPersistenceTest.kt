package app.inphotomaniak

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*

/** Also run with -e editionCheck seed/verify around adb install -r to test updates. */
class EditionPersistenceTest {
    @Test fun credentialsPreferencesAndIndexSurviveEditionReplacement() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val settings=Settings(context)
        val mode=InstrumentationRegistry.getArguments().getString("editionCheck","roundTrip")
        val account=Account("123","edition-switch-fixture")
        val rules=FolderRules(setOf("/2~Private/3~Photos"),setOf("/2~Private/3~Photos/4~Excluded"))
        val asset=Asset(RemoteEntry("/5~photo.jpg","photo.jpg",false,"image/jpeg",1,"fixture"),CaptureDate(1,"2026-11-14","Filename"),true)
        if(mode!="verify") {
            settings.clear();settings.save(account);settings.save(rules)
            settings.save(ThemeMode.DARK);settings.save(ThumbnailSize.LARGE)
            settings.save(ColorPalette.CUSTOM,"CC3377")
            AssetDb(context).use { it.replace(listOf(asset)) }
        }
        assertEquals(account,settings.account())
        assertEquals(rules,settings.rules())
        assertEquals(ThemeMode.DARK,settings.themeMode())
        assertEquals(ThumbnailSize.LARGE,settings.thumbnailSize())
        assertEquals(ColorPalette.CUSTOM,settings.colorPalette())
        assertEquals("CC3377",settings.customColor())
        AssetDb(context).use { assertEquals(listOf(asset),it.all()) }
        if(mode=="roundTrip") { settings.clear();AssetDb(context).use { it.replace(emptyList()) } }
    }
}
