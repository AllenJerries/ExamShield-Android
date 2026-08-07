from sqlalchemy import Column, Integer, String, DateTime, func
from database import Base


class Room(Base):
    __tablename__ = "rooms"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String(100), nullable=False)
    room_number = Column(String(20), nullable=False)
    institution = Column(String(200), nullable=False)
    created_at = Column(DateTime, server_default=func.now())

    def to_dict(self):
        return {
            "id": self.id,
            "name": self.name,
            "room_number": self.room_number,
            "institution": self.institution,
            "created_at": self.created_at.isoformat() if self.created_at else None,
        }
