from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session
import os
import tempfile
from database import get_db
from services.report_generator import generate_pdf_report

router = APIRouter(prefix="/reports", tags=["Reports"])


@router.post("/generate/{exam_id}")
def generate_report(exam_id: int, db: Session = Depends(get_db)):
    pdf_path = generate_pdf_report(exam_id, db)
    if not pdf_path:
        raise HTTPException(status_code=404, detail="Exam not found or no data to report")
    return FileResponse(
        pdf_path,
        media_type="application/pdf",
        filename=f"exam_report_{exam_id}.pdf",
        headers={"Content-Disposition": f"attachment; filename=exam_report_{exam_id}.pdf"},
    )
