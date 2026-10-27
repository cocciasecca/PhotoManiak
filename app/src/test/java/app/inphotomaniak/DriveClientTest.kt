package app.inphotomaniak
import org.junit.Test
import org.junit.Assert.*
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.IOException

class DriveClientTest {
    private val account=Account("123","test-token")
    private fun page(id: Int,more: Boolean=false,cursor: String="next")="""{"result":"success","data":[{"id":$id,"name":"Foto + vacanze","type":"dir","last_modified_at":1700000000}],"has_more":$more,"cursor":"$cursor"}"""
    @Test fun allPagesAreLoadedWithBearer() { MockWebServer().use { server ->
        server.enqueue(MockResponse().setBody(page(2,true,"a+b/=")));server.enqueue(MockResponse().setBody(page(3)))
        val client=DriveClient(account,server.url("/"),0)
        val items=client.list("/");assertEquals(2,items.size)
        val first=server.takeRequest();assertEquals("GET",first.method);assertEquals("Bearer test-token",first.getHeader("Authorization"));assertEquals("/3/drive/123/files/1/files",first.requestUrl!!.encodedPath)
        assertEquals("a+b/=",server.takeRequest().requestUrl!!.queryParameter("cursor"))
    } }
    @Test fun namesAndIdentifiersArePreserved() {
        val entry=DriveClient.parsePage(page(42),"/7~Parent/").entries.single()
        assertEquals("42",DriveClient(account).fileId(entry.path));assertTrue(entry.directory)
        assertEquals("/Parent/Foto + vacanze/",DriveClient.displayPath(entry.path))
    }
    @Test fun remoteTimestampIsSeconds() { assertEquals(1700000000000L,DriveClient.parsePage(page(2),"/").entries.single().modified) }
    @Test fun missingCursorFailsInsteadOfTruncating() { assertThrows(IOException::class.java) { DriveClient.parsePage("""{"result":"success","data":[],"has_more":true}""","/") } }
    @Test fun missingDataFailsInsteadOfErasingIndex() { assertThrows(IOException::class.java) { DriveClient.parsePage("""{"result":"success"}""","/") } }
    @Test fun apiErrorFailsEvenWithHttp200() { assertThrows(IOException::class.java) { DriveClient.parsePage("""{"result":"error","data":[]}""","/") } }
    @Test fun repeatedCursorFails() { MockWebServer().use { server ->
        repeat(2) {server.enqueue(MockResponse().setBody(page(2,true)))}
        assertThrows(IOException::class.java) { DriveClient(account,server.url("/"),0).list("/") }
    } }
    @Test fun unauthorizedTokenHasActionableError() { MockWebServer().use { server ->
        server.enqueue(MockResponse().setResponseCode(401))
        val error=assertThrows(IOException::class.java) { DriveClient(account,server.url("/"),0).list("/") }
        assertTrue(error.message!!.contains("token",ignoreCase=true))
    } }
    @Test fun writesNeverReachNetwork() { MockWebServer().use { server ->
        val client=DriveClient(account,server.url("/"),0)
        assertThrows(IllegalArgumentException::class.java) { client.http.newCall(Request.Builder().url(server.url("/x")).put("x".toRequestBody()).build()).execute() }
        assertEquals(0,server.requestCount)
    } }
    @Test fun attackerRedirectIsRejectedBeforeRequest() { MockWebServer().use { server -> MockWebServer().use { attacker ->
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location",attacker.url("/")))
        val client=DriveClient(account,server.url("/"),0)
        assertThrows(IOException::class.java) { client.list("/") };assertEquals(0,attacker.requestCount)
    } } }
    @Test fun bearerIsRemovedForDownloadHost() {
        val client=DriveClient(account)
        val request=Request.Builder().url("https://a.download.kdrive.infomaniakusercontent.com/video?signature=x").header("Authorization","Bearer should-not-leak").header("Range","bytes=0-99").build()
        val outgoing=client.authorized(request)
        assertNull(outgoing.header("Authorization"));assertEquals("bytes=0-99",outgoing.header("Range"))
    }
    @Test fun accountNeverPrintsToken() { assertFalse(account.toString().contains("test-token")) }
    @Test fun invalidDriveRejected() { assertThrows(IllegalArgumentException::class.java) { Account("x.example","secret") } }
    @Test fun blankTokenRejected() { assertThrows(IllegalArgumentException::class.java) { Account("1","") } }
    @Test fun sameOriginRedirectWorks() { MockWebServer().use { server ->
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location",server.url("/final")))
        server.enqueue(MockResponse().setBody(page(2)))
        assertEquals(1,DriveClient(account,server.url("/"),0).list("/").size)
        server.takeRequest();assertEquals("Bearer test-token",server.takeRequest().getHeader("Authorization"))
    } }
}
