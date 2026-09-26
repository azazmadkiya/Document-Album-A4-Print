package com.example.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.Bundle
import android.os.CancellationSignal
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.provider.MediaStore
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

enum class CardPrintSize(
    val title: String,
    val description: String,
    val widthMm: Float
) {
    AUTO_BEST("Auto Best (Recommended)", "Clear, balanced & sharp (~130 mm)", 130f),
    REAL_CARD("Real Card (1:1)", "Exact physical wallet size (85.6 mm)", 85.6f),
    COMPACT("Compact", "Slim fit for notes/signature (~105 mm)", 105f),
    LARGE("Large Doc", "Full-width certificate style (~160 mm)", 160f)
}

object A4DocumentGenerator {

    const val A4_WIDTH_PX = 2480
    const val A4_HEIGHT_PX = 3508
    private const val MM_TO_PX = A4_WIDTH_PX.toFloat() / 210f

    fun generateA4Bitmap(
        context: Context,
        title: String,
        aadhaarFrontUri: Uri?,
        aadhaarBackUri: Uri?,
        panFrontUri: Uri?,
        panBackUri: Uri?,
        voterFrontUri: Uri?,
        voterBackUri: Uri?,
        dlFrontUri: Uri? = null,
        dlBackUri: Uri? = null,
        studentFrontUri: Uri? = null,
        studentBackUri: Uri? = null,
        coverFrontUri: Uri?,
        coverBackUri: Uri?,
        layoutStyle: String = "MULTI_ID_GRID",
        cardPrintSize: CardPrintSize = CardPrintSize.AUTO_BEST,
        filterType: String = "COLOR",
        showCutGuides: Boolean = true,
        showLabels: Boolean = true,
        showVerticalMargin: Boolean = false,
        showHorizontalMargin: Boolean = false,
        cardScale: Float = 1.0f
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(A4_WIDTH_PX, A4_HEIGHT_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        var aF = loadBitmap(context, aadhaarFrontUri)
        var aB = loadBitmap(context, aadhaarBackUri)
        var pF = loadBitmap(context, panFrontUri)
        var pB = loadBitmap(context, panBackUri)
        var vF = loadBitmap(context, voterFrontUri)
        var vB = loadBitmap(context, voterBackUri)
        var dF = loadBitmap(context, dlFrontUri)
        var dB = loadBitmap(context, dlBackUri)
        var sF = loadBitmap(context, studentFrontUri)
        var sB = loadBitmap(context, studentBackUri)
        var cF = loadBitmap(context, coverFrontUri)
        var cB = loadBitmap(context, coverBackUri)

        if (filterType == "BW") {
            aF = aF?.let { applyBwFilter(it) }
            aB = aB?.let { applyBwFilter(it) }
            pF = pF?.let { applyBwFilter(it) }
            pB = pB?.let { applyBwFilter(it) }
            vF = vF?.let { applyBwFilter(it) }
            vB = vB?.let { applyBwFilter(it) }
            dF = dF?.let { applyBwFilter(it) }
            dB = dB?.let { applyBwFilter(it) }
            sF = sF?.let { applyBwFilter(it) }
            sB = sB?.let { applyBwFilter(it) }
            cF = cF?.let { applyBwFilter(it) }
            cB = cB?.let { applyBwFilter(it) }
        }

        if (layoutStyle == "MULTI_ID_GRID" || layoutStyle == "MULTI_ID_GRID_CENTERED") {
            val pairs = mutableListOf<Pair<Bitmap?, Bitmap?>>()
            if (pF != null || pB != null) pairs.add(Pair(pF, pB))
            if (vF != null || vB != null) pairs.add(Pair(vF, vB))
            if (aF != null || aB != null) pairs.add(Pair(aF, aB))
            if (dF != null || dB != null) pairs.add(Pair(dF, dB))
            if (sF != null || sB != null) pairs.add(Pair(sF, sB))
            if (cF != null || cB != null) pairs.add(Pair(cF, cB))

            if (pairs.isEmpty()) {
                pairs.add(Pair(pF, pB))
            }

            if (layoutStyle == "MULTI_ID_GRID") {
                drawMultiIdGridCustom(canvas, pairs, showCutGuides, showLabels, showVerticalMargin, showHorizontalMargin, cardScale)
            } else {
                drawCenteredMultiIdGridCustom(canvas, pairs, showCutGuides, showLabels, showVerticalMargin, showHorizontalMargin, cardScale)
            }
        } else {
            val baseTargetW = (cardPrintSize.widthMm * MM_TO_PX).toInt()
            drawStackedCards(
                canvas = canvas,
                frontBmp = aF,
                backBmp = aB,
                targetW = baseTargetW,
                contentTop = 160f,
                availableH = A4_HEIGHT_PX - 320f,
                showCutGuides = showCutGuides,
                showLabels = showLabels,
                showVerticalMargin = showVerticalMargin,
                showHorizontalMargin = showHorizontalMargin
            )
        }

        return bitmap
    }

    private fun drawMultiIdGridCustom(
        canvas: Canvas,
        pairs: List<Pair<Bitmap?, Bitmap?>>,
        showCutGuides: Boolean,
        showLabels: Boolean,
        showVerticalMargin: Boolean,
        showHorizontalMargin: Boolean,
        cardScale: Float
    ) {
        val numRows = max(1, pairs.size)
        val cardWidthMm = 85.6f * cardScale
        val maxAllowedH = (A4_HEIGHT_PX.toFloat() - 120f) / numRows / MM_TO_PX
        val cardHeightMm = min(53.98f * cardScale, maxAllowedH * cardScale)

        val cardW = cardWidthMm * MM_TO_PX
        val cardH = cardHeightMm * MM_TO_PX

        val hGap = if (showHorizontalMargin) 40f else 20f
        val totalGridW = (cardW * 2f) + hGap
        val startX = if (showHorizontalMargin) {
            80f
        } else {
            ((A4_WIDTH_PX.toFloat() - totalGridW) / 2f).coerceAtLeast(20f)
        }

        val startY = if (showVerticalMargin) 80f else 60f
        val vGap = if (showVerticalMargin) 40f else 15f

        pairs.forEachIndexed { index, (leftBmp, rightBmp) ->
            val cardTop = startY + (index * (cardH + vGap))

            drawCardItem(
                canvas = canvas,
                bmp = leftBmp,
                left = startX,
                top = cardTop,
                width = cardW,
                height = cardH,
                showCutGuides = showCutGuides
            )

            val backLeft = startX + cardW + hGap
            drawCardItem(
                canvas = canvas,
                bmp = rotateBitmap180(rightBmp),
                left = backLeft,
                top = cardTop,
                width = cardW,
                height = cardH,
                showCutGuides = showCutGuides
            )
        }
    }

    private fun drawCenteredMultiIdGridCustom(
        canvas: Canvas,
        pairs: List<Pair<Bitmap?, Bitmap?>>,
        showCutGuides: Boolean,
        showLabels: Boolean,
        showVerticalMargin: Boolean,
        showHorizontalMargin: Boolean,
        cardScale: Float
    ) {
        val numRows = max(1, pairs.size)
        val cardWidthMm = 54.0f * cardScale
        val maxAllowedH = (A4_HEIGHT_PX.toFloat() - 120f) / numRows / MM_TO_PX
        val cardHeightMm = min(85.6f * cardScale, maxAllowedH * cardScale)
        val cardW = cardWidthMm * MM_TO_PX
        val cardH = cardHeightMm * MM_TO_PX
        val hGap = if (showHorizontalMargin) 40f else 20f
        val totalGridW = (cardW * 2f) + hGap
        val vGap = if (showVerticalMargin) 40f else 15f
        val totalGridH = (cardH * numRows) + (vGap * (numRows - 1))
        val startX = (A4_WIDTH_PX.toFloat() - totalGridW) / 2f
        val startY = (A4_HEIGHT_PX.toFloat() - totalGridH) / 2f

        pairs.forEachIndexed { index, (leftBmp, rightBmp) ->
            val cardTop = startY + (index * (cardH + vGap))

            drawCardItem(
                canvas = canvas,
                bmp = leftBmp,
                left = startX,
                top = cardTop,
                width = cardW,
                height = cardH,
                showCutGuides = showCutGuides
            )

            val backLeft = startX + cardW + hGap
            drawCardItem(
                canvas = canvas,
                bmp = rotateBitmap180(rightBmp),
                left = backLeft,
                top = cardTop,
                width = cardW,
                height = cardH,
                showCutGuides = showCutGuides
            )
        }
    }

    private fun rotateBitmap180(bmp: Bitmap?): Bitmap? {
        if (bmp == null) return null
        return try {
            val matrix = Matrix().apply { postRotate(180f) }
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        } catch (e: Exception) {
            bmp
        }
    }

    private fun drawStackedCards(
        canvas: Canvas,
        frontBmp: Bitmap?,
        backBmp: Bitmap?,
        targetW: Int,
        contentTop: Float,
        availableH: Float,
        showCutGuides: Boolean,
        showLabels: Boolean,
        showVerticalMargin: Boolean,
        showHorizontalMargin: Boolean
    ) {
        val hMargin = if (showHorizontalMargin) 80f else 0f
        val cardW = A4_WIDTH_PX.toFloat() - (hMargin * 2f)
        val vMargin = if (showVerticalMargin) 80f else 0f
        val vGap = if (showVerticalMargin) 40f else 0f
        val cardH = (A4_HEIGHT_PX.toFloat() - (vMargin * 2f) - vGap) / 2f

        val top1 = vMargin
        val top2 = top1 + cardH + vGap

        drawCardItem(canvas, frontBmp, hMargin, top1, cardW, cardH, showCutGuides)
        drawCardItem(canvas, backBmp, hMargin, top2, cardW, cardH, showCutGuides)
    }

    private fun drawCardItem(
        canvas: Canvas,
        bmp: Bitmap?,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        showCutGuides: Boolean
    ) {
        val destRect = RectF(left, top, left + width, top + height)

        if (bmp != null) {
            val srcRect = Rect(0, 0, bmp.width, bmp.height)
            canvas.drawBitmap(bmp, srcRect, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#F8FAFC")
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(destRect, 12f, 12f, bgPaint)
        }

        if (showCutGuides) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#CBD5E1")
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            canvas.drawRoundRect(destRect, 10f, 10f, borderPaint)

            val cutMarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#94A3B8")
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
            }
            val markLen = 24f
            val offset = 12f

            canvas.drawLine(left - offset - markLen, top - offset, left - offset, top - offset, cutMarkPaint)
            canvas.drawLine(left - offset, top - offset - markLen, left - offset, top - offset, cutMarkPaint)

            canvas.drawLine(left + width + offset, top - offset, left + width + offset + markLen, top - offset, cutMarkPaint)
            canvas.drawLine(left + width + offset, top - offset - markLen, left + width + offset, top - offset, cutMarkPaint)

            canvas.drawLine(left - offset - markLen, top + height + offset, left - offset, top + height + offset, cutMarkPaint)
            canvas.drawLine(left - offset, top + height + offset, left - offset, top + height + offset + markLen, cutMarkPaint)

            canvas.drawLine(left + width + offset, top + height + offset, left + width + offset + markLen, top + height + offset, cutMarkPaint)
            canvas.drawLine(left + width + offset, top + height + offset, left + width + offset, top + height + offset + markLen, cutMarkPaint)
        }
    }

