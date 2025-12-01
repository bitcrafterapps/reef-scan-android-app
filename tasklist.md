# 🐠 ReefScan — Build Task List

## Overview
Build a Dribbble-grade Marine Aquarium Scanner Android app using Kotlin + Jetpack Compose with GPT-5 Vision AI.

**Starting Point:** Empty Compose Activity project from Android Studio (Compose + Material3 already configured)

---

## Phase 0: Understand Existing Project Structure

**Already configured by Android Studio:**
- [x] Kotlin + Jetpack Compose setup
- [x] Material3 dependency
- [x] Compose BOM (Bill of Materials)
- [x] Basic `MainActivity.kt` with Compose
- [x] Theme files (`Color.kt`, `Theme.kt`, `Type.kt`)
- [x] Edge-to-edge display enabled
- [x] Build configuration (`build.gradle.kts`)
- [x] Version catalog (`libs.versions.toml`)

**Existing file structure:**
```
app/src/main/java/com/example/reefscan/
├── MainActivity.kt (basic Compose activity)
└── ui/theme/
    ├── Color.kt
    ├── Theme.kt
    └── Type.kt
```

---

## Phase 1: Add Missing Dependencies

- [ ] **1.1** Update `gradle/libs.versions.toml` to add version entries for:
  - CameraX (camera-core, camera-camera2, camera-lifecycle, camera-view)
  - Navigation Compose
  - Retrofit + OkHttp
  - Moshi (JSON parsing)
  - Coil (image loading)
  - Lottie (animations)
  - Room (local database)
  - Accompanist Permissions
  - Kotlin Coroutines

- [ ] **1.2** Update `app/build.gradle.kts` to add library dependencies:
  - CameraX libraries
  - Navigation Compose
  - Retrofit + OkHttp + logging interceptor
  - Moshi + Moshi Kotlin + converter
  - Coil Compose
  - Lottie Compose
  - Room (runtime, ktx, compiler with KSP)
  - Accompanist Permissions
  - ViewModel Compose

- [ ] **1.3** Add KSP plugin for Room annotation processing:
  - Add KSP plugin to `build.gradle.kts`
  - Add KSP to `libs.versions.toml`

- [ ] **1.4** Sync Gradle and verify all dependencies resolve

---

## Phase 2: Configure Theme & Branding

- [ ] **2.1** Update `Color.kt` with ReefScan brand colors:
  - Deep Ocean `#0A2036`
  - Aqua Blue `#1FA3C9`
  - Seafoam `#0ED4A1`
  - Coral Accent `#F66C84`
  - Glass White (semi-transparent white for glassmorphism)
  - Supporting colors (surface, background variants)

- [ ] **2.2** Update `Type.kt` with custom typography:
  - Add Inter or Poppins font family (download and add to res/font)
  - Define Display, Headline, Title, Body, Label styles
  - Medium-weight for readability

- [ ] **2.3** Update `Theme.kt`:
  - Create dark ocean color scheme (primary theme)
  - Define custom shapes (16-24dp rounded corners)
  - Apply custom typography
  - Remove light theme (app is ocean-dark only)

---

## Phase 3: Permissions & Manifest

- [ ] **3.1** Update `AndroidManifest.xml` to add permissions:
  ```xml
  <uses-permission android:name="android.permission.CAMERA" />
  <uses-permission android:name="android.permission.INTERNET" />
  <uses-feature android:name="android.hardware.camera" android:required="true" />
  ```

- [ ] **3.2** Update `AndroidManifest.xml` for splash screen:
  - Set activity theme for splash (if using SplashScreen API)
  - Or configure windowBackground for custom splash

- [ ] **3.3** Update `minSdk` to 26 (Android 8.0) for better CameraX support

---

## Phase 4: Create Package Structure

- [ ] **4.1** Create package directories:
  ```
  com/example/reefscan/
  ├── navigation/
  ├── ui/
  │   ├── screens/
  │   └── components/
  ├── data/
  │   ├── model/
  │   ├── local/
  │   └── remote/
  └── util/
  ```

