package com.example.fieldmaintenance.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object DownloadStore {
    /**
     * Copies [sourceFile] into the public Downloads/FieldMaintenance folder (MediaStore on API 29+).
     * Returns the Uri where it was saved.
     */
    fun saveToDownloads(
        context: Context,
        sourceFile: File,
        displayName: String,
        mimeType: String
    ): Uri {
        require(sourceFile.exists()) { "Archivo no existe: ${sourceFile.absolutePath}" }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FieldMaintenance")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("No se pudo crear archivo en Descargas")

            resolver.openOutputStream(uri, "w")?.use { out ->
                FileInputStream(sourceFile).use { input -> input.copyTo(out) }
            } ?: throw IllegalStateException("No se pudo abrir salida en Descargas")

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "FieldMaintenance"
            )
            if (!dir.exists()) dir.mkdirs()
            val dest = File(dir, displayName)
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            Uri.fromFile(dest)
        }
    }
}


