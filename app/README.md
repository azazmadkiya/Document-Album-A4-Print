# A4 Document Studio

**A4 Document Studio** is a powerful, offline-first Android application built with Kotlin and Jetpack Compose designed to simplify document scanning, ID card arrangement, PDF generation, and secure document management.

---

## 🌟 Core Features

- **A4 Layout Management**: Arrange multiple ID cards (Aadhaar, PAN, Voter ID, Driving License, Student IDs) and documents (Front & Back) onto professional A4-sized grids and custom layouts.
- **PDF Password Support**: Secure your exported PDF documents with custom encryption and password protection before sharing or printing.
- **Smart Edge Detection & Cropping**: Automatically detect document boundaries and crop/clean scans for professional output.
- **Image Filters & Editing**: Enhance scans with Black & White, Grayscale, or Color filters, contrast adjustments, and rotation.
- **Local-First & Secure (Room Database)**: All your scanned documents, albums, and PDFs are stored securely and exclusively on your device using a local Room database with zero cloud tracking.
- **High-Resolution PDF Export & Print**: Generate crisp A4 PDFs and send them directly to wireless printers or share via messaging and email.

---

## 🛠️ Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose & Material Design 3 (M3)
- **Architecture**: MVVM (Model-View-ViewModel) with Kotlin Coroutines & Flows
- **Database**: Room Database (with KSP)
- **PDF Handling**: PdfBox-Android

---

## 🔒 Privacy Policy

A4 Document Studio is 100% offline-first. Your documents never leave your device. Read our hosted [Privacy Policy](https://azazmadkiya.github.io/Document-Album-A4-Print/).

---

## 🚀 Build Instructions

1. Clone the repository:
   ```bash
   git clone https://github.com/azazmadkiya/Document-Album-A4-Print.git
   ```
2. Open the project in **Android Studio Koala or newer**.
3. Sync the project with Gradle files.
4. Run the app on an Android emulator or physical device (`minSdk 24`, `targetSdk 36`).

---

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.
