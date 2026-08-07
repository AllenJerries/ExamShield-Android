from pydantic import BaseModel, ConfigDict, Field
from datetime import datetime


class IncidentCreate(BaseModel):
    exam_id: int
    device_id: int | None = None
    severity: str | None = Field("medium", max_length=20)
    description: str | None = Field(None, max_length=1000)
    evidence_notes: str | None = Field(None, max_length=2000)


class IncidentResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    exam_id: int
    device_id: int | None = None
    severity: str | None = None
    description: str | None = None
    evidence_notes: str | None = None
    timestamp: datetime | None = None
    resolved: bool | None = None


class IncidentList(BaseModel):
    incidents: list[IncidentResponse]
    total: int


class DashboardResponse(BaseModel):
    active_exams: int
    total_detections_today: int
    confirmed_incidents_today: int
    monitored_rooms: int
    most_common_device_types: list[dict]
    recent_alerts: list[dict]
