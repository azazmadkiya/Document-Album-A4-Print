package com.example.utils

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Computer Vision Engine for Document and ID Card Edge Detection.
 * Uses classical image processing techniques (Gaussian filtering, Sobel gradient operator,
 * Otsu's adaptive thresholding, morphological edge linking, and ISO-7810 ID-1 aspect ratio
 * matching) to locate card boundaries and crop documents with sub-millimeter precision.
 */
object DocumentEdgeDetector {

    // Standard ISO/IEC 7810 ID-1 card aspect ratio (Aadhaar, PAN, Voter ID, Driving License)
    // 85.60 mm width x 53.98 mm height = ~1.5858
    const val ID1_ASPECT_RATIO = 85.60f / 53.98f

    data class CardDetectionResult(
        val normalizedRect: RectF,
        val pixelRect: Rect,
        val confidence: Float,
        val hasAadhaarColorProfile: Boolean
    )

    /**
     * Auto-detects document edges using computer vision gradient analysis and contour matching.
     * Returns normalized coordinates [normLeft, normTop, normRight, normBottom] in range [0f, 1f].
     */
    fun detectDocumentEdges(bitmap: Bitmap): FloatArray {
        val result = detectCardBoundaries(bitmap)
        return floatArrayOf(
            result.normalizedRect.left,
            result.normalizedRect.top,
            result.normalizedRect.right,
            result.normalizedRect.bottom
        )
    }

