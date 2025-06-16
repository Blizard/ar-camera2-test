# Simplified Camera Preview Implementation

## Approach Changes

### 1. **Removed Complex APIs**
- **Removed**: SessionConfiguration (newer API that was causing issues)
- **Back to**: Legacy `createCaptureSession(List<Surface>, StateCallback, Handler)` 
- **Reason**: Better compatibility and simpler implementation

### 2. **Simplified Size Selection**
- **Removed**: Complex aspect ratio matching algorithms
- **Added**: Preferred size list with common resolutions
- **Logic**: Try standard sizes first (1920x1080, 1280x720, etc.), fallback to largest available

### 3. **Simplified Transform Logic**
- **Removed**: Complex orientation handling and matrix operations
- **Added**: Simple center-crop scaling
- **Approach**: Scale to fill screen, center the result

### 4. **Removed Problematic Features**
- **Removed**: Dynamic transform reconfiguration on orientation change
- **Removed**: Complex surface size change handling
- **Reason**: These were causing the "fluent liquid" and rotation issues

## Current Implementation

### Size Selection
```kotlin
fun getOptimalPreviewSize(sizes: Array<Size>, viewWidth: Int, viewHeight: Int): Size {
    // Try common resolutions first
    val preferredSizes = listOf(
        Size(1920, 1080), // 16:9
        Size(1280, 720),  // 16:9
        Size(1024, 768),  // 4:3
        Size(800, 600),   // 4:3
        Size(640, 480)    // 4:3
    )
    
    // Find first available preferred size
    for (preferred in preferredSizes) {
        val match = sizes.find { it.width == preferred.width && it.height == preferred.height }
        if (match != null) return match
    }
    
    // Fallback to largest reasonable size
    return sizes.filter { it.width <= 1920 && it.height <= 1080 }
                .maxByOrNull { it.width * it.height } ?: sizes[0]
}
```

### Simple Transform
```kotlin
fun applyTransform(textureView: TextureView, previewSize: Size) {
    val matrix = android.graphics.Matrix()
    
    // Center crop scaling
    val scaleX = viewWidth.toFloat() / previewSize.width
    val scaleY = viewHeight.toFloat() / previewSize.height
    val scale = Math.max(scaleX, scaleY) // Fill screen
    
    // Center the result
    val scaledWidth = previewSize.width * scale
    val scaledHeight = previewSize.height * scale
    val dx = (viewWidth - scaledWidth) / 2f
    val dy = (viewHeight - scaledHeight) / 2f
    
    matrix.setScale(scale, scale)
    matrix.postTranslate(dx, dy)
    textureView.setTransform(matrix)
}
```

### Legacy Camera Session
```kotlin
// Using legacy API instead of SessionConfiguration
camera.createCaptureSession(
    listOf(previewSurface, readerSurface),
    object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            // Setup preview request
        }
        override fun onConfigureFailed(session: CameraCaptureSession) {
            // Handle failure
        }
    },
    handler
)
```

## Expected Results

### Portrait Mode
- ✅ **Full Width**: Should now fill entire screen width
- ✅ **Full Height**: Should fill entire screen height
- ✅ **No Distortion**: Simple scaling should prevent weird aspect ratios

### Landscape Mode  
- ✅ **Proper Orientation**: No 90-degree rotation issues
- ✅ **No "Fluent Liquid"**: Simplified approach should eliminate distortion
- ✅ **Stable Display**: No dynamic reconfiguration causing instability

## Debug Information
- Added comprehensive logging to track:
  - Available camera sizes
  - Selected preview size
  - TextureView dimensions
  - Transform calculations
  - Surface size changes

This simplified approach should resolve the fullscreen and orientation issues by removing the complex transform logic that was causing problems.