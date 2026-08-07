MANUFACTURER_PREFIXES = {
    0x004C: "Apple",
    0x0075: "Samsung",
    0x0050: "LG",
    0x0006: "Motorola",
    0x00E0: "Nokia",
    0x001D: "Huawei",
    0x00A0: "Sony",
    0x0010: "Google",
    0x00F1: "Xiaomi",
    0x0042: "OnePlus",
    0x00B0: "Oppo",
    0x00D0: "Vivo",
    0x00C0: "Realme",
    0x00E2: "Poco",
    0x001E: "JBL",
    0x0020: "Bose",
    0x0030: "Sennheiser",
    0x00F0: "Fitbit",
    0x0012: "Garmin",
}


def resolve_manufacturer_from_company_id(company_id: int) -> str:
    if company_id in MANUFACTURER_PREFIXES:
        return MANUFACTURER_PREFIXES[company_id]
    return "Unknown"


def resolve_manufacturer_from_name(device_name: str) -> str:
    if not device_name:
        return "Unknown"
    name_lower = device_name.lower()
    for mfr in ["samsung", "apple", "xiaomi", "oneplus", "oppo", "vivo",
                 "huawei", "google", "motorola", "nokia", "lg", "sony",
                 "realme", "poco", "infinix", "tecno", "jbl", "bose",
                 "sennheiser", "fitbit", "garmin", "boAt", "noise"]:
        if mfr in name_lower:
            return mfr.capitalize()
    return "Unknown"
