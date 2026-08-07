from sqlalchemy.orm import Session
from sqlalchemy import func, cast, Date, extract, select
from datetime import datetime, timedelta, date
from models.exam import Exam
from models.device import DetectedDevice
from models.incident import Incident
from models.room import Room


def get_overall_statistics(db: Session) -> dict:
    total_exams = db.execute(select(func.count()).select_from(Exam)).scalar()
    active_exams = db.execute(
        select(func.count()).select_from(Exam).where(Exam.status == "active")
    ).scalar()
    total_rooms = db.execute(select(func.count()).select_from(Room)).scalar()

    today = date.today()
    detections_today = db.execute(
        select(func.count()).select_from(DetectedDevice).where(
            cast(DetectedDevice.detected_at, Date) == today
        )
    ).scalar()

    total_detections = db.execute(select(func.count()).select_from(DetectedDevice)).scalar()
    total_incidents = db.execute(select(func.count()).select_from(Incident)).scalar()

    risk_distribution = db.execute(
        select(
            DetectedDevice.risk_level,
            func.count(DetectedDevice.id).label("count")
        ).group_by(DetectedDevice.risk_level)
    ).all()

    device_type_distribution = db.execute(
        select(
            DetectedDevice.device_type,
            func.count(DetectedDevice.id).label("count")
        ).group_by(DetectedDevice.device_type).order_by(
            func.count(DetectedDevice.id).desc()
        ).limit(10)
    ).all()

    detections_by_hour = db.execute(
        select(
            extract("hour", DetectedDevice.detected_at).label("hour"),
            func.count(DetectedDevice.id).label("count")
        ).group_by("hour").order_by("hour")
    ).all()

    detections_last_7_days = db.execute(
        select(
            cast(DetectedDevice.detected_at, Date).label("day"),
            func.count(DetectedDevice.id).label("count")
        ).where(
            DetectedDevice.detected_at >= datetime.utcnow() - timedelta(days=7)
        ).group_by("day").order_by("day")
    ).all()

    room_comparison = db.execute(
        select(
            Room.name,
            Room.room_number,
            func.count(DetectedDevice.id).label("detections")
        ).join(Exam, Exam.room_id == Room.id)
         .join(DetectedDevice, DetectedDevice.exam_id == Exam.id)
         .group_by(Room.id, Room.name, Room.room_number)
         .order_by(func.count(DetectedDevice.id).desc())
         .limit(10)
    ).all()

    return {
        "total_exams": total_exams,
        "active_exams": active_exams,
        "total_rooms": total_rooms,
        "detections_today": detections_today,
        "total_detections": total_detections,
        "total_incidents": total_incidents,
        "risk_distribution": [
            {"risk_level": r.risk_level, "count": r.count} for r in risk_distribution
        ],
        "device_type_distribution": [
            {"device_type": dt.device_type, "count": dt.count} for dt in device_type_distribution
        ],
        "detections_by_hour": [
            {"hour": int(d.hour), "count": d.count} for d in detections_by_hour
        ],
        "detections_last_7_days": [
            {"date": str(d.day), "count": d.count} for d in detections_last_7_days
        ],
        "room_comparison": [
            {"name": r.name, "room_number": r.room_number, "detections": r.detections}
            for r in room_comparison
        ],
    }
