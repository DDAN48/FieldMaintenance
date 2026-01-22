package com.example.fieldmaintenance.util

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.ContentResolver
import android.os.ParcelFileDescriptor
import android.util.Base64
import androidx.core.content.ContextCompat
import com.example.fieldmaintenance.R
import com.example.fieldmaintenance.data.model.*
import com.example.fieldmaintenance.data.model.label
import com.example.fieldmaintenance.data.repository.MaintenanceRepository
import com.example.fieldmaintenance.util.CiscoHfcAmpCalculator
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Div
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.AreaBreakType
import com.itextpdf.layout.properties.HorizontalAlignment
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import com.itextpdf.layout.properties.VerticalAlignment
import com.itextpdf.layout.borders.SolidBorder
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.FileInputStream
import java.io.FileOutputStream as JFileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class ExportManager(private val context: Context, private val repository: MaintenanceRepository) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val switchPrefs by lazy {
        context.getSharedPreferences("measurement_switch_positions", Context.MODE_PRIVATE)
    }

    private fun safeFilePart(value: String): String {
        val v = value.trim().ifBlank { "NA" }
        // Keep letters/numbers/_/-, replace everything else with underscore
        return v
            .replace(Regex("[^A-Za-z0-9_-]+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .ifBlank { "NA" }
    }

    private fun exportBaseName(report: MaintenanceReport, now: Date = Date()): String {
        val node = safeFilePart(report.nodeName.ifBlank { report.id })
        val event = safeFilePart(report.eventName.ifBlank { "evento" })
        val dt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(now)
        return "${node}_${event}_$dt"
    }

    fun exportDisplayName(report: MaintenanceReport, extensionWithDot: String, now: Date = Date()): String {
        return "${exportBaseName(report, now)}$extensionWithDot"
    }
    
    private fun assetSortKey(a: Asset): String {
        return when (a.type) {
            AssetType.NODE -> "0_NODE"
            AssetType.AMPLIFIER -> {
                val p = a.port?.name ?: "Z"
                val idx = a.portIndex ?: 999
                "1_AMP_${p}_${String.format("%03d", idx)}"
            }
        }
    }

    private fun collectSwitchSelections(assets: List<Asset>): Map<String, Map<String, String>> {
        val allPrefs = switchPrefs.all
        val result = mutableMapOf<String, MutableMap<String, String>>()
        assets.forEach { asset ->
            val prefix = "switch_${asset.id}_"
            allPrefs.forEach { (key, value) ->
                if (key.startsWith(prefix) && value is String) {
                    val label = key.removePrefix(prefix)
                    result.getOrPut(asset.id) { mutableMapOf() }[label] = value
                }
            }
        }
        return result
    }

    private fun applySwitchSelections(selections: Map<String, Map<String, String>>?) {
        if (selections.isNullOrEmpty()) return
        val editor = switchPrefs.edit()
        selections.forEach { (assetId, values) ->
            values.forEach { (label, value) ->
                editor.putString("switch_${assetId}_$label", value)
            }
        }
        editor.apply()
    }

    private fun collectMeasurementFiles(
        reportFolderName: String,
        asset: Asset
    ): List<ExportMeasurementFile> {
        val result = mutableListOf<ExportMeasurementFile>()
        val assetDirs = buildList {
            add(MaintenanceStorage.ensureAssetDir(context, reportFolderName, asset))
            if (asset.type == AssetType.NODE) {
                add(MaintenanceStorage.ensureAssetDir(context, reportFolderName, asset.copy(type = AssetType.AMPLIFIER)))
            }
        }
        assetDirs.forEach { dir ->
            dir.listFiles()
                ?.filter { it.isFile }
                ?.forEach { file ->
                    val rel = File(dir.name, file.name).path.replace("\\", "/")
                    val payload = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                    result.add(ExportMeasurementFile(relativePath = rel, base64 = payload))
                }
        }
        if (result.isEmpty()) {
            assetDirs.forEach { dir ->
                val rel = File(dir.name, ".keep").path.replace("\\", "/")
                result.add(ExportMeasurementFile(relativePath = rel, base64 = ""))
            }
        }
        return result
    }

    private fun photoSortKey(p: Photo): String {
        val typeOrder = when (p.photoType) {
            PhotoType.MODULE -> "0_MODULE"
            PhotoType.OPTICS -> "1_OPTICS"
            PhotoType.MONITORING -> "2_MONITORING"
            PhotoType.SPECTRUM -> "3_SPECTRUM"
        }
        return "${typeOrder}_${p.fileName}"
    }

    private fun photoSectionTitle(type: PhotoType): String {
        return when (type) {
            PhotoType.MODULE -> "Foto del Módulo y Tapa"
            PhotoType.OPTICS -> "Foto TX  y RX con pads"
            PhotoType.MONITORING -> "Foto de monitoria de PO directa y retorno"
            PhotoType.SPECTRUM -> "Fotos de Inyección de portadoras por puerto"
        }
    }

    private fun assetHeaderLine(report: MaintenanceReport, asset: Asset): String {
        val node = report.nodeName.ifBlank { "Nodo" }
        val freq = "${asset.frequencyMHz} MHz"
        return when (asset.type) {
            AssetType.NODE -> "Activo $node $freq"
            AssetType.AMPLIFIER -> {
                val code = if (asset.port != null && asset.portIndex != null) {
                    "${asset.port.name}${String.format("%02d", asset.portIndex)}"
                } else {
                    "SIN-COD"
                }
                val mode = asset.amplifierMode?.label ?: "SIN-TIPO"
                // Match app card info: <Nodo> <PuertoNN> <Tipo> <Frecuencia>
                "Activo $node $code $mode $freq"
            }
        }
    }

    private fun freqEnumFromMHz(mhz: Int): Frequency? = when (mhz) {
        42 -> Frequency.MHz_42
        85 -> Frequency.MHz_85
        120 -> Frequency.MHz_120
        else -> null
    }

    private fun addTable(
        document: Document,
        title: String,
        headers: List<String>,
        rows: List<List<Pair<String, com.itextpdf.kernel.colors.Color?>>>,
        colWidths: FloatArray
    ) {
        if (title.isNotBlank()) {
            document.add(Paragraph(title).setBold())
        }
        val table = Table(UnitValue.createPercentArray(colWidths)).useAllAvailableWidth()
        headers.forEach { h ->
            table.addHeaderCell(
                Cell()
                    .add(Paragraph(h).setBold().setFontSize(9f))
                    .setBorder(SolidBorder(ColorConstants.BLACK, 0.5f))
                    .setPadding(3f)
            )
        }
        rows.forEach { row ->
            row.forEach { (txt, color) ->
                val p = Paragraph(txt).setFontSize(9f)
                if (color != null) p.setFontColor(color)
                table.addCell(
                    Cell()
                        .add(p)
                        .setBorder(SolidBorder(ColorConstants.BLACK, 0.5f))
                        .setPadding(3f)
                )
            }
        }
        document.add(table)
        document.add(Paragraph(" "))
    }

    private fun coverCell(text: String, bold: Boolean = false, size: Float = 10f, color: com.itextpdf.kernel.colors.Color? = null): Paragraph {
        val p = Paragraph(text).setFontSize(size)
        if (bold) p.setBold()
        if (color != null) p.setFontColor(color)
        return p
    }

    private fun drawableToPngBytes(drawableRes: Int, targetW: Int = 220, targetH: Int = 66): ByteArray? {
        val drawable = ContextCompat.getDrawable(context, drawableRes) ?: return null
        val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, targetW, targetH)
        drawable.draw(canvas)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    private suspend fun countAdjustedAssets(report: MaintenanceReport, assets: List<Asset>): Int {
        fun moduleOk(photos: List<Photo>): Boolean = photos.count { it.photoType == PhotoType.MODULE } == 2
        fun opticsOk(photos: List<Photo>): Boolean {
            val c = photos.count { it.photoType == PhotoType.OPTICS }
            return c in 1..2
        }

        var count = 0
        for (asset in assets) {
            val photos = repository.getPhotosByAssetId(asset.id).first()
            val baseOk = asset.frequencyMHz > 0 && moduleOk(photos)
            if (!baseOk) continue

            val ok = when (asset.type) {
                AssetType.NODE -> opticsOk(photos)
                AssetType.AMPLIFIER -> {
                    val fieldsOk = asset.amplifierMode != null && asset.port != null && asset.portIndex != null
                    if (!fieldsOk) false
                    else {
                        val adj = repository.getAmplifierAdjustment(asset.id).first()
                        adj != null &&
                            adj.inputCh50Dbmv != null &&
                            adj.inputCh116Dbmv != null &&
                            (adj.inputHighFreqMHz == null || adj.inputHighFreqMHz == 750 || adj.inputHighFreqMHz == 870) &&
                            adj.planLowDbmv != null &&
                            adj.planHighDbmv != null &&
                            adj.outCh50Dbmv != null &&
                            adj.outCh70Dbmv != null &&
                            adj.outCh110Dbmv != null &&
                            adj.outCh116Dbmv != null &&
                            adj.outCh136Dbmv != null
                    }
                }
            }

            if (ok) count++
        }
        return count
    }

    suspend fun exportToJSON(report: MaintenanceReport): File = withContext(Dispatchers.IO) {
val assets = repository.getAssetsByReportId(report.id).first()
            .sortedBy { assetSortKey(it) }
        
        val photosMap = mutableMapOf<String, List<Photo>>()
        val nodeAdjustmentsMap = mutableMapOf<String, NodeAdjustment>()
        val adjustmentsMap = mutableMapOf<String, AmplifierAdjustment>()
        val measurementFiles = mutableListOf<ExportMeasurementFile>()
        assets.forEach { asset ->
            photosMap[asset.id] = repository.getPhotosByAssetId(asset.id).first()
                .sortedBy { photoSortKey(it) }
            // Include NodeAdjustment for NODE assets
            if (asset.type == AssetType.NODE) {
                runCatching { repository.getNodeAdjustmentOne(asset.id) }.getOrNull()?.let {
                    nodeAdjustmentsMap[asset.id] = it
                }
            }
            repository.getAmplifierAdjustment(asset.id).first()?.let {
                adjustmentsMap[asset.id] = it
            }
            measurementFiles.addAll(collectMeasurementFiles(MaintenanceStorage.reportFolderName(report.eventName, report.id), asset))
        }
        
        val passives = repository.getPassivesByReportId(report.id).first()
        val reportPhotos = repository.getReportPhotosByReportId(report.id).first()
        val switchSelections = collectSwitchSelections(assets)

        val exportData = ExportData(
            report = report,
            assets = assets,
            photos = photosMap,
            passives = passives,
            reportPhotos = reportPhotos,
            nodeAdjustments = if (nodeAdjustmentsMap.isNotEmpty()) nodeAdjustmentsMap else null,
            adjustments = if (adjustmentsMap.isNotEmpty()) adjustmentsMap else null,
            measurementFiles = if (measurementFiles.isNotEmpty()) measurementFiles else null,
            switchSelections = if (switchSelections.isNotEmpty()) switchSelections else null
        )
        
        val jsonString = gson.toJson(exportData)
        val file = File(context.getExternalFilesDir(null), "maintenance_${report.id}.json")
        file.writeText(jsonString)
        file
    }
    
    suspend fun exportToPDF(report: MaintenanceReport): File = withContext(Dispatchers.IO) {
        val assets = repository.getAssetsByReportId(report.id).first()
            .sortedBy { assetSortKey(it) }
        val passives = repository.getPassivesByReportId(report.id).first()
        val reportPhotos = repository.getReportPhotosByReportId(report.id).first()
        
        val file = File(context.getExternalFilesDir(null), "maintenance_${report.id}.pdf")
        val writer = PdfWriter(file)
        val pdf = PdfDocument(writer)
        // Default: a bit more vertical room to avoid 1 leftover box spilling to a new page.
        val normalPage = PageSize(PageSize.A4.width, PageSize.A4.height + 120f)
        // For amplifier adjustment tables: longer page so ALL tables fit on one sheet.
        val tablesPage = PageSize(PageSize.A4.width, PageSize.A4.height + 900f)
        pdf.defaultPageSize = normalPage
        val document = Document(pdf)
        document.setMargins(18f, 18f, 18f, 18f)

        // ========================
        // Portada (estilo plantilla)
        // ========================
        val brandBlue = DeviceRgb(0, 103, 184)
        val lightGray = DeviceRgb(240, 240, 240)
        val dateShort = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(report.executionDate))

        // Agregar espacio arriba para bajar el contenido
        document.add(Paragraph(" "))
        document.add(Paragraph(" "))
        document.add(Paragraph(" "))

        // Header table: logo / title / date
        val header = Table(UnitValue.createPercentArray(floatArrayOf(22f, 58f, 20f))).useAllAvailableWidth()
        val logoCell = Cell()
            .setBorder(SolidBorder(ColorConstants.BLACK, 0.8f))
            .setVerticalAlignment(VerticalAlignment.MIDDLE)
        runCatching {
            val bytes = drawableToPngBytes(R.drawable.telecentro_logo, targetW = 90, targetH = 45)
            if (bytes != null) {
                val brandText = Paragraph("TELECENTRO")
                    .setBold()
                    .setFontSize(16f)
                    .setFontColor(DeviceRgb(30, 30, 30))
                    .setMargin(0f)

                val icon = Image(ImageDataFactory.create(bytes))
                    .scaleToFit(42f, 42f)
                    .setHorizontalAlignment(HorizontalAlignment.LEFT)

                val brandTable = Table(UnitValue.createPercentArray(floatArrayOf(26f, 74f)))
                    .useAllAvailableWidth()
                brandTable.addCell(
                    Cell()
                        .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                        .setVerticalAlignment(VerticalAlignment.MIDDLE)
                        .add(icon)
                )
                brandTable.addCell(
                    Cell()
                        .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                        .setVerticalAlignment(VerticalAlignment.MIDDLE)
                        .add(brandText)
                )
                logoCell.add(brandTable)
            } else {
                logoCell.add(coverCell("Telecentro", bold = true, size = 18f, color = brandBlue))
            }
        }.onFailure {
            logoCell.add(coverCell("Telecentro", bold = true, size = 18f, color = brandBlue))
        }
        header.addCell(logoCell)
        header.addCell(
            Cell()
                .add(coverCell("Informe de Mantenimiento Preventivo", bold = true, size = 12f))
                .setBorder(SolidBorder(ColorConstants.BLACK, 0.8f))
                .setTextAlignment(TextAlignment.CENTER)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
        )
        header.addCell(
            Cell()
                .add(coverCell("FECHA: $dateShort", bold = false, size = 10f))
                .setBorder(SolidBorder(ColorConstants.BLACK, 0.8f))
                .setTextAlignment(TextAlignment.RIGHT)
                .setVerticalAlignment(VerticalAlignment.TOP)
        )
        document.add(header)

        // Info row: CONTRACTISTA / GERENCIA / NODO / Evento
        val info = Table(UnitValue.createPercentArray(floatArrayOf(25f, 25f, 15f, 35f))).useAllAvailableWidth()
        listOf("CONTRATISTA", "GERENCIA", "NODO", "Evento").forEach { h ->
            info.addCell(
                Cell()
                    .add(coverCell(h, bold = true, size = 8f))
                    .setBackgroundColor(lightGray)
                    .setBorder(SolidBorder(ColorConstants.BLACK, 0.8f))
                    .setPadding(4f)
            )
        }
        info.addCell(Cell().add(coverCell(report.contractor.ifBlank { "—" }, size = 9f)).setBorder(SolidBorder(ColorConstants.BLACK, 0.8f)).setPadding(4f))
        info.addCell(Cell().add(coverCell("Mantenimiento Red de Acceso", size = 9f)).setBorder(SolidBorder(ColorConstants.BLACK, 0.8f)).setPadding(4f))
        info.addCell(Cell().add(coverCell(report.nodeName.ifBlank { "—" }, size = 9f)).setBorder(SolidBorder(ColorConstants.BLACK, 0.8f)).setPadding(4f))
        info.addCell(Cell().add(coverCell(report.eventName.ifBlank { "—" }, size = 9f)).setBorder(SolidBorder(ColorConstants.BLACK, 0.8f)).setPadding(4f))
        document.add(info)

        // Agregar más espacio antes del título grande
        document.add(Paragraph(" "))
        document.add(Paragraph(" "))
        document.add(Paragraph(" "))

        // Big centered title box
        val titleBox = Table(UnitValue.createPercentArray(floatArrayOf(1f))).setWidth(UnitValue.createPercentValue(85f))
            .setHorizontalAlignment(HorizontalAlignment.CENTER)
        titleBox.addCell(
            Cell()
                .add(coverCell("Informe de Mantenimiento Preventivo", bold = true, size = 16f))
                .setBackgroundColor(lightGray)
                .setBorder(SolidBorder(ColorConstants.BLACK, 1.2f))
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(16f)
        )
        document.add(Paragraph(" "))
        document.add(titleBox)
        
        // Agregar más espacio antes de la tabla de información general
        document.add(Paragraph(" "))
        document.add(Paragraph(" "))

        // Información General table (blue headers like template)
        val adjustedCount = countAdjustedAssets(report, assets)
        val passiveCounts = passives.groupingBy { it.type }.eachCount()
        fun countOf(t: PassiveType) = passiveCounts[t] ?: 0

        val blueHeader = DeviceRgb(0, 103, 184)
        val white = ColorConstants.WHITE
        val ig = Table(UnitValue.createPercentArray(floatArrayOf(72f, 28f))).useAllAvailableWidth()

        fun addBlueRow(left: String, right: String) {
            ig.addCell(
                Cell()
                    .add(coverCell(left, bold = true, size = 10f, color = white))
                    .setBackgroundColor(blueHeader)
                    .setBorder(SolidBorder(ColorConstants.BLACK, 0.8f))
                    .setPadding(5f)
            )
            ig.addCell(
                Cell()
                    .add(coverCell(right, bold = true, size = 10f, color = white))
                    .setBackgroundColor(blueHeader)
                    .setBorder(SolidBorder(ColorConstants.BLACK, 0.8f))
                    .setPadding(5f)
            )
        }
        fun addRow(left: String, right: String) {
            ig.addCell(Cell().add(coverCell(left, bold = true, size = 10f)).setBorder(SolidBorder(ColorConstants.BLACK, 0.8f)).setPadding(5f))
            ig.addCell(Cell().add(coverCell(right, bold = false, size = 10f)).setBorder(SolidBorder(ColorConstants.BLACK, 0.8f)).setPadding(5f))
        }

        // Section title
        ig.addCell(
            Cell(1, 2)
                .add(coverCell("Información General", bold = true, size = 12f))
                .setBorder(SolidBorder(ColorConstants.BLACK, 0.8f))
                .setPadding(6f)
        )

        addBlueRow("Datos Generales", "Nombre")
        addRow("Responsable", report.responsible.ifBlank { "—" })
        addRow("Contratista", report.contractor.ifBlank { "—" })
        addRow("Número del Medidor", report.meterNumber.ifBlank { "—" })

        addBlueRow("Items de Mantenimiento", "Cantidad")
        addRow("Taps Spliteado a reforma", countOf(PassiveType.TAPS_SPLITEADO_A_REFORMA).toString())
        addRow("Acometidas cortadas", countOf(PassiveType.ACOMETIDAS_CORTADAS).toString())
        addRow("Antirrobo Colocado", countOf(PassiveType.COLOCA_ANTIRROBO_A_TAP).toString())
        addRow("Pasivos normalizado o reparado", countOf(PassiveType.PASIVO_NORMALIZADO_O_REPARADO).toString())
        addRow("Activos ajustados", adjustedCount.toString())

        document.add(ig)
        document.add(Paragraph(" "))

        // ========================
        // Monitoria y QR (1 hoja después de la portada)
        // ========================
        run {
            pdf.defaultPageSize = normalPage
            document.add(AreaBreak(AreaBreakType.NEXT_PAGE))
            document.add(Paragraph("Monitoria y QR").setBold())
            document.add(Paragraph(" "))

            val byType = reportPhotos.groupBy { it.type }
            val qr = byType[ReportPhotoType.QR_RESULT].orEmpty().take(1)
            val nodo = byType[ReportPhotoType.NODE_PARAMS].orEmpty().take(1)
            val otros = byType[ReportPhotoType.OTHER_PARAMS].orEmpty().take(2)

            // Si no hay nada en "Otros Parámetros", no se generan esos recuadros.
            val includeOtros = otros.isNotEmpty()

            data class Slot(val title: String, val filePath: String?)
            val slots = mutableListOf<Slot>()
            slots += Slot("Resultado de QR", qr.firstOrNull()?.filePath)
            slots += Slot("Parámetros de Nodo", nodo.firstOrNull()?.filePath)
            if (includeOtros) {
                // Requisito actualizado: si hay "Otros Parámetros", la hoja debe tener 3 fotos como Inyección (3 por hoja).
                // Usamos la primera foto de "Otros Parámetros" en esta hoja.
                val first = otros.getOrNull(0)?.filePath
                slots += Slot("Otros Parámetros", first)
            }

            // Requisito: si NO hay "Otros Parámetros" -> 2 recuadros en la hoja con el MISMO tamaño que "Foto del Módulo y Tapa" (2 por hoja).
            // Si hay "Otros Parámetros" -> 3 recuadros en la hoja, repartidos como "Inyección de portadoras por puerto" (3 por hoja).
            val perPage = if (slots.size == 3) 3 else 2
            val boxH = if (perPage == 3) 260f else 360f
            val imgMaxH = if (perPage == 3) 220f else 330f

            fun addBoxWithContent(height: Float, content: (Cell) -> Unit) {
                val table = Table(UnitValue.createPercentArray(floatArrayOf(1f)))
                    .setWidth(UnitValue.createPercentValue(92f))
                    .setHorizontalAlignment(HorizontalAlignment.CENTER)
                val cell = Cell()
                    .setHeight(height)
                    .setBorder(SolidBorder(ColorConstants.BLACK, 1f))
                    .setTextAlignment(TextAlignment.CENTER)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE)
                content(cell)
                table.addCell(cell)
                document.add(table)
            }

            fun addMissingPhotoBox(sectionTitle: String, height: Float) {
                addBoxWithContent(height) { cell ->
                    cell.add(Paragraph("FOTO FALTANTE").setBold())
                    cell.add(Paragraph(sectionTitle))
                }
            }

            fun addImageBox(path: String, height: Float) {
                addBoxWithContent(height) { cell ->
                    val src = File(path)
                    if (!src.exists()) {
                        cell.add(Paragraph("FOTO FALTANTE").setBold())
                        return@addBoxWithContent
                    }
                    val tmp = File(context.cacheDir, "export/${report.id}/monitor/${src.name}")
                    runCatching { ImageCompressor.compressForExport(src, tmp) }
                    val use = if (tmp.exists()) tmp else src
                    val img = Image(ImageDataFactory.create(use.absolutePath))
                    img.scaleToFit(520f, imgMaxH)
                    img.setHorizontalAlignment(HorizontalAlignment.CENTER)
                    cell.add(img)
                }
            }

            // Una sola hoja: agregamos los recuadros en el orden definido.
            slots.take(perPage).forEach { s ->
                if (s.filePath.isNullOrBlank()) addMissingPhotoBox(s.title, boxH)
                else addImageBox(s.filePath, boxH)
            }
        }

        val reportFolderName = MaintenanceStorage.reportFolderName(report.eventName, report.id)
        val errorRed = DeviceRgb(183, 28, 28)
        fun formatDbmv(value: Double?): String = value?.let { CiscoHfcAmpCalculator.format1(it) } ?: "—"
        fun formatFreq(value: Double?): String =
            value?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "—"

        assets.forEach { asset ->
            // Next page size depends on the section we're about to start.
            pdf.defaultPageSize = if (asset.type == AssetType.AMPLIFIER) tablesPage else normalPage
            // Each asset starts on a new page (except first asset page after general info)
            document.add(AreaBreak(AreaBreakType.NEXT_PAGE))

            val headerLine = assetHeaderLine(report, asset)
            document.add(Paragraph(headerLine).setBold())
            document.add(Paragraph(" "))

            // For NODE: print "Ajuste de Nodo" on a full page BEFORE photos (as first subtitle after header)
            if (asset.type == AssetType.NODE) {
                document.add(Paragraph("Ajuste de Nodo").setBold())

                val nodeAdj = runCatching { repository.getNodeAdjustmentOne(asset.id) }.getOrNull()
                if (nodeAdj != null) {
                    // Plan snapshot
                    val planRows = listOfNotNull(
                        nodeAdj.planNode?.takeIf { it.isNotBlank() }?.let { listOf("Nodo" to null, it to null) },
                        nodeAdj.planContractor?.takeIf { it.isNotBlank() }?.let { listOf("Contratista" to null, it to null) },
                        nodeAdj.planTechnology?.takeIf { it.isNotBlank() }?.let { listOf("Tecnología" to null, it to null) },
                        nodeAdj.planPoDirecta?.takeIf { it.isNotBlank() }?.let { listOf("PO Directa" to null, it to null) },
                        nodeAdj.planPoRetorno?.takeIf { it.isNotBlank() }?.let { listOf("PO Retorno" to null, it to null) },
                        nodeAdj.planDistanciaSfp?.takeIf { it.isNotBlank() }?.let { listOf("Distancia SFP" to null, it to null) },
                    )
                    if (planRows.isNotEmpty()) {
                        addTable(
                            document,
                            title = "Datos del Plan",
                            headers = listOf("Campo", "Valor"),
                            rows = planRows,
                            colWidths = floatArrayOf(35f, 65f)
                        )
                    }

                    // Get technology from asset first, then fallback to plan
                    val tech = asset.technology?.trim()?.lowercase() 
                        ?: nodeAdj.planTechnology?.trim()?.lowercase() 
                        ?: "legacy"
                    val isLegacy = tech == "legacy"
                    val isRphy = tech == "rphy"
                    val isVccap = tech == "vccap"
                    val green = DeviceRgb(46, 125, 50)
                    val red = DeviceRgb(183, 28, 28)
                    fun okCell(ok: Boolean): Pair<String, com.itextpdf.kernel.colors.Color?> =
                        (if (ok) "Confirmado" else "Pendiente") to (if (ok) green else red)

                    when {
                        isRphy -> {
                            // RPHY: SFP + PO Directa + PO Retorno
                            val sfpText = nodeAdj.sfpDistance?.let { "$it km" } ?: "No seleccionado"
                            addTable(
                                document,
                                title = "RPHY - Configuración",
                                headers = listOf("Campo", "Valor"),
                                rows = listOf(
                                    listOf("SFP" to null, sfpText to null)
                                ),
                                colWidths = floatArrayOf(35f, 65f)
                            )
                            document.add(Paragraph(" "))
                            
                            addTable(
                                document,
                                title = "RPHY - PO Directa (llegando a Nodo)",
                                headers = listOf("Item", "Estado"),
                                rows = listOf(
                                    listOf("Confirmo PO Directa" to null, okCell(nodeAdj.poDirectaConfirmed))
                                ),
                                colWidths = floatArrayOf(75f, 25f)
                            )
                            nodeAdj.planPoDirecta?.takeIf { it.isNotBlank() }?.let { po ->
                                val poVal = Regex("[-+]?[0-9]*\\.?[0-9]+").find(po)?.value?.toDoubleOrNull()
                                val rangeText = when (nodeAdj.sfpDistance) {
                                    20 -> "-1 a -14 dBm"
                                    40 -> "-7 a -16 dBm"
                                    80 -> "-7 a -21 dBm"
                                    else -> "N/A"
                                }
                                val inRange = poVal != null && when (nodeAdj.sfpDistance) {
                                    20 -> poVal >= -14.0 && poVal <= -1.0
                                    40 -> poVal >= -16.0 && poVal <= -7.0
                                    80 -> poVal >= -21.0 && poVal <= -7.0
                                    else -> false
                                }
                                val color = if (inRange) green else red
                                document.add(
                                    Paragraph("PO Directa (Plan): $po  ·  Rango esperado: $rangeText")
                                        .setFontSize(9f)
                                        .setFontColor(color)
                                )
                            }
                            document.add(Paragraph(" "))
                            
                            addTable(
                                document,
                                title = "RPHY - PO Retorno (llegando a HUB)",
                                headers = listOf("Item", "Estado"),
                                rows = listOf(
                                    listOf("Confirmo PO Retorno" to null, okCell(nodeAdj.poRetornoConfirmed))
                                ),
                                colWidths = floatArrayOf(75f, 25f)
                            )
                            nodeAdj.planPoRetorno?.takeIf { it.isNotBlank() }?.let { po ->
                                val poVal = Regex("[-+]?[0-9]*\\.?[0-9]+").find(po)?.value?.toDoubleOrNull()
                                val rangeText = when (nodeAdj.sfpDistance) {
                                    20 -> "-1 a -14 dBm"
                                    40 -> "-7 a -16 dBm"
                                    80 -> "-7 a -21 dBm"
                                    else -> "N/A"
                                }
                                val inRange = poVal != null && when (nodeAdj.sfpDistance) {
                                    20 -> poVal >= -14.0 && poVal <= -1.0
                                    40 -> poVal >= -16.0 && poVal <= -7.0
                                    80 -> poVal >= -21.0 && poVal <= -7.0
                                    else -> false
                                }
                                val color = if (inRange) green else red
                                document.add(
                                    Paragraph("PO Retorno (Plan): $po  ·  Rango esperado: $rangeText")
                                        .setFontSize(9f)
                                        .setFontColor(color)
                                )
                            }
                        }
                        isVccap -> {
                            // VCCAP: SFP + PO Directa + PO Retorno + Espectro + DOCSIS
                            val sfpText = nodeAdj.sfpDistance?.let { "$it km" } ?: "No seleccionado"
                            addTable(
                                document,
                                title = "VCCAP - Configuración",
                                headers = listOf("Campo", "Valor"),
                                rows = listOf(
                                    listOf("SFP" to null, sfpText to null)
                                ),
                                colWidths = floatArrayOf(35f, 65f)
                            )
                            document.add(Paragraph(" "))
                            
                            addTable(
                                document,
                                title = "VCCAP - PO Directa (llegando a Nodo)",
                                headers = listOf("Item", "Estado"),
                                rows = listOf(
                                    listOf("Confirmo PO Directa" to null, okCell(nodeAdj.poDirectaConfirmed))
                                ),
                                colWidths = floatArrayOf(75f, 25f)
                            )
                            nodeAdj.planPoDirecta?.takeIf { it.isNotBlank() }?.let { po ->
                                val poVal = Regex("[-+]?[0-9]*\\.?[0-9]+").find(po)?.value?.toDoubleOrNull()
                                val rangeText = when (nodeAdj.sfpDistance) {
                                    20 -> "-1 a -14 dBm"
                                    40 -> "-7 a -16 dBm"
                                    80 -> "-7 a -21 dBm"
                                    else -> "N/A"
                                }
                                val inRange = poVal != null && when (nodeAdj.sfpDistance) {
                                    20 -> poVal >= -14.0 && poVal <= -1.0
                                    40 -> poVal >= -16.0 && poVal <= -7.0
                                    80 -> poVal >= -21.0 && poVal <= -7.0
                                    else -> false
                                }
                                val color = if (inRange) green else red
                                document.add(
                                    Paragraph("PO Directa (Plan): $po  ·  Rango esperado: $rangeText")
                                        .setFontSize(9f)
                                        .setFontColor(color)
                                )
                            }
                            document.add(Paragraph(" "))
                            
                            addTable(
                                document,
                                title = "VCCAP - PO Retorno (llegando a HUB)",
                                headers = listOf("Item", "Estado"),
                                rows = listOf(
                                    listOf("Confirmo PO Retorno" to null, okCell(nodeAdj.poRetornoConfirmed))
                                ),
                                colWidths = floatArrayOf(75f, 25f)
                            )
                            nodeAdj.planPoRetorno?.takeIf { it.isNotBlank() }?.let { po ->
                                val poVal = Regex("[-+]?[0-9]*\\.?[0-9]+").find(po)?.value?.toDoubleOrNull()
                                val rangeText = when (nodeAdj.sfpDistance) {
                                    20 -> "-1 a -14 dBm"
                                    40 -> "-7 a -16 dBm"
                                    80 -> "-7 a -21 dBm"
                                    else -> "N/A"
                                }
                                val inRange = poVal != null && when (nodeAdj.sfpDistance) {
                                    20 -> poVal >= -14.0 && poVal <= -1.0
                                    40 -> poVal >= -16.0 && poVal <= -7.0
                                    80 -> poVal >= -21.0 && poVal <= -7.0
                                    else -> false
                                }
                                val color = if (inRange) green else red
                                document.add(
                                    Paragraph("PO Retorno (Plan): $po  ·  Rango esperado: $rangeText")
                                        .setFontSize(9f)
                                        .setFontColor(color)
                                )
                            }
                            document.add(Paragraph(" "))
                            
                            addTable(
                                document,
                                title = "VCCAP - Espectro",
                                headers = listOf("Item", "Estado"),
                                rows = listOf(
                                    listOf("Confirmar espectro" to null, okCell(nodeAdj.spectrumConfirmed))
                                ),
                                colWidths = floatArrayOf(75f, 25f)
                            )
                            document.add(Paragraph(" "))
                            
                            val docsisText = when (asset.frequencyMHz) {
                                42 -> "DOCSIS en el equipo debe estar entre (29 a 34) dBmV ± 1dB"
                                85 -> "DOCSIS en el equipo debe estar entre (30 a 35) dBmV ± 1dB"
                                else -> "DOCSIS (frecuencia no especificada)"
                            }
                            addTable(
                                document,
                                title = "VCCAP - DOCSIS",
                                headers = listOf("Item", "Estado"),
                                rows = listOf(
                                    listOf(docsisText to null, okCell(nodeAdj.docsisConfirmed))
                                ),
                                colWidths = floatArrayOf(75f, 25f)
                            )
                        }
                        !isLegacy -> {
                            addTable(
                                document,
                                title = "Validación (No Legacy)",
                                headers = listOf("Item", "Estado"),
                                rows = listOf(
                                    listOf(
                                        "Asegure con HUB y operador que los parámetros estén correctos." to null,
                                        okCell(nodeAdj.nonLegacyConfirmed)
                                    )
                                ),
                                colWidths = floatArrayOf(75f, 25f)
                            )
                        }
                        else -> {
                            // Legacy
                            // DIRECTA
                            val rxOk = nodeAdj.tx1310Confirmed || nodeAdj.tx1550Confirmed
                            val directaRows = mutableListOf<List<Pair<String, com.itextpdf.kernel.colors.Color?>>>()
                            
                            // Solo mostrar las opciones confirmadas
                            if (nodeAdj.tx1310Confirmed) {
                                directaRows.add(listOf("Con Tx de 1310nm instalados en HUB colocar Pad de 9 en receptora de nodo" to null, okCell(true)))
                            }
                            if (nodeAdj.tx1550Confirmed) {
                                directaRows.add(listOf("Con Tx de 1550nm instalados en HUB colocar Pad de 10 en receptora de nodo" to null, okCell(true)))
                            }
                            
                            // Agregar PO solo si hay al menos una confirmación de TX
                            if (rxOk) {
                                directaRows.add(listOf("Confirmo PO" to null, okCell(nodeAdj.poConfirmed)))
                            }
                            
                            if (directaRows.isNotEmpty()) {
                                addTable(
                                    document,
                                    title = "DIRECTA",
                                    headers = listOf("Item", "Estado"),
                                    rows = directaRows,
                                    colWidths = floatArrayOf(75f, 25f)
                                )
                            }

                        // PO range helper (best-effort parse)
                        val poVal = Regex("[-+]?[0-9]*\\.?[0-9]+").find(nodeAdj.planPoDirecta.orEmpty())?.value?.toDoubleOrNull()
                        if (poVal != null) {
                            val inRange = poVal in 0.8..1.2
                            val color = if (inRange) green else red
                            document.add(
                                Paragraph("PO Directa (Plan): ${CiscoHfcAmpCalculator.format1(poVal)}  ·  Rango 0.8–1.2")
                                    .setFontSize(9f)
                                    .setFontColor(color)
                            )
                            document.add(Paragraph(" "))
                        }

                            // RETORNO
                            val txOk = !nodeAdj.rxPadSelection.isNullOrBlank()
                            val txPadText = when (nodeAdj.rxPadSelection) {
                                "TP_BLACK" -> {
                                    when (asset.frequencyMHz) {
                                        85 -> "Con FRECUENCIA de 85Mhz con test point y cartucho negro colocar pad de 8 en transmisora de nodo"
                                        42 -> "Con FRECUENCIA de 42Mhz con test point y cartucho negro colocar pad de 10 en transmisora de nodo"
                                        else -> "Con test point y cartucho negro"
                                    }
                                }
                                "TP_NO_BLACK" -> {
                                    when (asset.frequencyMHz) {
                                        85 -> "Con FRECUENCIA de 85Mhz con test point y SIN cartucho negro colocar pad de 9 en transmisora de nodo"
                                        42 -> "Con FRECUENCIA de 42Mhz con test point y SIN cartucho negro colocar pad de 10 en transmisora de nodo"
                                        else -> "Con test point y SIN cartucho negro"
                                    }
                                }
                                else -> "No seleccionado"
                            }
                            addTable(
                                document,
                                title = "RETORNO",
                                headers = listOf("Item", "Estado"),
                                rows = listOf(
                                    listOf("Pads TX: $txPadText" to null, okCell(txOk)),
                                    listOf("Confirmar medición en test point de RX" to null, okCell(nodeAdj.measurementConfirmed)),
                                    listOf("Confirmar espectro" to null, okCell(nodeAdj.spectrumConfirmed)),
                                ),
                                colWidths = floatArrayOf(75f, 25f)
                            )
                        }
                    }
                } else {
                    document.add(Paragraph("Sin datos de ajuste de nodo guardados.").setFontSize(9f))
                    document.add(Paragraph(" "))
                }

                // Move photos to the NEXT page; keep header at top like other pages.
                document.add(AreaBreak(AreaBreakType.NEXT_PAGE))
                document.add(Paragraph(headerLine).setBold())
                document.add(Paragraph(" "))
            }

            // For amplifiers: print "Ajuste de Amplificador" tables on a full page BEFORE photos
            if (asset.type == AssetType.AMPLIFIER) {
                document.add(Paragraph("Ajuste de Amplificador").setBold())

                val adj = repository.getAmplifierAdjustment(asset.id).first()
                if (adj != null) {
                    val bw = freqEnumFromMHz(asset.frequencyMHz)
                    val entradaCalc = CiscoHfcAmpCalculator.nivelesEntradaCalculados(adj)
                    val salidaCalc = CiscoHfcAmpCalculator.nivelesSalidaCalculados(adj)
                    val pad = CiscoHfcAmpCalculator.fwdInPad(adj, bw, asset.amplifierMode)
                    val tilt = CiscoHfcAmpCalculator.fwdInEqTilt(adj, bw)
                    val agc = CiscoHfcAmpCalculator.agcPad(adj, bw, asset.amplifierMode)

                    addTable(
                        document,
                        "Niveles ENTRADA medido vs plano",
                        headers = listOf("CANAL", "FREQ", "MEDIDO (dBmV)", "PLANO (dBmV)"),
                        rows = listOf(
                            listOf(
                                "CH50" to null,
                                "379 MHz" to null,
                                (adj.inputCh50Dbmv?.let { CiscoHfcAmpCalculator.format1(it) } ?: "—") to null,
                                (adj.inputPlanCh50Dbmv?.let { CiscoHfcAmpCalculator.format1(it) } ?: "—") to null
                            ),
                            listOf(
                                (if ((adj.inputHighFreqMHz ?: 750) == 870) "CH136" else "CH116") to null,
                                "${adj.inputHighFreqMHz ?: 750} MHz" to null,
                                (adj.inputCh116Dbmv?.let { CiscoHfcAmpCalculator.format1(it) } ?: "—") to null,
                                (adj.inputPlanHighDbmv?.let { CiscoHfcAmpCalculator.format1(it) } ?: "—") to null
                            )
                        ),
                        colWidths = floatArrayOf(20f, 20f, 30f, 30f)
                    )

                    val entradaRows = listOf(
                        "L 54" to 54,
                        "L102" to 102,
                        "CH3" to 61,
                        "CH50" to 379,
                        "CH70" to 495,
                        "CH116" to 750,
                        "CH136" to 870,
                        "CH158" to 1000
                    ).map { (c, f) ->
                        listOf(
                            c to null,
                            "$f MHz" to null,
                            (entradaCalc?.get(c)?.let { CiscoHfcAmpCalculator.format1(it) } ?: "—") to null
                        )
                    }
                    addTable(
                        document,
                        "Niveles ENTRADA calculados",
                        headers = listOf("CANAL", "FREQ", "CALC (dBmV)"),
                        rows = entradaRows,
                        colWidths = floatArrayOf(20f, 25f, 55f)
                    )

                    addTable(
                        document,
                        "Niveles SALIDA por plano",
                        headers = listOf("PUNTO", "FREQ", "AMPLITUD (dBmV)"),
                        rows = listOf(
                            listOf(
                                "L output" to null,
                                "${adj.planLowFreqMHz ?: 54} MHz" to null,
                                (adj.planLowDbmv?.let { CiscoHfcAmpCalculator.format1(it) } ?: "—") to null
                            ),
                            listOf(
                                "H output" to null,
                                "${adj.planHighFreqMHz ?: 750} MHz" to null,
                                (adj.planHighDbmv?.let { CiscoHfcAmpCalculator.format1(it) } ?: "—") to null
                            )
                        ),
                        colWidths = floatArrayOf(25f, 25f, 50f)
                    )

                    val salidaRows = listOf(
                        "L54" to 54,
                        "L102" to 102,
                        "CH3" to 61,
                        "CH50" to 379,
                        "CH70" to 495,
                        "CH110" to 711,
                        "CH116" to 750,
                        "CH136" to 870,
                        "CH158" to 1000
                    ).map { (c, f) ->
                        listOf(
                            c to null,
                            "$f MHz" to null,
                            (salidaCalc?.get(c)?.let { CiscoHfcAmpCalculator.format1(it) } ?: "—") to null
                        )
                    }
                    addTable(
                        document,
                        "Niveles SALIDA calculados",
                        headers = listOf("CANAL", "FREQ", "CALC (dBmV)"),
                        rows = salidaRows,
                        colWidths = floatArrayOf(20f, 25f, 55f)
                    )

                    val red = DeviceRgb(183, 28, 28)
                    fun difCell(calc: Double?, med: Double?): Pair<String, com.itextpdf.kernel.colors.Color?> {
                        if (calc == null || med == null) return "—" to null
                        val dif = med - calc
                        val abs = kotlin.math.abs(dif)
                        val txt = (if (dif >= 0) "+" else "") + CiscoHfcAmpCalculator.format1(dif)
                        return txt to (if (abs > 1.2) red else null)
                    }
                    val medRows = listOf(
                        Triple("CH50", 379, adj.outCh50Dbmv),
                        Triple("CH70", 495, adj.outCh70Dbmv),
                        Triple("CH110", 711, adj.outCh110Dbmv),
                        Triple("CH116", 750, adj.outCh116Dbmv),
                        Triple("CH136", 870, adj.outCh136Dbmv),
                    ).map { (c, f, med) ->
                        val calc = salidaCalc?.get(c)
                        val dif = difCell(calc, med)
                        listOf(
                            c to null,
                            "$f MHz" to null,
                            (calc?.let { CiscoHfcAmpCalculator.format1(it) } ?: "—") to null,
                            (med?.let { CiscoHfcAmpCalculator.format1(it) } ?: "—") to null,
                            dif
                        )
                    }
                    addTable(
                        document,
                        "Niveles SALIDA calculado vs medido",
                        headers = listOf("CANAL", "FREQ", "CALC", "medido", "DIF"),
                        rows = medRows,
                        colWidths = floatArrayOf(15f, 20f, 20f, 20f, 25f)
                    )

                    addTable(
                        document,
                        "FWD IN PAD/ EQ /AGC PAD a colocar",
                        headers = listOf("ITEM", "VALOR (dB)"),
                        rows = listOf(
                            listOf("FWD IN PAD" to null, (pad?.let { CiscoHfcAmpCalculator.format1(it) } ?: "—") to null),
                            listOf("FWD IN EQ (TILT)" to null, (tilt?.let { CiscoHfcAmpCalculator.format1(it) } ?: "—") to null),
                            listOf("AGC IN PAD" to null, (agc?.let { CiscoHfcAmpCalculator.format1(it) } ?: "—") to null),
                        ),
                        colWidths = floatArrayOf(60f, 40f)
                    )
                } else {
                    document.add(Paragraph("Sin datos de ajuste guardados."))
                    document.add(Paragraph(" "))
                }

                // Move photos to the NEXT page; keep header at top like other pages.
                document.add(AreaBreak(AreaBreakType.NEXT_PAGE))
                document.add(Paragraph(headerLine).setBold())
                document.add(Paragraph(" "))
            }

            // Carga de Mediciones (incluye RX + Módulo para NODO)
            run {
                val measurementAssets = buildList {
                    add("RX" to asset)
                    if (asset.type == AssetType.NODE) {
                        add("Módulo" to asset.copy(type = AssetType.AMPLIFIER))
                    }
                }
                val anyMeasurements = measurementAssets.any { (_, measurementAsset) ->
                    val measurementDir = MaintenanceStorage.ensureAssetDir(context, reportFolderName, measurementAsset)
                    measurementDir.listFiles()?.any { it.isFile } == true
                }
                if (anyMeasurements) {
                    document.add(Paragraph("Carga de Mediciones").setBold())
                }
                measurementAssets.forEach { (label, measurementAsset) ->
                    val measurementDir = MaintenanceStorage.ensureAssetDir(context, reportFolderName, measurementAsset)
                    val files = measurementDir.listFiles()?.filter { it.isFile } ?: emptyList()
                    val discardedLabels = loadDiscardedLabels(File(measurementDir, ".discarded_measurements.txt"))
                    if (files.isEmpty()) {
                        if (anyMeasurements) {
                            document.add(Paragraph("$label: Sin mediciones cargadas.").setFontSize(9f))
                            document.add(Paragraph(" "))
                        }
                        return@forEach
                    }

                    val required = requiredCounts(measurementAsset.type, isModule = measurementAsset.type == AssetType.AMPLIFIER && asset.type == AssetType.NODE)
                    val summary = verifyMeasurementFiles(
                        context = context,
                        files = files,
                        asset = measurementAsset,
                        repository = repository,
                        discardedLabels = discardedLabels,
                        expectedDocsisOverride = required.expectedDocsis,
                        expectedChannelOverride = required.expectedChannel
                    )
                    val entries = summary.result.measurementEntries
                        .filter { !it.isDiscarded && (it.type == "docsisexpert" || it.type == "channelexpert") }

                    if (entries.isEmpty()) {
                        document.add(Paragraph("$label: Sin mediciones válidas.").setFontSize(9f))
                        document.add(Paragraph(" "))
                        return@forEach
                    }

                    entries.forEach { entry ->
                        document.add(Paragraph("$label - ${entry.label}").setBold().setFontSize(10f))
                        if (entry.type == "docsisexpert") {
                            val rows = entry.docsisLevels.keys.sorted().map { freq ->
                                val meta = entry.docsisMeta[freq]
                                val channel = meta?.channel?.toString() ?: "—"
                                val freqText = formatFreq(meta?.frequencyMHz ?: freq)
                                val level = formatDbmv(entry.docsisLevels[freq])
                                val icfr = formatDbmv(entry.docsisIcfr[freq])
                                val ok = entry.docsisLevelOk[freq] != false
                                listOf(
                                    channel to null,
                                    "$freqText MHz" to null,
                                    level to if (ok) null else errorRed,
                                    icfr to null
                                )
                            }
                            addTable(
                                document,
                                title = "DOCSIS Expert - Upstream Channels",
                                headers = listOf("UCD", "Frecuencia (MHz)", "Nivel (dBmV)", "ICFR (dB)"),
                                rows = rows,
                                colWidths = floatArrayOf(15f, 30f, 30f, 25f)
                            )
                        } else if (entry.type == "channelexpert") {
                            val pilotRows: List<List<Pair<String, com.itextpdf.kernel.colors.Color?>>> =
                                entry.pilotLevels.entries.sortedBy { it.key }.map { (channel, level) ->
                                    val meta = entry.pilotMeta[channel]
                                    val freqText = formatFreq(meta?.frequencyMHz)
                                    val ok = entry.pilotLevelOk[channel] != false
                                    listOf(
                                        channel.toString() to null,
                                        "$freqText MHz" to null,
                                        formatDbmv(level) to if (ok) null else errorRed
                                    )
                                }
                            addTable(
                                document,
                                title = "Channel Expert - Downstream Analogic Channels",
                                headers = listOf("Canal", "Freq (MHz)", "Nivel (dBmV)"),
                                rows = pilotRows,
                                colWidths = floatArrayOf(20f, 35f, 45f)
                            )

                            val digitalRows: List<List<Pair<String, com.itextpdf.kernel.colors.Color?>>> =
                                entry.digitalRows.map { row ->
                                    val invalidMer = row.merOk == false
                                    val invalidBerPre = row.berPreOk == false
                                    val invalidBerPost = row.berPostOk == false
                                    val invalidIcfr = row.icfrOk == false
                                    listOf(
                                        row.channel.toString() to null,
                                        "${formatFreq(row.frequencyMHz)} MHz" to null,
                                        formatDbmv(row.levelDbmv) to null,
                                        formatDbmv(row.mer) to if (invalidMer) errorRed else null,
                                        row.berPre?.toString() ?: "—" to if (invalidBerPre) errorRed else null,
                                        row.berPost?.toString() ?: "—" to if (invalidBerPost) errorRed else null,
                                        formatDbmv(row.icfr) to if (invalidIcfr) errorRed else null
                                    )
                                }
                            addTable(
                                document,
                                title = "Channel Expert - Downstream Digital Channels",
                                headers = listOf("Canal", "Freq (MHz)", "Nivel (dBmV)", "MER", "BER pre", "BER post", "ICFR"),
                                rows = digitalRows,
                                colWidths = floatArrayOf(12f, 18f, 18f, 12f, 12f, 12f, 16f)
                            )
                        }
                    }
                }
            }

            // From here on, photos pages should use the normal height.
            pdf.defaultPageSize = normalPage

            // Photos (compressed on the fly to ~400KB)
            val photos = repository.getPhotosByAssetId(asset.id).first()
                .sortedBy { photoSortKey(it) }

            val grouped = photos.groupBy { it.photoType }
            fun requiredCount(asset: Asset, photoType: PhotoType): Int {
                val technology = asset.technology?.uppercase(Locale.ROOT)
                val isNode = asset.type == AssetType.NODE
                return when (photoType) {
                    PhotoType.MODULE -> if (isNode && technology == "RPHY") 0 else 2
                    PhotoType.OPTICS -> if (isNode && technology == "VCCAP") 0 else if (isNode) 2 else 0
                    PhotoType.MONITORING -> if (isNode) 2 else 0
                    PhotoType.SPECTRUM -> 3
                }
            }
            fun photoBoxHeight(t: PhotoType): Float = if (t == PhotoType.SPECTRUM) 260f else 360f

            fun addBoxWithContent(height: Float, content: (Cell) -> Unit) {
                val table = Table(UnitValue.createPercentArray(floatArrayOf(1f)))
                    .setWidth(UnitValue.createPercentValue(92f))
                    .setHorizontalAlignment(HorizontalAlignment.CENTER)
                val cell = Cell()
                    .setHeight(height)
                    .setBorder(SolidBorder(ColorConstants.BLACK, 1f))
                    .setTextAlignment(TextAlignment.CENTER)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE)
                content(cell)
                table.addCell(cell)
                document.add(table)
            }

            fun addMissingPhotoBox(sectionTitle: String, height: Float) {
                addBoxWithContent(height) { cell ->
                    cell.add(Paragraph("FOTO FALTANTE").setBold())
                    cell.add(Paragraph(sectionTitle))
                }
            }

            fun addImageBox(img: Image, height: Float) {
                addBoxWithContent(height) { cell ->
                    img.setHorizontalAlignment(HorizontalAlignment.CENTER)
                    cell.add(img)
                }
            }
            fun addPhotoBlock(
                types: List<PhotoType>,
                perPageOverride: ((PhotoType) -> Int)? = null
            ) {
                var firstSection = true
                types.forEach { t ->
                    val list = grouped[t].orEmpty()
                    val sectionTitle = photoSectionTitle(t)
                    val req = requiredCount(asset, t)
                    if (req <= 0) return@forEach

                    val perPage = perPageOverride?.invoke(t) ?: if (t == PhotoType.SPECTRUM) 3 else 2
                    val slots = mutableListOf<() -> Unit>()
                    val boxH = photoBoxHeight(t)

                    // Images (never more than required count)
                    val used = list.take(req)
                    used.forEach { photo ->
                        slots.add {
                            val src = File(photo.filePath)
                            if (!src.exists()) return@add
                            val tmp = File(context.cacheDir, "export/${report.id}/${asset.id}/${photo.id}.jpg")
                            runCatching { ImageCompressor.compressForExport(src, tmp) }
                            if (!tmp.exists()) return@add
                            val imgData = ImageDataFactory.create(tmp.absolutePath)
                            val img = Image(imgData)
                            // Fit so we can place 2 per page (or 3 for spectrum)
                            val maxH = if (t == PhotoType.SPECTRUM) 220f else 330f
                            img.scaleToFit(520f, maxH)
                            addImageBox(img, boxH)
                        }
                    }

                    // Missing placeholders
                    val missing = (req - used.size).coerceAtLeast(0)
                    repeat(missing) {
                        slots.add { addMissingPhotoBox(sectionTitle, boxH) }
                    }

                    if (slots.isEmpty()) return@forEach

                    // Ensure each photo section starts at the top of a page with its title + photos.
                    // First section already begins on a fresh asset page.
                    if (!firstSection) {
                        document.add(AreaBreak(AreaBreakType.NEXT_PAGE))
                        document.add(Paragraph(headerLine).setBold())
                        document.add(Paragraph(" "))
                    }
                    firstSection = false

                    // Paginate: 2 per page (3 for spectrum)
                    slots.chunked(perPage).forEachIndexed { pageIdx, chunk ->
                        if (pageIdx > 0) {
                            document.add(AreaBreak(AreaBreakType.NEXT_PAGE))
                            // repeat asset header for continuation pages
                            document.add(Paragraph(headerLine).setBold())
                            document.add(Paragraph(" "))
                        }
                        document.add(Paragraph(sectionTitle).setBold())
                        chunk.forEach { it.invoke() }
                        document.add(Paragraph(" "))
                    }

                }
            }

            // Render sections. Each section starts on a fresh page (except the first).
            // Pagination inside section: 2 per page, except Spectrum: 3 per page.
            if (asset.type == AssetType.AMPLIFIER) {
                addPhotoBlock(listOf(PhotoType.MODULE, PhotoType.SPECTRUM))
            } else {
                addPhotoBlock(listOf(PhotoType.MODULE, PhotoType.OPTICS, PhotoType.MONITORING, PhotoType.SPECTRUM))
            }
        }

        // Passives table at the end
        if (passives.isNotEmpty()) {
            pdf.defaultPageSize = normalPage
            document.add(AreaBreak(AreaBreakType.NEXT_PAGE))
            document.add(Paragraph("Intervenciones en Pasivos").setBold())
            document.add(Paragraph(" "))

            val rows = passives.map { p ->
                listOf(
                    p.address to null,
                    p.type.label to null,
                    p.observation to null
                )
            }
            addTable(
                document,
                title = "",
                headers = listOf("Dirección", "Tipo", "Observación"),
                rows = rows,
                colWidths = floatArrayOf(40f, 30f, 30f)
            )
        }
        
        document.close()
        file
    }

    suspend fun exportPdfToDownloads(report: MaintenanceReport): Uri = withContext(Dispatchers.IO) {
        val tmp = exportToPDF(report)
        val displayName = "${exportBaseName(report)}.pdf"
        DownloadStore.saveToDownloads(context, tmp, displayName, "application/pdf")
    }
    
    suspend fun exportToZIP(report: MaintenanceReport): File = withContext(Dispatchers.IO) {
        val assets = repository.getAssetsByReportId(report.id).first()
            .sortedBy { assetSortKey(it) }
        val passives = repository.getPassivesByReportId(report.id).first()
        val reportPhotos = repository.getReportPhotosByReportId(report.id).first()
        val exportDir = File(context.cacheDir, "export_zip/${report.id}")
        if (exportDir.exists()) exportDir.deleteRecursively()
        exportDir.mkdirs()

        val imagesDir = File(exportDir, "images").apply { mkdirs() }
        val measurementsDir = File(exportDir, "measurements").apply { mkdirs() }

        val photosByAsset = mutableMapOf<String, List<Photo>>()
        val imagesByAsset = mutableMapOf<String, List<ExportImageRef>>()
        val adjustmentsByAsset = mutableMapOf<String, AmplifierAdjustment>()
        val nodeAdjustmentsByAsset = mutableMapOf<String, NodeAdjustment>()
        val reportImageRefs = mutableListOf<ExportReportImageRef>()
        val switchSelections = collectSwitchSelections(assets)

        val reportFolderName = MaintenanceStorage.reportFolderName(report.eventName, report.id)
        assets.forEach { asset ->
            // Persist amplifier adjustment (if any) inside report.json
            repository.getAmplifierAdjustment(asset.id).first()?.let { adj ->
                adjustmentsByAsset[asset.id] = adj
            }
            // Persist node adjustment (if any) inside report.json
            if (asset.type == AssetType.NODE) {
                repository.getNodeAdjustment(asset.id).first()?.let { adj ->
                    nodeAdjustmentsByAsset[asset.id] = adj
                }
            }

            val photos = repository.getPhotosByAssetId(asset.id).first()
                .sortedBy { photoSortKey(it) }
            photosByAsset[asset.id] = photos

            val refs = mutableListOf<ExportImageRef>()
            photos.forEach { photo ->
                val src = File(photo.filePath)
                if (!src.exists()) return@forEach

                val safeName = "${asset.id}_${photo.photoType.name.lowercase()}_${photo.id}.jpg"
                val dest = File(imagesDir, safeName)
                runCatching { ImageCompressor.compressForExport(src, dest) }
                if (!dest.exists()) return@forEach

                refs.add(
                    ExportImageRef(
                        photoId = photo.id,
                        photoType = photo.photoType,
                        fileName = "images/$safeName"
                    )
                )
            }
            imagesByAsset[asset.id] = refs

            val measurementAssets = buildList {
                add(asset)
                if (asset.type == AssetType.NODE) {
                    add(asset.copy(type = AssetType.AMPLIFIER))
                }
            }
            measurementAssets.forEach { measurementAsset ->
                val sourceMeasurementsDir = MaintenanceStorage.ensureAssetDir(context, reportFolderName, measurementAsset)
                if (sourceMeasurementsDir.exists()) {
                    val measurementTargetDir = File(measurementsDir, MaintenanceStorage.assetFolderName(measurementAsset))
                        .apply { mkdirs() }
                    sourceMeasurementsDir.listFiles()
                        ?.filter { it.isFile }
                        ?.forEach { file ->
                            file.copyTo(File(measurementTargetDir, file.name), overwrite = true)
                        }
                }
            }
        }

        // Report-level images (Monitoria y QR)
        reportPhotos.forEach { rp ->
            val src = File(rp.filePath)
            if (!src.exists()) return@forEach

            val safeName = "report_${rp.type.name.lowercase()}_${rp.id}.jpg"
            val dest = File(imagesDir, safeName)
            runCatching { ImageCompressor.compressForExport(src, dest) }
            if (!dest.exists()) return@forEach

            reportImageRefs.add(
                ExportReportImageRef(
                    photoId = rp.id,
                    type = rp.type,
                    fileName = "images/$safeName"
                )
            )
        }

        val exportData = ExportDataV2(
            report = report,
            assets = assets,
            images = imagesByAsset,
            reportImages = if (reportImageRefs.isEmpty()) null else reportImageRefs,
            adjustments = if (adjustmentsByAsset.isEmpty()) null else adjustmentsByAsset,
            nodeAdjustments = if (nodeAdjustmentsByAsset.isNotEmpty()) nodeAdjustmentsByAsset else null,
            passives = passives,
            switchSelections = if (switchSelections.isNotEmpty()) switchSelections else null
        )
        File(exportDir, "report.json").writeText(gson.toJson(exportData))

        val zipFile = File(context.getExternalFilesDir(null), "maintenance_${report.id}.zip")
        if (zipFile.exists()) zipFile.delete()
        
        ZipFile(zipFile).apply {
            addFolder(exportDir)
        }
        
        zipFile
    }

    suspend fun exportToBundleZip(report: MaintenanceReport): File = withContext(Dispatchers.IO) {
        val pdfFile = exportToPDF(report)
        val jsonZip = exportToZIP(report)
        val baseName = exportBaseName(report)

        val bundleZip = File(context.getExternalFilesDir(null), "maintenance_${report.id}_bundle.zip")
        if (bundleZip.exists()) bundleZip.delete()

        ZipFile(bundleZip).apply {
            addFile(
                pdfFile,
                ZipParameters().apply { fileNameInZip = "$baseName.pdf" }
            )
            addFile(
                jsonZip,
                ZipParameters().apply { fileNameInZip = "${baseName}_json.zip" }
            )
        }

        bundleZip
    }

    suspend fun exportZipToDownloads(report: MaintenanceReport): Uri = withContext(Dispatchers.IO) {
        val tmp = exportToZIP(report)
        val displayName = "${exportBaseName(report)}.zip"
        DownloadStore.saveToDownloads(context, tmp, displayName, "application/zip")
    }

    suspend fun exportBundleToDownloads(report: MaintenanceReport): Uri = withContext(Dispatchers.IO) {
        val tmp = exportToBundleZip(report)
        val displayName = "${exportBaseName(report)}.zip"
        DownloadStore.saveToDownloads(context, tmp, displayName, "application/zip")
    }

    /**
     * Exporta a un Uri (ej: Google Drive vía picker). Si falla, lanza excepción.
     */
    suspend fun exportPdfToUri(report: MaintenanceReport, uri: Uri, contentResolver: ContentResolver = context.contentResolver) =
        withContext(Dispatchers.IO) {
            val tmp = exportToPDF(report)
            require(tmp.exists()) { "PDF temporal no existe" }
            if (tmp.length() <= 0L) throw IllegalStateException("PDF temporal vacío")

            // Use ParcelFileDescriptor for better compatibility with Drive providers.
            // "rwt" helps some providers (truncate + write).
            val pfd: ParcelFileDescriptor =
                (contentResolver.openFileDescriptor(uri, "rwt")
                    ?: contentResolver.openFileDescriptor(uri, "w"))
                    ?: throw IllegalStateException("No se pudo abrir FileDescriptor para Uri")

            pfd.use { fd ->
                FileInputStream(tmp).use { input ->
                    JFileOutputStream(fd.fileDescriptor).use { out ->
                        input.copyTo(out)
                        out.flush()
                        runCatching { out.fd.sync() }
                    }
                }
            }
        }

    /**
     * Exporta ZIP editable a un Uri (ej: Google Drive vía picker). Si falla, lanza excepción.
     */
    suspend fun exportZipToUri(report: MaintenanceReport, uri: Uri, contentResolver: ContentResolver = context.contentResolver) =
        withContext(Dispatchers.IO) {
            val tmp = exportToZIP(report)
            require(tmp.exists()) { "ZIP temporal no existe" }
            if (tmp.length() <= 0L) throw IllegalStateException("ZIP temporal vacío")

            val pfd: ParcelFileDescriptor =
                (contentResolver.openFileDescriptor(uri, "rwt")
                    ?: contentResolver.openFileDescriptor(uri, "w"))
                    ?: throw IllegalStateException("No se pudo abrir FileDescriptor para Uri")

            pfd.use { fd ->
                FileInputStream(tmp).use { input ->
                    JFileOutputStream(fd.fileDescriptor).use { out ->
                        input.copyTo(out)
                        out.flush()
                        runCatching { out.fd.sync() }
                    }
                }
            }
        }

    suspend fun exportBundleToUri(report: MaintenanceReport, uri: Uri, contentResolver: ContentResolver = context.contentResolver) =
        withContext(Dispatchers.IO) {
            val tmp = exportToBundleZip(report)
            require(tmp.exists()) { "ZIP temporal no existe" }
            if (tmp.length() <= 0L) throw IllegalStateException("ZIP temporal vacío")

            val pfd: ParcelFileDescriptor =
                (contentResolver.openFileDescriptor(uri, "rwt")
                    ?: contentResolver.openFileDescriptor(uri, "w"))
                    ?: throw IllegalStateException("No se pudo abrir FileDescriptor para Uri")

            pfd.use { fd ->
                FileInputStream(tmp).use { input ->
                    JFileOutputStream(fd.fileDescriptor).use { out ->
                        input.copyTo(out)
                        out.flush()
                        runCatching { out.fd.sync() }
                    }
                }
            }
        }
    
    suspend fun importFromJSON(uri: Uri): MaintenanceReport? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val jsonString = inputStream?.bufferedReader().use { it?.readText() }
            val exportData: ExportData = gson.fromJson(jsonString, ExportData::class.java)
            
            repository.insertReport(exportData.report)
            exportData.assets.forEach { asset ->
                repository.insertAsset(asset)
            }
            exportData.photos.values.flatten().forEach { photo ->
                repository.insertPhoto(photo)
            }
            exportData.passives?.forEach { p ->
                repository.insertPassive(p)
            }
            exportData.reportPhotos?.forEach { rp ->
                repository.upsertReportPhoto(rp)
            }
            exportData.adjustments?.forEach { (assetId, adj) ->
                repository.upsertAmplifierAdjustment(adj.copy(assetId = assetId))
            }
            exportData.nodeAdjustments?.forEach { (assetId, adj) ->
                // Ensure assetId and reportId are preserved from the imported data
                repository.upsertNodeAdjustment(adj.copy(assetId = assetId, reportId = exportData.report.id))
            }

            exportData.measurementFiles?.let { files ->
                val reportFolderName = MaintenanceStorage.reportFolderName(exportData.report.eventName, exportData.report.id)
                val reportDir = MaintenanceStorage.ensureReportDir(context, reportFolderName)
                files.forEach { measurement ->
                    val rel = measurement.relativePath.removePrefix("/").replace("\\", "/")
                    val dest = File(reportDir, rel)
                    dest.parentFile?.mkdirs()
                    if (measurement.base64.isNotBlank()) {
                        val bytes = Base64.decode(measurement.base64, Base64.DEFAULT)
                        dest.writeBytes(bytes)
                    } else {
                        dest.writeText("")
                    }
                }
            }
            applySwitchSelections(exportData.switchSelections)
            
            exportData.report
        } catch (e: Exception) {
            null
        }
    }

    suspend fun importFromZip(uri: Uri): MaintenanceReport? = withContext(Dispatchers.IO) {
        try {
            val tmpZip = File(context.cacheDir, "import_${System.currentTimeMillis()}.zip")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmpZip).use { output -> input.copyTo(output) }
            } ?: return@withContext null

            val extractDir = File(context.cacheDir, "import_zip/${System.currentTimeMillis()}")
            if (extractDir.exists()) extractDir.deleteRecursively()
            extractDir.mkdirs()

            ZipFile(tmpZip).extractAll(extractDir.absolutePath)

            // The zip may contain a top-level folder (e.g. reportId). Search recursively.
            var reportJson = extractDir.walkTopDown().firstOrNull { it.isFile && it.name == "report.json" }
            var baseDir = reportJson?.parentFile ?: extractDir

            if (reportJson == null) {
                val nestedZip = extractDir.walkTopDown()
                    .firstOrNull { it.isFile && it.extension.equals("zip", ignoreCase = true) }
                    ?: return@withContext null

                val nestedDir = File(context.cacheDir, "import_nested/${System.currentTimeMillis()}")
                if (nestedDir.exists()) nestedDir.deleteRecursively()
                nestedDir.mkdirs()
                ZipFile(nestedZip).extractAll(nestedDir.absolutePath)
                reportJson = nestedDir.walkTopDown().firstOrNull { it.isFile && it.name == "report.json" }
                    ?: return@withContext null
                baseDir = reportJson.parentFile ?: nestedDir
            }

            val exportData: ExportDataV2 = gson.fromJson(reportJson.readText(), ExportDataV2::class.java)
            val report = exportData.report.copy(deletedAt = null)

            // If this report already exists, wipe its current assets/photos first to avoid duplication.
            val existing = repository.getReportById(report.id)
            if (existing != null) {
                val existingAssets = repository.listAssetsByReportId(report.id)
                existingAssets.forEach { asset ->
                    // deletes photos (and their files) + asset row
                    repository.deleteAsset(asset)
                }
                repository.deleteReportPhotosByReportId(report.id)
                repository.deletePassivesByReportId(report.id)
            }

            // Insert report + assets
            repository.insertReport(report)
            exportData.assets.forEach { repository.insertAsset(it) }

            // Restore amplifier adjustments (if present in the zip)
            exportData.adjustments?.forEach { (assetId, adj) ->
                repository.upsertAmplifierAdjustment(adj.copy(assetId = assetId))
            }
            // Restore node adjustments (if present in the zip)
            exportData.nodeAdjustments?.forEach { (assetId, adj) ->
                repository.upsertNodeAdjustment(adj.copy(assetId = assetId, reportId = report.id))
            }

            // Restore passives
            exportData.passives?.forEach { p ->
                repository.insertPassive(p.copy(reportId = report.id))
            }

            applySwitchSelections(exportData.switchSelections)

            // Restore report images (Monitoria y QR)
            exportData.reportImages?.forEach { ref ->
                val rel = ref.fileName.removePrefix("/").replace("\\", "/")
                val src = File(baseDir, rel)
                if (!src.exists()) return@forEach

                val destDir = ImageStore.reportPhotoDir(
                    context = context,
                    reportId = report.id,
                    section = ref.type.name.lowercase()
                )
                val dest = File(destDir, src.name)
                src.copyTo(dest, overwrite = true)
                repository.upsertReportPhoto(
                    ReportPhoto(
                        id = ref.photoId,
                        reportId = report.id,
                        type = ref.type,
                        filePath = dest.absolutePath,
                        fileName = dest.name
                    )
                )
            }

            // Copy images into app storage and create Photo records
            exportData.images.forEach { (assetId, refs) ->
                refs.forEach { ref ->
                    val rel = ref.fileName.removePrefix("/").replace("\\", "/")
                    val src = File(baseDir, rel)
                    if (!src.exists()) return@forEach

                    val destDir = ImageStore.assetPhotoDir(
                        context = context,
                        reportId = report.id,
                        assetId = assetId,
                        photoType = ref.photoType.name.lowercase()
                    )
                    val dest = File(destDir, src.name)
                    // Keep the already-compressed file, but ensure orientation rule for the app isn't needed here.
                    src.copyTo(dest, overwrite = true)

                    repository.insertPhoto(
                        Photo(
                            id = ref.photoId,
                            assetId = assetId,
                            photoType = ref.photoType,
                            filePath = dest.absolutePath,
                            fileName = dest.name
                        )
                    )
                }
            }

            val measurementsDir = File(baseDir, "measurements")
            if (measurementsDir.exists()) {
                val reportFolderName = MaintenanceStorage.reportFolderName(report.eventName, report.id)
                exportData.assets.forEach { asset ->
                    val measurementAssets = buildList {
                        add(asset)
                        if (asset.type == AssetType.NODE) {
                            add(asset.copy(type = AssetType.AMPLIFIER))
                        }
                    }
                    measurementAssets.forEach { measurementAsset ->
                        val sourceDir = File(measurementsDir, MaintenanceStorage.assetFolderName(measurementAsset))
                        if (!sourceDir.exists()) return@forEach
                        val destDir = MaintenanceStorage.ensureAssetDir(context, reportFolderName, measurementAsset)
                        sourceDir.listFiles()
                            ?.filter { it.isFile }
                            ?.forEach { file ->
                                file.copyTo(File(destDir, file.name), overwrite = true)
                            }
                    }
                }
            }

            report
        } catch (_: Exception) {
            null
        }
    }
}

