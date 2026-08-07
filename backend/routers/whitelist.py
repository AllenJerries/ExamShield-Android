from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from sqlalchemy import select
from database import get_db
from models.device import WhitelistDevice
from models.room import Room
from schemas.device import WhitelistDeviceCreate, WhitelistDeviceResponse, WhitelistList

router = APIRouter(prefix="/whitelist", tags=["Whitelist"])


@router.post("", response_model=WhitelistDeviceResponse)
def add_to_whitelist(device: WhitelistDeviceCreate, db: Session = Depends(get_db)):
    result = db.execute(select(Room).where(Room.id == device.room_id))
    room = result.scalars().first()
    if not room:
        raise HTTPException(status_code=404, detail="Room not found")
    existing = db.execute(
        select(WhitelistDevice).where(
            WhitelistDevice.room_id == device.room_id,
            WhitelistDevice.mac_hash == device.mac_hash,
        )
    ).scalars().first()
    if existing:
        raise HTTPException(status_code=400, detail="Device already in whitelist for this room")
    db_device = WhitelistDevice(
        room_id=device.room_id,
        mac_hash=device.mac_hash,
        device_name=device.device_name,
        device_type=device.device_type,
    )
    db.add(db_device)
    db.commit()
    db.refresh(db_device)
    return db_device


@router.get("/{room_id}", response_model=WhitelistList)
def get_whitelist(room_id: int, db: Session = Depends(get_db)):
    result = db.execute(
        select(WhitelistDevice).where(WhitelistDevice.room_id == room_id)
    )
    devices = result.scalars().all()
    return {"devices": [d.to_dict() for d in devices], "total": len(devices)}


@router.delete("/{device_id}")
def remove_from_whitelist(device_id: int, db: Session = Depends(get_db)):
    result = db.execute(select(WhitelistDevice).where(WhitelistDevice.id == device_id))
    device = result.scalars().first()
    if not device:
        raise HTTPException(status_code=404, detail="Whitelist device not found")
    db.delete(device)
    db.commit()
    return {"message": "Device removed from whitelist"}


@router.post("/bulk", response_model=WhitelistList)
def bulk_add_to_whitelist(devices: list[WhitelistDeviceCreate], db: Session = Depends(get_db)):
    created = []
    for d in devices:
        existing = db.execute(
            select(WhitelistDevice).where(
                WhitelistDevice.room_id == d.room_id,
                WhitelistDevice.mac_hash == d.mac_hash,
            )
        ).scalars().first()
        if not existing:
            db_device = WhitelistDevice(
                room_id=d.room_id,
                mac_hash=d.mac_hash,
                device_name=d.device_name,
                device_type=d.device_type,
            )
            db.add(db_device)
            created.append(db_device)
    db.commit()
    for c in created:
        db.refresh(c)
    return {"devices": [d.to_dict() for d in created], "total": len(created)}
