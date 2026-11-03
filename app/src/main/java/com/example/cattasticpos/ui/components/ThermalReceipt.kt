package com.example.cattasticpos.ui.components

import com.example.cattasticpos.domain.model.SplitPayment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.example.cattasticpos.ui.icons.FluentIcon
import com.example.cattasticpos.ui.icons.FluentIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cattasticpos.domain.model.Order
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val RECEIPT_DIVIDER = "----------------------------------------"
private const val STORE_NAME = "Brew ni Cat"

fun formatReceiptShareText(order: Order): String {
    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(order.timestamp))
    val lines = buildList {
        add(STORE_NAME)
        add("Receipt")
        add(RECEIPT_DIVIDER)
        add("Order #${order.receiptNumber}")
        add("Date: $dateStr")
        add("Payment: ${SplitPayment.describe(order.paymentMethod, order.paymentReference)}")
        if (!order.tableLabel.isNullOrBlank()) {
            add("Name: ${order.tableLabel}")
        }
        add(RECEIPT_DIVIDER)
        order.items.forEach { item ->
            val left = "${item.quantity}x ${item.itemName} (${item.variantName})"
            val right = "PHP ${String.format(Locale.US, "%.0f", item.totalPrice)}"
            add(formatReceiptLine(left, right, 40))
        }
        add(RECEIPT_DIVIDER)
        add(formatReceiptLine("Subtotal", "PHP ${String.format(Locale.US, "%.0f", order.subtotal)}", 40))
        add(formatReceiptLine("Discount", "PHP ${String.format(Locale.US, "%.0f", order.discountDeduction)}", 40))
        add(formatReceiptLine("TOTAL", "PHP ${String.format(Locale.US, "%.0f", order.total)}", 40))
        add(RECEIPT_DIVIDER)
        add("Thank you for your purchase!")
    }
    return lines.joinToString("\n")
}

private fun formatReceiptLine(left: String, right: String, width: Int): String {
    val trimmedLeft = if (left.length > width - right.length - 1) {
        left.take(width - right.length - 4) + "..."
    } else {
        left
    }
    val spaces = (width - trimmedLeft.length - right.length).coerceAtLeast(1)
    return trimmedLeft + " ".repeat(spaces) + right
}

@Composable
fun ThermalReceiptCard(
    order: Order,
    modifier: Modifier = Modifier
) {
    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(order.timestamp))
    val mono = FontFamily.Monospace
    val ink = Color(0xFF1A1A1A)
    val paper = Color(0xFFF8F4EC)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(paper, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = STORE_NAME,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontFamily = mono,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = ink
        )
        Text(
            text = "Receipt",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontFamily = mono,
            fontSize = 12.sp,
            color = ink.copy(alpha = 0.75f)
        )
        if (!order.tableLabel.isNullOrBlank()) {
            Text(
                text = order.tableLabel!!,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                textAlign = TextAlign.Center,
                fontFamily = mono,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = ink
            )
        }
        ReceiptDivider(mono, ink)
        ReceiptMetaLine("Order #", order.receiptNumber, mono, ink)
        ReceiptMetaLine("Date", dateStr, mono, ink)
        ReceiptMetaLine("Payment", SplitPayment.describe(order.paymentMethod, order.paymentReference), mono, ink)
        ReceiptDivider(mono, ink)

        order.items.forEach { item ->
            val label = buildString {
                append("${item.quantity}x ${item.itemName}")
                if (!item.variantName.isNullOrBlank()) append(" (${item.variantName})")
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    fontFamily = mono,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = ink
                )
                Text(
                    text = "PHP ${String.format(Locale.US, "%.0f", item.totalPrice)}",
                    fontFamily = mono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ink
                )
            }
        }

        ReceiptDivider(mono, ink)
        ReceiptTotalLine("Subtotal", order.subtotal, mono, ink)
        ReceiptTotalLine("Discount", order.discountDeduction, mono, ink)
        ReceiptTotalLine("TOTAL", order.total, mono, ink, bold = true)
        ReceiptDivider(mono, ink)
        Text(
            text = "Thank you for your purchase!",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontFamily = mono,
            fontSize = 11.sp,
            color = ink.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun ReceiptDivider(mono: FontFamily, ink: Color) {
    Text(
        text = RECEIPT_DIVIDER,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        fontFamily = mono,
        fontSize = 10.sp,
        color = ink.copy(alpha = 0.45f),
        maxLines = 1,
        overflow = TextOverflow.Clip
    )
}

@Composable
private fun ReceiptMetaLine(label: String, value: String, mono: FontFamily, ink: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontFamily = mono, fontSize = 11.sp, color = ink.copy(alpha = 0.7f))
        Text(
            text = value,
            fontFamily = mono,
            fontSize = 11.sp,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ReceiptTotalLine(
    label: String,
    amount: Double,
    mono: FontFamily,
    ink: Color,
    bold: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontFamily = mono,
            fontSize = if (bold) 12.sp else 11.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = ink
        )
        Text(
            text = "PHP ${String.format(Locale.US, "%.0f", amount)}",
            fontFamily = mono,
            fontSize = if (bold) 12.sp else 11.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = ink
        )
    }
}

@Composable
fun ReceiptPreviewDialog(
    order: Order,
    onDismiss: () -> Unit,
    onShare: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            FluentIcon(
                imageVector = FluentIcons.Receipt,
                contentDescription = null
            )
        },
        title = { Text("Receipt Preview", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                ThermalReceiptCard(order = order)
            }
        },
        confirmButton = {
            Button(onClick = onShare) {
                FluentIcon(
                    imageVector = FluentIcons.Share,
                    contentDescription = null,
                    size = 18.dp,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