data class ExportData(
    val report: MaintenanceReport,
    val assets: List<Asset>,
    val photos: Map<String, List<Photo>>,
    val passives: List<PassiveItem>? = null,
    val reportPhotos: List<ReportPhoto>? = null,
    val nodeAdjustments: Map<String, NodeAdjustment>? = null,
    val adjustments: Map<String, AmplifierAdjustment>? = null,
    val measurementFiles: List<ExportMeasurementFile>? = null,
    val switchSelections: Map<String, Map<String, String>>? = null
)

data class ExportMeasurementFile(
    val relativePath: String,
    val base64: String
)

data class ExportImageRef(
    val photoId: String,
    val photoType: PhotoType,
    val fileName: String
)

/**
 * V2 export: images are stored as relative file names inside the zip,
 * so the package is portable between devices.
 */
data class ExportDataV2(
    val report: MaintenanceReport,
    val assets: List<Asset>,
    val images: Map<String, List<ExportImageRef>>,
    val reportImages: List<ExportReportImageRef>? = null,
    val adjustments: Map<String, AmplifierAdjustment>? = null,
    val nodeAdjustments: Map<String, NodeAdjustment>? = null,
    val passives: List<PassiveItem>? = null,
    val switchSelections: Map<String, Map<String, String>>? = null
)

data class ExportReportImageRef(
    val photoId: String,
    val type: ReportPhotoType,
    val fileName: String
)
