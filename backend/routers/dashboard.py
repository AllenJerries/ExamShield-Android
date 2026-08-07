from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from sqlalchemy import func, cast, Date, select
from datetime import date
from database import get_db
from models.exam import Exam
from models.device import DetectedDevice
from models.incident import Incident
from models.room import Room

router = APIRouter(prefix="/dashboard", tags=["Dashboard"])


@router.get("")
def get_dashboard(db: Session = Depends(get_db)):
    today = date.today()

    active_exams = db.execute(
        select(func.count()).select_from(Exam).where(Exam.status == "active")
    ).scalar()

    total_detections_today = db.execute(
        select(func.count()).select_from(DetectedDevice).where(
            cast(DetectedDevice.detected_at, Date) == today
        )
    ).scalar()

    confirmed_incidents_today = db.execute(
        select(func.count()).select_from(Incident).where(
            cast(Incident.timestamp, Date) == today
        )
    ).scalar()

    monitored_rooms = db.execute(
        select(func.count()).select_from(Room)
    ).scalar()

    device_types = db.execute(
        select(
            DetectedDevice.device_type,
            func.count(DetectedDevice.id).label("count")
        ).group_by(DetectedDevice.device_type).order_by(
            func.count(DetectedDevice.id).desc()
        ).limit(10)
    ).all()

    recent_alerts = db.execute(
        select(DetectedDevice).order_by(
            DetectedDevice.detected_at.desc()
        ).limit(20)
    ).scalars().all()

    return {
        "active_exams": active_exams,
        "total_detections_today": total_detections_today,
        "confirmed_incidents_today": confirmed_incidents_today,
        "monitored_rooms": monitored_rooms,
        "most_common_device_types": [
            {"type": dt.device_type, "count": dt.count} for dt in device_types
        ],
        "recent_alerts": [
            {
                "id": a.id,
                "device_name": a.device_name,
                "device_type": a.device_type,
                "risk_level": a.risk_level,
                "rssi": a.rssi,
                "detected_at": a.detected_at.isoformat() if a.detected_at else None,
            }
            for a in recent_alerts
        ],
    }
