
# 🐠 **PRD — ReefScan: Marine Aquarium Scanner App (v1.0)**  
### **Tagline: Beyond-the-glass vision for your reef.**
### **Updated to Use OpenAI GPT-5 Vision as the Primary and Only AI Model**

**Platform:** Android (Android Studio, Kotlin + Jetpack Compose)  
**AI Engine:** **OpenAI GPT-5 Vision** (Single Model Architecture)  
**Tools:** Cursor + Android Studio  
**Starting Point:** Empty Compose Activity project (Compose + Material3 pre-configured)  
**Goal:** Ship a **beautiful, functional, Dribbble-grade MVP** by **tomorrow**  
**Scope:** Marine Reef Aquarium ID, problem detection, recommendations

---

# 1. **Product Overview**

**ReefScan** is a single-feature Android app that uses **GPT-5 Vision** to instantly identify:

- **Fish**  
- **Corals (SPS, LPS, Softies)**  
- **Invertebrates**  
- **Algae (beneficial & nuisance)**  
- **Pests (aiptasia, flatworms, vermetid snails, hydroids)**  
- **Diseases & coral problems (RTN, STN, bleaching, bacterial infections)**  
- **Tank issues (cloudy water, dinos, cyano, gha, diatoms)**  

The app returns:

- Identification name  
- Classification category  
- Confidence score  
- Whether there is a problem  
- Severity level  
- Short description  
- 3 recommendations  

The UI is **simple**, **premium**, and **Dribbble-inspired**, designed to feel like a modern, elegant science tool for reef keepers.

---

# 2. **Design Language (Dribbble-Inspired)**

### 🎨 **Visual Style**
- Ocean-inspired gradients:  
  - Deep Navy → Aqua Blue  
  - Teal → Coral Pink accent  
- Rounded cards (16–24dp) with glassmorphism  
- Soft shadows + clean spacing  
- Minimal text & large imagery  

### 🖼️ **Inspiration Search Terms on Dribbble**
- "AI scan app"  
- "Object recognition UI"  
- "Gradient camera app"  
- "Glassmorphism card"  
- "Modern minimal camera app"  

### 🔤 **Typography**
- **Inter** or **Google Sans**  
- Display sizes used sparingly  
- Medium-weight for readability  

### 🧭 **UX Principles**
- One action per screen  
- Smooth micro-animations  
- Clear and calm tone  
- Maximum white space  
- Zero clutter  

---

# 3. **User Flow**

### **1. Splash Screen**
- Animated ocean gradient  
- Coral silhouette logo  
- Subtle ripple animation  

### **2. Home Screen**
- Large floating “Scan Reef Life” button  
- Minimal text  
- Wave background animation  
- History icon in top-right  

### **3. Camera Screen**
- Fullscreen camera (CameraX)  
- Circular shutter button  
- Gallery import option  
- Soft capture ripple  

### **4. Loading/Analyzing Screen**
- Glassmorphic shimmer card  
- Aqua waveform loader  
- “Analyzing reef life…”  

### **5. Results Screen**
Glassmorphism card showing:

- Photo thumbnail  
- Name (big)  
- Category chip (Fish, SPS Coral, Algae, etc.)  
- Confidence %  
- Issue badge (Healthy / Warning / Problem)  
- Severity (Low/Medium/High)  
- Short description  
- 3 actionable recommendations  
- “Save Scan” button  
- “Scan Again” full-width button  

### **6. Saved Scans Screen**
- Card list of last 10 scans  
- Thumbnail + name + category  
- Tap for full results  

No clutter, no accounts, no ads.

---

# 4. **Core Features (MVP Scope)**

### ✔️ **Take photo (CameraX)**  
### ✔️ **Upload photo from gallery**  
### ✔️ **GPT-5 Vision identification & problem detection**  
### ✔️ **Glassmorphic results card**  
### ✔️ **Local storage of last 10 scans**  
### ✔️ **Fast & beautiful UI**  

### ❌ **Excluded in MVP**
- Login/accounts  
- Cloud sync  
- Subscription payments  
- Parameter charts  
- Push notifications  

We add these after launch.

---

# 5. **AI Model Specification (GPT-5 Vision)**

### Why GPT-5 Vision?
- Best image recognition of marine species  
- High accuracy even on small pests  
- Strong biological reasoning  
- Excellent error detection and confidence reporting  
- Perfect JSON compliance for mobile apps  
- Zero need for multiple models or fallback systems  

### Strengths for Our Use Case:
- 95%+ accuracy on fish species  
- 90%+ on coral categories  
- Detects small pests (flatworms, aiptasia, vermetids)  
- Differentiates algae types (cyano vs dinos vs gha vs diatoms)  
- Can detect coral tissue recession patterns  
- Provides contextual guidance (water quality problems, lighting burn, etc.)  

