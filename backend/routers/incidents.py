from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from sqlalchemy import select
from database import get_db
from models.incident import Incident
from schemas.incident import IncidentCreate, IncidentResponse, IncidentList

router = APIRouter(prefix="/incidents", tags=["Incidents"])


@router.post("", response_model=IncidentResponse)
def create_incident(incident: IncidentCreate, db: Session = Depends(get_db)):
    db_incident = Incident(
        exam_id=incident.exam_id,
        device_id=incident.device_id,
        severity=incident.severity,
        description=incident.description,
        evidence_notes=incident.evidence_notes,
    )
    db.add(db_incident)
    db.commit()
    db.refresh(db_incident)
    return db_incident


@router.get("/{exam_id}", response_model=IncidentList)
def get_incidents(
    exam_id: int,
    skip: int = 0,
    limit: int = 100,
    resolved: bool = None,
    severity: str = None,
    db: Session = Depends(get_db),
):
    query = select(Incident).where(Incident.exam_id == exam_id)
    if resolved is not None:
        query = query.where(Incident.resolved == resolved)
    if severity:
        query = query.where(Incident.severity == severity)
    query = query.order_by(Incident.timestamp.desc())
    result = db.execute(query.offset(skip).limit(limit))
    incidents = result.scalars().all()
    return {"incidents": [i.to_dict() for i in incidents], "total": len(incidents)}


@router.put("/{incident_id}/resolve")
def resolve_incident(incident_id: int, db: Session = Depends(get_db)):
    result = db.execute(select(Incident).where(Incident.id == incident_id))
    incident = result.scalars().first()
    if not incident:
        raise HTTPException(status_code=404, detail="Incident not found")
    incident.resolved = True
    db.commit()
    return {"message": "Incident resolved", "id": incident_id}
