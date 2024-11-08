package com.akshar.composecamera2api

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat

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

    AndroidView(modifier = modifier, factory = { ctx ->
        TextureView(ctx).apply {
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                /**
                 * Called when the SurfaceTexture is available for use.
                 *
                 * @param texture The SurfaceTexture.
                 * @param width The width of the SurfaceTexture.
                 * @param height The height of the SurfaceTexture.
                 */
                override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                    if (isCamera2Supported(context)) {
                        openCamera(context, this@apply, Handler(Looper.getMainLooper()))
                    } else {
                        showSnackbar = true
                    }
                }

                /**
                 * Called when the SurfaceTexture's size changes.
                 *
                 * @param texture The SurfaceTexture.
                 * @param width The new width of the SurfaceTexture.
                 * @param height The new height of the SurfaceTexture.
                 */
                override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {}

                /**
                 * Called when the SurfaceTexture is about to be destroyed.
                 *
                 * @param texture The SurfaceTexture.
                 * @return True if the SurfaceTexture should be destroyed, false otherwise.
                 */
                override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean = true

                /**
                 * Called when the SurfaceTexture is updated.
                 *
                 * @param texture The SurfaceTexture.
                 */
                override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
            }
        }
    })

    if (showSnackbar) {
        LaunchedEffect(snackbarHostState) {
            snackbarHostState.showSnackbar("Camera2 API is not supported on this device")
        }
    }
    SnackbarHost(hostState = snackbarHostState)
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
 * Opens the camera and starts the preview.
 *
 * @param context The context of the application.
 * @param textureView The TextureView to display the camera preview.
 * @param handler The handler to run the camera operations.
 */
fun openCamera(context: Context, textureView: TextureView, handler: Handler) {
    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val cameraId = cameraManager.cameraIdList[0]

    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
        return
    }

    cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
        /**
         * Called when the camera is opened.
         *
         * @param camera The opened CameraDevice.
         */
        override fun onOpened(camera: CameraDevice) {
            val surface = Surface(textureView.surfaceTexture)
            val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
            }
            camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                /**
                 * Called when the camera capture session is configured.
                 *
                 * @param session The configured CameraCaptureSession.
                 */
                override fun onConfigured(session: CameraCaptureSession) {
                    captureRequest.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                    session.setRepeatingRequest(captureRequest.build(), null, handler)
                }

                /**
                 * Called when the camera capture session configuration fails.
                 *
                 * @param session The failed CameraCaptureSession.
                 */
                override fun onConfigureFailed(session: CameraCaptureSession) {}
            }, handler)
        }

        /**
         * Called when the camera is disconnected.
         *
         * @param camera The disconnected CameraDevice.
         */
        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
        }

        /**
         * Called when an error occurs with the camera.
         *
         * @param camera The CameraDevice with the error.
         * @param error The error code.
         */
        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
        }
    }, handler)
}