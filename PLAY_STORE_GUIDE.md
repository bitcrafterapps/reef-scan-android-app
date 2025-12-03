# 🚀 ReefScan - Google Play Store Publishing Guide

A comprehensive step-by-step guide to publishing ReefScan on the Google Play Store.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Create a Google Play Developer Account](#2-create-a-google-play-developer-account)
3. [Generate a Signed Release Build](#3-generate-a-signed-release-build)
4. [Create Your App on Google Play Console](#4-create-your-app-on-google-play-console)
5. [Prepare Store Listing](#5-prepare-store-listing)
6. [Upload Your App Bundle](#6-upload-your-app-bundle)
7. [Set Up Pricing & Distribution](#7-set-up-pricing--distribution)
8. [Complete App Content Declaration](#8-complete-app-content-declaration)
9. [Submit for Review](#9-submit-for-review)
10. [Post-Launch Checklist](#10-post-launch-checklist)

---

## 1. Prerequisites

Before you begin, ensure you have:

- [ ] **Android Studio** installed (latest stable version)
- [ ] **ReefScan project** builds successfully in debug mode
- [ ] **Google account** for Play Console
- [ ] **$25 USD** for one-time developer registration fee
- [ ] **Privacy Policy URL** hosted online
- [ ] **App screenshots** (phone and tablet if applicable)
- [ ] **Feature graphic** (1024 x 500 px)
- [ ] **App icon** (512 x 512 px, PNG, 32-bit with alpha)

---

## 2. Create a Google Play Developer Account

### Step 2.1: Register for Google Play Console

1. Go to [Google Play Console](https://play.google.com/console)
2. Sign in with your Google account
3. Click **"Get Started"** or **"Create account"**

### Step 2.2: Accept Developer Agreement

1. Read the Google Play Developer Distribution Agreement
2. Check the box to accept the terms
3. Click **"Continue"**

### Step 2.3: Pay Registration Fee

1. Enter payment details
2. Pay the **$25 one-time registration fee**
3. Wait for payment confirmation (usually instant)

### Step 2.4: Complete Account Details

1. **Developer name**: Enter your name or company name (this is public)
2. **Email address**: Contact email for users
3. **Phone number**: For account verification
4. **Website**: Optional, but recommended

> ⏱️ **Note**: Account verification can take up to 48 hours

---

## 3. Generate a Signed Release Build

### Step 3.1: Create a Keystore (First Time Only)

In Android Studio:

1. Go to **Build → Generate Signed Bundle / APK**
2. Select **Android App Bundle** (recommended) or APK
3. Click **Next**
4. Click **Create new...** under Key store path

Fill in the keystore details:

```
Key store path: /path/to/reefscan-release-key.jks
Password: [strong password]
Alias: reefscan-key
Key password: [strong password]
Validity: 25 years (9125 days)

Certificate:
  First and Last Name: [Your Name]
  Organizational Unit: [Your Team/Dept]
  Organization: [Your Company]
  City: [Your City]
  State: [Your State]
  Country Code: [US, etc.]
```

5. Click **OK** to create the keystore

> ⚠️ **CRITICAL**: Back up your keystore file and passwords securely! You cannot update your app without them.

### Step 3.2: Store Keystore Credentials Securely

Create a file `keystore.properties` in your project root (add to `.gitignore`!):

```properties
storePassword=your_store_password
keyPassword=your_key_password
keyAlias=reefscan-key
storeFile=/path/to/reefscan-release-key.jks
```

### Step 3.3: Build the Release Bundle

**Option A: Using Android Studio UI**

1. Go to **Build → Generate Signed Bundle / APK**
2. Select **Android App Bundle**
3. Click **Next**
4. Select your keystore and enter passwords
5. Select **release** build variant
6. Click **Create**
7. Find the AAB file at: `app/build/outputs/bundle/release/app-release.aab`

**Option B: Using Command Line**

```bash
# Navigate to project root
cd /Users/sandyfriedman/ReefScan

# Build release bundle
./gradlew bundleRelease

# Output location
# app/build/outputs/bundle/release/app-release.aab
```

### Step 3.4: Verify the Build

1. Check the AAB file was created
2. Note the file size (should be reasonable, typically 10-50MB)
3. Optionally test with: `bundletool build-apks --bundle=app-release.aab --output=app.apks`

---

## 4. Create Your App on Google Play Console

### Step 4.1: Create New App

1. Log into [Google Play Console](https://play.google.com/console)
2. Click **"Create app"**
3. Fill in the form:

| Field | Value |
|-------|-------|
| App name | ReefScan - AI Aquarium Scanner |
| Default language | English (United States) |
| App or game | App |
| Free or paid | Free |

4. Check the declarations checkboxes
5. Click **"Create app"**

### Step 4.2: Set Up Your App Dashboard

After creation, you'll see your app dashboard with tasks to complete:

- [ ] Set up your app
- [ ] Release your app
- [ ] Get discovered on Google Play

---

## 5. Prepare Store Listing

### Step 5.1: Main Store Listing

Navigate to **Grow → Store presence → Main store listing**

#### App Details

| Field | Content |
|-------|---------|
| **App name** | ReefScan - AI Aquarium Scanner |
| **Short description** | AI-powered reef aquarium scanner. Identify fish, coral, algae & detect problems. |
| **Full description** | See [PLAY_STORE_LISTING.md](./PLAY_STORE_LISTING.md) |

#### Graphics

**App Icon (512 x 512 px)**
- Export from `res/mipmap-xxxhdpi/ic_launcher.webp`
- Or create a 512x512 PNG version

**Feature Graphic (1024 x 500 px)**
- Create in design tool (Figma, Canva, etc.)
- Ocean gradient background
- ReefScan logo centered
- Tagline: "AI-Powered Aquarium Diagnostics"

**Screenshots (Required)**

Minimum 2 screenshots per device type. Recommended: 4-8

| Type | Dimensions | Required |
|------|------------|----------|
| Phone | 1080 x 1920 px (or 16:9) | Yes (2-8) |
| 7" Tablet | 1200 x 1920 px | Optional |
| 10" Tablet | 1600 x 2560 px | Optional |

**How to capture screenshots:**
```bash
# Using ADB
adb shell screencap -p /sdcard/screenshot.png
adb pull /sdcard/screenshot.png

# Or use Android Studio's Logcat window camera icon
```

**Recommended screenshot sequence:**
1. Splash screen with logo
2. My Tanks list
3. Home/Scan screen
4. Camera capture screen
5. Loading/analyzing screen
6. Results with identification
7. Saved scans history
8. Photo gallery

### Step 5.2: Store Settings

Navigate to **Grow → Store presence → Store settings**

| Setting | Value |
|---------|-------|
| App category | Tools |
| Tags | Aquarium, Fish, Coral, Scanner, AI |
| Email | your-support@email.com |
| Phone | Optional |
| Website | Optional |

---

## 6. Upload Your App Bundle

### Step 6.1: Create a Release Track

Navigate to **Release → Production** (or Testing for beta)

1. Click **"Create new release"**
2. For first release, you'll need to set up **Play App Signing**

### Step 6.2: Set Up Play App Signing

Google Play App Signing is required for new apps:

1. Click **"Continue"** to opt in to Play App Signing
2. Choose **"Use Google-generated key"** (recommended) or upload your own
3. Click **"Save"**

> 💡 **Tip**: Google manages the signing key, you keep the upload key. This is more secure.

### Step 6.3: Upload the AAB

1. Drag and drop your `app-release.aab` file
2. Or click **"Upload"** and select the file
3. Wait for processing and validation

### Step 6.4: Add Release Notes

```
Version 1.0.0 - Initial Release

🐠 Welcome to ReefScan!

• AI-powered fish, coral, and algae identification
• Multi-tank management
• Photo gallery with ratings
• Blue light camera filters
• Scan history and saved results
• Beautiful ocean-themed interface

Powered by GPT-4o Vision AI
```

### Step 6.5: Review and Save

1. Review the release summary
2. Click **"Save"** (don't roll out yet)

---

## 7. Set Up Pricing & Distribution

### Step 7.1: Countries/Regions

Navigate to **Release → Production → Countries/regions**

1. Click **"Add countries/regions"**
2. Select all countries or specific ones
3. Click **"Add countries/regions"**

### Step 7.2: Pricing

Navigate to **Monetize → Products → App pricing**

1. Select **"Free"** (ReefScan is free)
2. Click **"Save"**

> ⚠️ **Note**: Once published as free, you cannot change to paid

---

## 8. Complete App Content Declaration

### Step 8.1: Privacy Policy

Navigate to **Policy → App content → Privacy policy**

1. Enter your Privacy Policy URL
2. Click **"Save"**

**Sample Privacy Policy should include:**
- What data is collected (images for AI analysis)
- How data is used (sent to OpenAI for processing)
- Data retention policy
- User rights
- Contact information

> 💡 **Tip**: Use a privacy policy generator or consult legal advice

### Step 8.2: App Access

Navigate to **Policy → App content → App access**

Select: **"All functionality is available without special access"**

### Step 8.3: Ads Declaration

Navigate to **Policy → App content → Ads**

Select: **"No, my app does not contain ads"**

### Step 8.4: Content Rating

Navigate to **Policy → App content → Content rating**

1. Click **"Start questionnaire"**
2. Enter your email
3. Select category: **"Utility, Productivity, Communication, or Other"**
4. Answer all questions honestly:
   - Violence: No
   - Sexual content: No
   - Language: No
   - Controlled substances: No
   - etc.
5. Click **"Save"** then **"Calculate rating"**
6. Review ratings (should be **Everyone / PEGI 3**)
7. Click **"Apply rating"**

### Step 8.5: Target Audience

Navigate to **Policy → App content → Target audience**

1. Select age group: **"18 and over"** (safest for utility apps)
2. Confirm app is not designed for children
3. Click **"Save"**

### Step 8.6: News App Declaration

Select: **"No, my app is not a news app"**

### Step 8.7: COVID-19 Apps (if prompted)

Select: **"No"** - ReefScan is not COVID-related

### Step 8.8: Data Safety

Navigate to **Policy → App content → Data safety**

This is **REQUIRED** and detailed. Fill out honestly:

**Data Collection:**

| Data Type | Collected | Shared | Required | Purpose |
|-----------|-----------|--------|----------|---------|
| Photos | Yes | Yes (OpenAI) | Yes | AI analysis |
| App activity | No | No | - | - |
| Device info | No | No | - | - |

**Security Practices:**
- Data encrypted in transit: Yes (HTTPS)
- Data deletion available: Yes (user can delete scans)

Click **"Save"** when complete.

---

## 9. Submit for Review

### Step 9.1: Final Checklist

Before submitting, verify:

- [ ] All store listing fields completed
- [ ] App icon uploaded (512x512)
- [ ] Feature graphic uploaded (1024x500)
- [ ] At least 2 phone screenshots
- [ ] Privacy policy URL valid and accessible
- [ ] Content rating completed
- [ ] Data safety form completed
- [ ] Target audience set
- [ ] Countries selected
- [ ] AAB uploaded and validated

### Step 9.2: Review Release

Navigate to **Release → Production**

1. Click on your draft release
2. Click **"Review release"**
3. Review all warnings and errors
4. Fix any blocking issues

### Step 9.3: Start Rollout

1. Click **"Start rollout to Production"**
2. Confirm by clicking **"Rollout"**

### Step 9.4: Wait for Review

- **Initial review**: 1-7 days (can be longer for new developers)
- **Subsequent updates**: Usually 1-3 days
- You'll receive email notification when approved/rejected

> 💡 **Tip**: First-time apps often take longer. Be patient!

---

## 10. Post-Launch Checklist

### After Approval

- [ ] Verify app appears in Play Store search
- [ ] Test download on a real device
- [ ] Check all store listing displays correctly
- [ ] Share your Play Store link!

### Play Store URL Format
```
https://play.google.com/store/apps/details?id=com.example.reefscan
```

### Monitor Your App

- **Google Play Console → Statistics**: Downloads, ratings, crashes
- **Android Vitals**: Performance metrics, ANRs, crashes
- **Reviews**: Respond to user feedback

### Update Process

For future updates:
1. Increment `versionCode` and `versionName` in `build.gradle.kts`
2. Build new signed AAB
3. Create new release in Play Console
4. Upload AAB with release notes
5. Submit for review

---

## 📋 Quick Reference Commands

```bash
# Build release AAB
./gradlew bundleRelease

# Build release APK (if needed)
./gradlew assembleRelease

# Check signing
jarsigner -verify -verbose app-release.aab

# List AAB contents
bundletool build-apks --bundle=app-release.aab --output=temp.apks --mode=universal
unzip -l temp.apks
```

---

## 🆘 Troubleshooting

### Common Issues

| Issue | Solution |
|-------|----------|
| "Version code already used" | Increment `versionCode` in build.gradle.kts |
| "Keystore not found" | Check path in keystore.properties |
| "Target SDK too low" | Update `targetSdk` to latest stable |
| "Missing privacy policy" | Add valid URL in App Content section |
| "Screenshots wrong size" | Use exact dimensions required |
| "App rejected" | Read rejection email, fix issues, resubmit |

### Getting Help

- [Google Play Console Help](https://support.google.com/googleplay/android-developer)
- [Android Developer Guides](https://developer.android.com/distribute)
- [Play Console Community](https://support.google.com/googleplay/android-developer/community)

---

## 📅 Timeline Estimate

| Task | Time |
|------|------|
| Developer account setup | 1-2 days |
| Generate signed build | 30 minutes |
| Create store listing | 2-4 hours |
| Complete content declarations | 1-2 hours |
| Review process | 1-7 days |
| **Total** | **3-10 days** |

---

**Good luck with your ReefScan launch! 🐠🚀**

