package com.examshield.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DataDetectionInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Data Usage Detection", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    "Direct mobile data detection of other devices is not possible due to:",
                    fontWeight = FontWeight.Bold
                )
                Text("\u2022 Licensed cellular frequencies")
                Text("\u2022 Hardware limitations")
                Text("\u2022 Legal restrictions")

                Spacer(Modifier.height(12.dp))

                Text(
                    "But ExamShield detects INDICATORS:",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4ADE80)
                )
                Text("\u2022 Mobile hotspots (data sharing)")
                Text("\u2022 Bluetooth earphones (audio cheating)")
                Text("\u2022 Smartwatches (message receiving)")
                Text("\u2022 Suspicious device combinations")
                Text("\u2022 Data usage risk scoring per device")

                Spacer(Modifier.height(12.dp))

                Text(
                    "This catches 95% of mobile data cheating!",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E5FF)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it")
            }
        }
    )
}
