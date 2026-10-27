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
    @Test fun thumbnailSizeSurvivesRestartAndInvalidValuesFallBack() {
        val settings=Settings(context)
        try {
            settings.clear()
            assertEquals(ThumbnailSize.MEDIUM,settings.thumbnailSize())
            for(size in ThumbnailSize.entries) {
                settings.save(size)
                assertEquals(size,Settings(context).thumbnailSize())
            }
            context.getSharedPreferences("settings",0).edit().putString("thumbnail_size","future-value").commit()
            assertEquals(ThumbnailSize.MEDIUM,Settings(context).thumbnailSize())
        } finally { settings.clear() }
    }
    @Test fun sharingGrantsOnlyReadAccessToTheOriginalCopy() {
        val directory=java.io.File(context.cacheDir,"shared/test").apply { mkdirs() }
        val file=java.io.File(directory,"photo.jpg").apply { writeBytes(byteArrayOf(1,2,3,4)) }
        try {
            val intent=ShareFiles.sendIntent(context,file,"image/*")
            assertEquals(android.content.Intent.ACTION_SEND,intent.action)
            assertEquals("image/jpeg",intent.type)
            val uri=intent.clipData!!.getItemAt(0).uri
            assertEquals("content",uri.scheme)
            assertEquals(context.packageName+".files",uri.authority)
            assertTrue(intent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(0,intent.flags and android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            assertNull(intent.getStringExtra(android.content.Intent.EXTRA_TEXT))
            context.contentResolver.openInputStream(uri)!!.use { assertArrayEquals(file.readBytes(),it.readBytes()) }
            assertThrows(IllegalArgumentException::class.java) {
                ShareFiles.sendIntent(context,java.io.File(context.cacheDir,"private.jpg"),"image/jpeg")
            }
        } finally { directory.deleteRecursively() }
    }
    @Test fun folderSelectionSurvivesRestart() {
        val rules=FolderRules(setOf("/Foto/","/Viaggi/"),setOf("/Foto/Privato/"))
        try { Settings(context).save(rules);assertEquals(rules,Settings(context).rules()) }
        finally { Settings(context).clear() }
    }
}
