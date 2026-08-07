from sqlalchemy import Column, Integer, String, Boolean, Text, DateTime, ForeignKey, func
from sqlalchemy.orm import relationship
from database import Base


class Incident(Base):
    __tablename__ = "incidents"

    id = Column(Integer, primary_key=True, index=True)
    exam_id = Column(Integer, ForeignKey("exams.id", ondelete="CASCADE"), nullable=False)
    device_id = Column(Integer, ForeignKey("detected_devices.id", ondelete="SET NULL"), nullable=True)
    severity = Column(String(20))
    description = Column(Text)
    evidence_notes = Column(Text)
    timestamp = Column(DateTime, server_default=func.now())
    resolved = Column(Boolean, default=False)

    exam = relationship("Exam", backref="incidents")
    device = relationship("DetectedDevice", backref="incidents")

    def to_dict(self):
        return {
            "id": self.id,
            "exam_id": self.exam_id,
            "device_id": self.device_id,
            "severity": self.severity,
            "description": self.description,
            "evidence_notes": self.evidence_notes,
            "timestamp": self.timestamp.isoformat() if self.timestamp else None,
            "resolved": self.resolved,
        }
