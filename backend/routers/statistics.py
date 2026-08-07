from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from database import get_db
from services.statistics_service import get_overall_statistics

router = APIRouter(prefix="/statistics", tags=["Statistics"])


@router.get("")
def get_statistics(db: Session = Depends(get_db)):
    return get_overall_statistics(db)
