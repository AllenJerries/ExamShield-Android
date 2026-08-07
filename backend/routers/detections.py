from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from sqlalchemy import select
from database import get_db
from models.device import DetectedDevice
from schemas.device import DetectionCreate, DetectionResponse, DetectionList

router = APIRouter(prefix="/detections", tags=["Detections"])


@router.post("", response_model=DetectionResponse)
def create_detection(detection: DetectionCreate, db: Session = Depends(get_db)):
    db_detection = DetectedDevice(
        exam_id=detection.exam_id,
        mac_hash=detection.mac_hash,
        device_name=detection.device_name,
        device_type=detection.device_type,
        risk_level=detection.risk_level,
        rssi=detection.rssi,
    )
    db.add(db_detection)
    db.commit()
    db.refresh(db_detection)
    return db_detection


@router.get("/{exam_id}", response_model=DetectionList)
def get_detections(
    exam_id: int,
    skip: int = 0,
    limit: int = 100,
    risk_level: str = None,
    db: Session = Depends(get_db),
):
    query = select(DetectedDevice).where(DetectedDevice.exam_id == exam_id)
    if risk_level:
        query = query.where(DetectedDevice.risk_level == risk_level)
    query = query.order_by(DetectedDevice.detected_at.desc())
    result = db.execute(query.offset(skip).limit(limit))
    detections = result.scalars().all()
    return {"detections": [d.to_dict() for d in detections], "total": len(detections)}


@router.put("/{detection_id}/action")
def update_detection_action(
    detection_id: int,
    action_taken: str,
    confirmed_cheating: bool = False,
    db: Session = Depends(get_db),
):
    result = db.execute(select(DetectedDevice).where(DetectedDevice.id == detection_id))
    detection = result.scalars().first()
    if not detection:
        raise HTTPException(status_code=404, detail="Detection not found")
    detection.action_taken = action_taken
    detection.confirmed_cheating = confirmed_cheating
    db.commit()
    return {"message": "Detection updated", "id": detection_id}