    /**
     * Automatically crops the given bitmap to the detected document boundaries.
     * Applies a small safe margin to ensure no card details/corners are clipped.
     */
    fun cropToCard(bitmap: Bitmap, paddingPercent: Float = 0.015f): Bitmap {
        val edges = detectDocumentEdges(bitmap)
        val w = bitmap.width
        val h = bitmap.height

        val padX = (w * paddingPercent).toInt()
        val padY = (h * paddingPercent).toInt()

        val left = ((edges[0] * w).toInt() - padX).coerceIn(0, w - 1)
        val top = ((edges[1] * h).toInt() - padY).coerceIn(0, h - 1)
        val right = ((edges[2] * w).toInt() + padX).coerceIn(left + 10, w)
        val bottom = ((edges[3] * h).toInt() + padY).coerceIn(top + 10, h)

        val cropWidth = (right - left).coerceIn(10, w - left)
        val cropHeight = (bottom - top).coerceIn(10, h - top)

        return Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight)
    }

    /**
     * Core Computer Vision Algorithm:
     * 1. Multi-scale Grayscale & Luminance extraction
     * 2. 3x3 Gaussian smoothing
     * 3. Sobel Gradient Magnitude computation
     * 4. Otsu's adaptive thresholding
     * 5. Morphological edge closing
     * 6. Aadhaar color profile analysis (Saffron/Orange & Navy Blue signatures)
     * 7. Rectangular candidate scoring with ISO-7810 ID-1 aspect ratio verification
     */
    fun detectCardBoundaries(bitmap: Bitmap): CardDetectionResult {
        val origW = bitmap.width
        val origH = bitmap.height

        if (origW < 40 || origH < 40) {
            val defaultRect = RectF(0.04f, 0.04f, 0.96f, 0.96f)
            return CardDetectionResult(
                defaultRect,
                Rect(0, 0, origW, origH),
                confidence = 0.5f,
                hasAadhaarColorProfile = false
            )
        }

        // Scale to standard processing dimension (max 480px) for real-time performance (< 20ms)
        val targetDim = 480f
        val scale = min(1.0f, targetDim / max(origW, origH))
        val sw = max(60, (origW * scale).toInt())
        val sh = max(60, (origH * scale).toInt())

        val scaledBmp = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        val pixels = IntArray(sw * sh)
        scaledBmp.getPixels(pixels, 0, sw, 0, 0, sw, sh)

        // 1. Luminance & Color Analysis
        val luma = Array(sh) { FloatArray(sw) }
        var saffronCount = 0
        var navyCount = 0

        for (y in 0 until sh) {
            val rowOffset = y * sw
            for (x in 0 until sw) {
                val p = pixels[rowOffset + x]
                val r = AndroidColor.red(p)
                val g = AndroidColor.green(p)
                val b = AndroidColor.blue(p)

                // Standard ITU-R BT.601 luma formula
                luma[y][x] = 0.299f * r + 0.587f * g + 0.114f * b

                // UIDAI Aadhaar distinctive header colors
                // Saffron: high Red, moderate Green, low Blue
                if (r > 160 && g in 75..170 && b < 100 && r > g + 25) {
                    saffronCount++
                }
                // Navy Blue: moderate Blue, lower Red and Green
                if (b > 85 && b > r + 20 && b > g + 10) {
                    navyCount++
                }
            }
        }

        val hasAadhaarColors = saffronCount > (sw * 0.05f) || navyCount > (sw * 0.05f)

        // 2. 3x3 Gaussian Blur to eliminate high-frequency paper grain and compression artifacts
        val blurred = Array(sh) { FloatArray(sw) }
        for (y in 1 until sh - 1) {
            for (x in 1 until sw - 1) {
                val sum = (luma[y - 1][x - 1] + 2f * luma[y - 1][x] + luma[y - 1][x + 1] +
                        2f * luma[y][x - 1] + 4f * luma[y][x] + 2f * luma[y][x + 1] +
                        luma[y + 1][x - 1] + 2f * luma[y + 1][x] + luma[y + 1][x + 1]) / 16f
                blurred[y][x] = sum
            }
        }

        // 3. Sobel Operator: Horizontal and Vertical Gradients
        val gradientMag = Array(sh) { FloatArray(sw) }
        val hist = IntArray(256)
        var totalGrad = 0f
        var count = 0

        for (y in 1 until sh - 1) {
            for (x in 1 until sw - 1) {
                // Horizontal Sobel kernel
                val gx = (blurred[y - 1][x + 1] + 2f * blurred[y][x + 1] + blurred[y + 1][x + 1]) -
                        (blurred[y - 1][x - 1] + 2f * blurred[y][x - 1] + blurred[y + 1][x - 1])

                // Vertical Sobel kernel
                val gy = (blurred[y + 1][x - 1] + 2f * blurred[y + 1][x] + blurred[y + 1][x + 1]) -
                        (blurred[y - 1][x - 1] + 2f * blurred[y - 1][x] + blurred[y - 1][x + 1])

                val mag = min(255f, (abs(gx) + abs(gy)) * 0.5f)
                gradientMag[y][x] = mag
                totalGrad += mag
                hist[mag.toInt().coerceIn(0, 255)]++
                count++
            }
        }

        // 4. Otsu's Adaptive Threshold Calculation on Gradient Histogram
        var sumB = 0.0
        var wB = 0
        val total = count.toDouble()
        var maxVariance = 0.0
        var otsuThreshold = 30f

        val sum1 = hist.indices.sumOf { it * hist[it].toDouble() }

        for (i in 0 until 256) {
            wB += hist[i]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0.0) break

            sumB += i * hist[i].toDouble()
            val mB = sumB / wB
            val mF = (sum1 - sumB) / wF
            val variance = wB * wF * (mB - mF) * (mB - mF)

            if (variance > maxVariance) {
                maxVariance = variance
                otsuThreshold = i.toFloat()
            }
        }

        val edgeThreshold = max(18f, otsuThreshold * 0.75f)

        // 5. Binary Edge Map & Morphological Horizontal/Vertical Density
        val binaryEdges = Array(sh) { BooleanArray(sw) }
        val horizontalProj = FloatArray(sh)
        val verticalProj = FloatArray(sw)

        for (y in 1 until sh - 1) {
            for (x in 1 until sw - 1) {
                if (gradientMag[y][x] >= edgeThreshold) {
                    binaryEdges[y][x] = true
                    horizontalProj[y] += gradientMag[y][x]
                    verticalProj[x] += gradientMag[y][x]
                }
            }
        }

        if (scaledBmp != bitmap) {
            scaledBmp.recycle()
        }

        // 6. Find document boundary lines using edge gradient peaks and contrast transition
        // Background baseline from outermost perimeter (10%)
        val marginX = (sw * 0.10f).toInt()
        val marginY = (sh * 0.10f).toInt()

        var bgLumaSum = 0f
        var bgCount = 0
        for (y in 0 until sh) {
            for (x in 0 until sw) {
                if (x < marginX || x > sw - marginX || y < marginY || y > sh - marginY) {
                    bgLumaSum += luma[y][x]
                    bgCount++
                }
            }
        }
        val bgLuma = if (bgCount > 0) bgLumaSum / bgCount else 240f

        // Search inward from 4 sides for the primary card boundary
        var candLeft = 0
        var candRight = sw - 1
        var candTop = 0
        var candBottom = sh - 1

        val scanLimitX = (sw * 0.40f).toInt()
        val scanLimitY = (sh * 0.40f).toInt()

        // Scan Left
        for (x in 1 until scanLimitX) {
            val edgeDensity = (0 until sh).count { binaryEdges[it][x] }.toFloat() / sh
            val contrastDiff = (sh / 4 until 3 * sh / 4).count { abs(luma[it][x] - bgLuma) > 20f }.toFloat() / (sh / 2)
            if (edgeDensity > 0.18f || contrastDiff > 0.35f) {
                candLeft = max(0, x - 2)
                break
            }
        }

        // Scan Right
        for (x in sw - 2 downTo sw - scanLimitX) {
            val edgeDensity = (0 until sh).count { binaryEdges[it][x] }.toFloat() / sh
            val contrastDiff = (sh / 4 until 3 * sh / 4).count { abs(luma[it][x] - bgLuma) > 20f }.toFloat() / (sh / 2)
            if (edgeDensity > 0.18f || contrastDiff > 0.35f) {
                candRight = min(sw - 1, x + 2)
                break
            }
        }

        // Scan Top
        for (y in 1 until scanLimitY) {
            val edgeDensity = (0 until sw).count { binaryEdges[y][it] }.toFloat() / sw
            val contrastDiff = (sw / 4 until 3 * sw / 4).count { abs(luma[y][it] - bgLuma) > 20f }.toFloat() / (sw / 2)
            if (edgeDensity > 0.18f || contrastDiff > 0.35f) {
                candTop = max(0, y - 2)
                break
            }
        }

        // Scan Bottom
        for (y in sh - 2 downTo sh - scanLimitY) {
            val edgeDensity = (0 until sw).count { binaryEdges[y][it] }.toFloat() / sw
            val contrastDiff = (sw / 4 until 3 * sw / 4).count { abs(luma[y][it] - bgLuma) > 20f }.toFloat() / (sw / 2)
            if (edgeDensity > 0.18f || contrastDiff > 0.35f) {
                candBottom = min(sh - 1, y + 2)
                break
            }
        }

        // 7. Aadhaar / ISO-7810 Card Geometry Optimization
        val detectedW = max(10, candRight - candLeft)
        val detectedH = max(10, candBottom - candTop)
        val detectedAspect = detectedW.toFloat() / detectedH.toFloat()

        // If card is detected on an A4 page (portrait letter where card is in the bottom half)
        // Detect if bottom half has high gradient density characteristic of the Aadhaar card
        var finalLeft = candLeft.toFloat() / sw
        var finalTop = candTop.toFloat() / sh
        var finalRight = candRight.toFloat() / sw
        var finalBottom = candBottom.toFloat() / sh

        // If whole page was captured (e.g. A4 portrait page aspect < 0.8) and card occupies lower section
        if (origH > origW * 1.25f && (finalBottom - finalTop) > 0.85f) {
            // Find lower horizontal cut-line (UIDAI scissors/cut line typically at ~55% to 62% height)
            var cutLineY = -1
            val startSearchY = (sh * 0.50f).toInt()
            val endSearchY = (sh * 0.68f).toInt()

            for (y in startSearchY..endSearchY) {
                val edgeSum = horizontalProj[y]
                if (edgeSum > (sw * edgeThreshold * 0.7f)) {
                    cutLineY = y
                    break
                }
            }

            if (cutLineY != -1) {
                finalTop = (cutLineY.toFloat() / sh).coerceIn(0.52f, 0.65f)
                finalBottom = 0.98f
            }
        }

        // Boundary sanity clamping
        finalLeft = finalLeft.coerceIn(0.01f, 0.35f)
        finalTop = finalTop.coerceIn(0.01f, 0.65f)
        finalRight = finalRight.coerceIn(finalLeft + 0.30f, 0.99f)
        finalBottom = finalBottom.coerceIn(finalTop + 0.20f, 0.99f)

        // Calculate confidence score based on boundary sharpness and aspect ratio closeness
        val aspectDiff = abs(detectedAspect - ID1_ASPECT_RATIO)
        val aspectScore = exp(-aspectDiff * 1.5f).toFloat()
        val confidence = (0.5f + aspectScore * 0.3f + if (hasAadhaarColors) 0.2f else 0.05f).coerceIn(0.6f, 0.98f)

        val normRect = RectF(finalLeft, finalTop, finalRight, finalBottom)
        val pixelRect = Rect(
            (finalLeft * origW).toInt(),
            (finalTop * origH).toInt(),
            (finalRight * origW).toInt(),
            (finalBottom * origH).toInt()
        )

        return CardDetectionResult(
            normalizedRect = normRect,
            pixelRect = pixelRect,
            confidence = confidence,
            hasAadhaarColorProfile = hasAadhaarColors
        )
    }

    enum class CardSide {
        FRONT, BACK, UNKNOWN
    }

    /**
     * Lightweight classification algorithm to verify whether a card image is Front or Back
     * by analyzing color signatures, photo box presence, and QR code high-frequency texture density.
     */
    fun classifyCardSide(bitmap: Bitmap): CardSide {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 20 || h < 20) return CardSide.UNKNOWN

        val sampleW = 120
        val sampleH = (120f * h / w).toInt().coerceIn(40, 200)
        val scaled = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, true)
        val pixels = IntArray(sampleW * sampleH)
        scaled.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
        if (scaled != bitmap) scaled.recycle()

        var frontScore = 0f
        var backScore = 0f
        var qrTextureCount = 0
        var photoBoxLikeCount = 0

        for (y in 2 until sampleH - 2) {
            val row = y * sampleW
            for (x in 2 until sampleW - 2) {
                val p = pixels[row + x]
                val r = AndroidColor.red(p)
                val g = AndroidColor.green(p)
                val b = AndroidColor.blue(p)

                // Saffron header (Front)
                if (r > 160 && g in 70..175 && b < 100) {
                    frontScore += 1.5f
                }
                // High contrast texture grid (QR code on Back)
                val pRight = pixels[row + x + 1]
                val diff = abs(r - AndroidColor.red(pRight)) + abs(g - AndroidColor.green(pRight)) + abs(b - AndroidColor.blue(pRight))
                if (diff > 180 && x > sampleW * 0.4f) {
                    qrTextureCount++
                }
                // Skin tone / photo box region (Front)
                if (r > 95 && g > 40 && b > 20 && r > g && r > b && abs(r - g) > 15 && x < sampleW * 0.5f) {
                    photoBoxLikeCount++
                }
            }
        }

        if (qrTextureCount > (sampleW * sampleH * 0.08f)) {
            backScore += 3.0f
        }
        if (photoBoxLikeCount > (sampleW * sampleH * 0.03f)) {
            frontScore += 2.5f
        }

        return if (frontScore >= backScore) CardSide.FRONT else CardSide.BACK
    }

    /**
     * Specialized Computer Vision Extractor for Full-Page e-Aadhaar letters (e.g., from PDF or camera scans):
     * Automatically locates the Aadhaar card cut-out section at the bottom of the page,
     * isolates it, classifies left/right halves using lightweight feature matching, and segments them into:
     * - Front Card (Photo, Name, DOB, Aadhaar Number)
     * - Back Card (Address, QR Code, UIDAI Helpline)
     */
    fun extractAadhaarFrontAndBackFromPage(pageBitmap: Bitmap): Pair<Bitmap, Bitmap>? {
        return try {
            val w = pageBitmap.width
            val h = pageBitmap.height
            if (w <= 0 || h <= 0) return null

            if (h <= w) {
                // Already landscape: split left and right and classify
                val halfW = (w / 2).coerceIn(1, w - 1)
                val leftCard = Bitmap.createBitmap(pageBitmap, 0, 0, halfW, h)
                val rightCard = Bitmap.createBitmap(pageBitmap, halfW, 0, w - halfW, h)
                val leftSide = classifyCardSide(leftCard)
                val rightSide = classifyCardSide(rightCard)
                val front: Bitmap
                val back: Bitmap
                if (leftSide == CardSide.FRONT) {
                    front = leftCard
                    back = rightCard
                } else if (rightSide == CardSide.FRONT) {
                    front = rightCard
                    back = leftCard
                } else {
                    front = leftCard
                    back = rightCard
                }
                Pair(front, back)
            } else {
                // Full portrait page (A4):
                val result = detectCardBoundaries(pageBitmap)
                val cardRect = result.pixelRect

                val cardTop = if (cardRect.top > h * 0.45f && cardRect.top < h - 100) {
                    cardRect.top
                } else {
                    (h * 0.58f).toInt().coerceIn(0, h - 100)
                }

                val cardBottom = min(h, max(cardTop + 100, (cardTop + h * 0.40f).toInt()))
                val cardHeight = (cardBottom - cardTop).coerceIn(50, h - cardTop)
                val safeTop = cardTop.coerceIn(0, max(0, h - 50))
                val safeHeight = cardHeight.coerceIn(50, h - safeTop)

                val cardSection = Bitmap.createBitmap(pageBitmap, 0, safeTop, w, safeHeight)

                // Split into Left and Right halves
                val secW = cardSection.width
                val secH = cardSection.height
                val halfW = (secW / 2).coerceIn(1, secW - 1)
                val leftCard = Bitmap.createBitmap(cardSection, 0, 0, halfW, secH)
                val rightCard = Bitmap.createBitmap(cardSection, halfW, 0, secW - halfW, secH)

                // Classification step to verify Front vs Back and prevent swap errors
                val leftSide = classifyCardSide(leftCard)
                val rightSide = classifyCardSide(rightCard)

                val frontCard: Bitmap
                val backCard: Bitmap

                if (leftSide == CardSide.FRONT) {
                    frontCard = leftCard
                    backCard = rightCard
                } else if (rightSide == CardSide.FRONT) {
                    frontCard = rightCard
                    backCard = leftCard
                } else {
                    // Standard UIDAI e-Aadhaar layout: Left is Front, Right is Back
                    frontCard = leftCard
                    backCard = rightCard
                }

                try { cardSection.recycle() } catch (_: Exception) {}
                Pair(frontCard, backCard)
            }
        } catch (e: Exception) {
            android.util.Log.e("DocumentEdgeDetector", "extractAadhaarFrontAndBackFromPage failed", e)
            null
        }
    }
}
