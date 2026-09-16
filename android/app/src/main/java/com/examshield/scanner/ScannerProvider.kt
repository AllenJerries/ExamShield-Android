package com.examshield.scanner

import android.content.Context

object ScannerProvider {

    @Volatile
    private var instance: UnifiedScanner? = null

    fun get(context: Context): UnifiedScanner {
        val current = instance
        if (current != null) return current
        return synchronized(this) {
            instance ?: UnifiedScanner(context.applicationContext).also { instance = it }
        }
    }
}