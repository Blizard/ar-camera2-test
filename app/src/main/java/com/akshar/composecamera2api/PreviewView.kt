package com.akshar.composecamera2api

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.util.Size
import android.view.Display
import android.view.WindowManager
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
import android.hardware.camera2.params.SessionConfiguration
import android.hardware.camera2.params.OutputConfiguration
import android.graphics.SurfaceTexture
import android.util.Log
import java.util.concurrent.Executors
import android.view.Surface
import android.view.TextureView
import android.view.View.MeasureSpec
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
    var previewSize by remember { mutableStateOf<Size?>(null) }
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
                                // Post to ensure view dimensions are available
                                post {
                                    openCameraWithCapture(
                                        context = context,
                                        textureView = this@apply,
                                        handler = Handler(Looper.getMainLooper()),
                                        onCameraOpened = { device, session, reader ->
                                            cameraDevice = device
                                            captureSession = session
                                            imageReader = reader
                                        },
                                        onPreviewSizeSet = { size ->
                                            previewSize = size
                                            // Configure transform for proper orientation
                                            configureTransform(this@apply, size, context)
                                        },
                                        onError = { showSnackbar = true }
                                    )
                                }
                            } else {
                                showSnackbar = true
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                            Log.d("CameraPreview", "Surface size changed to: ${width}x${height}")
                            // Reconfigure transform when surface size changes
                            previewSize?.let { size ->
                                configureTransform(this@apply, size, context)
                            }
                        }
                        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean = true
                        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
                    }
                }
            }
        )

        // Capture button positioned at the bottom center with system UI consideration
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp, start = 32.dp, end = 32.dp), // Extra bottom padding for navigation bar
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

        if (showSnackbar) {
            LaunchedEffect(snackbarHostState) {
                snackbarHostState.showSnackbar("Camera2 API is not supported on this device")
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopCenter) // Position at top to avoid overlap with capture button
        )
    }
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
 * Opens the camera with a simpler, more reliable approach.
 */
fun openCameraWithCapture(
    context: Context, 
    textureView: TextureView, 
    handler: Handler,
    onCameraOpened: (CameraDevice, CameraCaptureSession, ImageReader) -> Unit,
    onPreviewSizeSet: (Size) -> Unit,
    onError: () -> Unit
) {
    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val cameraId = cameraManager.cameraIdList[0]

    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
        onError()
        return
    }

    try {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        
        // Get available sizes
        val previewSizes = map.getOutputSizes(SurfaceTexture::class.java)
        val jpegSizes = map.getOutputSizes(ImageFormat.JPEG)
        
        // Choose preview size - use a common resolution
        val previewSize = getOptimalPreviewSize(previewSizes, textureView.width, textureView.height)
        val jpegSize = jpegSizes.maxByOrNull { it.width * it.height } ?: jpegSizes[0]
        
        Log.d("CameraPreview", "Selected preview size: ${previewSize.width}x${previewSize.height}")
        Log.d("CameraPreview", "TextureView size: ${textureView.width}x${textureView.height}")
        
        // Set up TextureView with the chosen size
        textureView.surfaceTexture?.setDefaultBufferSize(previewSize.width, previewSize.height)
        onPreviewSizeSet(previewSize)
        
        // Create ImageReader for photo capture
        val imageReader = ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, 1)
        
        // Create surfaces
        val previewSurface = Surface(textureView.surfaceTexture)
        val readerSurface = imageReader.surface
        
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                // Use legacy API for better compatibility
                camera.createCaptureSession(
                    listOf(previewSurface, readerSurface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            try {
                                // Create preview request
                                val previewRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                    addTarget(previewSurface)
                                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                                    set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                                }
                                
                                session.setRepeatingRequest(previewRequest.build(), null, handler)
                                onCameraOpened(camera, session, imageReader)
                            } catch (e: Exception) {
                                Log.e("CameraPreview", "Failed to start preview", e)
                                onError()
                            }
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Log.e("CameraPreview", "Session configuration failed")
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
                Log.e("CameraPreview", "Camera error: $error")
                camera.close()
                onError()
            }
        }, handler)
    } catch (e: Exception) {
        Log.e("CameraPreview", "Failed to open camera", e)
        onError()
    }
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

/**
 * Get optimal preview size using a simple, fixed approach.
 */
fun getOptimalPreviewSize(sizes: Array<Size>, viewWidth: Int, viewHeight: Int): Size {
    Log.d("CameraPreview", "Available sizes: ${sizes.joinToString { "${it.width}x${it.height}" }}")
    
    // Use a fixed size that works well for fullscreen
    val targetSize = Size(1280, 720) // 16:9 aspect ratio
    
    // Check if our target size is available
    val match = sizes.find { it.width == targetSize.width && it.height == targetSize.height }
    if (match != null) {
        Log.d("CameraPreview", "Using target size: ${match.width}x${match.height}")
        return match
    }
    
    // Fallback to largest available size under 1920x1080
    val result = sizes.filter { it.width <= 1920 && it.height <= 1080 }
                       .maxByOrNull { it.width * it.height } ?: sizes[0]
    
    Log.d("CameraPreview", "Using fallback size: ${result.width}x${result.height}")
    return result
}

/**
 * Configures the TextureView transform matrix to handle device orientation changes.
 * This fixes camera preview orientation issues in landscape mode.
 */
fun configureTransform(textureView: TextureView, streamSize: Size, context: Context) {
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val rotation = windowManager.defaultDisplay.rotation
    
    val matrix = Matrix()
    val viewRect = RectF(0f, 0f, textureView.width.toFloat(), textureView.height.toFloat())
    val bufferRect = RectF(0f, 0f, streamSize.height.toFloat(), streamSize.width.toFloat())
    val centerX = viewRect.centerX()
    val centerY = viewRect.centerY()
    
    Log.d("CameraPreview", "Configuring transform - Rotation: $rotation, View: ${textureView.width}x${textureView.height}, Stream: ${streamSize.width}x${streamSize.height}")
    
    when (rotation) {
        Surface.ROTATION_90 -> {
            // Landscape left (counter-clockwise from portrait)
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            
            val scale = Math.max(
                textureView.height.toFloat() / streamSize.height,
                textureView.width.toFloat() / streamSize.width
            )
            
            matrix.postScale(scale, scale, centerX, centerY)
            // For ROTATION_90, we need to rotate -90 degrees (or 270) to correct the orientation
            matrix.postRotate(-90f, centerX, centerY)
            
            Log.d("CameraPreview", "Applied landscape left transform - rotation: -90°, scale: $scale")
        }
        Surface.ROTATION_270 -> {
            // Landscape right (clockwise from portrait)
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            
            val scale = Math.max(
                textureView.height.toFloat() / streamSize.height,
                textureView.width.toFloat() / streamSize.width
            )
            
            matrix.postScale(scale, scale, centerX, centerY)
            // For ROTATION_270, we need to rotate 90 degrees to correct the orientation
            matrix.postRotate(90f, centerX, centerY)
            
            Log.d("CameraPreview", "Applied landscape right transform - rotation: 90°, scale: $scale")
        }
        Surface.ROTATION_180 -> {
            // Upside down portrait
            matrix.postRotate(180f, centerX, centerY)
            Log.d("CameraPreview", "Applied 180° rotation for upside-down portrait")
        }
        Surface.ROTATION_0 -> {
            // Normal portrait - minimal transform needed
            Log.d("CameraPreview", "Normal portrait orientation - no transform needed")
        }
    }
    
    textureView.setTransform(matrix)
}
