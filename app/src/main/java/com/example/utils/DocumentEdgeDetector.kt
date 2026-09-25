package com.example.utils

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object DocumentEdgeDetector {

    /**
     * Auto-detects document edges using standard Kotlin/Android Bitmap operations.
     * Analyzes luminance gradients and background contrast to locate document boundaries.
     * Returns normalized coordinates [normLeft, normTop, normRight, normBottom] in range [0f, 1f].
     */
    fun detectDocumentEdges(bitmap: Bitmap): FloatArray {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 50 || height < 50) {
            return floatArrayOf(0.05f, 0.05f, 0.95f, 0.95f)
        }

        // Downsample for fast analysis (max 300x300)
        val scale = min(1.0f, 300f / max(width, height))
        val sampleW = max(50, (width * scale).toInt())
        val sampleH = max(50, (height * scale).toInt())
        
        val scaledBmp = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, true)
        
        // Extract grayscale / luminance values
        val pixels = IntArray(sampleW * sampleH)
        scaledBmp.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
        
        val luminance = Array(sampleH) { y ->
            FloatArray(sampleW) { x ->
                val pixel = pixels[y * sampleW + x]
                val r = AndroidColor.red(pixel)
                val g = AndroidColor.green(pixel)
                val b = AndroidColor.blue(pixel)
                // Standard luma formula
                0.299f * r + 0.587f * g + 0.114f * b
            }
        }
        
        if (scaledBmp != bitmap) {
            scaledBmp.recycle()
        }

        // Sample background color from 4 corners
        val cornerLuminances = floatArrayOf(
            luminance[0][0],
            luminance[0][sampleW - 1],
            luminance[sampleH - 1][0],
            luminance[sampleH - 1][sampleW - 1]
        )
        val bgLuma = cornerLuminances.average().toFloat()
        
        // Threshold for edge detection based on contrast from background
        val threshold = max(25f, abs(bgLuma * 0.15f))

        var left = 0
        var right = sampleW - 1
        var top = 0
        var bottom = sampleH - 1

        // Scan Left edge inward
        outerLeft@ for (x in 0 until sampleW / 3) {
            var diffCount = 0
            for (y in sampleH / 4 until 3 * sampleH / 4) {
                if (abs(luminance[y][x] - bgLuma) > threshold) {
                    diffCount++
                }
            }
            if (diffCount > (sampleH / 4) * 0.4f) {
                left = max(0, x - 2)
                break@outerLeft
            }
        }

        // Scan Right edge inward
        outerRight@ for (x in sampleW - 1 downTo 2 * sampleW / 3) {
            var diffCount = 0
            for (y in sampleH / 4 until 3 * sampleH / 4) {
                if (abs(luminance[y][x] - bgLuma) > threshold) {
                    diffCount++
                }
            }
            if (diffCount > (sampleH / 4) * 0.4f) {
                right = min(sampleW - 1, x + 2)
                break@outerRight
            }
        }

        // Scan Top edge inward
        outerTop@ for (y in 0 until sampleH / 3) {
            var diffCount = 0
            for (x in sampleW / 4 until 3 * sampleW / 4) {
                if (abs(luminance[y][x] - bgLuma) > threshold) {
                    diffCount++
                }
            }
            if (diffCount > (sampleW / 4) * 0.4f) {
                top = max(0, y - 2)
                break@outerTop
            }
        }

        // Scan Bottom edge inward
        outerBottom@ for (y in sampleH - 1 downTo 2 * sampleH / 3) {
            var diffCount = 0
            for (x in sampleW / 4 until 3 * sampleW / 4) {
                if (abs(luminance[y][x] - bgLuma) > threshold) {
                    diffCount++
                }
            }
            if (diffCount > (sampleW / 4) * 0.4f) {
                bottom = min(sampleH - 1, y + 2)
                break@outerBottom
            }
        }

        // Validate bounds and fallback if invalid
        if (right <= left + 20 || bottom <= top + 20) {
            return floatArrayOf(0.06f, 0.08f, 0.94f, 0.92f)
        }

        val normLeft = (left.toFloat() / sampleW).coerceIn(0f, 0.25f)
        val normTop = (top.toFloat() / sampleH).coerceIn(0f, 0.25f)
        val normRight = (right.toFloat() / sampleW).coerceIn(0.75f, 1f)
        val normBottom = (bottom.toFloat() / sampleH).coerceIn(0.75f, 1f)

        return floatArrayOf(normLeft, normTop, normRight, normBottom)
    }
}
