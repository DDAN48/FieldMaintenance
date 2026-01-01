package com.example.fieldmaintenance.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import com.example.fieldmaintenance.util.DownloadStore
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.layout.element.Paragraph
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var text by remember { mutableStateOf("Cargando manual…") }
    var blocks by remember { mutableStateOf<List<ManualBlock>>(emptyList()) }

    LaunchedEffect(Unit) {
        text = runCatching {
            withContext(Dispatchers.IO) {
                context.assets.open("manual_es.txt")
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
            }
        }.getOrElse {
            "No se pudo cargar el manual.\n\nDetalle: ${it.message.orEmpty()}"
        }
        blocks = parseManual(text)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Manual") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val result = runCatching { exportManualPdf(context, blocks) }
                            if (result.isSuccess) {
                                snackbarHostState.showSnackbar("Manual PDF guardado en Descargas/FieldMaintenance")
                            } else {
                                snackbarHostState.showSnackbar("No se pudo exportar el PDF")
                            }
                        }
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "Exportar PDF")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            blocks.forEach { b ->
                when (b) {
                    is ManualBlock.Text -> {
                        SelectionContainer {
                            Text(text = b.value, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    is ManualBlock.Image -> {
                        val assetPath = remember(b.imageNumber) {
                            findFirstAssetImagePathOrNull(context, b.imageNumber)
                        }
                        val bmp = remember(assetPath) {
                            assetPath?.let { p ->
                                runCatching {
                                    context.assets.open(p).use { input ->
                                        BitmapFactory.decodeStream(input)
                                    }
                                }.getOrNull()
                            }
                        }
                        if (bmp != null) {
                            androidx.compose.foundation.Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = b.label,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = "Imagen no encontrada: ${b.label}\nBuscando: manual_images/Imagen_${b.imageNumber}.(png/jpg/jpeg/webp)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

private sealed class ManualBlock {
    data class Text(val value: String) : ManualBlock()
    data class Image(val label: String, val imageNumber: Int) : ManualBlock()
}

private fun parseManual(text: String): List<ManualBlock> {
    // Markers like: [Imagen 12: Importar]
    val regex = Regex("\\[Imagen\\s+([^\\]]+)]")
    val blocks = mutableListOf<ManualBlock>()
    var last = 0
    regex.findAll(text).forEach { m ->
        val start = m.range.first
        val end = m.range.last + 1
        if (start > last) {
            val chunk = text.substring(last, start).trimEnd()
            if (chunk.isNotBlank()) blocks.add(ManualBlock.Text(chunk))
        }
        val label = m.value.trim('[', ']') // e.g. "Imagen 12: Importar"
        val inner = m.groupValues.getOrNull(1).orEmpty() // e.g. "12: Importar"
        val n = Regex("^\\s*(\\d+)").find(inner)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (n != null) {
            blocks.add(ManualBlock.Image(label = label, imageNumber = n))
        } else {
            // Fallback if number can't be parsed
            blocks.add(ManualBlock.Text(label))
        }
        last = end
    }
    if (last < text.length) {
        val chunk = text.substring(last).trim()
        if (chunk.isNotBlank()) blocks.add(ManualBlock.Text(chunk))
    }
    return blocks
}

private fun findFirstAssetImagePathOrNull(context: android.content.Context, imageNumber: Int): String? {
    val base = "manual_images/Imagen_$imageNumber"
    val exts = listOf(".png", ".jpg", ".jpeg", ".webp")
    for (e in exts) {
        val p = "$base$e"
        val ok = runCatching { context.assets.open(p).close(); true }.getOrDefault(false)
        if (ok) return p
    }
    return null
}

private suspend fun exportManualPdf(context: android.content.Context, blocks: List<ManualBlock>): Uri =
    withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "manual_export.pdf")
        if (tmp.exists()) tmp.delete()

        val pdf = PdfDocument(PdfWriter(tmp))
        pdf.defaultPageSize = PageSize.A4
        val document = Document(pdf)
        document.setMargins(18f, 18f, 18f, 18f)

        blocks.forEach { b ->
            when (b) {
                is ManualBlock.Text -> {
                    // Preserve lines with a modest font size.
                    b.value.lines().forEach { line ->
                        document.add(Paragraph(line).setFontSize(9f))
                    }
                    document.add(Paragraph(" "))
                }
                is ManualBlock.Image -> {
                    val assetPath = findFirstAssetImagePathOrNull(context, b.imageNumber)
                    if (assetPath != null) {
                        val bytes = context.assets.open(assetPath).use { it.readBytes() }
                        val img = Image(ImageDataFactory.create(bytes))
                        img.scaleToFit(520f, 680f)
                        document.add(img)
                        document.add(Paragraph(" "))
                    } else {
                        document.add(Paragraph("Imagen no encontrada: ${b.label}").setFontSize(9f))
                        document.add(Paragraph(" "))
                    }
                }
            }
        }

        document.close()
        DownloadStore.saveToDownloads(context, tmp, "Manual_FieldMaintenance.pdf", "application/pdf")
    }


