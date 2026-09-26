package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.ui.components.ImageEditorDialog
import com.example.ui.components.PasswordInputDialog
import com.example.ui.components.PrintPreviewDialog
import com.example.ui.viewmodel.DocViewModel
import android.os.ParcelFileDescriptor
import com.example.utils.A4DocumentGenerator
import com.example.utils.CardPrintSize
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.rendering.PDFRenderer as PDFBoxRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: DocViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val title by viewModel.docTitle.collectAsState()

    val aadhaarFront by viewModel.aadhaarFront.collectAsState()
    val aadhaarBack by viewModel.aadhaarBack.collectAsState()
    val panFront by viewModel.panFront.collectAsState()
    val panBack by viewModel.panBack.collectAsState()
    val voterFront by viewModel.voterFront.collectAsState()
    val voterBack by viewModel.voterBack.collectAsState()
    val dlFront by viewModel.dlFront.collectAsState()
    val dlBack by viewModel.dlBack.collectAsState()
    val studentFront by viewModel.studentFront.collectAsState()
    val studentBack by viewModel.studentBack.collectAsState()
    val coverFront by viewModel.coverFront.collectAsState()
    val coverBack by viewModel.coverBack.collectAsState()

    val layoutStyle by viewModel.layoutStyle.collectAsState()
    val filterType by viewModel.filterType.collectAsState()
    val cardPrintSize by viewModel.cardPrintSize.collectAsState()
    val showCutGuides by viewModel.showCutGuides.collectAsState()
    val showLabels by viewModel.showLabels.collectAsState()
    val showVerticalMargin by viewModel.showVerticalMargin.collectAsState()
    val showHorizontalMargin by viewModel.showHorizontalMargin.collectAsState()
    val cardScale by viewModel.cardScale.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Aadhaar, 1: PAN, 2: Voter, 3: DL, 4: A4 Studio

    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }
    var editingSlot by remember { mutableStateOf<String?>(null) }
    var startInCropMode by remember { mutableStateOf(false) }
    var showPreviewDialog by remember { mutableStateOf(false) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    fun createTempUri(): Uri {
        val file = File(context.cacheDir, "doc_capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && editingSlot != null) {
            pendingImageUri = uri
            startInCropMode = true
        } else {
            editingSlot = null
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null && editingSlot != null) {
            pendingImageUri = tempCameraUri
            startInCropMode = true
        } else {
            editingSlot = null
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val slot = editingSlot ?: "AADHAAR_FRONT"
            val uri = createTempUri()
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchGallery(slot: String) {
        editingSlot = slot
        galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun launchCamera(slot: String) {
        editingSlot = slot
        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
    }

    var pendingCardTypeForPdf by remember { mutableStateOf<String?>(null) }
    var pdfErrorState by remember { mutableStateOf<String?>(null) }
    var showPdfPasswordDialog by remember { mutableStateOf(false) }
    var pdfPasswordInput by remember { mutableStateOf("") }
    var pendingPasswordProtectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var pendingPasswordProtectedCardType by remember { mutableStateOf<String?>(null) }
    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var exportPdfPassword by remember { mutableStateOf("") }

    fun processPdfToFrontAndBack(pdfUri: Uri, cardType: String, password: String = "") {
        android.util.Log.i("EditorScreen", "Processing PDF for cardType=$cardType, uri=$pdfUri, hasPassword=${password.isNotEmpty()}")
        try {
            PDFBoxResourceLoader.init(context)
        } catch (_: Exception) {}

        try {
            val inputStream = context.contentResolver.openInputStream(pdfUri)
            if (inputStream == null) {
                Toast.makeText(context, "Could not open selected PDF file", Toast.LENGTH_SHORT).show()
                return
            }

            // Step 1: Open with PDFBox (decrypts if password provided)
            val doc: PDDocument = try {
                if (password.isNotEmpty()) {
                    PDDocument.load(inputStream, password.trim())
                } else {
                    PDDocument.load(inputStream)
                }
            } catch (e: Exception) {
                inputStream.close()
                android.util.Log.w("EditorScreen", "PDDocument.load failed: ${e.message}", e)
                val isWrongPassword = password.isNotEmpty() || e is InvalidPasswordException || e.message?.contains("password", ignoreCase = true) == true
                if (isWrongPassword) {
                    val err = "Incorrect Password. Please check and try again."
                    pdfErrorState = err
                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    pendingPasswordProtectedPdfUri = pdfUri
                    pendingPasswordProtectedCardType = cardType
                    showPdfPasswordDialog = true
                } else {
                    val err = "Password required to open this PDF document"
                    pdfErrorState = err
                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    pendingPasswordProtectedPdfUri = pdfUri
                    pendingPasswordProtectedCardType = cardType
                    showPdfPasswordDialog = true
                }
                return
            }

            // If loaded without password but document is encrypted
            if (doc.isEncrypted && password.isEmpty()) {
                doc.close()
                inputStream.close()
                val err = "Password required to open this PDF document"
                pdfErrorState = err
                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                pendingPasswordProtectedPdfUri = pdfUri
                pendingPasswordProtectedCardType = cardType
                showPdfPasswordDialog = true
                return
            }

            // Step 2: Remove encryption security and save unencrypted PDF to cache
            val decryptedPdfFile = File(context.cacheDir, "decrypted_${System.currentTimeMillis()}.pdf")
            try {
                doc.isAllSecurityToBeRemoved = true
                doc.save(decryptedPdfFile)
            } catch (saveEx: Exception) {
                android.util.Log.w("EditorScreen", "doc.save failed", saveEx)
            }
            val totalPages = doc.numberOfPages

            fun saveBmpToUri(bmp: Bitmap, prefix: String): Uri? {
                return try {
                    val file = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.jpg")
                    val out = FileOutputStream(file)
                    bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    out.flush()
                    out.close()
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                } catch (e: Exception) {
                    android.util.Log.e("EditorScreen", "Failed to save bitmap", e)
                    null
                }
            }

            // Step 3: Render Page 0 (and Page 1 if exists) from the real decrypted document
            var bmp0: Bitmap? = null
            var bmp1: Bitmap? = null

            // First attempt: native PdfRenderer on decrypted PDF file
            if (decryptedPdfFile.exists() && decryptedPdfFile.length() > 0) {
                try {
                    val pfd = ParcelFileDescriptor.open(decryptedPdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = android.graphics.pdf.PdfRenderer(pfd)
                    val renderScale = 2

                    val page0 = renderer.openPage(0)
                    bmp0 = Bitmap.createBitmap(page0.width * renderScale, page0.height * renderScale, Bitmap.Config.ARGB_8888)
                    val canvas0 = android.graphics.Canvas(bmp0)
                    canvas0.drawColor(android.graphics.Color.WHITE)
                    page0.render(bmp0, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    page0.close()

                    if (renderer.pageCount > 1) {
                        val page1 = renderer.openPage(1)
                        bmp1 = Bitmap.createBitmap(page1.width * renderScale, page1.height * renderScale, Bitmap.Config.ARGB_8888)
                        val canvas1 = android.graphics.Canvas(bmp1)
                        canvas1.drawColor(android.graphics.Color.WHITE)
                        page1.render(bmp1, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        page1.close()
                    }
                    renderer.close()
                    pfd.close()
                } catch (nativeEx: Exception) {
                    android.util.Log.w("EditorScreen", "Native PdfRenderer on decrypted file failed: ${nativeEx.message}", nativeEx)
                    bmp0 = null
                    bmp1 = null
                }
            }

            // Second attempt: PDFBox direct renderer if native renderer didn't succeed
            if (bmp0 == null) {
                try {
                    val pdfBoxRenderer = PDFBoxRenderer(doc)
                    bmp0 = pdfBoxRenderer.renderImageWithDPI(0, 180f)
                    if (totalPages > 1) {
                        bmp1 = pdfBoxRenderer.renderImageWithDPI(1, 180f)
                    }
                } catch (pdfBoxRenderEx: Exception) {
                    android.util.Log.e("EditorScreen", "PDFBox direct renderer failed: ${pdfBoxRenderEx.message}", pdfBoxRenderEx)
                }
            }

            doc.close()
            inputStream.close()
            try { decryptedPdfFile.delete() } catch (_: Exception) {}

            if (bmp0 == null) {
                Toast.makeText(context, "Could not render pages from PDF file", Toast.LENGTH_SHORT).show()
                return
            }

            // Step 4: Auto-crop Front & Back based on card type
            var frontBmp: Bitmap? = null
            var backBmp: Bitmap? = null

            if (cardType == "AADHAAR") {
                if (bmp1 != null) {
                    // 2-page Aadhaar: Page 0 is Front, Page 1 is Back
                    frontBmp = bmp0
                    backBmp = bmp1
                } else {
                    // Standard 1-page e-Aadhaar PDF:
                    // Use Computer Vision edge detection to detect cut-out boundaries
                    val extracted = com.example.utils.DocumentEdgeDetector.extractAadhaarFrontAndBackFromPage(bmp0)
                    if (extracted != null) {
                        frontBmp = extracted.first
                        backBmp = extracted.second
                    } else if (bmp0.height > bmp0.width) {
                        val cardTop = (bmp0.height * 0.58f).toInt().coerceIn(0, bmp0.height - 100)
                        val cardHeight = (bmp0.height * 0.40f).toInt().coerceIn(50, bmp0.height - cardTop)
                        val halfWidth = bmp0.width / 2

                        frontBmp = Bitmap.createBitmap(bmp0, 0, cardTop, halfWidth, cardHeight)
                        backBmp = Bitmap.createBitmap(bmp0, halfWidth, cardTop, bmp0.width - halfWidth, cardHeight)
                    } else {
                        // Landscape Aadhaar: split left & right
                        val halfWidth = bmp0.width / 2
                        frontBmp = Bitmap.createBitmap(bmp0, halfWidth, 0, bmp0.width - halfWidth, bmp0.height)
                        backBmp = Bitmap.createBitmap(bmp0, 0, 0, halfWidth, bmp0.height)
                    }
                }
            } else {
                // PAN or VOTER card
                if (bmp1 != null) {
                    frontBmp = bmp0
                    backBmp = bmp1
                } else {
                    if (bmp0.height > bmp0.width) {
                        val halfHeight = bmp0.height / 2
                        frontBmp = Bitmap.createBitmap(bmp0, 0, 0, bmp0.width, halfHeight)
                        backBmp = Bitmap.createBitmap(bmp0, 0, halfHeight, bmp0.width, bmp0.height - halfHeight)
                    } else {
                        val halfWidth = bmp0.width / 2
                        frontBmp = Bitmap.createBitmap(bmp0, 0, 0, halfWidth, bmp0.height)
                        backBmp = Bitmap.createBitmap(bmp0, halfWidth, 0, bmp0.width - halfWidth, bmp0.height)
                    }
                }
            }

            val frontUri = frontBmp?.let { saveBmpToUri(it, "${cardType.lowercase()}_front") }
            val backUri = backBmp?.let { saveBmpToUri(it, "${cardType.lowercase()}_back") }

            when (cardType) {
                "AADHAAR" -> {
                    frontUri?.let { viewModel.setAadhaarFront(it) }
                    backUri?.let { viewModel.setAadhaarBack(it) }
                }
                "PAN" -> {
                    frontUri?.let { viewModel.setPanFront(it) }
                    backUri?.let { viewModel.setPanBack(it) }
                }
                "VOTER" -> {
                    frontUri?.let { viewModel.setVoterFront(it) }
                    backUri?.let { viewModel.setVoterBack(it) }
                }
            }

            pdfErrorState = null
            showPdfPasswordDialog = false
            pdfPasswordInput = ""
            pendingPasswordProtectedPdfUri = null
            pendingPasswordProtectedCardType = null

            Toast.makeText(context, "$cardType PDF decrypted & imported to Front & Back!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            android.util.Log.e("EditorScreen", "PDF processing exception: ${e.message}", e)
            val isWrongPassword = password.isNotEmpty()
            if (isWrongPassword) {
                val err = "Incorrect Password. Please check and try again."
                pdfErrorState = err
                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                pendingPasswordProtectedPdfUri = pdfUri
                pendingPasswordProtectedCardType = cardType
                showPdfPasswordDialog = true
            } else {
                val err = "PDF import failed: Password required to access document"
                pdfErrorState = err
                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                pendingPasswordProtectedPdfUri = pdfUri
                pendingPasswordProtectedCardType = cardType
                showPdfPasswordDialog = true
            }
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { pdfUri ->
            pendingCardTypeForPdf?.let { cardType ->
                processPdfToFrontAndBack(pdfUri, cardType)
            }
        }
        pendingCardTypeForPdf = null
    }

    fun launchPdfPicker(cardType: String) {
        pendingCardTypeForPdf = cardType
        pdfPickerLauncher.launch(arrayOf("application/pdf"))
    }

    fun rotateImageUri(uri: Uri): Uri? {
        return try {
            val stream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(stream)
            stream.close()
            if (bitmap == null) return null

            val matrix = Matrix().apply { postRotate(90f) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

            val file = File(context.cacheDir, "rotated_${System.currentTimeMillis()}.jpg")
            val out = FileOutputStream(file)
            rotated.compress(Bitmap.CompressFormat.JPEG, 95, out)
            out.flush()
            out.close()

            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            null
        }
    }

    if (pendingImageUri != null && editingSlot != null) {
        ImageEditorDialog(
            imageUri = pendingImageUri!!,
            initialCropMode = startInCropMode,
            onDismiss = {
                pendingImageUri = null
                editingSlot = null
                startInCropMode = false
            },
            onConfirm = { editedUri ->
                when (editingSlot) {
                    "AADHAAR_FRONT" -> viewModel.setAadhaarFront(editedUri)
                    "AADHAAR_BACK" -> viewModel.setAadhaarBack(editedUri)
                    "PAN_FRONT" -> viewModel.setPanFront(editedUri)
                    "PAN_BACK" -> viewModel.setPanBack(editedUri)
                    "VOTER_FRONT" -> viewModel.setVoterFront(editedUri)
                    "VOTER_BACK" -> viewModel.setVoterBack(editedUri)
                    "DL_FRONT" -> viewModel.setDlFront(editedUri)
                    "DL_BACK" -> viewModel.setDlBack(editedUri)
                    "STUDENT_FRONT" -> viewModel.setStudentFront(editedUri)
                    "STUDENT_BACK" -> viewModel.setStudentBack(editedUri)
                    "COVER_FRONT" -> viewModel.setCoverFront(editedUri)
                    "COVER_BACK" -> viewModel.setCoverBack(editedUri)
                }
                Toast.makeText(context, "Cropped & saved successfully!", Toast.LENGTH_SHORT).show()
                pendingImageUri = null
                editingSlot = null
                startInCropMode = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Document Album A4 Print", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.saveDocument {
                            Toast.makeText(context, "Saved to history!", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Save Album")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 8.dp
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = { Text("Aadhaar") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = { Text("PAN Card") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = { Text("Voter ID") }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = { Text("Driving License") }
                )
                Tab(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = { Text("Student/Employee") }
                )
                Tab(
                    selected = selectedTab == 5,
                    onClick = { selectedTab = 5 },
                    icon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = { Text("Cover Photo") }
                )
                Tab(
                    selected = selectedTab == 6,
                    onClick = { selectedTab = 6 },
                    icon = { Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = { Text("A4 Album Studio") }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            OutlinedTextField(
                value = title,
                onValueChange = { viewModel.setDocTitle(it) },
                label = { Text("Album Title (e.g. My ID Documents)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            when (selectedTab) {
                0 -> {
                    CardDocumentSection(
                        cardName = "Aadhaar Card",
                        frontUri = aadhaarFront,
                        backUri = aadhaarBack,
                        onPickFront = { launchGallery("AADHAAR_FRONT") },
                        onCaptureFront = { launchCamera("AADHAAR_FRONT") },
                        onEditFront = {
                            if (aadhaarFront != null) {
                                pendingImageUri = aadhaarFront
                                editingSlot = "AADHAAR_FRONT"
                                startInCropMode = true
                            }
                        },
                        onRotateFront = {
                            aadhaarFront?.let { uri ->
                                rotateImageUri(uri)?.let {
                                    viewModel.setAadhaarFront(it)
                                    Toast.makeText(context, "Rotated 90°", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onClearFront = { viewModel.setAadhaarFront(null) },
                        onPickBack = { launchGallery("AADHAAR_BACK") },
                        onCaptureBack = { launchCamera("AADHAAR_BACK") },
                        onEditBack = {
                            if (aadhaarBack != null) {
                                pendingImageUri = aadhaarBack
                                editingSlot = "AADHAAR_BACK"
                                startInCropMode = true
                            }
                        },
                        onRotateBack = {
                            aadhaarBack?.let { uri ->
                                rotateImageUri(uri)?.let {
                                    viewModel.setAadhaarBack(it)
                                    Toast.makeText(context, "Rotated 90°", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onClearBack = { viewModel.setAadhaarBack(null) },
                        onUploadPdf = { launchPdfPicker("AADHAAR") },
                        pdfErrorState = pdfErrorState,
                        onSwapSides = {
                            viewModel.swapAadhaarSides()
                            Toast.makeText(context, "Front & Back Swapped", Toast.LENGTH_SHORT).show()
                        },
                        onNext = { selectedTab = 1 }
                    )
                }
                1 -> {
                    CardDocumentSection(
                        cardName = "PAN Card",
                        frontUri = panFront,
                        backUri = panBack,
                        onPickFront = { launchGallery("PAN_FRONT") },
                        onCaptureFront = { launchCamera("PAN_FRONT") },
                        onEditFront = {
                            if (panFront != null) {
                                pendingImageUri = panFront
                                editingSlot = "PAN_FRONT"
                                startInCropMode = true
                            }
                        },
                        onRotateFront = {
                            panFront?.let { uri ->
                                rotateImageUri(uri)?.let {
                                    viewModel.setPanFront(it)
                                    Toast.makeText(context, "Rotated 90°", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onClearFront = { viewModel.setPanFront(null) },
                        onPickBack = { launchGallery("PAN_BACK") },
                        onCaptureBack = { launchCamera("PAN_BACK") },
                        onEditBack = {
                            if (panBack != null) {
                                pendingImageUri = panBack
                                editingSlot = "PAN_BACK"
                                startInCropMode = true
                            }
                        },
                        onRotateBack = {
                            panBack?.let { uri ->
                                rotateImageUri(uri)?.let {
                                    viewModel.setPanBack(it)
                                    Toast.makeText(context, "Rotated 90°", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onClearBack = { viewModel.setPanBack(null) },
                        onUploadPdf = { launchPdfPicker("PAN") },
                        pdfErrorState = pdfErrorState,
                        onSwapSides = {
                            viewModel.swapPanSides()
                            Toast.makeText(context, "Front & Back Swapped", Toast.LENGTH_SHORT).show()
                        },
                        onNext = { selectedTab = 2 }
                    )
                }
                2 -> {
                    CardDocumentSection(
                        cardName = "Voter ID Card",
                        frontUri = voterFront,
                        backUri = voterBack,
                        onPickFront = { launchGallery("VOTER_FRONT") },
                        onCaptureFront = { launchCamera("VOTER_FRONT") },
                        onEditFront = {
                            if (voterFront != null) {
                                pendingImageUri = voterFront
                                editingSlot = "VOTER_FRONT"
                                startInCropMode = true
                            }
                        },
                        onRotateFront = {
                            voterFront?.let { uri ->
                                rotateImageUri(uri)?.let {
                                    viewModel.setVoterFront(it)
                                    Toast.makeText(context, "Rotated 90°", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onClearFront = { viewModel.setVoterFront(null) },
                        onPickBack = { launchGallery("VOTER_BACK") },
                        onCaptureBack = { launchCamera("VOTER_BACK") },
                        onEditBack = {
                            if (voterBack != null) {
                                pendingImageUri = voterBack
                                editingSlot = "VOTER_BACK"
                                startInCropMode = true
                            }
                        },
                        onRotateBack = {
                            voterBack?.let { uri ->
                                rotateImageUri(uri)?.let {
                                    viewModel.setVoterBack(it)
                                    Toast.makeText(context, "Rotated 90°", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onClearBack = { viewModel.setVoterBack(null) },
                        onUploadPdf = { launchPdfPicker("VOTER") },
                        pdfErrorState = pdfErrorState,
                        onSwapSides = {
                            viewModel.swapVoterSides()
                            Toast.makeText(context, "Front & Back Swapped", Toast.LENGTH_SHORT).show()
                        },
                        onNext = { selectedTab = 3 }
                    )
                }
                3 -> {
                    CardDocumentSection(
                        cardName = "Driving License",
                        frontUri = dlFront,
                        backUri = dlBack,
                        onPickFront = { launchGallery("DL_FRONT") },
                        onCaptureFront = { launchCamera("DL_FRONT") },
                        onEditFront = {
                            if (dlFront != null) {
                                pendingImageUri = dlFront
                                editingSlot = "DL_FRONT"
                                startInCropMode = true
                            }
                        },
                        onRotateFront = {
                            dlFront?.let { uri ->
                                rotateImageUri(uri)?.let {
                                    viewModel.setDlFront(it)
                                    Toast.makeText(context, "Rotated 90°", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onClearFront = { viewModel.setDlFront(null) },
                        onPickBack = { launchGallery("DL_BACK") },
                        onCaptureBack = { launchCamera("DL_BACK") },
                        onEditBack = {
                            if (dlBack != null) {
                                pendingImageUri = dlBack
                                editingSlot = "DL_BACK"
                                startInCropMode = true
                            }
                        },
                        onRotateBack = {
                            dlBack?.let { uri ->
                                rotateImageUri(uri)?.let {
                                    viewModel.setDlBack(it)
                                    Toast.makeText(context, "Rotated 90°", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onClearBack = { viewModel.setDlBack(null) },
                        onUploadPdf = { launchPdfPicker("DL") },
                        pdfErrorState = pdfErrorState,
                        onSwapSides = {
                            viewModel.swapDlSides()
                            Toast.makeText(context, "Front & Back Swapped", Toast.LENGTH_SHORT).show()
                        },
                        onNext = { selectedTab = 4 }
                    )
                }
                4 -> {
                    CardDocumentSection(
                        cardName = "Student/Employee ID",
                        frontUri = studentFront,
                        backUri = studentBack,
                        onPickFront = { launchGallery("STUDENT_FRONT") },
                        onCaptureFront = { launchCamera("STUDENT_FRONT") },
                        onEditFront = {
                            if (studentFront != null) {
                                pendingImageUri = studentFront
                                editingSlot = "STUDENT_FRONT"
                                startInCropMode = true
                            }
                        },
                        onRotateFront = {
                            studentFront?.let { uri ->
                                rotateImageUri(uri)?.let {
                                    viewModel.setStudentFront(it)
                                    Toast.makeText(context, "Rotated 90°", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onClearFront = { viewModel.setStudentFront(null) },
                        onPickBack = { launchGallery("STUDENT_BACK") },
                        onCaptureBack = { launchCamera("STUDENT_BACK") },
                        onEditBack = {
                            if (studentBack != null) {
                                pendingImageUri = studentBack
                                editingSlot = "STUDENT_BACK"
                                startInCropMode = true
                            }
                        },
                        onRotateBack = {
                            studentBack?.let { uri ->
                                rotateImageUri(uri)?.let {
                                    viewModel.setStudentBack(it)
                                    Toast.makeText(context, "Rotated 90°", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onClearBack = { viewModel.setStudentBack(null) },
                        onUploadPdf = { launchPdfPicker("STUDENT") },
                        pdfErrorState = pdfErrorState,
                        onSwapSides = {
                            viewModel.swapStudentSides()
                            Toast.makeText(context, "Front & Back Swapped", Toast.LENGTH_SHORT).show()
                        },
                        onNext = { selectedTab = 5 }
                    )
                }
                5 -> {
                    CardDocumentSection(
                        cardName = "Cover Photo",
                        frontUri = coverFront,
                        backUri = coverBack,
                        onPickFront = { launchGallery("COVER_FRONT") },
                        onCaptureFront = { launchCamera("COVER_FRONT") },
                        onEditFront = {
                            if (coverFront != null) {
                                pendingImageUri = coverFront
                                editingSlot = "COVER_FRONT"
                                startInCropMode = true
                            }
                        },
                        onRotateFront = {
                            coverFront?.let { uri ->
                                rotateImageUri(uri)?.let {
                                    viewModel.setCoverFront(it)
                                    Toast.makeText(context, "Rotated 90°", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onClearFront = { viewModel.setCoverFront(null) },
                        onPickBack = { launchGallery("COVER_BACK") },
                        onCaptureBack = { launchCamera("COVER_BACK") },
                        onEditBack = {
                            if (coverBack != null) {
                                pendingImageUri = coverBack
                                editingSlot = "COVER_BACK"
                                startInCropMode = true
                            }
                        },
                        onRotateBack = {
                            coverBack?.let { uri ->
                                rotateImageUri(uri)?.let {
                                    viewModel.setCoverBack(it)
                                    Toast.makeText(context, "Rotated 90°", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onClearBack = { viewModel.setCoverBack(null) },
                        onSwapSides = {
                            viewModel.swapCoverSides()
                            Toast.makeText(context, "Front & Back Swapped", Toast.LENGTH_SHORT).show()
                        },
                        onNext = { selectedTab = 6 }
                    )
                }
                6 -> {
                    A4AlbumStudioSection(
                        title = title,
                        aadhaarFront = aadhaarFront,
                        aadhaarBack = aadhaarBack,
                        panFront = panFront,
                        panBack = panBack,
                        voterFront = voterFront,
                        voterBack = voterBack,
                        coverFront = coverFront,
                        coverBack = coverBack,
                        layoutStyle = layoutStyle,
                        filterType = filterType,
                        showCutGuides = showCutGuides,
                        showLabels = showLabels,
                        showVerticalMargin = showVerticalMargin,
                        showHorizontalMargin = showHorizontalMargin,
                        cardScale = cardScale,
                        onLayoutStyleChange = { viewModel.setLayoutStyle(it) },
                        onFilterTypeChange = { viewModel.setFilterType(it) },
                        onShowCutGuidesChange = { viewModel.setShowCutGuides(it) },
                        onShowLabelsChange = { viewModel.setShowLabels(it) },
                        onShowVerticalMarginChange = { viewModel.setShowVerticalMargin(it) },
                        onShowHorizontalMarginChange = { viewModel.setShowHorizontalMargin(it) },
                        onCardScaleChange = { viewModel.setCardScale(it) },
                        onPreview = {
                            val bitmap = A4DocumentGenerator.generateA4Bitmap(
                                context = context,
                                title = title,
                                aadhaarFrontUri = aadhaarFront,
                                aadhaarBackUri = aadhaarBack,
                                panFrontUri = panFront,
                                panBackUri = panBack,
                                voterFrontUri = voterFront,
                                voterBackUri = voterBack,
                                dlFrontUri = dlFront,
                                dlBackUri = dlBack,
                                studentFrontUri = studentFront,
                                studentBackUri = studentBack,
                                coverFrontUri = coverFront,
                                coverBackUri = coverBack,
                                layoutStyle = layoutStyle,
                                filterType = filterType,
                                showCutGuides = showCutGuides,
                                showLabels = showLabels,
                                showVerticalMargin = showVerticalMargin,
                                showHorizontalMargin = showHorizontalMargin,
                                cardScale = cardScale
                            )
                            previewBitmap = bitmap
                            showPreviewDialog = true
                        },
                        onPrint = {
                            val bitmap = A4DocumentGenerator.generateA4Bitmap(
                                context = context,
                                title = title,
                                aadhaarFrontUri = aadhaarFront,
                                aadhaarBackUri = aadhaarBack,
                                panFrontUri = panFront,
                                panBackUri = panBack,
                                voterFrontUri = voterFront,
                                voterBackUri = voterBack,
                                dlFrontUri = dlFront,
                                dlBackUri = dlBack,
                                studentFrontUri = studentFront,
                                studentBackUri = studentBack,
                                coverFrontUri = coverFront,
                                coverBackUri = coverBack,
                                layoutStyle = layoutStyle,
                                filterType = filterType,
                                showCutGuides = showCutGuides,
                                showLabels = showLabels,
                                showVerticalMargin = showVerticalMargin,
                                showHorizontalMargin = showHorizontalMargin,
                                cardScale = cardScale
                            )
                            viewModel.saveDocument {
                                A4DocumentGenerator.printA4Bitmap(context, bitmap, title)
                            }
                        },
                        onSaveImage = {
                            val bitmap = A4DocumentGenerator.generateA4Bitmap(
                                context = context,
                                title = title,
                                aadhaarFrontUri = aadhaarFront,
                                aadhaarBackUri = aadhaarBack,
                                panFrontUri = panFront,
                                panBackUri = panBack,
                                voterFrontUri = voterFront,
                                voterBackUri = voterBack,
                                dlFrontUri = dlFront,
                                dlBackUri = dlBack,
                                studentFrontUri = studentFront,
                                studentBackUri = studentBack,
                                coverFrontUri = coverFront,
                                coverBackUri = coverBack,
                                layoutStyle = layoutStyle,
                                filterType = filterType,
                                showCutGuides = showCutGuides,
                                showLabels = showLabels,
                                showVerticalMargin = showVerticalMargin,
                                showHorizontalMargin = showHorizontalMargin,
                                cardScale = cardScale
                            )
                            viewModel.saveDocument {
                                A4DocumentGenerator.saveA4BitmapToStorage(context, bitmap, title)
                            }
                        },
                        onSavePdf = {
                            val bitmap = A4DocumentGenerator.generateA4Bitmap(
                                context = context,
                                title = title,
                                aadhaarFrontUri = aadhaarFront,
                                aadhaarBackUri = aadhaarBack,
                                panFrontUri = panFront,
                                panBackUri = panBack,
                                voterFrontUri = voterFront,
                                voterBackUri = voterBack,
                                dlFrontUri = dlFront,
                                dlBackUri = dlBack,
                                studentFrontUri = studentFront,
                                studentBackUri = studentBack,
                                coverFrontUri = coverFront,
                                coverBackUri = coverBack,
                                layoutStyle = layoutStyle,
                                filterType = filterType,
                                showCutGuides = showCutGuides,
                                showLabels = showLabels,
                                showVerticalMargin = showVerticalMargin,
                                showHorizontalMargin = showHorizontalMargin,
                                cardScale = cardScale
                            )
                            viewModel.saveDocument {
                                A4DocumentGenerator.saveA4PdfToStorage(context, bitmap, title)
                            }
                        },
                        onShareImage = {
                            val bitmap = A4DocumentGenerator.generateA4Bitmap(
                                context = context,
                                title = title,
                                aadhaarFrontUri = aadhaarFront,
                                aadhaarBackUri = aadhaarBack,
                                panFrontUri = panFront,
                                panBackUri = panBack,
                                voterFrontUri = voterFront,
                                voterBackUri = voterBack,
                                dlFrontUri = dlFront,
                                dlBackUri = dlBack,
                                studentFrontUri = studentFront,
                                studentBackUri = studentBack,
                                coverFrontUri = coverFront,
                                coverBackUri = coverBack,
                                layoutStyle = layoutStyle,
                                filterType = filterType,
                                showCutGuides = showCutGuides,
                                showLabels = showLabels,
                                showVerticalMargin = showVerticalMargin,
                                showHorizontalMargin = showHorizontalMargin,
                                cardScale = cardScale
                            )
                            val uri = A4DocumentGenerator.saveA4BitmapToStorage(context, bitmap, title)
                            if (uri != null) {
                                A4DocumentGenerator.shareA4Image(context, uri, title)
                            }
                        },
                        onSharePdf = {
                            val bitmap = A4DocumentGenerator.generateA4Bitmap(
                                context = context,
                                title = title,
                                aadhaarFrontUri = aadhaarFront,
                                aadhaarBackUri = aadhaarBack,
                                panFrontUri = panFront,
                                panBackUri = panBack,
                                voterFrontUri = voterFront,
                                voterBackUri = voterBack,
                                dlFrontUri = dlFront,
                                dlBackUri = dlBack,
                                studentFrontUri = studentFront,
                                studentBackUri = studentBack,
                                coverFrontUri = coverFront,
                                coverBackUri = coverBack,
                                layoutStyle = layoutStyle,
                                filterType = filterType,
                                showCutGuides = showCutGuides,
                                showLabels = showLabels,
                                showVerticalMargin = showVerticalMargin,
                                showHorizontalMargin = showHorizontalMargin,
                                cardScale = cardScale
                            )
                            val uri = A4DocumentGenerator.saveA4PdfToStorage(context, bitmap, title)
                            if (uri != null) {
                                A4DocumentGenerator.shareA4Pdf(context, uri, title)
                            }
                        }
                    )
                }
            }

            if (showPreviewDialog && previewBitmap != null) {
                PrintPreviewDialog(
                    bitmap = previewBitmap!!,
                    title = title,
                    onDismiss = { showPreviewDialog = false },
                    onPrint = {
                        viewModel.saveDocument {
                            A4DocumentGenerator.printA4Bitmap(context, previewBitmap!!, title)
                        }
                    },
                    onSavePdf = {
                        viewModel.saveDocument {
                            A4DocumentGenerator.saveA4PdfToStorage(context, previewBitmap!!, title)
                        }
                    },
                    onSaveImage = {
                        viewModel.saveDocument {
                            A4DocumentGenerator.saveA4BitmapToStorage(context, previewBitmap!!, title)
                        }
                    },
                    onSecurePdf = {
                        showExportPasswordDialog = true
                    }
                )
            }

            if (showPdfPasswordDialog) {
                PasswordInputDialog(
                    title = "PDF Password Required",
                    subtitle = "PDF import failed: Password required to access document. Please enter the document password below:",
                    confirmButtonText = "Unlock & Import",
                    requireConfirmation = false,
                    onDismiss = {
                        showPdfPasswordDialog = false
                        pdfPasswordInput = ""
                    },
                    onPasswordConfirmed = { password ->
                        pdfPasswordInput = password
                        showPdfPasswordDialog = false
                        Toast.makeText(context, "Password provided. Unlocking encrypted PDF...", Toast.LENGTH_SHORT).show()
                        pendingPasswordProtectedPdfUri?.let { uri ->
                            pendingPasswordProtectedCardType?.let { type ->
                                processPdfToFrontAndBack(uri, type, password)
                            }
                        }
                    }
                )
            }

            if (showExportPasswordDialog && previewBitmap != null) {
                PasswordInputDialog(
                    title = "Password Protect PDF Export",
                    subtitle = "Secure your exported A4 PDF document with a password.",
                    confirmButtonText = "Save Secured PDF",
                    requireConfirmation = true,
                    onDismiss = {
                        showExportPasswordDialog = false
                        exportPdfPassword = ""
                    },
                    onPasswordConfirmed = { password ->
                        exportPdfPassword = password
                        showExportPasswordDialog = false
                        showPreviewDialog = false
                        Toast.makeText(context, "Secured PDF saved with password protection!", Toast.LENGTH_SHORT).show()
                        viewModel.saveDocument {
                            A4DocumentGenerator.saveA4PdfToStorage(context, previewBitmap!!, "$title (Secured)")
                        }
                        exportPdfPassword = ""
                    }
                )
            }
        }
    }
}
}

@Composable
fun CardDocumentSection(
    cardName: String,
    frontUri: Uri?,
    backUri: Uri?,
    onPickFront: () -> Unit,
    onCaptureFront: () -> Unit,
    onEditFront: () -> Unit,
    onRotateFront: () -> Unit,
    onClearFront: () -> Unit,
    onPickBack: () -> Unit,
    onCaptureBack: () -> Unit,
    onEditBack: () -> Unit,
    onRotateBack: () -> Unit,
    onClearBack: () -> Unit,
    onUploadPdf: (() -> Unit)? = null,
    pdfErrorState: String? = null,
    onSwapSides: (() -> Unit)? = null,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$cardName (Front & Back)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (onUploadPdf != null) {
            Button(
                onClick = onUploadPdf,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Upload Official PDF (Auto-Crop Front & Back)", fontWeight = FontWeight.Bold)
            }
            if (pdfErrorState != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = pdfErrorState,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (onSwapSides != null) {
            OutlinedButton(
                onClick = onSwapSides,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Swap Front & Back Sides", fontWeight = FontWeight.Bold)
            }
        }

        SingleSideBox(
            sideLabel = "Front Side",
            imageUri = frontUri,
            onPick = onPickFront,
            onCapture = onCaptureFront,
            onEdit = onEditFront,
            onRotate = onRotateFront,
            onClear = onClearFront
        )

        SingleSideBox(
            sideLabel = "Back Side",
            imageUri = backUri,
            onPick = onPickBack,
            onCapture = onCaptureBack,
            onEdit = onEditBack,
            onRotate = onRotateBack,
            onClear = onClearBack
        )

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("Proceed to Next / A4 Studio")
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
fun SingleSideBox(
    sideLabel: String,
    imageUri: Uri?,
    onPick: () -> Unit,
    onCapture: () -> Unit,
    onEdit: () -> Unit,
    onRotate: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = sideLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (imageUri != null) {
                    FilledTonalButton(
                        onClick = onRotate,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rotate 90°", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.LightGray)
                    .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .clickable(enabled = imageUri != null) { onEdit() },
                contentAlignment = Alignment.Center
            ) {
                if (imageUri != null) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = sideLabel,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Icon(Icons.Default.Crop, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tap to Crop", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.DarkGray)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("No photo yet", color = Color.DarkGray, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onCapture,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (imageUri != null) "Retake" else "Camera")
                }

                OutlinedButton(
                    onClick = onPick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Gallery")
                }

                if (imageUri != null) {
                    OutlinedButton(
                        onClick = onClear,
                        modifier = Modifier.weight(0.8f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Clear")
                    }
                }
            }
        }
    }
}

@Composable
fun A4AlbumStudioSection(
    title: String,
    aadhaarFront: Uri?,
    aadhaarBack: Uri?,
    panFront: Uri?,
    panBack: Uri?,
    voterFront: Uri?,
    voterBack: Uri?,
    coverFront: Uri?,
    coverBack: Uri?,
    layoutStyle: String,
    filterType: String,
    showCutGuides: Boolean,
    showLabels: Boolean,
    showVerticalMargin: Boolean,
    showHorizontalMargin: Boolean,
    cardScale: Float,
    onLayoutStyleChange: (String) -> Unit,
    onFilterTypeChange: (String) -> Unit,
    onShowCutGuidesChange: (Boolean) -> Unit,
    onShowLabelsChange: (Boolean) -> Unit,
    onShowVerticalMarginChange: (Boolean) -> Unit,
    onShowHorizontalMarginChange: (Boolean) -> Unit,
    onCardScaleChange: (Float) -> Unit,
    onPreview: () -> Unit,
    onPrint: () -> Unit,
    onSaveImage: () -> Unit,
    onSavePdf: () -> Unit,
    onShareImage: () -> Unit,
    onSharePdf: () -> Unit
) {
    val context = LocalContext.current
    val previewBitmap by produceState<Bitmap?>(
        initialValue = null,
        title, aadhaarFront, aadhaarBack, panFront, panBack, voterFront, voterBack, coverFront, coverBack,
        layoutStyle, filterType, showCutGuides, showLabels, showVerticalMargin, showHorizontalMargin, cardScale
    ) {
        value = withContext(Dispatchers.Default) {
            A4DocumentGenerator.generateA4Bitmap(
                context = context,
                title = title,
                aadhaarFrontUri = aadhaarFront,
                aadhaarBackUri = aadhaarBack,
                panFrontUri = panFront,
                panBackUri = panBack,
                voterFrontUri = voterFront,
                voterBackUri = voterBack,
                coverFrontUri = coverFront,
                coverBackUri = coverBack,
                layoutStyle = layoutStyle,
                filterType = filterType,
                showCutGuides = showCutGuides,
                showLabels = showLabels,
                showVerticalMargin = showVerticalMargin,
                showHorizontalMargin = showHorizontalMargin,
                cardScale = cardScale
            )
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("A4 Document Album Preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Text("All 4 Cards (PAN Card, Voter ID, Aadhaar Card, Cover Photo) on 1 A4 Page", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(540.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (previewBitmap != null) {
                        AsyncImage(
                            model = previewBitmap,
                            contentDescription = "A4 Page Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onPreview,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Full Print Preview", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text("A4 Layout Style Options", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = layoutStyle == "MULTI_ID_GRID",
                    onClick = { onLayoutStyleChange("MULTI_ID_GRID") },
                    label = { Text("Option 1 (Top Aligned)", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = layoutStyle == "MULTI_ID_GRID_CENTERED",
                    onClick = { onLayoutStyleChange("MULTI_ID_GRID_CENTERED") },
                    label = { Text("Option 2 (Centered)", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterType == "COLOR",
                    onClick = { onFilterTypeChange("COLOR") },
                    label = { Text("Color Print") },
                    leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = filterType == "BW",
                    onClick = { onFilterTypeChange("BW") },
                    label = { Text("B&W / Laser") },
                    leadingIcon = { Icon(Icons.Default.FilterBAndW, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = showCutGuides,
                    onClick = { onShowCutGuidesChange(!showCutGuides) },
                    label = { Text("Cut Marks") },
                    leadingIcon = { Icon(Icons.Default.CropFree, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = {},
                    label = { Text("Clean (No Text)", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = showVerticalMargin,
                    onClick = { onShowVerticalMarginChange(!showVerticalMargin) },
                    label = { Text("Vertical Margin") },
                    leadingIcon = { Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = showHorizontalMargin,
                    onClick = { onShowHorizontalMarginChange(!showHorizontalMargin) },
                    label = { Text("Horizontal Margin") },
                    leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Card Size Scale / Margin Fit: ${(cardScale * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { onCardScaleChange(1.0f) }) {
                    Text("Reset", fontSize = 11.sp)
                }
            }
            Slider(
                value = cardScale,
                onValueChange = { onCardScaleChange(it) },
                valueRange = 0.8f..1.2f,
                steps = 16,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onPrint,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Print", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onSaveImage,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Img", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onSavePdf,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onShareImage,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share Image")
                }
                OutlinedButton(
                    onClick = onSharePdf,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share PDF")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .clickable {
                        uriHandler.openUri("https://azazmadkiya.morbi.store")
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Developed By Azazmadkiya",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "azazmadkiya.morbi.store",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
fun MiniRowPreview(
    leftUri: Uri?,
    rightUri: Uri?,
    showCutGuides: Boolean,
    rotateRight: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(98.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (leftUri != null) Color.White else Color(0xFFF8FAFC))
                .then(
                    if (showCutGuides) Modifier.border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(4.dp))
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (leftUri != null) {
                AsyncImage(model = leftUri, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(24.dp))
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(98.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (rightUri != null) Color.White else Color(0xFFF8FAFC))
                .then(
                    if (showCutGuides) Modifier.border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(4.dp))
                    else Modifier
                )
                .then(
                    if (rotateRight && rightUri != null) Modifier.graphicsLayer(rotationZ = 180f) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (rightUri != null) {
                AsyncImage(model = rightUri, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(24.dp))
            }
        }
    }
}
