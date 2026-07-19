package com.wxn.reader.data.dto

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "opds_catalogs",
    indices = [
        Index(value = ["predefinedId"], name = "idx_opds_catalogs_predefinedId"),
        Index(value = ["sortOrder"], name = "idx_opds_catalogs_sortOrder")
    ]
)
data class OpdsCatalogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val url: String,
    val description: String? = null,
    val iconUrl: String? = null,
    val searchUrl: String? = null,
    val supportsSearch: Boolean = false,
    val authType: String = "NONE",
    val isPredefined: Boolean = false,
    val predefinedId: String? = null,
    val language: String? = "",
    val isEnabled: Boolean = true,
    val sortOrder: Int = 0,
    val lastAccessedAt: Long? = null,
    val lastSyncAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
