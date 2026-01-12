package com.example.fieldmaintenance.util

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import com.example.fieldmaintenance.data.model.Asset
import com.example.fieldmaintenance.data.model.AssetType
import java.io.File
import java.io.FileOutputStream
import java.text.Normalizer
import java.util.Locale

object MaintenanceStorage {
    private const val BASE_FOLDER = "FieldMaintenance"

    fun baseDir(context: Context): File {
        val root = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        return File(root, BASE_FOLDER).apply { mkdirs() }
    }

    fun ensureReportDir(context: Context, reportFolderName: String): File {
        val sanitized = sanitizeName(reportFolderName)
        return File(baseDir(context), sanitized).apply { mkdirs() }
    }

    fun ensureAssetDir(context: Context, reportFolderName: String, asset: Asset): File {
        val reportDir = ensureReportDir(context, reportFolderName)
        val assetDir = File(reportDir, assetFolderName(asset))
        assetDir.mkdirs()
        return assetDir
    }

    fun copySharedFile(context: Context, uri: Uri): File? {
        val displayName = queryDisplayName(context, uri)
        val fileName = sanitizeName(displayName ?: "shared_${System.currentTimeMillis()}")
        val baseName = fileName.substringBeforeLast('.', fileName)
        val reportDir = ensureReportDir(context, baseName)
        val targetFile = uniqueFile(reportDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        } ?: return null
        return targetFile
    }

    private fun assetFolderName(asset: Asset): String {
        return when (asset.type) {
            AssetType.NODE -> "NODE"
            AssetType.AMPLIFIER -> {
                val portName = asset.port?.name?.uppercase(Locale.ROOT)
                val portIndex = asset.portIndex?.let { String.format("%02d", it) }
                when {
                    portName != null && portIndex != null -> "AMPLIFIER_${portName}${portIndex}"
                    else -> "AMPLIFIER_${asset.id.take(6)}"
                }
            }
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        val cursor: Cursor? = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
        }
        return null
    }

    private fun uniqueFile(dir: File, fileName: String): File {
        val sanitized = sanitizeName(fileName)
        var candidate = File(dir, sanitized)
        if (!candidate.exists()) return candidate
        val base = sanitized.substringBeforeLast('.', sanitized)
        val ext = sanitized.substringAfterLast('.', "")
        var index = 1
        while (candidate.exists()) {
            val suffix = "_${index}"
            val name = if (ext.isEmpty()) "$base$suffix" else "$base$suffix.$ext"
            candidate = File(dir, name)
            index++
        }
        return candidate
    }

    private fun sanitizeName(name: String): String {
        val normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
        val ascii = normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        val cleaned = ascii.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        return cleaned.trim('_').ifEmpty { "shared_${System.currentTimeMillis()}" }
    }
}
