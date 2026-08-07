package com.examshield.utils

import android.content.Context
import android.util.Log
import android.widget.Toast

object ErrorHandler {

    private const val TAG = "ErrorHandler"

    fun handle(
        context: Context,
        error: Throwable,
        userMessage: String,
        showToast: Boolean = false
    ) {
        Log.e(TAG, userMessage, error)
        if (showToast) {
            safeExecute("Show toast") {
                Toast.makeText(context, userMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun tryOrLog(
        operation: String,
        block: () -> Unit
    ) {
        try {
            block()
        } catch (e: SecurityException) {
            Log.e(TAG, "Security: $operation", e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "State: $operation", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed: $operation", e)
        }
    }

    suspend fun <T> tryOrDefault(
        default: T,
        block: suspend () -> T
    ): T {
        return try {
            block()
        } catch (e: SecurityException) {
            Log.e(TAG, "Security error, using default", e)
            default
        } catch (e: Exception) {
            Log.e(TAG, "Operation failed, using default", e)
            default
        }
    }

    fun safeExecute(operation: String, block: () -> Unit) {
        try {
            block()
        } catch (e: SecurityException) {
            Log.e(TAG, "Security: $operation", e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "State: $operation", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed: $operation", e)
        }
    }

    suspend fun <T> safeSuspend(default: T, block: suspend () -> T): T {
        return try {
            block()
        } catch (e: SecurityException) {
            Log.e(TAG, "Security suspend", e)
            default
        } catch (e: Exception) {
            Log.e(TAG, "Failed suspend", e)
            default
        }
    }

}
