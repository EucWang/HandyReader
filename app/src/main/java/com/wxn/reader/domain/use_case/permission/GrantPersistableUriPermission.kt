package com.wxn.reader.domain.use_case.permission

import android.net.Uri
import com.wxn.reader.domain.repository.PermissionRepository
import javax.inject.Inject


class GrantPersistableUriPermission @Inject constructor(
    private val repository: PermissionRepository
) {

    suspend fun execute(uri: Uri) {
        repository.grantPersistableUriPermission(uri)
    }
}