- [ ] **4.2** Create placeholder files for organization (can be empty initially)

---

## Phase 5: Navigation Setup

- [ ] **5.1** Create `navigation/Screen.kt` - sealed class for routes:
  - `Splash`
  - `Home`
  - `Camera`
  - `Loading` (with imageUri parameter)
  - `Results` (with scanId parameter)
  - `SavedScans`

- [ ] **5.2** Create `navigation/NavGraph.kt`:
  - Set up NavHost with all screen routes
  - Configure navigation arguments
  - Set Splash as start destination

- [ ] **5.3** Update `MainActivity.kt`:
  - Remove default Greeting composable
  - Add NavController
  - Call NavGraph composable
  - Handle system back button

---

## Phase 6: Assets & Resources

- [ ] **6.1** Add splash screen background image:
  - Create/download ocean reef background image
  - Add to `res/drawable/` (or `res/drawable-nodpi/` for large images)

- [ ] **6.2** Create ReefScan logo:
  - Design coral silhouette or fish outline vector
  - Add as `res/drawable/ic_logo.xml` (vector) or PNG

- [ ] **6.3** Create gradient backgrounds:
  - `ocean_gradient.xml` - Deep Ocean → Aqua Blue
  - `teal_coral_gradient.xml` - Teal → Coral Pink accent

- [ ] **6.4** Add Lottie animation files to `res/raw/`:
  - `wave_loader.json` - water wave animation
  - `bubbles.json` - bubbles rising animation
  - (Download from LottieFiles or create custom)

- [ ] **6.5** Update app icon:
  - Replace default icons in `res/mipmap-*/`
  - Create adaptive icon with coral/fish design
  - Update `ic_launcher.xml` and `ic_launcher_round.xml`

---

## Phase 7: Splash Screen

- [ ] **7.1** Create `ui/screens/SplashScreen.kt`:
  - Full-screen Box with image background (ocean/reef)
  - Centered Column with:
    - Logo Image (coral silhouette)
    - "ReefScan" text (large, bold)
    - "Beyond-the-glass vision for your reef" tagline
  - Use `fillMaxSize()` and proper content alignment

- [ ] **7.2** Add entrance animations:
  - Fade-in for background
  - Scale + fade for logo
  - Slide-up + fade for text
  - Use `AnimatedVisibility` or `animate*AsState`

- [ ] **7.3** Implement auto-navigation:
  - Use `LaunchedEffect` with delay (2500ms)
  - Navigate to Home screen
  - Use `popUpTo` to remove Splash from back stack

- [ ] **7.4** Optional: Add subtle shimmer or ripple effect on logo

---

## Phase 8: Home Screen

- [ ] **8.1** Create `ui/screens/HomeScreen.kt`:
  - Ocean gradient background (Box with brush)
  - Top section: Logo + app name
  - Center: Large floating "Scan Reef Life" button
  - Top-right: History icon button

- [ ] **8.2** Create `ui/components/ScanButton.kt`:
  - Large circular or rounded rectangle button
  - Aqua Blue gradient fill
  - Icon + text
  - Ripple effect on press
  - Shadow/elevation

- [ ] **8.3** Add subtle wave animation in background:
  - Use Canvas with animated paths, or
  - Use Lottie animation layer

- [ ] **8.4** Implement navigation:
  - Scan button → Camera screen
  - History icon → SavedScans screen

---

## Phase 9: Camera Screen

- [ ] **9.1** Create `ui/screens/CameraScreen.kt`:
  - Full-screen camera preview using CameraX
  - Use `AndroidView` to embed PreviewView

- [ ] **9.2** Implement camera permission handling:
  - Use Accompanist Permissions library
  - Show permission rationale if denied
  - Handle permanently denied state