    private fun loadBitmap(context: Context, uri: Uri?): Bitmap? {
        if (uri == null) return null
        return try {
            val stream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(stream)
        } catch (e: Exception) {
            null
        }
    }

    private fun applyBwFilter(src: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val matrix = ColorMatrix()
        matrix.setSaturation(0f)

        val contrastMatrix = ColorMatrix(floatArrayOf(
            1.25f, 0f, 0f, 0f, -25f,
            0f, 1.25f, 0f, 0f, -25f,
            0f, 0f, 1.25f, 0f, -25f,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(contrastMatrix)
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }

    fun saveA4BitmapToStorage(
        context: Context,
        bitmap: Bitmap,
        title: String
    ): Uri? {
        val sanitizedTitle = title.ifBlank { "Document_Album" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val filename = "DocAlbum_${sanitizedTitle}_${System.currentTimeMillis()}.jpg"

        return try {
            val resolver = context.contentResolver
            val uri: Uri?
            val fos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "Document Album A4 Print")
                }
                uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let { resolver.openOutputStream(it) }
            } else {
                val imagesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Document Album A4 Print")
                if (!imagesDir.exists()) imagesDir.mkdirs()
                val imageFile = File(imagesDir, filename)
                uri = Uri.fromFile(imageFile)
                FileOutputStream(imageFile)
            }

            fos?.use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
                Toast.makeText(context, "Saved Image to Pictures/Document Album A4 Print!", Toast.LENGTH_LONG).show()
            }
            uri
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to save image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    fun saveA4PdfToStorage(
        context: Context,
        bitmap: Bitmap,
        title: String
    ): Uri? {
        val sanitizedTitle = title.ifBlank { "Document_Album" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val filename = "DocAlbum_${sanitizedTitle}_${System.currentTimeMillis()}.pdf"

        return try {
            val resolver = context.contentResolver
            val uri: Uri?
            val fos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + File.separator + "Document Album A4 Print")
                }
                uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                uri?.let { resolver.openOutputStream(it) }
            } else {
                val docsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Document Album A4 Print")
                if (!docsDir.exists()) docsDir.mkdirs()
                val pdfFile = File(docsDir, filename)
                uri = Uri.fromFile(pdfFile)
                FileOutputStream(pdfFile)
            }

            fos?.use { outputStream ->
                val pdfDoc = android.graphics.pdf.PdfDocument()
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(A4_WIDTH_PX, A4_HEIGHT_PX, 1).create()
                val page = pdfDoc.startPage(pageInfo)
                page.canvas.drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
                pdfDoc.finishPage(page)
                pdfDoc.writeTo(outputStream)
                pdfDoc.close()
                Toast.makeText(context, "Saved PDF to Documents/Document Album A4 Print!", Toast.LENGTH_LONG).show()
            }
            uri
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to save PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    fun shareA4Image(context: Context, imageUri: Uri, title: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "A4 Document Album Image: $title")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share A4 Image"))
    }

