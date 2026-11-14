package app.inphotomaniak

import org.junit.Test
import org.junit.Assert.*
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class CastRelayTest {
    private val http=OkHttpClient.Builder().callTimeout(3,TimeUnit.SECONDS).build()
    private fun relay()=CastRelay(InetAddress.getLoopbackAddress())
    private val bytes="0123456789".toByteArray()
    @Test fun servesOnlyTheExplicitCurrentResource() { relay().use { relay ->
        val url=relay.publish(CastImage(bytes))
        http.newCall(Request.Builder().url(url).build()).execute().use { assertEquals(200,it.code);assertArrayEquals(bytes,it.body!!.bytes());assertEquals("image/jpeg",it.header("Content-Type")) }
        http.newCall(Request.Builder().url(url.substringBeforeLast('/')+"/other").build()).execute().use { assertEquals(404,it.code) }
        http.newCall(Request.Builder().url(url.substringBeforeLast('/')+"/../../etc/passwd").build()).execute().use { assertEquals(404,it.code) }
    } }
    @Test fun publishingNextItemRevokesPreviousCapability() { relay().use { relay ->
        val old=relay.publish(CastImage(bytes));val current=relay.publish(CastImage("new".toByteArray()))
        assertNotEquals(old,current)
        http.newCall(Request.Builder().url(old).build()).execute().use { assertEquals(404,it.code) }
        http.newCall(Request.Builder().url(current).build()).execute().use { assertEquals("new",it.body!!.string()) }
    } }
    @Test fun headAndCorsPreflightHaveNoMediaBody() { relay().use { relay ->
        val url=relay.publish(CastImage(bytes))
        http.newCall(Request.Builder().url(url).head().build()).execute().use { assertEquals(200,it.code);assertEquals("10",it.header("Content-Length"));assertEquals(0,it.body!!.bytes().size) }
        http.newCall(Request.Builder().url(url).method("OPTIONS",null).build()).execute().use { assertEquals(204,it.code);assertEquals("true",it.header("Access-Control-Allow-Private-Network")) }
    } }
    @Test fun rangesSupportSeekOpenEndedSuffixAndUnsatisfiable() { relay().use { relay ->
        val url=relay.publish(CastImage(bytes))
        for((range,expected) in listOf("bytes=2-4" to "234","bytes=8-" to "89","bytes=-3" to "789","bytes=8-100" to "89")) {
            http.newCall(Request.Builder().url(url).header("Range",range).build()).execute().use { assertEquals(206,it.code);assertEquals(expected,it.body!!.string());assertNotNull(it.header("Content-Range")) }
        }
        http.newCall(Request.Builder().url(url).header("Range","bytes=20-").build()).execute().use { assertEquals(416,it.code);assertEquals("bytes */10",it.header("Content-Range")) }
    } }
    @Test fun malformedRangesAndWritesAreRejected() { relay().use { relay ->
        val url=relay.publish(CastImage(bytes))
        for(range in listOf("bytes=5-2","bytes=-0","bytes=0-1,3-4","bytes=9999999999999999999-","garbage")) {
            http.newCall(Request.Builder().url(url).header("Range",range).build()).execute().use { assertEquals(416,it.code) }
        }
        http.newCall(Request.Builder().url(url).post("write".toRequestBody()).build()).execute().use { assertEquals(405,it.code) }
    } }
    @Test fun stoppingClosesListenerAndRevokesResource() {
        val relay=relay();val url=relay.publish(CastImage(bytes));relay.close()
        assertThrows(java.io.IOException::class.java) { http.newCall(Request.Builder().url(url).build()).execute() }
    }
    @Test fun sourceFailureReturnsGenericErrorWithoutSecrets() { relay().use { relay ->
        val url=relay.publish(object: CastContent {
            override fun open(range: String?): CastResponse = throw java.io.IOException("Bearer super-secret")
            override fun close() = Unit
        })
        http.newCall(Request.Builder().url(url).build()).execute().use { assertEquals(502,it.code);assertFalse(it.body!!.string().contains("secret")) }
    } }
    @Test fun videoForwardsRangeButNeverExposesTokenToReceiver() { MockWebServer().use { api -> relay().use { relay ->
        api.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range","bytes 2-4/10").setBody("234"))
        val client=DriveClient(Account("123","private-token"),api.url("/"),0)
        val entry=RemoteEntry("/42~video.mp4","video.mp4",false,"video/mp4",0,"")
        val url=relay.publish(CastVideo(client,entry,"video/mp4"))
        assertFalse(url.contains("private-token"));assertFalse(url.contains("42"+"~"))
        http.newCall(Request.Builder().url(url).header("Range","bytes=2-4").build()).execute().use {
            assertEquals(206,it.code);assertEquals("234",it.body!!.string());assertEquals("bytes 2-4/10",it.header("Content-Range"));assertNull(it.header("Authorization"))
        }
        val request=api.takeRequest();assertEquals("Bearer private-token",request.getHeader("Authorization"));assertEquals("bytes=2-4",request.getHeader("Range"));assertEquals("/2/drive/123/files/42/download",request.path)
    } } }
    @Test fun upstreamErrorsDoNotForwardErrorBodiesOrCredentials() { MockWebServer().use { api -> relay().use { relay ->
        api.enqueue(MockResponse().setResponseCode(403).setBody("private-token"))
        val client=DriveClient(Account("123","private-token"),api.url("/"),0)
        val url=relay.publish(CastVideo(client,RemoteEntry("/42~video.mp4","video.mp4",false,"video/mp4",0,""),"video/mp4"))
        http.newCall(Request.Builder().url(url).build()).execute().use { assertEquals(502,it.code);assertEquals("",it.body!!.string()) }
    } } }
}