- [ ] **9.3** Set up CameraX:
  - Create camera provider
  - Bind Preview use case
  - Bind ImageCapture use case
  - Handle lifecycle properly

- [ ] **9.4** Create capture UI:
  - Circular shutter button (bottom center)
  - Gallery import button (bottom corner)
  - Close/back button (top corner)

- [ ] **9.5** Implement photo capture:
  - Capture image to temp file
  - Handle capture callbacks
  - Show brief capture animation (flash/ripple)

- [ ] **9.6** Implement gallery picker:
  - Use `ActivityResultContracts.GetContent`
  - Filter for images only
  - Copy selected image to app storage

- [ ] **9.7** Navigate to Loading screen with image URI

---

## Phase 10: Data Models

- [ ] **10.1** Create `data/model/ScanResult.kt`:
  ```kotlin
  data class ScanResult(
      val name: String,
      val category: String,  // Fish, SPS Coral, LPS Coral, Soft Coral, Invertebrate, Algae, Pest, Disease
      val confidence: Int,   // 0-100
      val isProblem: Boolean,
      val severity: String?, // Low, Medium, High (null if not a problem)
      val description: String,
      val recommendations: List<String>  // 3 items
  )
  ```

- [ ] **10.2** Create `data/model/IssueStatus.kt`:
  ```kotlin
  enum class IssueStatus { HEALTHY, WARNING, PROBLEM }
  ```

- [ ] **10.3** Create API request/response models for OpenAI

---

## Phase 11: OpenAI GPT-5 Vision API Integration

- [ ] **11.1** Create `data/remote/OpenAIApi.kt`:
  - Retrofit interface
  - POST endpoint for chat completions
  - Request/response data classes

- [ ] **11.2** Create `data/remote/OpenAIService.kt`:
  - Retrofit instance with OkHttp client
  - Base URL: `https://api.openai.com/v1/`
  - Auth header interceptor for API key
  - Timeout configuration (30s)

- [ ] **11.3** Create `util/ImageUtils.kt`:
  - Function to encode image file to Base64
  - Function to resize/compress image for API
  - Max dimension ~1024px for efficiency

- [ ] **11.4** Create `data/remote/OpenAIRepository.kt`:
  - `suspend fun analyzeImage(imageUri: Uri): Result<ScanResult>`
  - Build GPT-5 Vision prompt
  - Parse JSON response to ScanResult
  - Handle errors

- [ ] **11.5** Create GPT-5 Vision prompt (system + user):
  - Instruct to identify marine aquarium life
  - Categories: Fish, Corals, Invertebrates, Algae, Pests, Diseases
  - Request structured JSON response
  - Include confidence scoring
  - Include problem detection + severity
  - Include 3 recommendations

- [ ] **11.6** Add API key configuration:
  - Add `OPENAI_API_KEY` to `local.properties`
  - Read via BuildConfig field
  - Update `build.gradle.kts` to expose BuildConfig field

- [ ] **11.7** Handle API errors:
  - Network errors
  - Rate limiting
  - Invalid responses
  - Timeout

---

## Phase 12: Loading Screen

- [ ] **12.1** Create `ui/screens/LoadingScreen.kt`:
  - Ocean gradient background
  - Centered glassmorphic card
  - Lottie wave/ripple animation inside card
  - "Analyzing reef life…" text

- [ ] **12.2** Create `ui/components/GlassmorphicCard.kt`:
  - Semi-transparent white background
  - Blur effect (if possible) or gradient simulation
  - Rounded corners (24dp)
  - Subtle border

- [ ] **12.3** Create ViewModel for loading state:
  - Receive image URI
  - Call OpenAI repository
  - Handle loading/success/error states

- [ ] **12.4** Implement navigation:
  - On success: Navigate to Results with scan data
  - On error: Show error state with retry button

---

## Phase 13: Results Screen

- [ ] **13.1** Create `ui/screens/ResultsScreen.kt`:
  - Ocean gradient background
  - Scrollable content
  - Large glassmorphic card with results

