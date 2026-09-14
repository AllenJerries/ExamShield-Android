package com.examshield.scanner

import android.content.Context

object ScannerProvider {
    @Volatile
    private var instance: UnifiedScanner? = null

    fun get(context: Context): UnifiedScanner {
        return instance ?: synchronized(this) {
            instance ?: UnifiedScanner(context.applicationContext).also { instance = it }
        }
    }
}