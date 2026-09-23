package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val frontUri: String = "",
    val backUri: String = "",
    val aadhaarFront: String = "",
    val aadhaarBack: String = "",
    val panFront: String = "",
    val panBack: String = "",
    val voterFront: String = "",
    val voterBack: String = "",
    val dlFront: String = "",
    val dlBack: String = "",
    val coverFront: String = "",
    val coverBack: String = "",
    val layoutStyle: String = "MULTI_ID_GRID",
    val filterType: String = "COLOR",
    val createdAt: Long = System.currentTimeMillis()
)
