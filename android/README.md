<p align="center">
  <img src="app/src/main/res/drawable/ic_logo_png.png" alt="ReefScan Logo" width="200" height="200">
</p>

<h1 align="center">🐠 ReefScan</h1>

<p align="center">
  <strong>AI-Powered Marine Aquarium Scanner</strong><br>
  <em>Beyond-the-glass vision for your reef.</em>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat&logo=android&logoColor=white" alt="Platform">
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=flat&logo=kotlin&logoColor=white" alt="Language">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat&logo=jetpackcompose&logoColor=white" alt="UI">
  <img src="https://img.shields.io/badge/AI-Gemini%20Vision-886FBF?style=flat&logo=google&logoColor=white" alt="AI">
  <img src="https://img.shields.io/badge/Min%20SDK-26-brightgreen" alt="Min SDK">
  <img src="https://img.shields.io/badge/Target%20SDK-35-blue" alt="Target SDK">
</p>

---

## 📖 About

**ReefScan** transforms your smartphone into a powerful reef aquarium diagnostic tool. Using Google's Gemini Vision AI, it instantly identifies fish, coral, invertebrates, algae, and potential problems in your saltwater or freshwater aquarium.

Simply point your camera at your tank and let ReefScan's AI do the work — providing species identification, health assessments, and actionable care recommendations in seconds.

---

## ✨ Features

### 🔍 Instant Identification
- **Fish ID** — Identify species with confidence scores
- **Coral ID** — Recognize SPS, LPS, and soft corals
- **Algae Detection** — Spot problematic algae types early
- **Pest Alerts** — Detect aiptasia, flatworms, vermetid snails, and other pests
- **Disease Detection** — Identify RTN, STN, bleaching, bacterial infections
- **Tank Issues** — Recognize cloudy water, dinos, cyano, GHA, diatoms

### 📊 Detailed Analysis
Each scan provides:
- Species identification with confidence percentage
- Health status assessment (Healthy / Warning / Problem)
- Severity ratings for detected issues
- Detailed descriptions and care information
- 3 actionable recommendations from AI experts

### 🏠 Multi-Tank Management
- Create profiles for each tank
- Track scan history per tank
- Organize photos by date
- Monitor tank health over time

### 📸 Photo Gallery
- Build a visual timeline of your reef
- Capture and organize tank photos
- Rate your best shots
- Track coral growth and changes

### 🎨 Blue Light Photography
Special camera filters for reef photography:
- Orange filter for blue/actinic lights
- Yellow filter for mixed lighting
- Capture true coral colors

---

## 🏗️ Architecture

ReefScan follows clean architecture principles with MVVM pattern:

```
app/src/main/java/com/example/reefscan/
├── billing/                    # Subscription & usage tracking
│   ├── SubscriptionManager.kt
│   ├── SubscriptionTier.kt
│   └── UsageTracker.kt
├── data/
│   ├── local/                  # Room database & repositories
│   │   ├── ScanDatabase.kt
│   │   ├── ScanEntity.kt
│   │   ├── TankEntity.kt
│   │   └── GalleryImageEntity.kt
│   ├── model/                  # Data models
│   │   ├── ScanResult.kt
│   │   ├── GeminiModels.kt
│   │   └── IssueStatus.kt
│   └── remote/                 # API services
│       ├── GeminiApi.kt
│       ├── GeminiService.kt
│       └── GeminiRepository.kt
├── navigation/                 # Compose Navigation
│   ├── NavGraph.kt
│   └── Screen.kt
├── ui/
│   ├── components/             # Reusable UI components
│   │   ├── GlassmorphicCard.kt
│   │   ├── ScanButton.kt
│   │   ├── CategoryChip.kt
│   │   └── Animations.kt
│   ├── screens/                # App screens + ViewModels
│   │   ├── SplashScreen.kt
│   │   ├── HomeScreen.kt
│   │   ├── CameraScreen.kt
│   │   ├── LoadingScreen.kt
│   │   ├── ResultsScreen.kt
│   │   ├── TanksScreen.kt
│   │   └── GalleryScreens.kt
│   └── theme/                  # Material3 theming
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
├── util/                       # Utilities
│   ├── ImageUtils.kt
│   └── WikipediaHelper.kt
└── MainActivity.kt             # Single activity entry point
```

---

## 🛠️ Tech Stack

