# ExamShield 🛡️

**Proximity-Based Wireless Device Detection System for Examination Halls**

A professional Android application that detects unauthorized wireless devices during examinations using smartphone sensors.

## 🎯 Key Features

- 🔍 Multi-Source Detection (Bluetooth, WiFi, Hotspots)
- 📏 Accurate Distance Estimation (cm and meters)
- 🧭 Sensor-Based Direction Guidance
- 🚨 Custom Siren Sound Alerts
- 🎯 Real-time Proximity Hunter
- 📊 Smart Device Classification
- 💾 Local Incident Storage
- 🎨 Material Design 3 Dark Theme
- 📡 Complete Offline Operation

## 🛠️ Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material Design 3
- **Architecture:** MVVM with StateFlow
- **Database:** Room
- **Concurrency:** Kotlin Coroutines
- **Audio:** AudioTrack (Custom Siren)

## 📋 Requirements

- Android 8.0+ (API 26+)
- Bluetooth Low Energy support
- WiFi capability
- Location services
- Android Studio Ladybug or later

## 🚀 Installation

### For Developers

git clone https://github.com/YOUR_USERNAME/ExamShield.git
cd ExamShield/android
./gradlew assembleDebug

### For Users

1. Download APK from Releases
2. Enable "Install from Unknown Sources"
3. Install and grant permissions

## 🎯 How to Use

1. Grant permissions on first launch
2. Enable Bluetooth, WiFi, Location
3. Start New Exam
4. Perform baseline scan
5. Start Exam Mode
6. Detections appear automatically
7. Tap HUNT to locate device
8. Follow beeps to find it

## 🏗️ Project Structure

ExamShield/
├── android/       Android app (Phase 1)
├── backend/       Python API (Phase 2)
├── dashboard/     React Web (Phase 2)
├── database/      SQL schemas (Phase 2)
├── README.md
└── LICENSE

## 🎓 Team

**Department of Computer Science & Engineering**

- Alagumalai P (71052302010)
- Allen Jerries A L (71052302011)
- Harini S (71052302039)
- Manasha V (71052302057)

**Guide:** Ms. M. Pushpalatha M.E., (AP/CSE)

## 📊 Development Phases

### Phase 1 (Complete) ✅
- Android app with full detection
- Bluetooth, WiFi, Hotspot scanning
- Proximity hunter with audio
- Local storage
- Professional UI

### Phase 2 (Upcoming) 🚀
- Cloud backend
- Web dashboard
- Multi-invigilator support
- PDF reports
- Analytics

## 🎯 Key Innovations

1. Custom Siren Generator (AudioTrack)
2. Multi-Strategy Device Tracking
3. Kalman Filtering for Distance
4. Sensor Fusion for Direction
5. Offline-First Architecture

## 📱 Tested On

- Vivo T3 Ultra (Android 15)
- Various Bluetooth devices
- Multiple WiFi networks

## 📄 License

MIT License - See LICENSE file

## ⚠️ Disclaimer

For legitimate examination monitoring only. Users must comply with local laws.

---

**Made with ❤️ for academic integrity**
