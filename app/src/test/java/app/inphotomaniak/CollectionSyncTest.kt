package app.inphotomaniak

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import org.junit.Assert.*
import java.io.IOException
import java.util.UUID

class CollectionSyncTest {
    private val edit=CollectionEdit(UUID.randomUUID().toString(),1,"favorite","42","true")
    private fun batch()=CollectionDb.Batch(CollectionJson.filename(),CollectionJson.encode("123",listOf(edit)))
    private class MemoryStore(var pending: CollectionDb.Batch? = null): CollectionStore {
        val edits=mutableListOf<CollectionEdit>()
        override fun merge(edits: List<CollectionEdit>) { this.edits.addAll(edits) }
        override fun received(id: String, etag: String)=false
        override fun recordReceived(id: String, etag: String) = Unit
        override fun nextBatch(drive: String)=pending
        override fun acknowledge(batch: CollectionDb.Batch, drive: String) { pending=null }
    }
    private fun page(batch: CollectionDb.Batch?=null) = """{"result":"success","data":[${if(batch==null) "" else """{"id":99,"name":"${batch.name}","type":"file","last_modified_at":123,"size":100}"""}]}"""
    private fun client(server: MockWebServer)=DriveClient(Account("123","token"),server.url("/"),0)
    @Test fun uploadUsesCreateOnlyRawBodyAndBearer() { MockWebServer().use { server ->
        val batch=batch();server.enqueue(MockResponse().setBody("""{"result":"success"}"""))
        client(server).uploadCollection("/8~PhotoManiak/",batch)
        val r=server.takeRequest();assertEquals("POST",r.method)
        assertEquals("/3/drive/123/upload",r.requestUrl!!.encodedPath)
        assertEquals("error",r.requestUrl!!.queryParameter("conflict"))
        assertEquals("8",r.requestUrl!!.queryParameter("directory_id"))
        assertEquals(batch.name,r.requestUrl!!.queryParameter("file_name"))
        assertEquals(batch.text.toByteArray().size.toString(),r.requestUrl!!.queryParameter("total_size"))
        assertEquals(batch.text,r.body.readUtf8());assertEquals("Bearer token",r.getHeader("Authorization"))
    } }
    @Test fun uploadRejectsArbitraryFilesRootAndWrongDriveBeforeNetwork() { MockWebServer().use { server ->
        val client=client(server)
        assertThrows(IllegalArgumentException::class.java) { client.uploadCollection("/",batch()) }
        assertThrows(IllegalArgumentException::class.java) { client.uploadCollection("/8~PhotoManiak/",batch().copy(name="photo.jpg")) }
        assertThrows(IllegalArgumentException::class.java) { client.uploadCollection("/8~PhotoManiak/",batch().copy(text=CollectionJson.encode("999",listOf(edit)))) }
        assertEquals(0,server.requestCount)
    } }
    @Test fun uploadsNeverFollowRedirects() { MockWebServer().use { server -> MockWebServer().use { other ->
        server.enqueue(MockResponse().setResponseCode(307).setHeader("Location",other.url("/")))
        assertThrows(IOException::class.java) { client(server).uploadCollection("/8~PhotoManiak/",batch()) }
        assertEquals(1,server.requestCount);assertEquals(0,other.requestCount)
    } } }
    @Test fun alreadyUploadedBatchIsVerifiedAndAcknowledgedWithoutUploadingAgain() { MockWebServer().use { server ->
        val batch=batch();val store=MemoryStore(batch)
        server.enqueue(MockResponse().setBody(page(batch)))
        repeat(2) { server.enqueue(MockResponse().setBody(batch.text)) }
        CollectionSync(client(server),store).run("/8~PhotoManiak/")
        assertNull(store.pending);assertEquals(listOf(edit),store.edits)
        repeat(3) { assertEquals("GET",server.takeRequest().method) }
    } }
    @Test fun failedUploadKeepsPendingChanges() { MockWebServer().use { server ->
        val batch=batch();val store=MemoryStore(batch)
        server.enqueue(MockResponse().setBody(page()))
        server.enqueue(MockResponse().setResponseCode(403))
        server.enqueue(MockResponse().setBody(page()))
        assertThrows(IOException::class.java) { CollectionSync(client(server),store).run("/8~PhotoManiak/") }
        assertEquals(batch,store.pending)
    } }
    @Test fun uncertainSuccessIsRecoveredByExactContent() { MockWebServer().use { server ->
        val batch=batch();val store=MemoryStore(batch)
        server.enqueue(MockResponse().setBody(page()))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody(page(batch)))
        server.enqueue(MockResponse().setBody(batch.text))
        CollectionSync(client(server),store).run("/8~PhotoManiak/")
        assertNull(store.pending);assertEquals(4,server.requestCount)
    } }
    @Test fun malformedRemoteFileStopsSyncBeforeUploading() { MockWebServer().use { server ->
        val batch=batch();val store=MemoryStore(batch)
        server.enqueue(MockResponse().setBody(page(batch)))
        server.enqueue(MockResponse().setBody("{}"))
        assertThrows(Exception::class.java) { CollectionSync(client(server),store).run("/8~PhotoManiak/") }
        assertEquals(batch,store.pending);assertEquals(2,server.requestCount)
    } }
    @Test fun sameNameDifferentContentCannotAcknowledgeLocalChanges() { MockWebServer().use { server ->
        val batch=batch();val store=MemoryStore(batch)
        server.enqueue(MockResponse().setBody(page(batch)))
        val other=CollectionJson.encode("123",listOf(edit.copy(value="false")))
        repeat(2) { server.enqueue(MockResponse().setBody(other)) }
        assertThrows(IllegalArgumentException::class.java) { CollectionSync(client(server),store).run("/8~PhotoManiak/") }
        assertEquals(batch,store.pending)
    } }
}
