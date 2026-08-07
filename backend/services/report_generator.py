from reportlab.lib.pagesizes import letter
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle
from sqlalchemy.orm import Session
from sqlalchemy import select
from models.exam import Exam
from models.room import Room
from models.device import DetectedDevice
from models.incident import Incident
import os
import tempfile
from datetime import datetime


def generate_pdf_report(exam_id: int, db: Session) -> str | None:
    result = db.execute(select(Exam).where(Exam.id == exam_id))
    exam = result.scalars().first()
    if not exam:
        return None

    result = db.execute(select(Room).where(Room.id == exam.room_id))
    room = result.scalars().first()

    result = db.execute(
        select(DetectedDevice).where(DetectedDevice.exam_id == exam_id).order_by(
            DetectedDevice.detected_at
        )
    )
    detections = result.scalars().all()

    result = db.execute(
        select(Incident).where(Incident.exam_id == exam_id).order_by(
            Incident.timestamp
        )
    )
    incidents = result.scalars().all()

    fd, path = tempfile.mkstemp(suffix=".pdf")
    os.close(fd)

    doc = SimpleDocTemplate(path, pagesize=letter)
    styles = getSampleStyleSheet()
    elements = []

    title_style = ParagraphStyle("Title2", parent=styles["Title"], fontSize=18, spaceAfter=20)
    heading_style = ParagraphStyle("Heading2", parent=styles["Heading2"], fontSize=14, spaceAfter=10)

    elements.append(Paragraph("ExamShield - Exam Report", title_style))
    elements.append(Paragraph(f"Generated: {datetime.now().strftime('%Y-%m-%d %H:%M')}", styles["Normal"]))
    elements.append(Spacer(1, 20))

    if room:
        elements.append(Paragraph(f"Institution: {room.institution}", styles["Normal"]))
        elements.append(Paragraph(f"Room: {room.name} ({room.room_number})", styles["Normal"]))
    elements.append(Paragraph(f"Exam: {exam.exam_name}", styles["Normal"]))
    elements.append(Paragraph(f"Date: {exam.exam_date}", styles["Normal"]))
    elements.append(Paragraph(f"Invigilator: {exam.invigilator_name or 'N/A'}", styles["Normal"]))
    elements.append(Paragraph(f"Status: {exam.status}", styles["Normal"]))
    elements.append(Spacer(1, 20))

    elements.append(Paragraph("Summary", heading_style))
    summary_data = [
        ["Metric", "Count"],
        ["Total Scans", str(len(detections))],
        ["High Risk Detections", str(sum(1 for d in detections if d.risk_level == "high"))],
        ["Medium Risk Detections", str(sum(1 for d in detections if d.risk_level == "medium"))],
        ["Low Risk Detections", str(sum(1 for d in detections if d.risk_level == "low"))],
        ["Confirmed Incidents", str(len(incidents))],
        ["Devices Found", str(sum(1 for d in detections if d.action_taken == "found"))],
    ]
    summary_table = Table(summary_data, colWidths=[300, 100])
    summary_table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), colors.grey),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.whitesmoke),
        ("ALIGN", (0, 0), (-1, -1), "CENTER"),
        ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
        ("FONTSIZE", (0, 0), (-1, -1), 10),
        ("BOTTOMPADDING", (0, 0), (-1, 0), 12),
        ("GRID", (0, 0), (-1, -1), 1, colors.black),
    ]))
    elements.append(summary_table)
    elements.append(Spacer(1, 20))

    if detections:
        elements.append(Paragraph("Device Detections Timeline", heading_style))
        detection_data = [["Time", "Device Name", "Type", "Risk", "RSSI", "Action"]]
        for d in detections:
            detection_data.append([
                d.detected_at.strftime("%H:%M:%S") if d.detected_at else "N/A",
                d.device_name or "Unknown",
                d.device_type or "Unknown",
                d.risk_level or "N/A",
                str(d.rssi) if d.rssi is not None else "N/A",
                d.action_taken or "Pending",
            ])
        detection_table = Table(detection_data, colWidths=[70, 100, 70, 50, 40, 70])
        detection_table.setStyle(TableStyle([
            ("BACKGROUND", (0, 0), (-1, 0), colors.grey),
            ("TEXTCOLOR", (0, 0), (-1, 0), colors.whitesmoke),
            ("ALIGN", (0, 0), (-1, -1), "CENTER"),
            ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
            ("FONTSIZE", (0, 0), (-1, -1), 8),
            ("BOTTOMPADDING", (0, 0), (-1, 0), 8),
            ("GRID", (0, 0), (-1, -1), 1, colors.black),
        ]))
        elements.append(detection_table)
        elements.append(Spacer(1, 20))

    if incidents:
        elements.append(Paragraph("Confirmed Incidents", heading_style))
        incident_data = [["Time", "Severity", "Description", "Resolved"]]
        for inc in incidents:
            incident_data.append([
                inc.timestamp.strftime("%H:%M:%S") if inc.timestamp else "N/A",
                inc.severity or "N/A",
                inc.description or "",
                "Yes" if inc.resolved else "No",
            ])
        incident_table = Table(incident_data, colWidths=[70, 60, 200, 50])
        incident_table.setStyle(TableStyle([
            ("BACKGROUND", (0, 0), (-1, 0), colors.grey),
            ("TEXTCOLOR", (0, 0), (-1, 0), colors.whitesmoke),
            ("ALIGN", (0, 0), (-1, -1), "CENTER"),
            ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
            ("FONTSIZE", (0, 0), (-1, -1), 9),
            ("BOTTOMPADDING", (0, 0), (-1, 0), 8),
            ("GRID", (0, 0), (-1, -1), 1, colors.black),
        ]))
        elements.append(incident_table)
        elements.append(Spacer(1, 30))

    elements.append(Spacer(1, 40))
    elements.append(Paragraph("Invigilator Certification", heading_style))
    elements.append(Paragraph("I hereby certify that the above report is a true and accurate record", styles["Normal"]))
    elements.append(Paragraph("of the wireless device detection conducted during this examination.", styles["Normal"]))
    elements.append(Spacer(1, 30))
    elements.append(Paragraph("Signature: ___________________________", styles["Normal"]))
    elements.append(Paragraph(f"Date: {datetime.now().strftime('%Y-%m-%d')}", styles["Normal"]))

    doc.build(elements)
    return path
