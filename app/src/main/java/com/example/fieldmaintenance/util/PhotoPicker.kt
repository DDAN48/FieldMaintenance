package com.example.fieldmaintenance.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun rememberPhotoPickerLauncher(
    onPhotoSelected: (Uri?) -> Unit
): androidx.activity.result.ActivityResultLauncher<String> {
    val context = LocalContext.current
    
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        onPhotoSelected(uri)
    }
}

@Composable
fun rememberCameraLauncher(
    onPhotoTaken: (Uri?) -> Unit
): androidx.activity.result.ActivityResultLauncher<Uri> {
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        // The URI is already handled by the contract
        onPhotoTaken(null) // You'll need to pass the URI from PhotoManager
    }
}

fun hasCameraPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
}

