# ExamShield Demo Script

**Total Demo Time: 5 minutes**
**Very impressive for evaluators**

---

## DEMO STEP 1: Setup Room & Baseline Scan (60 seconds)

1. Open ExamShield Android app on phone
2. Tap **"Start New Session"**
3. Fill in the setup form:
   - Hall Name: `Hall A`
   - Room Number: `Room 101`
   - Exam Name: `CS Final Exam`
   - Date: `2026-07-17`
4. Tap **"Scan Baseline"**
5. Wait 5 seconds
6. App shows: **"Baseline complete: 3 devices whitelisted"**
   - This means the app detected 3 authorized devices in the room
     (projector, AC, WiFi router) and whitelisted them
7. Tap **"Start Exam Mode"**

**What evaluator sees:** Clean UI, fast baseline scan, clear status

---

## DEMO STEP 2: Unauthorized Device Detection (60 seconds)

1. Ask a friend to:
   - Turn on their **Bluetooth earphones** (e.g., boAt, AirPods, JBL)
   - Keep phone nearby with Bluetooth ON
2. Wait 10-15 seconds
3. App shows **ALERT** banner:
   - **"1 Unauthorized Device(s) Detected!"**
   - Device shown: `boAt Airdopes 141` (or similar)
   - Risk: **HIGH**
   - Type: **earphone**
4. Red alert sound plays
5. Phone vibrates

**What evaluator sees:** Real-time detection, automatic classification,
alert with device details

---

## DEMO STEP 3: Proximity Hunt Mode (90 seconds)

1. On the ExamActive screen, tap **"HUNT DEVICE"** on the detected earphone
2. Proximity Hunter screen opens
3. Walk slowly toward your friend holding the earphones
4. Show evaluator these changes in REAL TIME:

| Distance | RSSI | Beep Speed | Screen Text | Color |
|----------|------|------------|-------------|-------|
| Far (10m+) | -85 dBm | Slow (2s) | **FAR AWAY** | Blue/Green |
| Medium (5m) | -70 dBm | Medium (1s) | **GETTING CLOSER** | Yellow |
| Close (2m) | -50 dBm | Fast (600ms) | **VERY CLOSE** | Orange |
| Very Close (1m) | -40 dBm | Very Fast (300ms) | **VERY CLOSE** | Deep Orange |
| Found (0.5m) | -30 dBm | **CONTINUOUS** | **DEVICE FOUND!** | **RED FLASH** |

5. RSSI meter bar fills up as you approach
6. Screen background flashes RED when found
7. Continuous alarm sounds
8. Incident auto-logged

**What evaluator sees:** Amazing real-time proximity tracking,
Geiger-counter-like audio feedback, clear visual indicators

---

## DEMO STEP 4: Web Dashboard (60 seconds)

1. Open laptop browser
2. Go to `http://localhost:5173`
3. Show **Dashboard Home**:
   - Active Exams: **1**
   - Detections Today: **5+**
   - Confirmed Incidents: **3**
   - Rooms Monitored: **2**
4. Show **Recent Alerts Feed** - detection just appeared
5. Click **"Live Monitoring"** - shows active exam room
6. Click on the room - shows all detected devices in table
7. Point out: data updates automatically every 15 seconds

**What evaluator sees:** Professional dashboard, real-time sync,
clean data visualization

---

## DEMO STEP 5: Generate PDF Report (30 seconds)

1. Click **"Incident Reports"** in sidebar
2. Select exam from dropdown
3. Show filtered incidents with severity levels
4. Click **"Export PDF"** button
5. PDF downloads and opens showing:
   - Institution header
   - Exam details
   - Summary statistics
   - Device detection timeline table
   - Incident details table
   - Invigilator certification section

**What evaluator sees:** Professional report generation,
complete audit trail, printable evidence

---

## KEY TALKING POINTS FOR EVALUATOR

1. **Privacy First**: All MAC addresses are SHA-256 hashed
   - Never stores raw MAC addresses
   - Compliant with data protection regulations

2. **Offline Capable**: Android app works without internet
   - All scanning works offline
   - Detections saved locally in Room DB
   - Auto-syncs when connection returns

3. **Multi-Protocol**: Detects BLE, Classic Bluetooth, AND WiFi
   - Most solutions only detect Bluetooth
   - We catch WiFi-enabled devices too

4. **Proximity Hunt**: Unique feature no other system has
   - Audio-guided device location
   - RSSI-based distance estimation
   - Reduces search time from hours to minutes

5. **Smart Classification**: AI-powered risk assessment
   - High risk: earphones, smartwatches, phones
   - Medium risk: unknown devices
   - Low risk: authorized room equipment

---

## TROUBLESHOOTING

If detection doesn't appear:
- Make sure friend's device has Bluetooth ON
- Make sure device is NOT in the whitelisted devices
- Try moving the device closer (within 10m)
- Check that location permissions are granted

If dashboard shows no data:
- Make sure backend is running: `uvicorn main:app --reload`
- Check API at: `http://localhost:8000/docs`
- Check .env has correct DATABASE_URL