    fun shareA4Pdf(context: Context, pdfUri: Uri, title: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, pdfUri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "A4 Document Album PDF: $title")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share A4 PDF"))
    }

    fun printA4Bitmap(
        context: Context,
        bitmap: Bitmap,
        docTitle: String
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Toast.makeText(context, "Print service unavailable on this device", Toast.LENGTH_SHORT).show()
            return
        }

        val jobName = "DocAlbum_${docTitle.ifBlank { "Document" }}"

        val printAdapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }

                val info = PrintDocumentInfo.Builder(jobName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()

                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                if (destination == null) {
                    callback?.onWriteFailed("Missing destination file")
                    return
                }

                val pdfDocument = PrintedPdfDocument(
                    context,
                    PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build()
                )

                try {
                    val page = pdfDocument.startPage(1)
                    val canvas = page.canvas
                    val pageW = page.info.pageWidth
                    val pageH = page.info.pageHeight

                    val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
                    val destRect = Rect(0, 0, pageW, pageH)
                    canvas.drawBitmap(bitmap, srcRect, destRect, Paint(Paint.FILTER_BITMAP_FLAG))

                    pdfDocument.finishPage(page)

                    FileOutputStream(destination.fileDescriptor).use { out ->
                        pdfDocument.writeTo(out)
                    }

                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.localizedMessage)
                } finally {
                    pdfDocument.close()
                }
            }
        }

        printManager.print(
            jobName,
            printAdapter,
            PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .build()
        )
    }
}
