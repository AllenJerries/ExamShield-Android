package com.examshield.scanner

import android.util.Log
import com.examshield.data.models.DeviceRisk
import com.examshield.data.models.DeviceType
import com.examshield.data.models.RiskLevel

object DeviceClassifier {

    private const val TAG = "DeviceClassifier"

    private val IGNORED_KEYWORDS = listOf(
        "laptop", "desktop", "printer", "scanner", "projector",
        "speaker", "soundbar", "tv", "television", "monitor",
        "router", "modem", "switch", "hub", "dongle",
        "keyboard", "mouse", "trackpad", "stylus",
        "camera", "webcam", "dashcam", "gopro",
        "car", "vehicle", "obd", "obdii",
        "refrigerator", "washing machine", "ac", "air conditioner",
        "microwave", "oven", "blender", "fan", "light", "bulb",
        "thermostat", "sensor", "beacon", "tile", "airtag",
        "kindle", "tablet", "ipad", "surface",
        "usb", "charger", "power bank", "battery",
        "headset", "headphone", "speaker", "earphone"
    )

    fun classifyDeviceStrict(
        deviceName: String,
        macAddress: String,
        rssi: Int,
        scanRecord: ByteArray? = null,
        isFromWifi: Boolean = false
    ): ClassificationResult {
        val nameLower = deviceName.lowercase().trim()
        val manufacturer = ManufacturerResolver.getManufacturer(macAddress)
        val manufacturerLower = manufacturer.lowercase()

        val bleManufacturer = scanRecord?.let { getManufacturerFromScanRecord(it) } ?: "Unknown"

        Log.d(TAG, "Classifying: '$deviceName' | RSSI: $rssi | WiFi: $isFromWifi | Mfr: $manufacturer")

        if (IGNORED_KEYWORDS.any { nameLower.contains(it) }) {
            Log.d(TAG, "-> IGNORED: $deviceName")
            return ClassificationResult(
                deviceType = DeviceType.UNKNOWN,
                riskLevel = RiskLevel.LOW,
                shouldShow = false,
                isRelevant = false,
                description = "Irrelevant device"
            )
        }

        if (nameLower.isEmpty() || nameLower == "unknown device") {
            if (rssi > -50) {
                Log.d(TAG, "-> HIDDEN_EARPIECE (very close): $macAddress")
                return ClassificationResult(
                    deviceType = DeviceType.HIDDEN_EARPIECE,
                    riskLevel = RiskLevel.CRITICAL,
                    shouldShow = true,
                    isRelevant = true,
                    description = "Suspicious hidden device — very close!"
                )
            }
            Log.d(TAG, "-> IGNORED (unknown, weak): $macAddress")
            return ClassificationResult(
                deviceType = DeviceType.UNKNOWN,
                riskLevel = RiskLevel.LOW,
                shouldShow = false,
                isRelevant = false,
                description = "Unknown device, weak signal"
            )
        }

        val hotspotKeywords = listOf(
            "hotspot", "androidap", "android_hotspot",
            "tethering", "mobile hotspot", "portable hotspot",
            "mywifi", "iphone", "galaxy", "redmi", "vivo",
            "oppo", "realme", "oneplus", "poco", "mi ",
            "samsung", "moto", "pixel", "nothing", "iqoo",
            "infinix", "tecno"
        )

        if (isFromWifi && hotspotKeywords.any { nameLower.contains(it) }) {
            Log.d(TAG, "-> MOBILE_HOTSPOT: $deviceName")
            return ClassificationResult(
                deviceType = DeviceType.MOBILE_HOTSPOT,
                riskLevel = RiskLevel.HIGH,
                shouldShow = true,
                isRelevant = true,
                description = "Mobile hotspot — student sharing internet"
            )
        }

        val earphoneKeywords = listOf(
            "airpod", "airpods", "buds", "earbud", "earbuds",
            "earphone", "tws", "pods", "airdopes", "rockerz",
            "nothing ear", "wf-", "wh-", "beats",
            "sony wf", "sony wh", "jbl tune", "jbl flip",
            "sennheiser", "bose", "jabra",
            "skullcandy", "marshall", "boat",
            "noise", "mivi", "portronics", "boult",
            "cmf pods", "realme buds", "oppo enco",
            "samsung buds", "galaxy buds", "galaxy bud"
        )

        if (earphoneKeywords.any { nameLower.contains(it) }) {
            Log.d(TAG, "-> EARPHONE: $deviceName")
            return ClassificationResult(
                deviceType = DeviceType.EARPHONE,
                riskLevel = RiskLevel.HIGH,
                shouldShow = true,
                isRelevant = true,
                description = "Wireless earphone — very suspicious"
            )
        }

        val smartwatchKeywords = listOf(
            "watch", "band", "gear", "fit",
            "bracelet", "wristband", "smart band",
            "mi band", "galaxy watch", "apple watch",
            "gtr", "gts", "bip", "amazfit",
            "gt2", "gt3", "colmi", "fire-boltt",
            "noise pulse", "boat wave", "boat storm",
            "realme watch", "oppo watch", "iqoo watch",
            "huawei watch", "tickwatch"
        )

        if (smartwatchKeywords.any { nameLower.contains(it) }) {
            Log.d(TAG, "-> SMARTWATCH: $deviceName")
            return ClassificationResult(
                deviceType = DeviceType.SMARTWATCH,
                riskLevel = RiskLevel.HIGH,
                shouldShow = true,
                isRelevant = true,
                description = "Smartwatch — can display messages"
            )
        }

        val iphoneKeywords = listOf("iphone", "ios", "apple mobile")
        if (iphoneKeywords.any { nameLower.contains(it) } ||
            (manufacturerLower.contains("apple") &&
                !nameLower.contains("watch") &&
                !nameLower.contains("airpod"))
        ) {
            Log.d(TAG, "-> PHONE_IOS: $deviceName")
            return ClassificationResult(
                deviceType = DeviceType.PHONE_IOS,
                riskLevel = RiskLevel.CRITICAL,
                shouldShow = true,
                isRelevant = true,
                description = "iPhone detected"
            )
        }

        val androidPhoneKeywords = listOf(
            "phone", "mobile", "galaxy", "redmi",
            "poco", "oneplus", "vivo", "oppo",
            "realme", "moto", "nokia", "nothing phone",
            "pixel", "iqoo", "infinix", "tecno",
            "samsung a", "samsung s", "samsung m",
            "galaxy a", "galaxy s", "galaxy m",
            "galaxy note", "mi 10", "mi 11",
            "mi 12", "mi 13", "mi 14", "redmi note"
        )

        val androidManufacturers = listOf(
            "samsung", "xiaomi", "vivo", "oppo",
            "oneplus", "realme", "google", "motorola",
            "nokia", "nothing", "poco", "iqoo",
            "infinix", "tecno"
        )

        val isAndroidPhone = androidPhoneKeywords.any { nameLower.contains(it) } ||
                androidManufacturers.any { manufacturerLower.contains(it) || nameLower.contains(it) }

        if (isAndroidPhone) {
            Log.d(TAG, "-> PHONE_ANDROID: $deviceName")
            return ClassificationResult(
                deviceType = DeviceType.PHONE_ANDROID,
                riskLevel = RiskLevel.HIGH,
                shouldShow = true,
                isRelevant = true,
                description = "Android phone detected"
            )
        }

        if (rssi > -50) {
            val effectiveMfr = if (manufacturer != "Unknown") manufacturer else bleManufacturer
            Log.d(TAG, "-> HIDDEN_EARPIECE (strong signal, named): $deviceName | $effectiveMfr")
            return ClassificationResult(
                deviceType = DeviceType.HIDDEN_EARPIECE,
                riskLevel = RiskLevel.CRITICAL,
                shouldShow = true,
                isRelevant = true,
                description = "Suspicious device — strong signal"
            )
        }

        Log.d(TAG, "-> IGNORED (unrecognized): $deviceName")
        return ClassificationResult(
            deviceType = DeviceType.UNKNOWN,
            riskLevel = RiskLevel.LOW,
            shouldShow = false,
            isRelevant = false,
            description = "Unrecognized device"
        )
    }

