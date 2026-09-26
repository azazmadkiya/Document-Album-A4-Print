package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.DocumentEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DocViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).documentDao()

    val allDocuments: Flow<List<DocumentEntity>> = dao.getAllDocuments()

    private val _docTitle = MutableStateFlow("A4 Document Studio Album")
    val docTitle: StateFlow<String> = _docTitle.asStateFlow()

    // ID Cards: Aadhaar, PAN, Voter ID, Driving License, Student/Employee ID, Cover Photo (Front & Back)
    private val _aadhaarFront = MutableStateFlow<Uri?>(null)
    val aadhaarFront: StateFlow<Uri?> = _aadhaarFront.asStateFlow()
    private val _aadhaarBack = MutableStateFlow<Uri?>(null)
    val aadhaarBack: StateFlow<Uri?> = _aadhaarBack.asStateFlow()

    private val _panFront = MutableStateFlow<Uri?>(null)
    val panFront: StateFlow<Uri?> = _panFront.asStateFlow()
    private val _panBack = MutableStateFlow<Uri?>(null)
    val panBack: StateFlow<Uri?> = _panBack.asStateFlow()

    private val _voterFront = MutableStateFlow<Uri?>(null)
    val voterFront: StateFlow<Uri?> = _voterFront.asStateFlow()
    private val _voterBack = MutableStateFlow<Uri?>(null)
    val voterBack: StateFlow<Uri?> = _voterBack.asStateFlow()

    private val _dlFront = MutableStateFlow<Uri?>(null)
    val dlFront: StateFlow<Uri?> = _dlFront.asStateFlow()
    private val _dlBack = MutableStateFlow<Uri?>(null)
    val dlBack: StateFlow<Uri?> = _dlBack.asStateFlow()

    private val _studentFront = MutableStateFlow<Uri?>(null)
    val studentFront: StateFlow<Uri?> = _studentFront.asStateFlow()
    private val _studentBack = MutableStateFlow<Uri?>(null)
    val studentBack: StateFlow<Uri?> = _studentBack.asStateFlow()

    private val _coverFront = MutableStateFlow<Uri?>(null)
    val coverFront: StateFlow<Uri?> = _coverFront.asStateFlow()
    private val _coverBack = MutableStateFlow<Uri?>(null)
    val coverBack: StateFlow<Uri?> = _coverBack.asStateFlow()

    private val _layoutStyle = MutableStateFlow("MULTI_ID_GRID")
    val layoutStyle: StateFlow<String> = _layoutStyle.asStateFlow()

    private val _filterType = MutableStateFlow("COLOR") // "COLOR", "BW"
    val filterType: StateFlow<String> = _filterType.asStateFlow()

    private val _cardPrintSize = MutableStateFlow(com.example.utils.CardPrintSize.AUTO_BEST)
    val cardPrintSize: StateFlow<com.example.utils.CardPrintSize> = _cardPrintSize.asStateFlow()

    private val _showCutGuides = MutableStateFlow(true)
    val showCutGuides: StateFlow<Boolean> = _showCutGuides.asStateFlow()

    private val _showLabels = MutableStateFlow(true)
    val showLabels: StateFlow<Boolean> = _showLabels.asStateFlow()

    private val _showVerticalMargin = MutableStateFlow(false)
    val showVerticalMargin: StateFlow<Boolean> = _showVerticalMargin.asStateFlow()

    private val _showHorizontalMargin = MutableStateFlow(false)
    val showHorizontalMargin: StateFlow<Boolean> = _showHorizontalMargin.asStateFlow()

    private val _cardScale = MutableStateFlow(1.0f)
    val cardScale: StateFlow<Float> = _cardScale.asStateFlow()

    fun setDocTitle(title: String) {
        _docTitle.value = title
    }

    fun setAadhaarFront(uri: Uri?) { _aadhaarFront.value = uri }
    fun setAadhaarBack(uri: Uri?) { _aadhaarBack.value = uri }
    fun setPanFront(uri: Uri?) { _panFront.value = uri }
    fun setPanBack(uri: Uri?) { _panBack.value = uri }
    fun setVoterFront(uri: Uri?) { _voterFront.value = uri }
    fun setVoterBack(uri: Uri?) { _voterBack.value = uri }
    fun setDlFront(uri: Uri?) { _dlFront.value = uri }
    fun setDlBack(uri: Uri?) { _dlBack.value = uri }
    fun setStudentFront(uri: Uri?) { _studentFront.value = uri }
    fun setStudentBack(uri: Uri?) { _studentBack.value = uri }
    fun setCoverFront(uri: Uri?) { _coverFront.value = uri }
    fun setCoverBack(uri: Uri?) { _coverBack.value = uri }

    fun setLayoutStyle(style: String) {
        _layoutStyle.value = style
    }

    fun setFilterType(filter: String) {
        _filterType.value = filter
    }

    fun setCardPrintSize(size: com.example.utils.CardPrintSize) {
        _cardPrintSize.value = size
    }

    fun setShowCutGuides(show: Boolean) {
        _showCutGuides.value = show
    }

    fun setShowLabels(show: Boolean) {
        _showLabels.value = show
    }

    fun setShowVerticalMargin(show: Boolean) {
        _showVerticalMargin.value = show
    }

    fun setShowHorizontalMargin(show: Boolean) {
        _showHorizontalMargin.value = show
    }

    fun setCardScale(scale: Float) {
        _cardScale.value = scale
    }

    fun saveDocument(onSaved: (Long) -> Unit) {
        val title = _docTitle.value.ifBlank { "A4 Document Album" }
        viewModelScope.launch {
            val entity = DocumentEntity(
                title = title,
                frontUri = _aadhaarFront.value?.toString() ?: "",
                backUri = _aadhaarBack.value?.toString() ?: "",
                aadhaarFront = _aadhaarFront.value?.toString() ?: "",
                aadhaarBack = _aadhaarBack.value?.toString() ?: "",
                panFront = _panFront.value?.toString() ?: "",
                panBack = _panBack.value?.toString() ?: "",
                voterFront = _voterFront.value?.toString() ?: "",
                voterBack = _voterBack.value?.toString() ?: "",
                dlFront = _dlFront.value?.toString() ?: "",
                dlBack = _dlBack.value?.toString() ?: "",
                studentFront = _studentFront.value?.toString() ?: "",
                studentBack = _studentBack.value?.toString() ?: "",
                coverFront = _coverFront.value?.toString() ?: "",
                coverBack = _coverBack.value?.toString() ?: "",
                layoutStyle = _layoutStyle.value,
                filterType = _filterType.value
            )
            val id = dao.insertDocument(entity)
            onSaved(id)
        }
    }

    fun deleteDocument(entity: DocumentEntity) {
        viewModelScope.launch {
            dao.deleteDocument(entity)
        }
    }

    fun loadDocument(entity: DocumentEntity) {
        _docTitle.value = entity.title
        _aadhaarFront.value = if (entity.aadhaarFront.isNotBlank()) Uri.parse(entity.aadhaarFront) else if (entity.frontUri.isNotBlank()) Uri.parse(entity.frontUri) else null
        _aadhaarBack.value = if (entity.aadhaarBack.isNotBlank()) Uri.parse(entity.aadhaarBack) else if (entity.backUri.isNotBlank()) Uri.parse(entity.backUri) else null
        _panFront.value = if (entity.panFront.isNotBlank()) Uri.parse(entity.panFront) else null
        _panBack.value = if (entity.panBack.isNotBlank()) Uri.parse(entity.panBack) else null
        _voterFront.value = if (entity.voterFront.isNotBlank()) Uri.parse(entity.voterFront) else null
        _voterBack.value = if (entity.voterBack.isNotBlank()) Uri.parse(entity.voterBack) else null
        _dlFront.value = if (entity.dlFront.isNotBlank()) Uri.parse(entity.dlFront) else null
        _dlBack.value = if (entity.dlBack.isNotBlank()) Uri.parse(entity.dlBack) else null
        _studentFront.value = if (entity.studentFront.isNotBlank()) Uri.parse(entity.studentFront) else null
        _studentBack.value = if (entity.studentBack.isNotBlank()) Uri.parse(entity.studentBack) else null
        _coverFront.value = if (entity.coverFront.isNotBlank()) Uri.parse(entity.coverFront) else null
        _coverBack.value = if (entity.coverBack.isNotBlank()) Uri.parse(entity.coverBack) else null
        _layoutStyle.value = entity.layoutStyle
        _filterType.value = entity.filterType
    }

    fun clearCurrent() {
        _docTitle.value = "A4 Document Studio Album"
        _aadhaarFront.value = null
        _aadhaarBack.value = null
        _panFront.value = null
        _panBack.value = null
        _voterFront.value = null
        _voterBack.value = null
        _dlFront.value = null
        _dlBack.value = null
        _studentFront.value = null
        _studentBack.value = null
        _coverFront.value = null
        _coverBack.value = null
        _layoutStyle.value = "MULTI_ID_GRID"
        _filterType.value = "COLOR"
    }

    fun swapAadhaarSides() {
        val temp = _aadhaarFront.value
        _aadhaarFront.value = _aadhaarBack.value
        _aadhaarBack.value = temp
    }

    fun swapPanSides() {
        val temp = _panFront.value
        _panFront.value = _panBack.value
        _panBack.value = temp
    }

    fun swapVoterSides() {
        val temp = _voterFront.value
        _voterFront.value = _voterBack.value
        _voterBack.value = temp
    }

    fun swapDlSides() {
        val temp = _dlFront.value
        _dlFront.value = _dlBack.value
        _dlBack.value = temp
    }

    fun swapStudentSides() {
        val temp = _studentFront.value
        _studentFront.value = _studentBack.value
        _studentBack.value = temp
    }

    fun swapCoverSides() {
        val temp = _coverFront.value
        _coverFront.value = _coverBack.value
        _coverBack.value = temp
    }
}
