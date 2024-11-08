# ComposeCamera2API

A sample Android application demonstrating the integration of Camera2 API in a Jetpack Compose application. This app displays a camera preview using Camera2 and handles camera permission requests dynamically in a modern, Compose-first UI.

## Features

- Uses **Camera2 API** to display a live camera preview.
- Integrates with **Jetpack Compose** for building UI.
- Dynamically handles **camera permission requests**.
- Provides an informative message when the camera permission is not granted, allowing the user to re-request permission.

## Screenshots

| Camera Permission Not Granted  | Camera Preview  |
|--------------------------------|-----------------|
| <img src="path/to/permission_screenshot.png" width="200"> | <img src="path/to/preview_screenshot.png" width="200"> |

## Getting Started

### Prerequisites

- Android Studio Flamingo or later
- Android SDK 21 (Lollipop) or higher

### Installation

1. **Clone the repository:**

   ```bash
   git clone https://github.com/Akshar062/ComposeCamera2Api.git
   cd ComposeCamera2API

2. **Open the project in Android Studio:**

  - Select File > Open... and navigate to the project directory.
  - Sync Gradle if prompted.
  
3. **Run the app on an emulator or a real device with Camera2 API support** (most modern devices support this).

### Usage

- When you first open the app, it will request permission to access the camera.
- If permission is granted, the app will display the live camera feed.
- If permission is denied, a message will prompt you to click and re-request camera permission.

## Code Overview

## Main Components

- **MainActivity.kt:** Contains the core logic for requesting camera permission and displaying the camera preview.
- **CameraPreview.kt:** A composable function that initializes and handles the Camera2 API preview within a TextureView.
- **Permission Handling:** Uses ActivityResultContracts.RequestPermission to handle dynamic camera permission requests in a Compose-friendly way.
  
## Key Files

- MainActivity.kt: Contains the main activity, handles permissions, and integrates the camera preview composable.
- CameraPreview.kt: Defines the CameraPreview composable function using TextureView and sets up the Camera2 session.

## Important Permissions

Make sure to add the following permission to AndroidManifest.xml to allow camera access:
   ```bash
   <uses-permission android:name="android.permission.CAMERA" />
   ```

## Troubleshooting

- Camera Not Showing: Ensure the device supports Camera2 API. Most devices running Android 5.0 (Lollipop) and above support it.
- Permission Denied: If you deny the camera permission and want to grant it later, go to device Settings > Apps > ComposeCamera2API > Permissions and enable the Camera permission manually.

## License
This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

This project demonstrates basic usage of:

- [Jetpack Compose](https://developer.android.com/compose) for UI
- [Camera2 API](https://developer.android.com/reference/android/hardware/camera2/package-summary) for camera functionality


Thank you for checking out this project! Feel free to reach out for any questions or suggestions.

