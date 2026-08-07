from sqlalchemy import Column, Integer, String, Boolean, DateTime, ForeignKey, func
from sqlalchemy.orm import relationship
from database import Base


class WhitelistDevice(Base):
    __tablename__ = "whitelist_devices"

    id = Column(Integer, primary_key=True, index=True)
    room_id = Column(Integer, ForeignKey("rooms.id", ondelete="CASCADE"), nullable=False)
    mac_hash = Column(String(64), nullable=False)
    device_name = Column(String(100))
    device_type = Column(String(50), default="unknown")
    added_at = Column(DateTime, server_default=func.now())

    room = relationship("Room", backref="whitelist_devices")

    def to_dict(self):
        return {
            "id": self.id,
            "room_id": self.room_id,
            "mac_hash": self.mac_hash,
            "device_name": self.device_name,
            "device_type": self.device_type,
            "added_at": self.added_at.isoformat() if self.added_at else None,
        }


class DetectedDevice(Base):
    __tablename__ = "detected_devices"

    id = Column(Integer, primary_key=True, index=True)
    exam_id = Column(Integer, ForeignKey("exams.id", ondelete="CASCADE"), nullable=False)
    mac_hash = Column(String(64), nullable=False)
    device_name = Column(String(100))
    device_type = Column(String(50), default="unknown")
    risk_level = Column(String(20))
    rssi = Column(Integer)
    detected_at = Column(DateTime, server_default=func.now())
    action_taken = Column(String(50), default="pending")
    confirmed_cheating = Column(Boolean, default=False)

    exam = relationship("Exam", backref="detected_devices")

    def to_dict(self):
        return {
            "id": self.id,
            "exam_id": self.exam_id,
            "mac_hash": self.mac_hash,
            "device_name": self.device_name,
            "device_type": self.device_type,
            "risk_level": self.risk_level,
            "rssi": self.rssi,
            "detected_at": self.detected_at.isoformat() if self.detected_at else None,
            "action_taken": self.action_taken,
            "confirmed_cheating": self.confirmed_cheating,
        }
