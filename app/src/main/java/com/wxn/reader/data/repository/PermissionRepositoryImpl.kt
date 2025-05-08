package com.wxn.reader.data.repository

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

private const val GRANT_URI = "GRANT URI PERM, REPO"
private const val RELEASE_URI = "RELEASE URI PERM, REPO"

@Singleton
class PermissionRepositoryImpl @Inject constructor(
    private val application: Application
) :  com.wxn.reader.domain.repository.PermissionRepository {

    override suspend fun grantPersistableUriPermission(uri: Uri) {
        Log.i(GRANT_URI, "Granting persistable uri permission to \"${uri.path}\" URI.")

        try {
            application.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(GRANT_URI, "Could not grant URI permission.")
        }
    }

    override suspend fun releasePersistableUriPermission(uri: Uri) {
        Log.i(RELEASE_URI, "Releasing persistable uri permission from \"${uri.path}\" URI.")

        try {
            application.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Log.w(RELEASE_URI, "No granted URI permission found.")
        }
    }
}