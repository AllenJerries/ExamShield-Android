-- ExamShield Sample Data
-- For dashboard demo and testing

-- ============================================
-- SAMPLE ROOMS
-- ============================================
INSERT INTO rooms (name, room_number, institution) VALUES
('Main Hall', '101', 'National Institute of Technology'),
('Block B', '204', 'National Institute of Technology');

-- ============================================
-- SAMPLE EXAMS
-- ============================================
INSERT INTO exams (room_id, exam_name, exam_date, start_time, invigilator_name, status) VALUES
(1, 'CS Final Exam - Data Structures', CURRENT_DATE, NOW() - INTERVAL '2 hours', 'Dr. Sharma', 'active'),
(2, 'EE Mid-Term - Circuit Theory', CURRENT_DATE, NOW() - INTERVAL '1 hour', 'Prof. Mehta', 'active'),
(1, 'Mathematics II - Quiz', CURRENT_DATE - 1, NOW() - INTERVAL '1 day', 'Dr. Verma', 'completed');

-- ============================================
-- SAMPLE WHITELIST DEVICES
-- (authorized devices found in room before exam)
-- ============================================
INSERT INTO whitelist_devices (room_id, mac_hash, device_name, device_type) VALUES
(1, 'a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6a7b8c9d0e1f2a3b4c5d6a7b8c9d0e1f2', 'Projector-HP', 'wifi_device'),
(1, 'b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6a7b8c9d0e1f2a3b4c5d6a7b8c9d0e1f2a3', 'AC-Samsung', 'wifi_device'),
(1, 'c3d4e5f6a7b8c9d0e1f2a3b4c5d6a7b8c9d0e1f2a3b4c5d6a7b8c9d0e1f2a3b4', 'Router-TP-Link', 'wifi_device'),
(2, 'd4e5f6a7b8c9d0e1f2a3b4c5d6a7b8c9d0e1f2a3b4c5d6a7b8c9d0e1f2a3b4c5', 'SmartBoard', 'wifi_device'),
(2, 'e5f6a7b8c9d0e1f2a3b4c5d6a7b8c9d0e1f2a3b4c5d6a7b8c9d0e1f2a3b4c5d6', 'AC-LG', 'wifi_device');

-- ============================================
-- SAMPLE DETECTED DEVICES
-- (unauthorized devices found during exam)
-- ============================================
INSERT INTO detected_devices (exam_id, mac_hash, device_name, device_type, risk_level, rssi, detected_at, action_taken, confirmed_cheating) VALUES
(1, 'f6a7b8c9d0e1f2a3b4c5d6a7b8c9d0e1f2a3b4c5d6a7b8c9d0e1f2a3b4c5d6e7', 'boAt Airdopes 141', 'earphone', 'high', -52, NOW() - INTERVAL '1 hour 45 minutes', 'found', true),
(1, 'a7b8c9d0e1f2a3b4c5d6a7b8c9d0e1f2a3b4c5d6a7b8c9d0e1f2a3b4c5d6e7f8', 'Samsung Galaxy Watch5', 'watch', 'high', -68, NOW() - INTERVAL '1 hour 30 minutes', 'found', true),
(1, 'b8c9d0e1f2a3b4c5d6a7b8c9d0e1f2a3b4c5d6a7b8c9d0e1f2a3b4c5d6e7f8a9', 'iPhone 15', 'phone', 'high', -74, NOW() - INTERVAL '1 hour 15 minutes', 'investigating', false),
(1, 'c9d0e1f2a3b4c5d6a7b8c9d0e1f2a3b4c5d6a7b8c9d0e1f2a3b4c5d6e7f8a9b0', 'Unknown Device', 'unknown', 'medium', -88, NOW() - INTERVAL '1 hour', 'pending', false),
(1, 'd0e1f2a3b4c5d6a7b8c9d0e1f2a3b4c5d6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1', 'Redmi Buds 4', 'earphone', 'high', -45, NOW() - INTERVAL '45 minutes', 'found', true),
(2, 'e1f2a3b4c5d6a7b8c9d0e1f2a3b4c5d6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2', 'JBL Tune 230NC', 'earphone', 'high', -58, NOW() - INTERVAL '50 minutes', 'found', true),
(2, 'f2a3b4c5d6a7b8c9d0e1f2a3b4c5d6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3', 'Realme Watch 3', 'watch', 'high', -72, NOW() - INTERVAL '40 minutes', 'investigating', false),
(2, 'a3b4c5d6a7b8c9d0e1f2a3b4c5d6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4', 'OnePlus Nord CE3', 'phone', 'high', -80, NOW() - INTERVAL '30 minutes', 'pending', false),
(1, 'b4c5d6a7b8c9d0e1f2a3b4c5d6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5', 'Smart Bulb-WiFi', 'wifi_device', 'low', -92, NOW() - INTERVAL '20 minutes', 'false_alarm', false),
(1, 'c5d6a7b8c9d0e1f2a3b4c5d6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6', 'Mi Band 8', 'watch', 'high', -61, NOW() - INTERVAL '10 minutes', 'found', true);

-- ============================================
-- SAMPLE INCIDENTS
-- ============================================
INSERT INTO incidents (exam_id, device_id, severity, description, evidence_notes, timestamp, resolved) VALUES
(1, 1, 'high', 'Device: boAt Airdopes 141, Type: earphone', 'RSSI: -52, Action: found - Confirmed cheating via proximity hunt', NOW() - INTERVAL '1 hour 40 minutes', false),
(1, 2, 'high', 'Device: Samsung Galaxy Watch5, Type: watch', 'RSSI: -68, Action: found - Smartwatch detected on student wrist', NOW() - INTERVAL '1 hour 25 minutes', false),
(1, 5, 'critical', 'Device: Redmi Buds 4, Type: earphone', 'RSSI: -45, Action: found - Very strong signal, device found on person', NOW() - INTERVAL '40 minutes', false);
