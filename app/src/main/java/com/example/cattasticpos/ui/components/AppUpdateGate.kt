package com.example.cattasticpos.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.cattasticpos.domain.model.UpdateInfo
import com.example.cattasticpos.service.ApkDownload
import com.example.cattasticpos.service.AppUpdateManager
import java.io.File

private enum class UpdatePhase { Downloading, NeedsPermission, Failed, Rejected, Launched }

/**
 * Fully automatic self-update. On launch it checks the cloud for a newer build and, if one
 * exists, downloads it and hands it to the system installer — no "Update now" tap required.
 */
@Composable
fun AppUpdateGate() {
    val context = LocalContext.current
    val manager = remember { AppUpdateManager(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var phase by remember { mutableStateOf(UpdatePhase.Downloading) }
    var progress by remember { mutableStateOf(0f) }
    var dismissed by remember { mutableStateOf(false) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    var downloadKey by remember { mutableStateOf(0) }

    // Check once for an update.
    LaunchedEffect(Unit) { info = manager.checkForUpdate() }

    // Download when an update is found (or on explicit retry). Never re-downloads a file we already have.
    LaunchedEffect(info, downloadKey) {
        val u = info ?: return@LaunchedEffect
        if (downloadedFile != null) return@LaunchedEffect
        phase = UpdatePhase.Downloading
        progress = 0f
        val file = when (val result = manager.downloadApk(u) { progress = it }) {
            is ApkDownload.Ok -> result.file
            ApkDownload.Failed -> { phase = UpdatePhase.Failed; return@LaunchedEffect }
            ApkDownload.Rejected -> { phase = UpdatePhase.Rejected; return@LaunchedEffect }
        }
        downloadedFile = file
        phase = if (manager.installApk(file)) UpdatePhase.Launched else UpdatePhase.NeedsPermission
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val file = downloadedFile
            if (event == Lifecycle.Event.ON_RESUME && phase == UpdatePhase.NeedsPermission && file != null) {
                if (manager.installApk(file)) phase = UpdatePhase.Launched
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val u = info
    if (u == null || dismissed || phase == UpdatePhase.Launched) return

    AlertDialog(
        onDismissRequest = { dismissed = true },
        title = {
            Text(
                when (phase) {
                    UpdatePhase.NeedsPermission -> "Allow updates"
                    UpdatePhase.Failed, UpdatePhase.Rejected -> "Update failed"
                    else -> "Updating app"
                }
            )
        },
        text = {
            Column {
                when (phase) {
                    UpdatePhase.Downloading -> {
                        Text(
                            if (u.versionName.isNotBlank())
                                "Downloading version ${u.versionName}…"
                            else
                                "Downloading the latest version…"
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            Text(
                                "${(progress * 100).toInt()}%",
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "On slow data this can take a few minutes. Tap Hide to keep taking orders — we'll install it once it's ready.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    UpdatePhase.NeedsPermission -> Text(
                        "Allow “Install unknown apps” for Brew ni Cat in the screen that opened, then come " +
                            "back — it installs by itself (already downloaded). You can also tap Install."
                    )
                    UpdatePhase.Failed -> Text(
                        "Couldn’t download the update. Check the internet connection and try again."
                    )
                    UpdatePhase.Rejected -> Text(
                        "The downloaded update didn’t match the published version, so it wasn’t installed. " +
                            "Try again later, and tell the developer if this keeps happening."
                    )
                    else -> Text("Preparing update…")
                }
            }
        },
        confirmButton = {
            when (phase) {
                UpdatePhase.NeedsPermission -> TextButton(onClick = {
                    val f = downloadedFile
                    if (f != null && manager.installApk(f)) phase = UpdatePhase.Launched
                }) { Text("Install") }
                UpdatePhase.Failed, UpdatePhase.Rejected -> TextButton(onClick = {
                    downloadedFile = null
                    downloadKey++
                }) { Text("Retry") }
                else -> {}
            }
        },
        dismissButton = {
            when (phase) {
                UpdatePhase.Downloading -> TextButton(onClick = { dismissed = true }) { Text("Hide") }
                UpdatePhase.NeedsPermission, UpdatePhase.Failed, UpdatePhase.Rejected ->
                    TextButton(onClick = { dismissed = true }) { Text("Later") }
                else -> {}
            }
        }
    )
}
