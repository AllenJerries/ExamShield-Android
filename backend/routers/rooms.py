from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from sqlalchemy import select
from database import get_db
from models.room import Room
from schemas.room import RoomCreate, RoomResponse, RoomList

router = APIRouter(prefix="/rooms", tags=["Rooms"])


@router.post("", response_model=RoomResponse)
def create_room(room: RoomCreate, db: Session = Depends(get_db)):
    db_room = Room(name=room.name, room_number=room.room_number, institution=room.institution)
    db.add(db_room)
    db.commit()
    db.refresh(db_room)
    return db_room


@router.get("", response_model=RoomList)
def get_rooms(skip: int = 0, limit: int = 100, db: Session = Depends(get_db)):
    result = db.execute(select(Room).offset(skip).limit(limit))
    rooms = result.scalars().all()
    return {"rooms": [r.to_dict() for r in rooms], "total": len(rooms)}


@router.get("/{room_id}", response_model=RoomResponse)
def get_room(room_id: int, db: Session = Depends(get_db)):
    result = db.execute(select(Room).where(Room.id == room_id))
    room = result.scalars().first()
    if not room:
        raise HTTPException(status_code=404, detail="Room not found")
    return room


@router.delete("/{room_id}")
def delete_room(room_id: int, db: Session = Depends(get_db)):
    result = db.execute(select(Room).where(Room.id == room_id))
    room = result.scalars().first()
    if not room:
        raise HTTPException(status_code=404, detail="Room not found")
    db.delete(room)
    db.commit()
    return {"message": "Room deleted successfully"}
