package com.akshar.composecamera2api

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Composable function to display a camera preview using Camera2 API.
 *
 * @param modifier Modifier to be applied to the layout.
 */
@Composable
fun CameraPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showSnackbar by remember { mutableStateOf(false) }
    var cameraDevice by remember { mutableStateOf<CameraDevice?>(null) }
    var captureSession by remember { mutableStateOf<CameraCaptureSession?>(null) }
    var imageReader by remember { mutableStateOf<ImageReader?>(null) }
    var captureMessage by remember { mutableStateOf("") }

    // Clean up camera resources when composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            captureSession?.close()
            cameraDevice?.close()
            imageReader?.close()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                            if (isCamera2Supported(context)) {
                                openCameraWithCapture(
                                    context = context,
                                    textureView = this@apply,
                                    handler = Handler(Looper.getMainLooper()),
                                    onCameraOpened = { device, session, reader ->
                                        cameraDevice = device
                                        captureSession = session
                                        imageReader = reader
                                    },
                                    onError = { showSnackbar = true }
                                )
                            } else {
                                showSnackbar = true
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {}
                        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean = true
                        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
                    }
                }
            }
        )

        // Capture button positioned at the bottom center
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (captureMessage.isNotEmpty()) {
                Text(
                    text = captureMessage,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .background(
                            Color.Black.copy(alpha = 0.7f),
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            
            Button(
                onClick = {
                    capturePhoto(
                        context = context,
                        captureSession = captureSession,
                        imageReader = imageReader,
                        onCaptureComplete = { message ->
                            captureMessage = message
                            // Clear message after 3 seconds
                            Handler(Looper.getMainLooper()).postDelayed({
                                captureMessage = ""
                            }, 3000)
                        }
                    )
                },
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape),
                enabled = captureSession != null && imageReader != null
            ) {
                Text("📷")
            }
        }
    }

    if (showSnackbar) {
        LaunchedEffect(snackbarHostState) {
            snackbarHostState.showSnackbar("Camera2 API is not supported on this device")
        }
    }
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.padding(16.dp)
    )
}

/**
 * Checks if the Camera2 API is supported on the device.
 *
 * @param context The context of the application.
 * @return True if Camera2 API is supported, false otherwise.
 */
fun isCamera2Supported(context: Context): Boolean {
    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val cameraId = cameraManager.cameraIdList[0] // Assume using the back camera

    val characteristics = cameraManager.getCameraCharacteristics(cameraId)
    val supportLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)

    return supportLevel != CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
}

/**
 * Opens the camera with photo capture capability and starts the preview.
 *
 * @param context The context of the application.
 * @param textureView The TextureView to display the camera preview.
 * @param handler The handler to run the camera operations.
 * @param onCameraOpened Callback when camera is successfully opened.
 * @param onError Callback when an error occurs.
 */
fun openCameraWithCapture(
    context: Context, 
    textureView: TextureView, 
    handler: Handler,
    onCameraOpened: (CameraDevice, CameraCaptureSession, ImageReader) -> Unit,
    onError: () -> Unit
) {
    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val cameraId = cameraManager.cameraIdList[0]

    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
        onError()
        return
    }

    // Get the largest available size for the image capture
    val characteristics = cameraManager.getCameraCharacteristics(cameraId)
    val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
    val largestSize = map.getOutputSizes(ImageFormat.JPEG).maxByOrNull { it.height * it.width }!!

    // Set up ImageReader for photo capture
    val imageReader = ImageReader.newInstance(largestSize.width, largestSize.height, ImageFormat.JPEG, 1)

    cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            val previewSurface = Surface(textureView.surfaceTexture)
            val captureSurface = imageReader.surface
            
            val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(previewSurface)
            }
            
            camera.createCaptureSession(
                listOf(previewSurface, captureSurface), 
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureRequest.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                        session.setRepeatingRequest(captureRequest.build(), null, handler)
                        onCameraOpened(camera, session, imageReader)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        onError()
                    }
                }, 
                handler
            )
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
            onError()
        }
    }, handler)
}

/**
 * Captures a photo and saves it to the device storage.
 *
 * @param context The context of the application.
 * @param captureSession The active camera capture session.
 * @param imageReader The ImageReader for capturing photos.
 * @param onCaptureComplete Callback when capture is complete with result message.
 */
fun capturePhoto(
    context: Context,
    captureSession: CameraCaptureSession?,
    imageReader: ImageReader?,
    onCaptureComplete: (String) -> Unit
) {
    if (captureSession == null || imageReader == null) {
        onCaptureComplete("Camera not ready")
        return
    }

    try {
        val reader = imageReader
        reader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                try {
                    saveImageToStorage(image) { success, message ->
                        onCaptureComplete(message)
                    }
                } finally {
                    image.close()
                }
            }
        }, Handler(Looper.getMainLooper()))

        // Create capture request using the existing session's device
        val captureRequestBuilder = captureSession.device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        captureRequestBuilder.addTarget(reader.surface)
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)

        // Use the existing capture session
        captureSession.capture(
            captureRequestBuilder.build(),
            object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    // Photo capture completed successfully
                }

                override fun onCaptureFailed(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    failure: android.hardware.camera2.CaptureFailure
                ) {
                    onCaptureComplete("Capture failed")
                }
            },
            Handler(Looper.getMainLooper())
        )
    } catch (e: Exception) {
        onCaptureComplete("Capture failed: ${e.message}")
    }
}

/**
 * Saves the captured image to device storage.
 *
 * @param context The context of the application.
 * @param image The captured image.
 * @param onComplete Callback when save operation is complete.
 */
fun saveImageToStorage(
    image: Image,
    onComplete: (Boolean, String) -> Unit
) {
    try {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.capacity())
        buffer.get(bytes)

        // Create filename with timestamp
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "IMG_$timeStamp.jpg"

        // Save to Pictures directory
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val appDir = File(picturesDir, "ComposeCamera2Api")
        if (!appDir.exists()) {
            appDir.mkdirs()
        }

        val file = File(appDir, fileName)
        FileOutputStream(file).use { output ->
            output.write(bytes)
        }

        onComplete(true, "Photo saved: ${file.absolutePath}")
    } catch (e: IOException) {
        onComplete(false, "Failed to save photo: ${e.message}")
    }
}