package com.examshield.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DeviceTypesGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Exam Cheating Devices")
                Text(
                    "Common devices used for malpractice",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        },
        text = {
            LazyColumn {
                item {
                    Text(
                        "CRITICAL RISK DEVICES:",
                        fontWeight = FontWeight.Bold,
                        color = Color.Red,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                item {
                    DeviceTypeGuideRow(
                        Icons.Default.PhoneAndroid,
                        "Android Phone",
                        "Most common cheating device",
                        Color.Red
                    )
                }
                item {
                    DeviceTypeGuideRow(
                        Icons.Default.PhoneIphone,
                        "iPhone",
                        "Hidden in pockets or bags",
                        Color.Red
                    )
                }
                item {
                    DeviceTypeGuideRow(
                        Icons.Default.Watch,
                        "Smartwatch",
                        "Can display messages secretly",
                        Color.Red
                    )
                }
                item {
                    DeviceTypeGuideRow(
                        Icons.Default.Headphones,
                        "Wireless Earphone",
                        "Very common - Airpods, TWS earbuds",
                        Color.Red
                    )
                }
                item {
                    DeviceTypeGuideRow(
                        Icons.Default.Hearing,
                        "Hidden Earpiece",
                        "Tiny devices hidden in ear canal",
                        Color.Red
                    )
                }
                item {
                    DeviceTypeGuideRow(
                        Icons.Default.WifiTethering,
                        "Mobile Hotspot",
                        "Student sharing internet from phone",
                        Color.Red
                    )
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "MEDIUM RISK:",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFA000),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                item {
                    DeviceTypeGuideRow(
                        Icons.Default.Wifi,
                        "WiFi Network",
                        "Nearby WiFi networks/routers",
                        Color(0xFFFFA000)
                    )
                }
                item {
                    DeviceTypeGuideRow(
                        Icons.Default.HelpOutline,
                        "Unknown Device",
                        "Unnamed device - investigate carefully",
                        Color(0xFFFFA000)
                    )
                }

                item {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Yellow.copy(alpha = 0.2f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Important:",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Nearby unknown devices with strong signal (-50 dBm or better) may be hidden cheating devices. Check students carefully!",
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it")
            }
        }
    )
}

@Composable
fun DeviceTypeGuideRow(
    icon: ImageVector,
    name: String,
    description: String,
    color: Color = Color(0xFF1976D2)
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(description, fontSize = 12.sp, color = Color.Gray)
        }
    }
}
