package app.inphotomaniak

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.mockwebserver.*
import okhttp3.tls.HeldCertificate
import okhttp3.tls.HandshakeCertificates
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.atomic.AtomicInteger

class ScanResumeDeviceTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val stores=mutableListOf<ViewModelStore>()
    private val models=mutableListOf<GalleryModel>()
    private lateinit var server: MockWebServer
    private lateinit var certificates: HandshakeCertificates
    private fun main(block: ()->Unit)=instrumentation.runOnMainSync(block)
    private fun awaitCondition(block: ()->Boolean) {
        val end=System.currentTimeMillis()+10000
        while(!block() && System.currentTimeMillis()<end) Thread.sleep(25)
        assertTrue("Condition timed out",block())
    }
    private class Owner: LifecycleOwner { override val lifecycle=LifecycleRegistry(this) }
    @Before fun setup() {
        Settings(context).clear();context.deleteDatabase("gallery.db")
        val certificate=HeldCertificate.Builder().addSubjectAlternativeName("localhost").addSubjectAlternativeName("127.0.0.1").build()
        certificates=HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val serverCertificates=HandshakeCertificates.Builder().heldCertificate(certificate).build()
        server=MockWebServer();server.useHttps(serverCertificates.sslSocketFactory(),false);server.start()
        Settings(context).save(Account("42","fixture-token"));Settings(context).save(FolderRules(setOf("/")))
    }
    @After fun cleanup() {
        main { stores.forEach { it.clear() } }
        awaitCondition { models.none { it.state.value.scanning } }
        server.shutdown();Settings(context).clear();context.deleteDatabase("gallery.db")
    }
    private fun model(owner: Owner): GalleryModel {
        lateinit var model: GalleryModel
        val base=server.url("/")
        main {
            model=GalleryModel(context.applicationContext as Application,owner.lifecycle) { DriveClient(it,base,0) { builder -> builder.sslSocketFactory(certificates.sslSocketFactory(),certificates.trustManager) } }
            val store=ViewModelStore();store.put("scan",model);stores.add(store);models.add(model)
        }
        awaitCondition { model.state.value.ready }
        return model
    }
    private fun owner(): Owner {
        lateinit var owner: Owner
        main { owner=Owner();owner.lifecycle.currentState=Lifecycle.State.STARTED }
        return owner
    }
    @Test fun backgroundCancelsOnlyScanAndReturnResumesSavedPage() {
        val next=AtomicInteger()
        server.dispatcher=object: Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.requestUrl!!.queryParameter("cursor")==null -> MockResponse().setBody("""{"result":"success","data":[],"has_more":true,"cursor":"next"}""")
                next.incrementAndGet()==1 -> MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
                else -> MockResponse().setBody("""{"result":"success","data":[],"has_more":false}""")
            }
        }
        val owner=owner();val model=model(owner)
        awaitCondition { next.get()==1 }
        main { owner.lifecycle.currentState=Lifecycle.State.CREATED }
        awaitCondition { !model.state.value.scanning }
        assertNull(model.state.value.error)
        AssetDb(context).use { assertEquals("next",it.checkpoint()!!.cursor) }
        main { stores.first().clear() }
        val restored=model(owner)
        main { owner.lifecycle.currentState=Lifecycle.State.STARTED }
        awaitCondition { next.get()==2 && !restored.state.value.scanning }
        assertEquals(3,server.requestCount)
        AssetDb(context).use { assertNull(it.checkpoint()) }
    }
    @Test fun manualStopSurvivesForegroundReturnAndModelRecreation() {
        server.dispatcher=object: Dispatcher() {
            override fun dispatch(request: RecordedRequest)=MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
        }
        val owner=owner();val model=model(owner)
        awaitCondition { server.requestCount==1 }
        main { model.stop();owner.lifecycle.currentState=Lifecycle.State.CREATED }
        awaitCondition { !model.state.value.scanning }
        main { owner.lifecycle.currentState=Lifecycle.State.STARTED }
        Thread.sleep(250);assertEquals(1,server.requestCount)
        main { stores.first().clear() }
        val replacement=model(owner)
        Thread.sleep(250);assertEquals(1,server.requestCount)
        assertFalse(replacement.state.value.scanning)
        main { replacement.refresh() }
        awaitCondition { server.requestCount==2 }
        main { replacement.stop() }
        awaitCondition { !replacement.state.value.scanning }
    }
    @Test fun quickReturnWaitsForCancellationThenRestartsOnce() {
        val requests=AtomicInteger()
        server.dispatcher=object: Dispatcher() {
            override fun dispatch(request: RecordedRequest)=if(requests.incrementAndGet()==1)
                MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
                else MockResponse().setBody("""{"result":"success","data":[],"has_more":false}""")
        }
        val owner=owner();val model=model(owner)
        awaitCondition { requests.get()==1 }
        main { owner.lifecycle.currentState=Lifecycle.State.CREATED;owner.lifecycle.currentState=Lifecycle.State.STARTED }
        awaitCondition { requests.get()==2 && !model.state.value.scanning }
        assertNull(model.state.value.error)
        assertEquals(2,server.requestCount)
    }
    @Test fun versionOneDatabaseMigratesWithoutLosingIndexedPhotos() {
        context.openOrCreateDatabase("gallery.db",0,null).use { db ->
            db.execSQL("CREATE TABLE assets(path TEXT PRIMARY KEY, name TEXT NOT NULL, mime TEXT NOT NULL, modified INTEGER NOT NULL, etag TEXT NOT NULL, captured INTEGER NOT NULL, day TEXT NOT NULL, source TEXT NOT NULL, metadata INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX timeline ON assets(captured DESC)")
            db.execSQL("INSERT INTO assets VALUES('/2~a.jpg','a.jpg','image/jpeg',1,'v1',1,'1970-01-01','Remote modification',1)")
            db.version=1
        }
        AssetDb(context).use { db ->
            assertEquals("/2~a.jpg",db.all().single().entry.path)
            assertNull(db.checkpoint())
            db.saveScan(ScanCheckpoint("migrated",false,listOf("/")))
            assertEquals("migrated",db.checkpoint()!!.key)
        }
    }
    @Test fun initialCompletionPersistsAndNextLaunchRefreshesAutomatically() {
        Settings(context).scanStopped(true)
        server.dispatcher=object: Dispatcher() {
            override fun dispatch(request: RecordedRequest)=MockResponse().setBody("""{"result":"success","data":[],"has_more":false}""")
        }
        val owner=owner();val first=model(owner)
        assertFalse(first.state.value.initialScanComplete)
        assertEquals(0,server.requestCount)
        main { first.refresh() }
        awaitCondition { first.state.value.initialScanComplete && !first.state.value.scanning }
        assertTrue(Settings(context).initialScanComplete())
        main { stores.first().clear() }
        val restored=model(owner)
        awaitCondition { server.requestCount==2 && !restored.state.value.scanning }
        assertTrue(restored.state.value.initialScanComplete)
    }
    @Test fun savedPageAndMetadataSurviveDatabaseReopen() {
        val entry=RemoteEntry("/2~a.jpg","a.jpg",false,"image/jpeg",1,"v1")
        val job=ScanCheckpoint("fixture",true,listOf("/"),cursor="next",cursors=setOf("next"))
        AssetDb(context).use { it.saveScan(job,entries=listOf(entry)) }
        val asset=Asset(entry,Dates.resolve(emptyMap(),entry.name,1),true)
        AssetDb(context).use {
            assertEquals(job,it.checkpoint());assertEquals(listOf(entry),it.staged())
            it.saveScan(job.copy(pending=emptyList(),metadata=true),replacement=listOf(asset))
        }
        AssetDb(context).use {
            assertTrue(it.checkpoint()!!.metadata);assertEquals(listOf(asset),it.all())
            it.clearScan();assertNull(it.checkpoint());assertEquals(listOf(asset),it.all())
        }
    }
}
