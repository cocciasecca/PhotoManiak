package app.inphotomaniak

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import okhttp3.HttpUrl.Companion.toHttpUrl

@RunWith(AndroidJUnit4::class)
class DeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun credentialsAreEncryptedAndCanBeCleared() {
        val settings=Settings(context)
        val account=Account("123456","fixture-token-do-not-use")
        try {
            settings.save(account)
            assertEquals(account,settings.account())
            val stored=context.getSharedPreferences("settings",0).getString("account","")!!
            assertFalse(stored.contains(account.token))
            settings.clear();assertNull(settings.account())
        } finally { settings.clear() }
    }
    @Test fun metadataUpsertDoesNotEraseOtherPhotos() {
        val db=AssetDb(context)
        fun asset(path: String,time: Long)=Asset(RemoteEntry(path,path,false,"image/jpeg",time,"etag"),CaptureDate(time,"2026-10-26","EXIF"),true)
        try {
            db.replace(listOf(asset("/a.jpg",10),asset("/b.jpg",20)))
            db.upsert(listOf(asset("/a.jpg",30)))
            val all=db.all()
            assertEquals(2,all.size);assertEquals("/a.jpg",all.first().entry.path);assertEquals(30L,all.first().date.millis)
        } finally { db.replace(emptyList());db.close() }
    }
    @Test fun apiParserWorksWithAndroidJsonImplementation() {
        val json="""{"result":"success","data":[{"id":2,"name":"Foto PC","type":"dir"}],"has_more":false}"""
        val item=DriveClient.parsePage(json,"/").entries.single()
        assertTrue(item.directory);assertEquals("Foto PC",item.name)
    }
    @Test fun folderSelectionSurvivesRestart() {
        val rules=FolderRules(setOf("/Foto/","/Viaggi/"),setOf("/Foto/Privato/"))
        try { Settings(context).save(rules);assertEquals(rules,Settings(context).rules()) }
        finally { Settings(context).clear() }
    }
}
