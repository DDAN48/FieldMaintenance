package com.example.fieldmaintenance.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.fieldmaintenance.ui.navigation.Screen
import com.example.fieldmaintenance.util.MaintenanceStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import java.io.File

@Composable
fun MeasurementsTrashScreen(navController: NavController) {
    val context = LocalContext.current
    var files by remember { mutableStateOf(MaintenanceStorage.listMeasurementTrashFiles(context)) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Papelera de Mediciones") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = {
                        navController.popBackStack(Screen.Home.route, inclusive = false)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (files.isEmpty()) {
                Text("No hay mediciones en la papelera.")
                return@Column
            }

            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(files) { file ->
                    MeasurementTrashRow(
                        file = file,
                        onRestore = {
                            scope.launch(Dispatchers.IO) {
                                MaintenanceStorage.restoreMeasurementFile(context, file)
                                val updated = MaintenanceStorage.listMeasurementTrashFiles(context)
                                withContext(Dispatchers.Main) { files = updated }
                            }
                        },
                        onDelete = {
                            scope.launch(Dispatchers.IO) {
                                file.delete()
                                val updated = MaintenanceStorage.listMeasurementTrashFiles(context)
                                withContext(Dispatchers.Main) { files = updated }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MeasurementTrashRow(
    file: File,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = file.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(text = file.parentFile?.name ?: "", style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onRestore) { Text("Restaurar") }
                TextButton(onClick = onDelete) { Text("Eliminar") }
            }
        }
    }
}
