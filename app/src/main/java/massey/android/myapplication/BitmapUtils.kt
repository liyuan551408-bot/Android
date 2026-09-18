package massey.android.myapplication

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.LruCache
object ThumbnailMemoryCache {
    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSizeKb = maxOf(1, maxMemoryKb / 8)
    private val cache =
        object : LruCache<String, Bitmap>(cacheSizeKb) {
            override fun sizeOf(
                key: String,
                value: Bitmap
            ): Int {
                return maxOf(
                    1,
                    value.byteCount / 1024
                )
            }
        }
    fun get(key: String): Bitmap? {
        return cache.get(key)
    }
    fun put(
        key: String,
        bitmap: Bitmap
    ) {
        cache.put(key, bitmap)
    }
}
fun calculateInSampleSize(
    width: Int,
    height: Int,
    requiredWidth: Int,
    requiredHeight: Int
): Int {
    if (width <= 0 || height <= 0) {
        return 1
    }
    var inSampleSize = 1
    while (
        width / (inSampleSize * 2) >= requiredWidth &&
        height / (inSampleSize * 2) >= requiredHeight
    ) {
        inSampleSize *= 2
    }
    return inSampleSize
}
fun calculateViewerInSampleSize(
    width: Int,
    height: Int
): Int {
    if (width <= 0 || height <= 0) {
        return 1
    }
    val targetMaximumDimension = 1600
    val maximumDimension = maxOf(width, height)
    var inSampleSize = 1
    while (
        maximumDimension / (inSampleSize * 2) >=
        targetMaximumDimension
    ) {
        inSampleSize *= 2
    }
    return inSampleSize
}
fun rotateBitmap(
    bitmap: Bitmap,
    orientation: Int
): Bitmap {
    val normalizedOrientation =
        ((orientation % 360) + 360) % 360
    if (normalizedOrientation == 0) {
        return bitmap
    }
    val matrix = Matrix().apply {
        postRotate(
            normalizedOrientation.toFloat()
        )
    }
    return Bitmap.createBitmap(
        bitmap,
        0,
        0,
        bitmap.width,
        bitmap.height,
        matrix,
        true
    )
}
