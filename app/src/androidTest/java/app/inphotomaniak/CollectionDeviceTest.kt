package app.inphotomaniak

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

class CollectionDeviceTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: CollectionDb
    @Before fun setup() { db=CollectionDb(context);db.clear() }
    @After fun cleanup() { db.clear();db.close() }
    @Test fun localChangesAndExactOutboxBytesSurviveRestart() {
        db.edit("favorite",listOf("42","43"),"true")
        db.edit("archive",listOf("43"),"true")
        val batch=db.nextBatch("123")!!
        db.close();db=CollectionDb(context)
        assertEquals(setOf("42","43"),db.snapshot().favorites)
        assertEquals(setOf("43"),db.snapshot().archived)
        assertEquals(batch,db.nextBatch("123"))
        db.acknowledge(batch,"123")
        assertEquals(0,db.pendingCount());assertNull(db.nextBatch("123"))
    }
    @Test fun editsMadeDuringUploadStayPendingAndDoNotResurrectRemovedFavorite() {
        db.edit("favorite",listOf("42"),"true")
        val batch=db.nextBatch("123")!!
        db.edit("favorite",listOf("42"),"false")
        db.acknowledge(batch,"123")
        db.merge(CollectionJson.decode(batch.text,"123"))
        assertTrue(db.snapshot().favorites.isEmpty())
        assertEquals(1,db.pendingCount())
    }
    @Test fun conflictingEditRollsBackWholeIncomingBatch() {
        db.edit("favorite",listOf("42"),"true")
        val original=db.all().single()
        val incoming=CollectionEdit(UUID.randomUUID().toString(),2,"archive","43","true")
        assertThrows(IllegalArgumentException::class.java) { db.merge(listOf(incoming,original.copy(value="false"))) }
        assertEquals(listOf(original),db.all())
        assertTrue(db.snapshot().archived.isEmpty())
    }
    @Test fun switchingSyncLocationPublishesAllPreviouslyAcknowledgedEdits() {
        db.edit("favorite",listOf("42"),"true")
        db.acknowledge(db.nextBatch("123")!!,"123")
        db.resetForLocation()
        assertEquals(1,db.pendingCount())
        assertEquals("42",CollectionJson.decode(db.nextBatch("123")!!.text,"123").single().target)
    }
    @Test fun importedClockPrecedesNewLocalEditsAndNewAlbumDeletionWins() {
        val album=UUID.randomUUID().toString()
        db.merge(listOf(CollectionEdit(UUID.randomUUID().toString(),100,"favorite","42","true")))
        db.edit("favorite",listOf("42"),"false")
        assertEquals(101,db.all().maxOf { it.clock }.toInt())
        db.edit("albumName",listOf(album),"Holiday")
        db.members(album,listOf("42"),true)
        db.edit("albumDeleted",listOf(album),"true")
        db.members(album,listOf("43"),true)
        assertTrue(db.snapshot().albums.isEmpty())
        assertTrue(db.snapshot().favorites.isEmpty())
    }
}
