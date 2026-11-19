package app.inphotomaniak

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.net.UnknownHostException

class LibraryScanTest {
    private class Store: ScanStore {
        var saved: String?=null
        val found=linkedMapOf<String,RemoteEntry>()
        var assets=listOf<Asset>()
        override fun checkpoint()=saved?.let(ScanCheckpoint::parse)
        override fun staged()=found.values.toList()
        override fun indexed()=assets
        override fun clearScan() { saved=null;found.clear() }
        override fun saveScan(state: ScanCheckpoint, entries: List<RemoteEntry>, reset: Boolean, replacement: List<Asset>?, updated: Asset?) {
            if(reset) clearScan()
            entries.forEach { found[it.path]=it }
            replacement?.let { assets=it }
            updated?.let { change -> assets=assets.map { if(it.entry.path==change.entry.path) change else it } }
            saved=state.json()
        }
    }
    private val rules=FolderRules(setOf("/"))
    private fun photo(id: Int)=RemoteEntry("/$id~IMG_20260101.jpg","IMG_20260101.jpg",false,"image/jpeg",1,"v1")
    private fun asset(entry: RemoteEntry)=Asset(entry,Dates.resolve(emptyMap(),entry.name,entry.modified),true)
    private fun scanner(store: Store, page: (String,String?)->DriveClient.Page,
                        tags: (RemoteEntry)->Map<String,String> = { emptyMap() },
                        progress: (String,Int,Int)->Unit = { _,_,_-> },
                        delay: suspend (Long)->Unit = {}, folders: FolderRules=rules)=
        LibraryScan(store,"42",folders,page,tags,{},progress,delay)

    @Test fun resumesAtSavedPageAndPreservesOldIndexUntilDiscoveryCompletes()=runBlocking {
        val store=Store();val old=asset(photo(99));store.assets=listOf(old)
        try {
            scanner(store,{ _,cursor -> if(cursor==null) DriveClient.Page(listOf(photo(2)),"page2") else throw CancellationException() }).run()
            fail("Expected interruption")
        } catch(_: CancellationException) { }
        assertEquals(listOf(old),store.assets)
        assertEquals("page2",store.checkpoint()!!.cursor)
        val requested=mutableListOf<String?>()
        scanner(store,{ _,cursor -> requested.add(cursor);DriveClient.Page(listOf(photo(3)),null) }).run()
        assertEquals(listOf("page2"),requested)
        assertEquals(setOf(photo(2).path,photo(3).path),store.assets.map { it.entry.path }.toSet())
        assertNull(store.checkpoint())
    }
    @Test fun metadataResumeDoesNotRepeatCompletedReadsOrRestartFullRefresh()=runBlocking {
        val store=Store();store.assets=listOf(asset(photo(2)),asset(photo(3)))
        val reads=mutableListOf<String>()
        try {
            scanner(store,{ _,_-> DriveClient.Page(listOf(photo(2),photo(3)),null) },{ entry ->
                if(entry.path==photo(3).path) throw CancellationException()
                reads.add(entry.path);emptyMap()
            }).run(full=true)
            fail("Expected interruption")
        } catch(_: CancellationException) { }
        assertTrue(store.checkpoint()!!.full)
        scanner(store,{ _,_-> error("Must not rediscover folders") },{ reads.add(it.path);emptyMap() }).run()
        assertEquals(listOf(photo(2).path,photo(3).path),reads)
        assertTrue(store.assets.all { it.metadataRead })
    }
    @Test fun temporaryDnsFailuresRetrySamePageWithBackoff()=runBlocking {
        val store=Store();var attempts=0;val waits=mutableListOf<Long>();val phases=mutableListOf<String>()
        scanner(store,{ _,_-> if(attempts++<3) throw UnknownHostException();DriveClient.Page(listOf(photo(2)),null) },
            progress={ phase,_,_->phases.add(phase) },delay={ waits.add(it) }).run()
        assertEquals(listOf(2000L,4000L,8000L),waits)
        assertEquals(3,phases.count { it=="waiting" })
        assertEquals(1,store.assets.size)
    }
    @Test fun authErrorIsNotRetriedAndDoesNotRemoveExistingIndex()=runBlocking {
        val store=Store();val old=asset(photo(99));store.assets=listOf(old)
        try {
            scanner(store,{ _,_-> throw DriveHttpException(403,"denied") },delay={ fail("Must not retry auth errors") }).run()
            fail("Expected permission error")
        } catch(e: DriveHttpException) { assertEquals(403,e.status) }
        assertEquals(listOf(old),store.assets)
        assertNotNull(store.checkpoint())
    }
    @Test fun changedFolderSelectionDiscardsStaleCheckpoint()=runBlocking {
        val store=Store()
        store.saveScan(ScanCheckpoint(ScanCheckpoint.key("42",rules),false,listOf("/old/")),entries=listOf(photo(99)),reset=true,replacement=null,updated=null)
        val folders=FolderRules(setOf("/new/"));val fresh=photo(2).copy(path="/new/2~photo.jpg")
        scanner(store,{ path,cursor -> assertEquals("/new/",path);assertNull(cursor);DriveClient.Page(listOf(fresh),null) },folders=folders).run()
        assertEquals(listOf(fresh.path),store.assets.map { it.entry.path })
    }
    @Test fun cancellationDuringNetworkWaitKeepsCheckpointForNextRun()=runBlocking {
        val store=Store()
        try {
            scanner(store,{ _,_->throw UnknownHostException() },delay={ throw CancellationException() }).run()
            fail("Expected cancellation")
        } catch(_: CancellationException) { }
        assertEquals(listOf("/"),store.checkpoint()!!.pending)
        scanner(store,{ _,_->DriveClient.Page(listOf(photo(2)),null) }).run()
        assertEquals(1,store.assets.size)
    }
    @Test fun repeatedCursorDoesNotCommitPartialListing()=runBlocking {
        val store=Store();val old=asset(photo(99));store.assets=listOf(old)
        try {
            scanner(store,{ _,_->DriveClient.Page(listOf(photo(2)),"same") }).run()
            fail("Expected invalid pagination")
        } catch(_: java.io.IOException) { }
        assertEquals(listOf(old),store.assets)
        assertEquals("same",store.checkpoint()!!.cursor)
    }
    @Test fun manualResumeRediscoversNewFilesWithoutRereadingCompletedDates()=runBlocking {
        val store=Store();val reads=mutableListOf<String>()
        try {
            scanner(store,{ _,_->DriveClient.Page(listOf(photo(2),photo(3)),null) },{
                if(it.path==photo(3).path) throw CancellationException()
                reads.add(it.path);emptyMap()
            }).run()
            fail("Expected pause")
        } catch(_: CancellationException) { }
        assertEquals(listOf(photo(2).path),reads)
        scanner(store,{ path,cursor ->
            assertEquals("/",path);assertNull(cursor)
            DriveClient.Page(listOf(photo(2),photo(3),photo(4)),null)
        },{ reads.add(it.path);emptyMap() }).run(recheckFolders=true)
        assertEquals(listOf(photo(2).path,photo(3).path,photo(4).path),reads)
        assertEquals(3,store.assets.size)
        assertTrue(store.assets.all { it.metadataRead })
    }

}
