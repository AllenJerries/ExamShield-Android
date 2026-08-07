package com.examshield.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SignalInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Understanding Signal & Distance")
        },
        text = {
            Column {
                Text(
                    "dBm = Signal Strength",
                    fontWeight = FontWeight.Bold
                )
                Text("Higher (closer to 0) = Stronger signal")
                Text("Lower (more negative) = Weaker signal")

                Spacer(Modifier.height(12.dp))

                Text(
                    "Signal to Distance Guide:",
                    fontWeight = FontWeight.Bold
                )
                Text("-30 dBm = Touching device")
                Text("-50 dBm = ~1-2 meters")
                Text("-70 dBm = ~5-10 meters")
                Text("-90 dBm = ~20+ meters")

                Spacer(Modifier.height(12.dp))

                Text(
                    "Note:",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Distance is estimated. Walls, people and objects can affect accuracy.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
