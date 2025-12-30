package com.example.fieldmaintenance.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ImageStore {
    fun assetPhotoDir(context: Context, reportId: String, assetId: String, photoType: String): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val dir = File(base, "reports/$reportId/$assetId/$photoType")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun reportPhotoDir(context: Context, reportId: String, section: String): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val dir = File(base, "reports/$reportId/_report/$section")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun copyUriToFile(context: Context, source: Uri, dest: File) = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(source)?.use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("No se pudo abrir el archivo seleccionado")
    }
}