---

# 6. **GPT-5 Vision Prompt (Final Production Version)**



# 7. **Architecture**

### **Starting Point**
- **Empty Compose Activity** project created in Android Studio
- Kotlin 2.0.0 + Jetpack Compose (already configured)
- Material3 (already configured)
- Compose BOM 2024.04.01 (already configured)
- Basic theme structure (Color.kt, Theme.kt, Type.kt) exists
- Target SDK 35, Min SDK 24

### **Frontend (to be added)**  
- **Kotlin + Jetpack Compose** (base exists)
- Navigation Compose for screen routing
- CameraX for image capture  
- Retrofit + OkHttp for API requests  
- Coil for image loading  
- Moshi for JSON parsing  
- Lottie Compose for animations
- Accompanist for permissions
- ViewModel Compose for state management

### **Backend**  
- **None (Direct OpenAI API calls)**  
- No custom backend servers  
- No user authentication  

### **Local Storage**  
- Room database for saved scans
- Store last 10 scans only
- Images saved to app internal storage  

---

# 8. **Non-Functional Requirements**

- Total scan time under **4 seconds**  
- APK under **5 MB**  
- Target Android 10+  
- Smooth 60FPS camera preview  
- Stable JSON responses  
- Minimal battery drain  
- No lag during transitions  

---

# 9. **Monetization (Not in MVP, but planned)**

### Free:
- Up to 10 scans/day  

### Premium (later):
- Unlimited scans  
- In-depth disease diagnostics  
- Coral care sheets  
- Growth tracking  
- Parameter prediction  

Use **RevenueCat** for subscription management in v1.1+.

---

# 10. **Roadmap**

### **v1.0 (Launch Tomorrow)**
- Camera capture  
- GPT-5 Vision scan  
- Dribbble-style results UI  
- Local history  
- Simple animations  

### **v1.1**
- Paywall + Premium  
- More polished animations  
- Extended species database  
- Parameter logger  

### **v2.0**
- Cloud sync  
- Tank profiles  
- AR coral placement  
- Growth tracking  
- Community tagging  

---

# 11. **Branding**

### Colors:
- Deep Ocean `#0A2036`  
- Aqua Blue `#1FA3C9`  
- Seafoam `#0ED4A1`  
- Coral Accent `#F66C84`  
- Glass White `rgba(255,255,255,0.2)`  

### Icon:
- Simple coral silhouette  
- Or minimalist fish outline  

### Animations:
- Water ripple effect on scan  
- Bubbles rising on success  
- Coral-pulse indicator for problems  

---

# 12. **Assets Needed**
- App icon (replace existing in `res/mipmap-*/`)
- Splash background image (`res/drawable/`)
- ReefScan logo image/vector (`res/drawable/`)
- Gradient backgrounds (`res/drawable/`)
- Coral & fish vector illustrations
- Lottie animations (`res/raw/`):
  - Wave/ripple loader
  - Bubbles rising
- Custom font files (`res/font/`) - Inter or Poppins  

---

# 13. **Open Items**
- Should we add sound effects?  
- Should we support “long touch to rescan”?  
- Dark mode or fixed ocean-dark theme?  

---

# 14. **Project Starting Point**

The project begins with an **Empty Compose Activity** created in Android Studio with:

### Already Configured:
- Kotlin 2.0.0
- Jetpack Compose with BOM 2024.04.01
- Material3 library
- Basic theme files (`Color.kt`, `Theme.kt`, `Type.kt`)
- `MainActivity.kt` with Compose setup
- Edge-to-edge display enabled
- Gradle version catalog (`libs.versions.toml`)
- Target SDK 35, Min SDK 24

### Existing File Structure:
```
app/src/main/java/com/example/reefscan/
├── MainActivity.kt
└── ui/theme/
    ├── Color.kt
    ├── Theme.kt
    └── Type.kt
```

### Needs to be Added:
- Additional dependencies (CameraX, Retrofit, Room, Coil, Lottie, Navigation)
- Navigation setup
- All screen composables
- API integration
- Local database
- Custom assets (images, animations, fonts)
- Brand theming updates

---

# 15. **Summary**

This updated PRD locks in **GPT-5 Vision** as the **single, primary, and best AI model** powering:

- Species identification  
- Pest detection  
- Disease analysis  
- Water quality recognition  
- Recommendations  

This simplifies engineering, improves accuracy, and ensures the app is deliverable **tomorrow** with:

- Beautiful Dribbble-style UI  
- Minimal navigation  
- Instant value  
- No backend required  

