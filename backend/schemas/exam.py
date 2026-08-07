from pydantic import BaseModel, ConfigDict, Field
from datetime import datetime, date


class ExamCreate(BaseModel):
    room_id: int
    exam_name: str = Field(..., max_length=200)
    exam_date: date
    invigilator_name: str | None = Field(None, max_length=100)


class ExamResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    room_id: int
    exam_name: str
    exam_date: date | None = None
    start_time: datetime | None = None
    end_time: datetime | None = None
    invigilator_name: str | None = None
    status: str
    created_at: datetime | None = None


class ExamEnd(BaseModel):
    end_time: datetime | None = None


class ExamList(BaseModel):
    exams: list[ExamResponse]
    total: int
