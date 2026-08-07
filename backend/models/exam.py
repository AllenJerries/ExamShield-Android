from sqlalchemy import Column, Integer, String, DateTime, Date, ForeignKey, func
from sqlalchemy.orm import relationship
from database import Base


class Exam(Base):
    __tablename__ = "exams"

    id = Column(Integer, primary_key=True, index=True)
    room_id = Column(Integer, ForeignKey("rooms.id", ondelete="CASCADE"), nullable=False)
    exam_name = Column(String(200), nullable=False)
    exam_date = Column(Date, nullable=False)
    start_time = Column(DateTime)
    end_time = Column(DateTime)
    invigilator_name = Column(String(100))
    status = Column(String(20), default="active")
    created_at = Column(DateTime, server_default=func.now())

    room = relationship("Room", backref="exams")

    def to_dict(self):
        return {
            "id": self.id,
            "room_id": self.room_id,
            "exam_name": self.exam_name,
            "exam_date": self.exam_date.isoformat() if self.exam_date else None,
            "start_time": self.start_time.isoformat() if self.start_time else None,
            "end_time": self.end_time.isoformat() if self.end_time else None,
            "invigilator_name": self.invigilator_name,
            "status": self.status,
            "created_at": self.created_at.isoformat() if self.created_at else None,
        }
