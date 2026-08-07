package com.examshield.scanner

import com.examshield.data.models.DeviceType
import com.examshield.data.models.RiskLevel

data class ClassificationResult(
    val deviceType: DeviceType,
    val riskLevel: RiskLevel,
    val shouldShow: Boolean,
    val isRelevant: Boolean,
    val description: String
)
