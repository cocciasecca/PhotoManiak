package app.inphotomaniak

import org.junit.Test
import org.junit.Assert.*

class SlideshowTest {
    @Test fun sequentialWrapsAndSinglePhotoStaysPut() {
        assertEquals(1,nextSlide(0,3,false))
        assertEquals(0,nextSlide(2,3,false))
        assertEquals(0,nextSlide(0,1,false))
        assertEquals(0,nextSlide(0,1,true))
    }
    @Test fun randomAlwaysChoosesADifferentValidPhoto() {
        for(count in listOf(2,3,10000)) for(current in listOf(0,count-1)) repeat(100) {
            val next=nextSlide(current,count,true)
            assertTrue(next in 0 until count);assertNotEquals(current,next)
        }
    }
}
