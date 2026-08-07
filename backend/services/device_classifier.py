import re

HIGH_RISK_KEYWORDS = [
    "earphone", "buds", "airpod", "headphone", "tws", "pods",
    "watch", "band", "gear", "fit", "smartwatch", "fitness tracker",
    "bluetooth", "wireless",
]

HIGH_RISK_PHONE_MANUFACTURERS = [
    "samsung", "apple", "xiaomi", "oneplus", "oppo", "vivo",
    "huawei", "google", "motorola", "nokia", "lg", "sony",
    "realme", "poco", "infinix", "tecno",
]


def classify_device(device_name: str, manufacturer: str, rssi: int) -> dict:
    name_lower = (device_name or "").lower()
    manufacturer_lower = (manufacturer or "").lower()

    high_risk_reasons = []
    medium_risk_reasons = []

    for keyword in HIGH_RISK_KEYWORDS:
        if keyword in name_lower:
            high_risk_reasons.append(f"Name contains '{keyword}'")

    for mfr in HIGH_RISK_PHONE_MANUFACTURERS:
        if mfr in manufacturer_lower:
            high_risk_reasons.append(f"Known phone manufacturer '{mfr}'")

    if not device_name or device_name.strip() == "":
        medium_risk_reasons.append("Unknown device name")

    if manufacturer and manufacturer_lower not in ["unknown", "", " "] and not high_risk_reasons:
        if manufacturer_lower not in [m.lower() for m in HIGH_RISK_PHONE_MANUFACTURERS]:
            medium_risk_reasons.append(f"Unrecognized manufacturer '{manufacturer}'")

    if high_risk_reasons:
        risk_level = "high"
        description = "; ".join(high_risk_reasons)
    elif medium_risk_reasons:
        risk_level = "medium"
        description = "; ".join(medium_risk_reasons)
    else:
        risk_level = "low"
        description = "Known authorized equipment"

    return {
        "level": risk_level,
        "type": _determine_device_type(device_name, manufacturer),
        "description": description,
    }


def _determine_device_type(device_name: str, manufacturer: str) -> str:
    name_lower = (device_name or "").lower()

    earphone_keywords = ["earphone", "buds", "airpod", "headphone", "tws", "pods", "earbud"]
    watch_keywords = ["watch", "band", "gear", "fit", "smartwatch"]
    phone_keywords = ["phone", "mobile", "smartphone", "iphone"]

    for kw in earphone_keywords:
        if kw in name_lower:
            return "earphone"
    for kw in watch_keywords:
        if kw in name_lower:
            return "watch"
    for kw in phone_keywords:
        if kw in name_lower:
            return "phone"

    if manufacturer and manufacturer.lower() in [m.lower() for m in HIGH_RISK_PHONE_MANUFACTURERS]:
        return "phone"

    if not device_name or device_name.strip() == "":
        return "unknown"

    return "other"