    fun classifyDevice(
        deviceName: String,
        macAddress: String,
        rssi: Int,
        scanRecord: ByteArray? = null,
        isFromWifi: Boolean = false
    ): Pair<DeviceType, DeviceRisk> {
        val result = classifyDeviceStrict(deviceName, macAddress, rssi, scanRecord, isFromWifi)
        return Pair(
            result.deviceType,
            DeviceRisk(
                level = result.riskLevel,
                description = result.description,
                icon = when (result.deviceType) {
                    DeviceType.EARPHONE, DeviceType.HIDDEN_EARPIECE -> "earphone"
                    DeviceType.SMARTWATCH -> "watch"
                    DeviceType.PHONE_IOS -> "phone_apple"
                    DeviceType.PHONE_ANDROID -> "phone_android"
                    DeviceType.MOBILE_HOTSPOT -> "hotspot"
                    DeviceType.WIFI_DEVICE -> "wifi"
                    else -> "bluetooth"
                }
            )
        )
    }

    fun resolveName(
        bluetoothName: String?,
        scanRecordName: String?,
        serviceName: String?,
        manufacturer: String?
    ): String {
        return bluetoothName?.takeIf { it.isNotBlank() }
            ?: scanRecordName?.takeIf { it.isNotBlank() }
            ?: serviceName?.takeIf { it.isNotBlank() }
            ?: manufacturer?.takeIf { it != "Unknown" }
            ?: "Unknown Device"
    }

