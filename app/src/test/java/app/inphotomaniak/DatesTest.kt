package app.inphotomaniak
import org.junit.Test
import org.junit.Assert.*
import java.time.Instant
class DatesTest {
    @Test fun originalWinsOverCreateDate() {
        val d=Dates.resolve(mapOf("CreateDate" to "2026:10:01 10:00:00","DateTimeOriginal" to "2020:06:02 12:00:00"),"photo.jpg",0)
        assertEquals("2020-06-02",d.day);assertEquals("DateTimeOriginal",d.source)
    }
    @Test fun subsecondCreationFollowsImmichPriority() {
        val d=Dates.resolve(mapOf("SubSecCreateDate" to "2021:01:01 00:00:00.123","DateTimeOriginal" to "2020:01:01 00:00:00"),"x",0)
        assertEquals("SubSecCreateDate",d.source)
    }
    @Test fun offsetPreservesCaptureDay() {
        val d=Dates.parse("2026:10:01 00:10:00+02:00","EXIF")!!
        assertEquals("2026-10-01",d.day);assertEquals(Instant.parse("2026-09-30T22:10:00Z").toEpochMilli(),d.millis)
    }
    @Test fun invalidExifFallsThrough() {
        val d=Dates.resolve(mapOf("DateTimeOriginal" to "0000:00:00 00:00:00","CreateDate" to "2024:02:29 12:00:00"),"x",0)
        assertEquals("2024-02-29",d.day)
    }
    @Test fun invalidCalendarDateRejected() { assertNull(Dates.parse("2023:02:29 12:00:00","EXIF")) }
    @Test fun compactVideoDate() {
        val d=Dates.parse("20261001T121314.000Z","CreationDate")!!
        assertEquals(Instant.parse("2026-10-01T12:13:14Z").toEpochMilli(),d.millis)
    }
    @Test fun filenameFallback() { assertEquals("2026-10-12",Dates.resolve(emptyMap(),"IMG_20261012_153012.jpg",0).day) }
    @Test fun whatsappFilename() { assertEquals("2024-03-02",Dates.resolve(emptyMap(),"IMG-20240302-WA0001.jpg",0).day) }
    @Test fun noGuessingEuropeanDate() { assertEquals("Unknown date",Dates.resolve(emptyMap(),"02-03-2024.jpg",0).source) }
    @Test fun remoteFallbackIsNotDownloadTime() { assertEquals(123456789000L,Dates.resolve(emptyMap(),"plain.jpg",123456789000L).millis) }
}
