package com.examshield.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examshield.utils.PermissionHelper
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionRequestDialog(
    onDismiss: () -> Unit,
    onPermissionsGranted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val permissionsState = rememberMultiplePermissionsState(
        permissions = PermissionHelper.getRequiredPermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            onPermissionsGranted()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "Permissions Required",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Text(
                text = "ExamShield needs Bluetooth, Location, and Notification permissions to scan for nearby devices during exams.",
                fontSize = 14.sp
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    permissionsState.launchMultiplePermissionRequest()
                }
            ) {
                Text("Grant Permissions")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
