# Camera Preview Fixes

## Issues Fixed

### 1. **Deprecated `createCaptureSession()` API**
- **Problem**: Using deprecated `createCaptureSession(List<Surface>, StateCallback, Handler)`
- **Solution**: Updated to use `createCaptureSession(SessionConfiguration)` with modern API
- **Implementation**:
```kotlin
val sessionConfig = SessionConfiguration(
    SessionConfiguration.SESSION_REGULAR,
    outputs,
    Executors.newSingleThreadExecutor(),
    stateCallback
)
camera.createCaptureSession(sessionConfig)
```

### 2. **Preview Zoom/Aspect Ratio Issues**
- **Problem**: Preview appeared zoomed in and had incorrect aspect ratio (3/4 width in portrait)
- **Solution**: 
  - Improved size selection algorithm to match aspect ratios properly
  - Fixed transform matrix to use `Math.min()` for proper fitting instead of `Math.max()` for filling
  - Removed complex rotation logic that was causing issues

### 3. **90-degree Rotation in Landscape**
- **Problem**: Preview was rotated incorrectly in landscape mode
- **Solution**: Simplified transform logic without forced rotation
- **Result**: Camera preview now maintains correct orientation in both portrait and landscape

## Technical Changes

### New SessionConfiguration API
```kotlin
// Before (deprecated)
camera.createCaptureSession(
    listOf(previewSurface, captureSurface), 
    stateCallback, 
    handler
)

// After (modern API)
val outputs = listOf(
    OutputConfiguration(previewSurface),
    OutputConfiguration(readerSurface)
)
val sessionConfig = SessionConfiguration(
    SessionConfiguration.SESSION_REGULAR,
    outputs,
    Executors.newSingleThreadExecutor(),
    stateCallback
)
camera.createCaptureSession(sessionConfig)
```

### Improved Size Selection
```kotlin
fun chooseOptimalSize(choices: Array<Size>, textureViewWidth: Int, textureViewHeight: Int): Size {
    // Filter reasonable sizes
    val validChoices = choices.filter { 
        it.width <= 1920 && it.height <= 1080 
    }
    
    // Find best aspect ratio match
    val targetRatio = textureViewHeight.toDouble() / textureViewWidth.toDouble()
    
    var bestChoice = validChoices[0]
    var bestRatioDiff = Double.MAX_VALUE
    
    for (size in validChoices) {
        val ratio = size.width.toDouble() / size.height.toDouble()
        val ratioDiff = Math.abs(ratio - targetRatio)
        
        if (ratioDiff < bestRatioDiff) {
            bestChoice = size
            bestRatioDiff = ratioDiff
        }
    }
    
    return bestChoice
}
```

### Simplified Transform Matrix
```kotlin
fun configureTransform(textureView: TextureView, previewSize: Size) {
    val matrix = android.graphics.Matrix()
    
    // Calculate scaling to fit (not fill) the preview in the view
    val scaleX = viewWidth.toFloat() / previewSize.width.toFloat()
    val scaleY = viewHeight.toFloat() / previewSize.height.toFloat()
    
    // Use MIN to fit within bounds (prevents zooming)
    val scale = Math.min(scaleX, scaleY)
    
    // Center the preview
    val scaledWidth = previewSize.width * scale
    val scaledHeight = previewSize.height * scale
    val dx = (viewWidth - scaledWidth) / 2f
    val dy = (viewHeight - scaledHeight) / 2f
    
    matrix.setScale(scale, scale)
    matrix.postTranslate(dx, dy)
    
    textureView.setTransform(matrix)
}
```

### Enhanced Error Handling
- Added comprehensive logging with `Log.d()` and `Log.e()`
- Better exception handling in camera operations
- Detailed error messages for debugging

## Expected Results

1. **Correct Aspect Ratio**: Preview now fills the screen properly in both orientations
2. **No Zoom Issues**: Preview shows the full camera field of view without unwanted cropping
3. **Proper Orientation**: No incorrect rotation in landscape mode
4. **Modern API Compliance**: No deprecation warnings
5. **Better Performance**: More efficient session management with modern APIs
6. **Improved Debugging**: Detailed logs for troubleshooting

The camera preview should now display correctly in both portrait and landscape orientations without zoom, rotation, or aspect ratio issues.