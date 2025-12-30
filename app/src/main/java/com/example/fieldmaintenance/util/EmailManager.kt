package com.example.fieldmaintenance.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object EmailManager {
    fun sendEmail(context: Context, reportTitle: String, attachments: List<File>) {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_SUBJECT, "Reporte de Mantenimiento: $reportTitle")
            putExtra(Intent.EXTRA_TEXT, "Adjunto encontrarás el reporte de mantenimiento HFC.")
            
            val uris = ArrayList<Uri>()
            attachments.forEach { file ->
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                uris.add(uri)
            }
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        
        context.startActivity(Intent.createChooser(intent, "Enviar correo"))
    }
}

