package app.inphotomaniak

import org.junit.Test
import org.junit.Assert.*
import java.util.UUID

class CollectionsTest {
    private val album="11111111-1111-1111-1111-111111111111"
    private fun e(clock: Long, kind: String, target: String="42", value: String="true", member: String="", id: String=UUID.randomUUID().toString()) = CollectionEdit(id,clock,kind,target,value,member)
    @Test fun independentOfflineEditsAndTombstonesMerge() {
        val changes=listOf(e(1,"favorite"),e(2,"favorite",value="false"),e(1,"archive",target="43"))
        val expected=Collections(favorites=emptySet(),archived=setOf("43"))
        assertEquals(expected,Collections.from(changes))
        assertEquals(expected,Collections.from(changes.reversed()+changes))
    }
    @Test fun concurrentSameFieldIsDeterministic() {
        val a=e(1,"favorite",id="11111111-1111-1111-1111-111111111111")
        val b=e(1,"favorite",value="false",id="22222222-2222-2222-2222-222222222222")
        assertEquals(Collections.from(listOf(a,b)),Collections.from(listOf(b,a)))
        assertTrue(Collections.from(listOf(a,b)).favorites.isEmpty())
    }
    @Test fun renameMembershipAndRemovalAreIndependent() {
        val changes=listOf(e(1,"albumName",album,"Trip"),e(2,"member",album,member="42"),e(3,"albumName",album,"Holiday"),e(4,"member",album,"false","42"),e(3,"member",album,member="43"))
        assertEquals(VirtualAlbum(album,"Holiday",setOf("43")),Collections.from(changes).albums.single())
    }
    @Test fun deletedAlbumCannotBeResurrectedByOfflineEdits() {
        val changes=listOf(e(1,"albumName",album,"Trip"),e(2,"albumDeleted",album),e(20,"member",album,member="42"),e(21,"albumName",album,"Old phone"))
        assertTrue(Collections.from(changes).albums.isEmpty())
    }
    @Test fun jsonRoundTripAndDriveIsolation() {
        val edits=listOf(e(1,"favorite"),e(2,"albumName",album,"Viaggio \"2026\" 🏖"))
        val json=CollectionJson.encode("123",edits)
        assertEquals(edits.toSet(),CollectionJson.decode(json,"123").toSet())
        assertThrows(IllegalArgumentException::class.java) { CollectionJson.decode(json,"999") }
    }
    @Test fun unknownSchemaAndInvalidOperationsAreRejected() {
        val json=CollectionJson.encode("123",listOf(e(1,"favorite")))
        assertThrows(IllegalArgumentException::class.java) { CollectionJson.decode(json.replace("\"version\":1","\"version\":2"),"123") }
        assertThrows(IllegalArgumentException::class.java) { CollectionJson.encode("123",listOf(e(1,"favorite",value="maybe"))) }
        assertThrows(IllegalArgumentException::class.java) { CollectionJson.encode("123",listOf(e(1,"member",album,member="../photo"))) }
    }
    @Test fun renamingOrMovingFilesKeepsMembershipAndArchiveDoesNotHideAlbum() {
        val original=RemoteEntry("/5~Photos/42~old.jpg","old.jpg",false,"image/jpeg",0,"")
        val moved=original.copy(path="/8~Other/42~new.jpg",name="new.jpg")
        assertEquals(original.collectionId(),moved.collectionId())
        val asset=Asset(moved,CaptureDate(0,"",""),true)
        val state=GalleryState(assets=listOf(asset),collections=Collections(setOf("42"),setOf("42"),listOf(VirtualAlbum(album,"Trip",setOf("42")))))
        assertTrue(state.visibleAssets().isEmpty())
        assertEquals(listOf(asset),state.copy(collectionView="favorites").visibleAssets())
        assertEquals(listOf(asset),state.copy(collectionView="archive").visibleAssets())
        assertEquals(listOf(asset),state.copy(collectionView="album",albumId=album).visibleAssets())
    }
}
