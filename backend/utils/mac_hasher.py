import hashlib


def hash_mac_address(mac_address: str) -> str:
    if not mac_address:
        return ""
    clean_mac = mac_address.replace(":", "").replace("-", "").replace(".", "").upper()
    return hashlib.sha256(clean_mac.encode()).hexdigest()
