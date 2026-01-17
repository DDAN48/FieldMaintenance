package com.example.fieldmaintenance.util

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import com.example.fieldmaintenance.data.model.Asset
import com.example.fieldmaintenance.data.model.AssetType
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.text.Normalizer
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

object MaintenanceStorage {
    private const val BASE_FOLDER = "FieldMaintenance"
    private const val TRASH_FOLDER = "Trash"
    private const val MEASUREMENTS_TRASH_FOLDER = "Measurements"

    fun reportFolderName(eventName: String?, fallbackId: String): String {
        return sanitizeName(eventName?.takeIf { it.isNotBlank() } ?: fallbackId)
    }

    fun baseDir(context: Context): File {
        val root = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        return File(root, BASE_FOLDER).apply { mkdirs() }
    }

    fun measurementTrashDir(context: Context): File {
        return File(baseDir(context), "$TRASH_FOLDER/$MEASUREMENTS_TRASH_FOLDER").apply { mkdirs() }
    }

    fun ensureReportDir(context: Context, reportFolderName: String): File {
        return File(baseDir(context), sanitizeName(reportFolderName)).apply { mkdirs() }
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
        return copySharedFileToDir(context, uri, reportDir, fileName)
    }

    fun copySharedFileToDir(
        context: Context,
        uri: Uri,
        targetDir: File,
        overrideFileName: String? = null
    ): File? {
        targetDir.mkdirs()
        val displayName = queryDisplayName(context, uri)
        val fileName = sanitizeName(overrideFileName ?: displayName ?: "shared_${System.currentTimeMillis()}")
        val targetFile = uniqueFile(targetDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        } ?: return null
        return targetFile
    }

    fun importMeasurementFileToDir(
        context: Context,
        uri: Uri,
        targetDir: File
    ): List<File> {
        targetDir.mkdirs()
        val displayName = queryDisplayName(context, uri)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return emptyList()
        val lowerName = displayName?.lowercase(Locale.ROOT).orEmpty()
        return when {
            lowerName.endsWith(".zip") || isZipBytes(bytes) -> {
                extractZipBytes(bytes, targetDir)
            }
            lowerName.endsWith(".gz") || isGzipBytes(bytes) -> {
                extractGzipBytes(bytes, displayName, targetDir)
            }
            else -> {
                val fileName = sanitizeName(displayName ?: "shared_${System.currentTimeMillis()}")
                val targetFile = uniqueFile(targetDir, fileName)
                FileOutputStream(targetFile).use { output -> output.write(bytes) }
                listOf(targetFile)
            }
        }
    }

    fun moveMeasurementFileToTrash(context: Context, source: File): File? {
        val base = baseDir(context)
        val trashRoot = measurementTrashDir(context)
        val relative = source.relativeToOrNull(base)?.path
        val destination = if (relative != null) {
            File(trashRoot, relative)
        } else {
            File(trashRoot, source.name)
        }
        destination.parentFile?.mkdirs()
        val target = if (destination.exists()) {
            uniqueFile(destination.parentFile ?: trashRoot, destination.name)
        } else {
            destination
        }
        return if (source.renameTo(target)) target else null
    }

    fun restoreMeasurementFile(context: Context, trashed: File): File? {
        val base = baseDir(context)
        val trashRoot = measurementTrashDir(context)
        val relative = trashed.relativeToOrNull(trashRoot)?.path ?: trashed.name
        val destination = File(base, relative)
        destination.parentFile?.mkdirs()
        val target = if (destination.exists()) {
            uniqueFile(destination.parentFile ?: base, destination.name)
        } else {
            destination
        }
        return if (trashed.renameTo(target)) target else null
    }

    fun listMeasurementTrashFiles(context: Context): List<File> {
        val trashRoot = measurementTrashDir(context)
        return trashRoot.walkTopDown()
            .filter { it.isFile }
            .toList()
    }

    fun assetFolderName(asset: Asset): String {
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

    private fun isZipBytes(bytes: ByteArray): Boolean {
        return bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte()
    }

    private fun isGzipBytes(bytes: ByteArray): Boolean {
        return bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
    }

    private fun extractZipBytes(bytes: ByteArray, targetDir: File): List<File> {
        val extracted = mutableListOf<File>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val entryName = entry.name.substringAfterLast('/').ifBlank { entry.name }
                    if (entryName.isNotBlank()) {
                        val targetFile = uniqueFile(targetDir, entryName)
                        FileOutputStream(targetFile).use { output ->
                            zip.copyTo(output)
                        }
                        extracted.add(targetFile)
                    }
                }
                entry = zip.nextEntry
            }
        }
        return extracted
    }

    private fun extractGzipBytes(bytes: ByteArray, displayName: String?, targetDir: File): List<File> {
        val decompressed = GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
        return if (isZipBytes(decompressed)) {
            extractZipBytes(decompressed, targetDir)
        } else {
            val name = displayName?.removeSuffix(".gz") ?: "shared_${System.currentTimeMillis()}"
            val fileName = sanitizeName(name)
            val targetFile = uniqueFile(targetDir, fileName)
            FileOutputStream(targetFile).use { output -> output.write(decompressed) }
            listOf(targetFile)
        }
    }

    private fun sanitizeName(name: String): String {
        val normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
        val ascii = normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        val cleaned = ascii.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        return cleaned.trim('_').ifEmpty { "shared_${System.currentTimeMillis()}" }
    }
}