- [ ] **13.2** Display photo thumbnail:
  - Rounded corners
  - Fixed aspect ratio
  - Load with Coil

- [ ] **13.3** Display identification name (large, bold text)

- [ ] **13.4** Create `ui/components/CategoryChip.kt`:
  - Rounded pill shape
  - Color-coded by category
  - Category icon + text

- [ ] **13.5** Display confidence percentage:
  - Text percentage
  - Visual progress arc or bar

- [ ] **13.6** Create `ui/components/IssueBadge.kt`:
  - Healthy (Seafoam green) / Warning (Amber) / Problem (Coral red)
  - Icon + text
  - Rounded badge shape

- [ ] **13.7** Display severity level (if problem):
  - Low / Medium / High
  - Color-coded

- [ ] **13.8** Display description text

- [ ] **13.9** Display 3 recommendations:
  - Numbered cards or list items
  - Icon + text for each

- [ ] **13.10** Add "Save Scan" button:
  - Secondary style
  - Save to Room database
  - Show confirmation

- [ ] **13.11** Add "Scan Again" button:
  - Full-width primary button
  - Navigate back to Camera

- [ ] **13.12** Add entrance animation (fade in, slide up)

---

## Phase 14: Local Storage (Room Database)

- [ ] **14.1** Create `data/local/ScanEntity.kt`:
  ```kotlin
  @Entity(tableName = "scans")
  data class ScanEntity(
      @PrimaryKey(autoGenerate = true) val id: Long = 0,
      val imagePath: String,
      val name: String,
      val category: String,
      val confidence: Int,
      val isProblem: Boolean,
      val severity: String?,
      val description: String,
      val recommendations: String,  // JSON string
      val timestamp: Long
  )
  ```

- [ ] **14.2** Create `data/local/ScanDao.kt`:
  - `@Insert` - insert scan
  - `@Query` - get all scans (ORDER BY timestamp DESC, LIMIT 10)
  - `@Query` - get scan by ID
  - `@Query` - delete oldest scans when count > 10
  - `@Delete` - delete scan

- [ ] **14.3** Create `data/local/ScanDatabase.kt`:
  - Room database class
  - Singleton pattern
  - Type converters if needed

- [ ] **14.4** Create `data/local/ScanRepository.kt`:
  - Save scan (with auto-cleanup of old scans)
  - Get all scans
  - Get scan by ID
  - Save image to internal storage

---

## Phase 15: Saved Scans Screen

- [ ] **15.1** Create `ui/screens/SavedScansScreen.kt`:
  - Ocean gradient background
  - Top bar with back button + "Saved Scans" title
  - Scrollable list of scan cards

- [ ] **15.2** Create scan list item card:
  - Thumbnail image (left)
  - Name + category chip (right)
  - Timestamp (bottom)
  - Glassmorphic card style

- [ ] **15.3** Implement tap to view full results:
  - Navigate to Results screen with saved scan data

- [ ] **15.4** Handle empty state:
  - Friendly illustration or icon
  - "No saved scans yet" message
  - "Start scanning" CTA button

- [ ] **15.5** Create ViewModel:
  - Load scans from Room
  - Handle loading/empty/data states

---

## Phase 16: UI Polish & Animations

- [ ] **16.1** Add screen transition animations:
  - Fade transitions between screens
  - Slide animations where appropriate
  - Configure in NavHost

- [ ] **16.2** Refine glassmorphism effect:
  - Test blur on various devices
  - Fallback for devices without blur support

- [ ] **16.3** Add button press animations:
  - Scale down on press
  - Ripple effect
  - Haptic feedback

- [ ] **16.4** Add micro-interactions:
  - Success checkmark animation on save
  - Loading shimmer effects
  - Smooth state transitions

- [ ] **16.5** Ensure smooth 60FPS:
  - Profile with Android Studio
  - Optimize heavy composables
  - Use `remember` and `derivedStateOf` appropriately

