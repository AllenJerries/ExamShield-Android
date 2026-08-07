-- ExamShield Database Schema
-- Proximity-Based Wireless Device Detection System
-- PostgreSQL

-- Drop existing tables if they exist (for development reset)
DROP TABLE IF EXISTS incidents CASCADE;
DROP TABLE IF EXISTS detected_devices CASCADE;
DROP TABLE IF EXISTS whitelist_devices CASCADE;
DROP TABLE IF EXISTS exams CASCADE;
DROP TABLE IF EXISTS rooms CASCADE;

-- ============================================
-- ROOMS TABLE
-- Stores examination hall/room information
-- ============================================
CREATE TABLE rooms (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    room_number VARCHAR(20) NOT NULL,
    institution VARCHAR(200) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

-- ============================================
-- EXAMS TABLE
-- Stores exam session information
-- ============================================
CREATE TABLE exams (
    id SERIAL PRIMARY KEY,
    room_id INTEGER NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    exam_name VARCHAR(200) NOT NULL,
    exam_date DATE NOT NULL,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    invigilator_name VARCHAR(100),
    status VARCHAR(20) DEFAULT 'active' CHECK (status IN ('active', 'completed', 'cancelled')),
    created_at TIMESTAMP DEFAULT NOW()
);

-- ============================================
-- WHITELIST DEVICES TABLE
-- Stores baseline devices detected before exam
-- These are authorized devices (e.g. AC units, projectors)
-- ============================================
CREATE TABLE whitelist_devices (
    id SERIAL PRIMARY KEY,
    room_id INTEGER NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    mac_hash VARCHAR(64) NOT NULL,
    device_name VARCHAR(100),
    device_type VARCHAR(50) DEFAULT 'unknown',
    added_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(room_id, mac_hash)
);

-- ============================================
-- DETECTED DEVICES TABLE
-- Stores all unauthorized devices detected during exam
-- ============================================
CREATE TABLE detected_devices (
    id SERIAL PRIMARY KEY,
    exam_id INTEGER NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    mac_hash VARCHAR(64) NOT NULL,
    device_name VARCHAR(100),
    device_type VARCHAR(50) DEFAULT 'unknown',
    risk_level VARCHAR(20) CHECK (risk_level IN ('high', 'medium', 'low')),
    rssi INTEGER,
    detected_at TIMESTAMP DEFAULT NOW(),
    action_taken VARCHAR(50) DEFAULT 'pending' CHECK (action_taken IN ('pending', 'investigating', 'found', 'false_alarm', 'ignored')),
    confirmed_cheating BOOLEAN DEFAULT FALSE
);

-- ============================================
-- INCIDENTS TABLE
-- Stores confirmed cheating incidents with evidence
-- ============================================
CREATE TABLE incidents (
    id SERIAL PRIMARY KEY,
    exam_id INTEGER NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    device_id INTEGER REFERENCES detected_devices(id) ON DELETE SET NULL,
    severity VARCHAR(20) CHECK (severity IN ('critical', 'high', 'medium', 'low')),
    description TEXT,
    evidence_notes TEXT,
    timestamp TIMESTAMP DEFAULT NOW(),
    resolved BOOLEAN DEFAULT FALSE
);

-- ============================================
-- INDEXES for performance
-- ============================================
CREATE INDEX idx_exams_status ON exams(status);
CREATE INDEX idx_exams_date ON exams(exam_date);
CREATE INDEX idx_exams_room ON exams(room_id);
CREATE INDEX idx_detected_exam ON detected_devices(exam_id);
CREATE INDEX idx_detected_time ON detected_devices(detected_at);
CREATE INDEX idx_detected_risk ON detected_devices(risk_level);
CREATE INDEX idx_whitelist_room ON whitelist_devices(room_id);
CREATE INDEX idx_incidents_exam ON incidents(exam_id);
CREATE INDEX idx_incidents_severity ON incidents(severity);

-- ============================================
-- VIEW: Current active exams with detection counts
-- ============================================
CREATE VIEW active_exam_monitoring AS
SELECT 
    e.id AS exam_id,
    e.exam_name,
    e.exam_date,
    e.invigilator_name,
    r.name AS room_name,
    r.room_number,
    r.institution,
    COUNT(dd.id) AS total_detections,
    COUNT(CASE WHEN dd.risk_level = 'high' THEN 1 END) AS high_risk_count,
    COUNT(CASE WHEN dd.confirmed_cheating = TRUE THEN 1 END) AS confirmed_incidents,
    MAX(dd.detected_at) AS last_detection
FROM exams e
JOIN rooms r ON e.room_id = r.id
LEFT JOIN detected_devices dd ON dd.exam_id = e.id
WHERE e.status = 'active'
GROUP BY e.id, e.exam_name, e.exam_date, e.invigilator_name, r.name, r.room_number, r.institution;

-- ============================================
-- VIEW: Detection summary by device type
-- ============================================
CREATE VIEW device_type_summary AS
SELECT 
    device_type,
    COUNT(*) AS total_detections,
    COUNT(CASE WHEN confirmed_cheating = TRUE THEN 1 END) AS confirmed_incidents,
    AVG(rssi) AS avg_rssi
FROM detected_devices
GROUP BY device_type
ORDER BY total_detections DESC;
