package com.example.cattasticpos.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cattasticpos.domain.model.ShopClosure
import com.example.cattasticpos.domain.model.ShopOpenState
import com.example.cattasticpos.domain.model.openState
import com.example.cattasticpos.ui.adaptive.LocalCupertinoColors
import kotlinx.coroutines.delay
import java.time.Instant

/** Label and colors for each state; kept apart from the UI so tests can check the words. */
internal fun shopStateLabel(state: ShopOpenState): String = when (state) {
    ShopOpenState.OPEN -> "Open"
    ShopOpenState.CLOSED_TODAY -> "Closed today"
    ShopOpenState.OUTSIDE_HOURS -> "Outside hours"
}

/**
 * The shop open/closed switch in the dashboard top bar (Connect FR-111, FR-112). The shop is
 * open by default and follows the opening hours; tapping it closes the shop for today, or opens
 * it again. Hidden until the server answers.
 */
@Composable
fun ShopStatusPill(
    closure: ShopClosure?,
    saving: Boolean,
    onSetClosed: (closed: Boolean, note: String) -> Unit
) {
    if (closure == null) return
    // Re-check every minute so the pill changes at opening and closing time.
    val now by produceState(Instant.now()) {
        while (true) {
            delay(60_000)
            value = Instant.now()
        }
    }
    val state = closure.openState(now)
    var asking by remember { mutableStateOf(false) }
    val cupertino = LocalCupertinoColors.current
    val (background, content) = when (state) {
        ShopOpenState.OPEN -> Color(0xFF34C759) to Color.White
        ShopOpenState.CLOSED_TODAY -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
        ShopOpenState.OUTSIDE_HOURS -> cupertino.fill to cupertino.label
    }
    Surface(
        onClick = { if (!saving) asking = true },
        shape = RoundedCornerShape(50),
        color = background,
        contentColor = content,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .semantics { contentDescription = "Shop status: ${shopStateLabel(state)}. Tap to change." }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (saving) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = content)
                Spacer(Modifier.width(6.dp))
            }
            Text(shopStateLabel(state), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }

    if (asking) {
        if (state == ShopOpenState.CLOSED_TODAY) {
            AlertDialog(
                onDismissRequest = { asking = false },
                title = { Text("Open the shop again?") },
                text = { Text("The website and Messenger will take online orders again during the opening hours.") },
                confirmButton = {
                    TextButton(onClick = { asking = false; onSetClosed(false, "") }) { Text("Open the shop") }
                },
                dismissButton = { TextButton(onClick = { asking = false }) { Text("Cancel") } }
            )
        } else {
            ShopClosedDialog(
                outsideHours = state == ShopOpenState.OUTSIDE_HOURS,
                onDismiss = { asking = false },
                onConfirm = { note -> asking = false; onSetClosed(true, note) }
            )
        }
    }
}

@Composable
private fun ShopClosedDialog(outsideHours: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Close the shop for today?") },
        text = {
            Column {
                Text(
                    if (outsideHours) {
                        "The shop is already outside its opening hours. Closing it keeps the website and Messenger closed for the rest of today. It opens again by itself tomorrow."
                    } else {
                        "The website and Messenger will say the shop is closed and won't take online orders today. It opens again by itself tomorrow."
                    }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.replace(Regex("[\\r\\n]+"), " ").take(ShopClosure.MAX_NOTE) },
                    label = { Text("Note for customers (optional)") },
                    placeholder = { Text("e.g. Back tomorrow at 1 PM") },
                    singleLine = true
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(note) }) { Text("Close today") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
