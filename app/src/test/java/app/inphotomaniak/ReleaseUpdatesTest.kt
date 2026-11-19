package app.inphotomaniak

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*

class ReleaseUpdatesTest {
    private fun release(version: String = "0.8.10", edition: String = "FOSS", url: String = AvailableUpdate(version,edition).downloadUrl): JSONObject =
        JSONObject().put("tag_name","v$version").put("draft",false).put("prerelease",false)
            .put("assets",JSONArray().put(JSONObject().put("name","PhotoManiak-$version-$edition.apk").put("browser_download_url",url).put("size",100)))
    @Test fun versionsCompareNumericallyAndNeverOfferDowngrades() {
        assertTrue(ReleaseUpdates.newer("0.8.10","0.8.2"))
        assertTrue(ReleaseUpdates.newer("1.0.0","0.99.99"))
        assertFalse(ReleaseUpdates.newer("0.8.2","0.8.2"))
        assertFalse(ReleaseUpdates.newer("0.8.1","0.8.2"))
        for(v in listOf("0.8.3-beta","../0.8.3","99999999999999999999999.0.0","1.2","01.2.3")) assertFalse(ReleaseUpdates.newer(v,"0.8.2"))
    }
    @Test fun offersOnlyMatchingEditionAndTrustedAsset() {
        assertEquals(AvailableUpdate("0.8.10","FOSS"),ReleaseUpdates.parse(release().toString(),"0.8.2","FOSS"))
        assertNull(ReleaseUpdates.parse(release().toString(),"0.8.2","nonFOSS"))
        assertNotNull(ReleaseUpdates.parse(release(edition="nonFOSS").toString(),"0.8.2","nonFOSS"))
        assertNull(ReleaseUpdates.parse(release(url="https://example.com/app.apk").toString(),"0.8.2","FOSS"))
    }
    @Test fun draftsPrereleasesAndEmptyAssetsAreIgnored() {
        assertNull(ReleaseUpdates.parse(release().put("draft",true).toString(),"0.8.2","FOSS"))
        assertNull(ReleaseUpdates.parse(release().put("prerelease",true).toString(),"0.8.2","FOSS"))
        assertNull(ReleaseUpdates.parse(release().put("assets",JSONArray()).toString(),"0.8.2","FOSS"))
        assertNull(ReleaseUpdates.parse(release(version="0.8.1").toString(),"0.8.2","FOSS"))
    }
    @Test fun automaticChecksAreDailyAndRecoverFromClockChanges() {
        assertTrue(ReleaseUpdates.checkDue(0,100))
        assertFalse(ReleaseUpdates.checkDue(100,101))
        assertFalse(ReleaseUpdates.checkDue(100,100+86_400_000-1))
        assertTrue(ReleaseUpdates.checkDue(100,100+86_400_000))
        assertTrue(ReleaseUpdates.checkDue(100,99))
    }
}
