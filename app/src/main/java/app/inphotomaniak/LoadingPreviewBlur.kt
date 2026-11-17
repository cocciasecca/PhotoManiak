package app.inphotomaniak

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.roundToInt

/** Small software blur, including Android versions where Compose blur is unavailable. */
internal object LoadingPreviewBlur : Transformation {
    override val cacheKey = "loading-preview-blur-v1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val factor = minOf(1f, 96f / maxOf(input.width, input.height))
        val width = (input.width * factor).roundToInt().coerceAtLeast(1)
        val height = (input.height * factor).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(input, width, height, true)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        // The input belongs to Coil and must never be recycled or mutated.
        if(scaled !== input) scaled.recycle()
        val temporary = IntArray(pixels.size)
        val context = currentCoroutineContext()
        val radius = 4
        for(horizontal in listOf(true, false)) {
            val source = if(horizontal) pixels else temporary
            val target = if(horizontal) temporary else pixels
            for(y in 0 until height) {
                context.ensureActive()
                for(x in 0 until width) {
                    var alpha = 0; var red = 0; var green = 0; var blue = 0
                    for(offset in -radius..radius) {
                        val sx = if(horizontal) (x + offset).coerceIn(0, width - 1) else x
                        val sy = if(horizontal) y else (y + offset).coerceIn(0, height - 1)
                        val color = source[sy * width + sx]
                        alpha += color ushr 24
                        red += (color ushr 16) and 255
                        green += (color ushr 8) and 255
                        blue += color and 255
                    }
                    val count = radius * 2 + 1
                    target[y * width + x] = ((alpha / count) shl 24) or
                        ((red / count) shl 16) or ((green / count) shl 8) or (blue / count)
                }
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
