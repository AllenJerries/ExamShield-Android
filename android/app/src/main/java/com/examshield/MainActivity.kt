package com.examshield

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.examshield.ui.navigation.AppNavigation
import com.examshield.ui.theme.ExamShieldTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var permissionDialogVisible by mutableStateOf(false)
    private var servicesDialogVisible by mutableStateOf(false)
    private var missingServices by mutableStateOf<List<String>>(emptyList())
    private var checkKey by mutableStateOf(0)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Log.d(TAG, "All permissions granted")
            checkKey++
        } else {
            Log.e(TAG, "Some permissions denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ExamShieldTheme {
                LaunchedEffect(checkKey) {
                    val missingPerms = checkAllPermissions()
                    if (missingPerms.isNotEmpty()) {
                        permissionDialogVisible = true
                    } else {
                        val services = checkServicesEnabled()
                        missingServices = services
                        servicesDialogVisible = services.isNotEmpty()
                    }
                }

                if (permissionDialogVisible) {
                    PermissionDialog(
                        onGrant = {
                            permissionDialogVisible = false
                            requestAllPermissions()
                        },
                        onDismiss = {
                            permissionDialogVisible = false
                        }
                    )
                }

                if (servicesDialogVisible) {
                    ServicesDialog(
                        missingServices = missingServices,
                        onEnable = {
                            servicesDialogVisible = false
                            enableServices(missingServices)
                        },
                        onDismiss = {
                            servicesDialogVisible = false
                        }
                    )
                }

                AppNavigation()
            }
        }
    }

    private fun checkAllPermissions(): List<String> {
        val required = getRequiredPermissions()
        return required.filter { permission ->
            ActivityCompat.checkSelfPermission(this, permission) !=
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.VIBRATE,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return permissions
    }

    private fun requestAllPermissions() {
        val missing = checkAllPermissions()
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun checkServicesEnabled(): List<String> {
        val missing = mutableListOf<String>()

        // Check Bluetooth
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE)
            as? BluetoothManager
        val btAdapter = btManager?.adapter
        if (btAdapter == null || !btAdapter.isEnabled) {
            missing.add("Bluetooth")
        }

        // Check WiFi
        val wifiManager = applicationContext.getSystemService(
            Context.WIFI_SERVICE
        ) as? WifiManager
        if (wifiManager?.isWifiEnabled != true) {
            missing.add("WiFi")
        }

        // Check Location
        val locationManager = getSystemService(Context.LOCATION_SERVICE)
            as? LocationManager
        val gpsEnabled = locationManager?.isProviderEnabled(
            LocationManager.GPS_PROVIDER
        ) ?: false
        val networkEnabled = locationManager?.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        ) ?: false
        if (!gpsEnabled && !networkEnabled) {
            missing.add("Location")
        }

        return missing
    }

    private fun enableServices(services: List<String>) {
        services.forEach { service ->
            when (service) {
                "Bluetooth" -> openBluetoothSettings()
                "WiFi" -> openWiFiSettings()
                "Location" -> openLocationSettings()
            }
        }
    }

    private fun openBluetoothSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Open BT settings error", e)
        }
    }

    private fun openWiFiSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_WIFI_SETTINGS)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Open WiFi settings error", e)
        }
    }

    private fun openLocationSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Open location settings error", e)
        }
    }
}

@Composable
fun PermissionDialog(
    onGrant: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Security,
                null,
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                "Permissions Required",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    "ExamShield needs these permissions to detect wireless devices:",
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(12.dp))

                PermissionItem(
                    Icons.Default.Bluetooth,
                    "Bluetooth",
                    "To scan nearby Bluetooth devices"
                )
                PermissionItem(
                    Icons.Default.Wifi,
                    "WiFi",
                    "To detect WiFi networks and hotspots"
                )
                PermissionItem(
                    Icons.Default.LocationOn,
                    "Location",
                    "Required for BLE and WiFi scanning"
                )
                PermissionItem(
                    Icons.Default.Notifications,
                    "Notifications",
                    "For alert notifications"
                )
                PermissionItem(
                    Icons.Default.Vibration,
                    "Vibration",
                    "For alert vibrations"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5FF)
                )
            ) {
                Text("Grant Permissions", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later")
            }
        }
    )
}

@Composable
fun PermissionItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, null,
            tint = Color(0xFF00E5FF),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(description, fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
fun ServicesDialog(
    missingServices: List<String>,
    onEnable: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Warning,
                null,
                tint = Color(0xFFFFA726),
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                "Enable Services",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    "Please enable these services for full detection:",
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(12.dp))

                missingServices.forEach { service ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val icon = when (service) {
                            "Bluetooth" -> Icons.Default.Bluetooth
                            "WiFi" -> Icons.Default.Wifi
                            "Location" -> Icons.Default.LocationOn
                            else -> Icons.Default.Warning
                        }
                        Icon(
                            icon, null,
                            tint = Color(0xFFFFA726),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Turn ON $service",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    "Without these, detection won't work properly",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onEnable,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5FF)
                )
            ) {
                Text("Enable Now", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Skip")
            }
        }
    )
}