    fun getRiskLevel(rssi: Int): RiskLevel {
        return when {
            rssi > -40 -> RiskLevel.CRITICAL
            rssi > -55 -> RiskLevel.HIGH
            rssi > -70 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
    }

    fun getManufacturerFromScanRecord(scanRecord: ByteArray?): String {
        if (scanRecord == null) return "Unknown"
        var i = 0
        while (i < scanRecord.size) {
            val length = scanRecord[i].toInt() and 0xff
            if (length == 0) break
            if (i + 1 >= scanRecord.size) break
            val type = scanRecord[i + 1].toInt() and 0xff
            if (type == 0xff && i + 3 < scanRecord.size) {
                val companyId =
                    (scanRecord[i + 3].toInt() and 0xff) shl 8 or
                            (scanRecord[i + 2].toInt() and 0xff)
                return getCompanyName(companyId)
            }
            i += length + 1
        }
        return "Unknown"
    }

    fun getCompanyName(id: Int): String {
        return when (id) {
            0x004C -> "Apple"
            0x0075 -> "Samsung"
            0x00E0, 0x0139 -> "Google"
            0x0087 -> "Garmin"
            0x038F, 0x017E -> "Xiaomi"
            0x02D5 -> "Realtek"
            0x0499 -> "Ruuvi"
            0x0157 -> "Anhui Huami"
            0x0006 -> "Microsoft"
            0x000F -> "Broadcom"
            0x004E -> "Sony"
            0x0030 -> "Mitel"
            0x0171, 0x01DA -> "Amazon"
            0x0310, 0x0102, 0x001D, 0x02FF -> "Qualcomm"
            0x0059, 0x0022 -> "Nordic Semiconductor"
            0x000D -> "Texas Instruments"
            0x0002, 0x0004, 0x0005 -> "Intel"
            0x0010 -> "Infineon"
            0x001E -> "Cambridge Silicon Radio"
            0x002C, 0x0046 -> "MediaTek"
            0x005A, 0x0076, 0x008C, 0x0094 -> "Motorola"
            0x002D, 0x003A -> "Anker"
            0x0042 -> "Belkin"
            0x004D -> "Vivo"
            0x005E -> "OPPO"
            0x0074 -> "Realme"
            0x0085 -> "Nothing"
            0x008F -> "OnePlus"
            0x00B0, 0x0153, 0x0173 -> "Huawei"
            else -> "Unknown"
        }
    }
}
