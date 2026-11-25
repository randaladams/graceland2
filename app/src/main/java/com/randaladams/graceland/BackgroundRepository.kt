package com.randaladams.graceland

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads Graceland background images organized by time of day from
 * `assets/backgrounds/{morning,midday,dusk,night}/`.
 */
class BackgroundRepository(private val context: Context) {

    private val supportedExtensions = setOf("jpg", "jpeg", "png", "webp")

    fun listAssetPaths(period: GracelandMath.TimeOfDayPeriod): List<String> {
        val folder = "backgrounds/${period.folderName}"
        val files = context.assets.list(folder)?.sorted().orEmpty()
        return files
            .filter { fileName ->
                val ext = fileName.substringAfterLast('.', "").lowercase()
                ext in supportedExtensions
            }
            .map { "$folder/$it" }
    }

    suspend fun loadScaledBitmap(
        assetPath: String,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(assetPath).use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds, reqWidth.coerceAtLeast(720), reqHeight.coerceAtLeast(1280))
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            context.assets.open(assetPath).use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
        }.getOrNull()
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
