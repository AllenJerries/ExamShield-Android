from pydantic import BaseModel, ConfigDict, Field
from datetime import datetime


class RoomCreate(BaseModel):
    name: str = Field(..., max_length=100)
    room_number: str = Field(..., max_length=20)
    institution: str = Field(..., max_length=200)


class RoomResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str
    room_number: str
    institution: str
    created_at: datetime | None = None


class RoomList(BaseModel):
    rooms: list[RoomResponse]
    total: int
