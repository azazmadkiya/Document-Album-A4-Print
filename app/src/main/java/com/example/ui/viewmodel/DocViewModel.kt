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

    private val _docTitle = MutableStateFlow("My ID Document Album")
    val docTitle: StateFlow<String> = _docTitle.asStateFlow()

    // 4 Documents: Aadhaar, PAN, Voter ID, Cover Photo (Front & Back)
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

    private val _marginTopMm = MutableStateFlow(10f)
    val marginTopMm: StateFlow<Float> = _marginTopMm.asStateFlow()

    private val _marginBottomMm = MutableStateFlow(10f)
    val marginBottomMm: StateFlow<Float> = _marginBottomMm.asStateFlow()

    private val _marginLeftMm = MutableStateFlow(10f)
    val marginLeftMm: StateFlow<Float> = _marginLeftMm.asStateFlow()

    private val _marginRightMm = MutableStateFlow(10f)
    val marginRightMm: StateFlow<Float> = _marginRightMm.asStateFlow()

    fun setDocTitle(title: String) {
        _docTitle.value = title
    }

    fun setAadhaarFront(uri: Uri?) { _aadhaarFront.value = uri }
    fun setAadhaarBack(uri: Uri?) { _aadhaarBack.value = uri }
    fun setPanFront(uri: Uri?) { _panFront.value = uri }
    fun setPanBack(uri: Uri?) { _panBack.value = uri }
    fun setVoterFront(uri: Uri?) { _voterFront.value = uri }
    fun setVoterBack(uri: Uri?) { _voterBack.value = uri }
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

    fun setMarginTop(value: Float) { _marginTopMm.value = value }
    fun setMarginBottom(value: Float) { _marginBottomMm.value = value }
    fun setMarginLeft(value: Float) { _marginLeftMm.value = value }
    fun setMarginRight(value: Float) { _marginRightMm.value = value }

    fun saveDocument(onSaved: (Long) -> Unit) {
        val title = _docTitle.value.ifBlank { "ID Document Album" }
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
                dlFront = "",
                dlBack = "",
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
        _coverFront.value = if (entity.coverFront.isNotBlank()) Uri.parse(entity.coverFront) else if (entity.dlFront.isNotBlank()) Uri.parse(entity.dlFront) else null
        _coverBack.value = if (entity.coverBack.isNotBlank()) Uri.parse(entity.coverBack) else if (entity.dlBack.isNotBlank()) Uri.parse(entity.dlBack) else null
        _layoutStyle.value = entity.layoutStyle
        _filterType.value = entity.filterType
    }

    fun clearCurrent() {
        _docTitle.value = "My ID Document Album"
        _aadhaarFront.value = null
        _aadhaarBack.value = null
        _panFront.value = null
        _panBack.value = null
        _voterFront.value = null
        _voterBack.value = null
        _coverFront.value = null
        _coverBack.value = null
        _layoutStyle.value = "MULTI_ID_GRID"
        _filterType.value = "COLOR"
    }
}
