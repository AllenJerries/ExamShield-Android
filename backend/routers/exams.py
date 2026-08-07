from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from sqlalchemy import select
from datetime import datetime
from database import get_db
from models.exam import Exam
from models.room import Room
from schemas.exam import ExamCreate, ExamResponse, ExamEnd, ExamList

router = APIRouter(prefix="/exams", tags=["Exams"])


@router.post("", response_model=ExamResponse)
def create_exam(exam: ExamCreate, db: Session = Depends(get_db)):
    result = db.execute(select(Room).where(Room.id == exam.room_id))
    room = result.scalars().first()
    if not room:
        raise HTTPException(status_code=404, detail="Room not found")
    db_exam = Exam(
        room_id=exam.room_id,
        exam_name=exam.exam_name,
        exam_date=exam.exam_date,
        invigilator_name=exam.invigilator_name,
        status="active",
        start_time=datetime.utcnow(),
    )
    db.add(db_exam)
    db.commit()
    db.refresh(db_exam)
    return db_exam


@router.get("", response_model=ExamList)
def get_exams(
    skip: int = 0,
    limit: int = 100,
    status: str = None,
    room_id: int = None,
    db: Session = Depends(get_db),
):
    query = select(Exam)
    if status:
        query = query.where(Exam.status == status)
    if room_id:
        query = query.where(Exam.room_id == room_id)
    query = query.order_by(Exam.created_at.desc())
    result = db.execute(query.offset(skip).limit(limit))
    exams = result.scalars().all()
    return {"exams": [e.to_dict() for e in exams], "total": len(exams)}


@router.get("/{exam_id}", response_model=ExamResponse)
def get_exam(exam_id: int, db: Session = Depends(get_db)):
    result = db.execute(select(Exam).where(Exam.id == exam_id))
    exam = result.scalars().first()
    if not exam:
        raise HTTPException(status_code=404, detail="Exam not found")
    return exam


@router.put("/{exam_id}/end", response_model=ExamResponse)
def end_exam(exam_id: int, body: ExamEnd = None, db: Session = Depends(get_db)):
    result = db.execute(select(Exam).where(Exam.id == exam_id))
    exam = result.scalars().first()
    if not exam:
        raise HTTPException(status_code=404, detail="Exam not found")
    exam.status = "completed"
    exam.end_time = body.end_time if body and body.end_time else datetime.utcnow()
    db.commit()
    db.refresh(exam)
    return exam


@router.delete("/{exam_id}")
def delete_exam(exam_id: int, db: Session = Depends(get_db)):
    result = db.execute(select(Exam).where(Exam.id == exam_id))
    exam = result.scalars().first()
    if not exam:
        raise HTTPException(status_code=404, detail="Exam not found")
    db.delete(exam)
    db.commit()
    return {"message": "Exam deleted successfully"}