---

## Phase 17: Error Handling

- [ ] **17.1** Create error UI components:
  - Error card with message + retry button
  - Network error state
  - Generic error state

- [ ] **17.2** Handle camera permission denied:
  - Show explanation
  - Button to open app settings

- [ ] **17.3** Handle no internet:
  - Detect connectivity
  - Show offline message

- [ ] **17.4** Handle API errors gracefully:
  - Rate limiting message
  - Server error message
  - Timeout message

- [ ] **17.5** Handle non-reef images:
  - Parse API response for "unable to identify"
  - Show friendly message

---

## Phase 18: Testing & QA

- [ ] **18.1** Test splash → home transition
- [ ] **18.2** Test camera capture on physical device
- [ ] **18.3** Test gallery import
- [ ] **18.4** Test full scan flow (capture → analyze → results)
- [ ] **18.5** Test save scan functionality
- [ ] **18.6** Test saved scans list + detail view
- [ ] **18.7** Test error states (airplane mode, etc.)
- [ ] **18.8** Test on Android 10+ devices
- [ ] **18.9** Verify scan time < 4 seconds (network dependent)
- [ ] **18.10** Check final APK size

---

## Phase 19: Final Polish & Release

- [ ] **19.1** Review all UI for consistency
- [ ] **19.2** Optimize image compression for API
- [ ] **19.3** Configure ProGuard/R8 rules for release
- [ ] **19.4** Test release build
- [ ] **19.5** Generate signed APK/AAB
- [ ] **19.6** Prepare Play Store assets (screenshots, description)

---

## Quick Reference: Files to Create/Update

### Update Existing Files:
- `gradle/libs.versions.toml` - add dependency versions
- `app/build.gradle.kts` - add dependencies + KSP
- `app/src/main/AndroidManifest.xml` - add permissions
- `ui/theme/Color.kt` - brand colors
- `ui/theme/Type.kt` - typography
- `ui/theme/Theme.kt` - custom theme
- `MainActivity.kt` - navigation setup

### Create New Files:
```
navigation/
├── Screen.kt
└── NavGraph.kt

ui/screens/
├── SplashScreen.kt
├── HomeScreen.kt
├── CameraScreen.kt
├── LoadingScreen.kt
├── ResultsScreen.kt
└── SavedScansScreen.kt

ui/components/
├── GlassmorphicCard.kt
├── CategoryChip.kt
├── IssueBadge.kt
├── ScanButton.kt
└── RecommendationCard.kt

data/model/
├── ScanResult.kt
└── IssueStatus.kt

data/local/
├── ScanEntity.kt
├── ScanDao.kt
├── ScanDatabase.kt
└── ScanRepository.kt

data/remote/
├── OpenAIApi.kt
├── OpenAIService.kt
└── OpenAIRepository.kt

util/
├── ImageUtils.kt
└── Constants.kt
```

### Resource Files:
```
res/drawable/
├── splash_background.jpg (or .png)
├── ic_logo.xml
├── ocean_gradient.xml
└── teal_coral_gradient.xml

res/raw/
├── wave_loader.json
└── bubbles.json

res/font/
└── inter_*.ttf (or poppins)
```

---

## Brand Colors Reference

| Name | Hex | Usage |
|------|-----|-------|
| Deep Ocean | `#0A2036` | Primary background |
| Aqua Blue | `#1FA3C9` | Accent, buttons |
| Seafoam | `#0ED4A1` | Success, healthy |
| Coral Accent | `#F66C84` | Problems, warnings |
| Glass White | `#33FFFFFF` | Glassmorphism overlay |

---

## Current Project Versions (from libs.versions.toml)
- Kotlin: 2.0.0
- AGP: 8.8.0
- Compose BOM: 2024.04.01
- Target SDK: 35
- Min SDK: 24 (consider updating to 26)

---

**Total Estimated Tasks: 85+**
**Priority: Phases 1-15 for MVP launch**
