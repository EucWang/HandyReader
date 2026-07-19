package com.wxn.reader.domain.repository

import android.net.Uri

interface PermissionRepository {

    suspend fun grantPersistableUriPermission(
        uri: Uri
    )

    suspend fun releasePersistableUriPermission(
        uri: Uri
    )
}