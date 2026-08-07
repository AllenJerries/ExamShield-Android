from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from database import engine, Base
from routers import (
    rooms, exams, detections,
    incidents, dashboard, reports
)

app = FastAPI(
    title="ExamShield API",
    description="Proximity-Based Wireless Device Detection System",
    version="1.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

Base.metadata.create_all(bind=engine)

app.include_router(rooms.router, prefix="/api")
app.include_router(exams.router, prefix="/api")
app.include_router(detections.router, prefix="/api")
app.include_router(incidents.router, prefix="/api")
app.include_router(dashboard.router, prefix="/api")
app.include_router(reports.router, prefix="/api")

@app.get("/")
def root():
    return {
        "message": "ExamShield API Running",
        "status": "active",
        "version": "1.0.0"
    }

@app.get("/health")
def health_check():
    return {"status": "healthy"}
