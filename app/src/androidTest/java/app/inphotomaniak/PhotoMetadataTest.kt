package app.inphotomaniak

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class PhotoMetadataTest {
    @Test fun signedGpsCoordinatesAreReadWithoutInventingMissingLocation() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File.createTempFile("photo-info", ".jpg",context.cacheDir)
        try {
            val bitmap=Bitmap.createBitmap(8,6,Bitmap.Config.ARGB_8888)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,90,it) };bitmap.recycle()
            assertNull(file.inputStream().use { MediaReader.photoTags(it) }["Latitude"])
            ExifInterface(file).apply {
                setLatLong(-33.8688,151.2093)
                setAttribute(ExifInterface.TAG_MODEL,"Fixture camera")
                saveAttributes()
            }
            val tags=file.inputStream().use { MediaReader.photoTags(it) }
            assertEquals(-33.8688,requireNotNull(tags["Latitude"]).toDouble(),0.00001)
            assertEquals(151.2093,requireNotNull(tags["Longitude"]).toDouble(),0.00001)
            assertEquals("Fixture camera",tags["Camera"])
        } finally { file.delete() }
    }
}
