from pydantic import BaseModel, ConfigDict, Field
from datetime import datetime


class WhitelistDeviceCreate(BaseModel):
    room_id: int
    mac_hash: str = Field(..., max_length=64)
    device_name: str | None = Field(None, max_length=100)
    device_type: str | None = Field("unknown", max_length=50)


class WhitelistDeviceResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    room_id: int
    mac_hash: str
    device_name: str | None = None
    device_type: str | None = None
    added_at: datetime | None = None


class DetectionCreate(BaseModel):
    exam_id: int
    mac_hash: str = Field(..., max_length=64)
    device_name: str | None = Field(None, max_length=100)
    device_type: str | None = Field("unknown", max_length=50)
    risk_level: str | None = Field("medium", max_length=20)
    rssi: int | None = None


class DetectionResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    exam_id: int
    mac_hash: str
    device_name: str | None = None
    device_type: str | None = None
    risk_level: str | None = None
    rssi: int | None = None
    detected_at: datetime | None = None
    action_taken: str | None = None
    confirmed_cheating: bool | None = None


class DetectionList(BaseModel):
    detections: list[DetectionResponse]
    total: int


class WhitelistList(BaseModel):
    devices: list[WhitelistDeviceResponse]
    total: int