| Category | Technology |
|----------|------------|
| **Language** | Kotlin 2.0 |
| **UI Framework** | Jetpack Compose + Material3 |
| **Architecture** | MVVM + Clean Architecture |
| **Navigation** | Navigation Compose |
| **Camera** | CameraX |
| **AI/ML** | Google Gemini Vision API |
| **Networking** | Retrofit + OkHttp + Moshi |
| **Local Storage** | Room Database |
| **Image Loading** | Coil |
| **Animations** | Lottie Compose |
| **Permissions** | Accompanist Permissions |
| **Async** | Kotlin Coroutines + Flow |
| **Subscriptions** | RevenueCat |
| **Preferences** | DataStore |

---

## 🎨 Design System

### Color Palette
| Color | Hex | Usage |
|-------|-----|-------|
| Deep Ocean | `#0A2036` | Primary background |
| Aqua Blue | `#1FA3C9` | Primary accent |
| Seafoam | `#0ED4A1` | Success/healthy states |
| Coral Accent | `#F66C84` | Warning/problem states |
| Glass White | `rgba(255,255,255,0.2)` | Glassmorphism effects |

### Design Principles
- Ocean-inspired gradients (Deep Navy → Aqua Blue)
- Glassmorphism cards with soft shadows
- Rounded corners (16–24dp)
- Minimal text & maximum imagery
- Smooth micro-animations
- One action per screen

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 11+
- Android device or emulator (API 26+)
- Gemini API key from [Google AI Studio](https://aistudio.google.com/)

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/bitcrafterapps/reef-scan-android-app.git
   cd reef-scan-android-app
   ```

2. **Configure API keys**
   
   Create or edit `local.properties` in the project root:
   ```properties
   # Gemini API Key (required)
   GEMINI_API_KEY=your_gemini_api_key_here
   
   # RevenueCat API Key (optional, for subscriptions)
   REVENUECAT_API_KEY=your_revenuecat_key_here
   ```

3. **Build and run**
   ```bash
   ./gradlew assembleDebug
   ```
   
   Or open in Android Studio and click Run ▶️

---

## 📱 Screenshots

| Splash | Home | Camera | Results |
|--------|------|--------|---------|
| *Ocean gradient splash* | *Scan buttons* | *CameraX preview* | *AI analysis card* |

| Tanks | Gallery | Saved Scans | Subscription |
|-------|---------|-------------|--------------|
| *Multi-tank management* | *Photo timeline* | *Scan history* | *Premium features* |

---

## 🔧 Configuration

### Build Variants
- **debug** — Development builds with logging
- **release** — Optimized, minified production builds

### APK Splitting
APKs are split by ABI for smaller download sizes:
- `armeabi-v7a` — 32-bit ARM
- `arm64-v8a` — 64-bit ARM (most devices)
- `x86` / `x86_64` — Emulators

---

## 📋 Requirements

- **Minimum SDK:** 26 (Android 8.0 Oreo)
- **Target SDK:** 35 (Android 15)
- **Permissions:**
  - Camera — For scanning
  - Internet — For AI analysis

---

## 🗺️ Roadmap

### v1.0 ✅ (Current)
- [x] AI-powered species identification
- [x] Multi-tank management
- [x] Photo gallery with ratings
- [x] Blue light camera filters
- [x] Scan history
- [x] Beautiful ocean-themed UI

### v1.1 (Planned)
- [ ] Premium subscription features
- [ ] Extended species database
- [ ] Parameter logger
- [ ] More polished animations

### v2.0 (Future)
- [ ] Cloud sync
- [ ] Tank profiles & sharing
- [ ] AR coral placement
- [ ] Growth tracking over time
- [ ] Community tagging

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📄 License

This project is proprietary software. All rights reserved.

---

## 👥 Team

Developed by **BitCrafter Apps**

- 🌐 [bitcraft-apps.com](https://www.bitcraft-apps.com)

---

## 🙏 Acknowledgments

- [Google Gemini](https://deepmind.google/technologies/gemini/) for the powerful vision AI
- [Jetpack Compose](https://developer.android.com/jetpack/compose) for the modern UI toolkit
- [Material Design 3](https://m3.material.io/) for the design system
- The reef keeping community for inspiration

---

<p align="center">
  <strong>🐠 Happy Reefing! 🐠</strong>
</p>


