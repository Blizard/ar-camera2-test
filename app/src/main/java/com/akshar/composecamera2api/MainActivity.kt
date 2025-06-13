package com.akshar.composecamera2api

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akshar.composecamera2api.ui.theme.ComposeCamera2ApiTheme

class MainActivity : ComponentActivity() {
    private var cameraPermissionGranted by mutableStateOf(false)

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        cameraPermissionGranted = isGranted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Check permissions and request if not granted
        cameraPermissionGranted = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

        if (!cameraPermissionGranted) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            ComposeCamera2ApiTheme {
                MainScreen(
                    cameraPermissionGranted = cameraPermissionGranted,
                    grantCameraPermission = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                )
            }
        }
    }
}

@Composable
private fun MainScreen(
    cameraPermissionGranted: Boolean,
    grantCameraPermission: () -> Unit,
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        when {
            cameraPermissionGranted -> {
                CameraPreview(modifier = Modifier.padding(innerPadding))
            }
            !cameraPermissionGranted -> {
                PermissionScreen(
                    message = "Camera permission is required to use this feature.",
                    onGrantPermission = grantCameraPermission,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun PermissionScreen(
    message: String,
    onGrantPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message + " Click here to grant permission.",
            modifier = Modifier
                .padding(16.dp)
                .clickable { onGrantPermission() }
        )
    }
}