package com.examshield.ui.navigation

import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.examshield.ui.screens.*
import java.net.URLDecoder
import java.net.URLEncoder

private const val TAG = "ExamShield_Nav"

object Routes {
    const val HOME = "home"
    const val SETUP = "setup"
    const val BASELINE = "baseline/{examId}"
    const val EXAM_ACTIVE = "exam_active/{examId}"
    const val PROXIMITY_HUNT = "proximity_hunt?mac={mac}&name={name}&type={type}&source={source}"
    const val INCIDENT_HISTORY = "incident_history"
    const val SETTINGS = "settings"

    fun baseline(examId: Long) = "baseline/$examId"
    fun examActive(examId: Long) = "exam_active/$examId"

    fun proximityHunt(mac: String, name: String, type: String = "UNKNOWN", source: String = "BLUETOOTH"): String {
        val encodedMac = mac.replace(":", "-")
        val encodedName = URLEncoder.encode(name.ifEmpty { "Unknown Device" }, "UTF-8")
        val encodedType = URLEncoder.encode(type, "UTF-8")
        val encodedSource = URLEncoder.encode(source, "UTF-8")
        Log.d(TAG, "Navigating to hunter: mac=$mac source=$source | name=$name | type=$type")
        return "proximity_hunt?mac=$encodedMac&name=$encodedName&type=$encodedType&source=$encodedSource"
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onStartExam = { navController.navigate(Routes.SETUP) },
                onViewHistory = { navController.navigate(Routes.INCIDENT_HISTORY) },
                onSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.SETUP) {
            SetupScreen(
                onExamCreated = { examId ->
                    navController.navigate(Routes.baseline(examId)) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.BASELINE,
            arguments = listOf(navArgument("examId") { type = NavType.LongType })
        ) { backStackEntry ->
            val examId = backStackEntry.arguments?.getLong("examId") ?: return@composable
            BaselineScanScreen(
                examId = examId,
                onStartExam = { id ->
                    navController.navigate(Routes.examActive(id)) {
                        popUpTo(Routes.HOME)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.EXAM_ACTIVE,
            arguments = listOf(navArgument("examId") { type = NavType.LongType })
        ) { backStackEntry ->
            val examId = backStackEntry.arguments?.getLong("examId") ?: return@composable
            ExamActiveScreen(
                examId = examId,
                onHuntDevice = { mac, name, type ->
                    Log.d(TAG, "onHuntDevice callback: mac=$mac name=$name type=$type")
                    val source = when {
                        type.contains("HOTSPOT") -> "WIFI_HOTSPOT"
                        type.contains("WIFI") -> "WIFI_NETWORK"
                        else -> "BLUETOOTH"
                    }
                    navController.navigate(Routes.proximityHunt(mac, name, type, source))
                },
                onEndExam = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.PROXIMITY_HUNT,
            arguments = listOf(
                navArgument("mac") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("name") {
                    type = NavType.StringType
                    defaultValue = "Unknown"
                },
                navArgument("type") {
                    type = NavType.StringType
                    defaultValue = "UNKNOWN"
                },
                navArgument("source") {
                    type = NavType.StringType
                    defaultValue = "BLUETOOTH"
                }
            )
        ) { backStackEntry ->
            val macEncoded = backStackEntry.arguments?.getString("mac") ?: ""
            val nameEncoded = backStackEntry.arguments?.getString("name") ?: "Unknown"
            val typeEncoded = backStackEntry.arguments?.getString("type") ?: "UNKNOWN"
            val sourceEncoded = backStackEntry.arguments?.getString("source") ?: "BLUETOOTH"

            val mac = macEncoded.replace("-", ":")
            val name = try {
                URLDecoder.decode(nameEncoded, "UTF-8")
            } catch (_: Exception) {
                nameEncoded
            }
            val type = try {
                URLDecoder.decode(typeEncoded, "UTF-8")
            } catch (_: Exception) {
                typeEncoded
            }
            val source = try {
                URLDecoder.decode(sourceEncoded, "UTF-8")
            } catch (_: Exception) {
                sourceEncoded
            }

            Log.d(TAG, "Hunter screen: mac=$mac name=$name type=$type source=$source")

            ProximityHunterScreen(
                navController = navController,
                targetMacAddress = mac,
                targetDeviceName = name,
                targetDeviceType = type,
                targetSource = source
            )
        }

        composable(Routes.INCIDENT_HISTORY) {
            HistoryScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel(),
                onBack = { navController.popBackStack() }
            )
        }
    }
}
