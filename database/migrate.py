"""
Database migration helper for ExamShield.
Run this script to initialize or migrate the database schema.
"""

import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'backend'))

from database import engine, Base, init_db
from models import Room, Exam, WhitelistDevice, DetectedDevice, Incident


def run_migration():
    print("ExamShield Database Migration")
    print("=" * 40)
    print(f"Database URL: {engine.url}")
    print(f"Tables to create: {len(Base.metadata.tables)}")
    print()
    init_db()
    print("Migration completed successfully!")
    print(f"Created tables: {', '.join(Base.metadata.tables.keys())}")


if __name__ == "__main__":
    run_migration()
