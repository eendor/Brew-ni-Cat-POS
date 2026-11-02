package com.example.cattasticpos.ui.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cattasticpos.domain.model.OnlineOrder
import com.example.cattasticpos.domain.model.OnlineOrderRules
import com.example.cattasticpos.domain.model.OnlineOrderStatus
import com.example.cattasticpos.ui.adaptive.AdaptiveScaffold
import com.example.cattasticpos.ui.adaptive.AdaptiveSnackbarHost
import com.example.cattasticpos.ui.adaptive.AdaptiveTopAppBar
import com.example.cattasticpos.ui.adaptive.LocalCupertinoColors
import com.example.cattasticpos.ui.adaptive.rememberAdaptiveTopBarScrollBehavior
import com.example.cattasticpos.ui.components.unstyled.PosPrimaryButton
import com.example.cattasticpos.ui.icons.FluentIcon
import com.example.cattasticpos.ui.icons.FluentIcons
import com.example.cattasticpos.ui.theme.AdaptiveGlassCard
import com.example.cattasticpos.ui.theme.AdaptiveGlassDialog
import com.example.cattasticpos.ui.theme.adaptiveBodyMuted
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class OrdersTab { Active, Past }

/** What the screen says for each status, and how its badge is colored. */
private fun statusColors(status: OnlineOrderStatus): Pair<Color, Color> = when (status) {
    OnlineOrderStatus.Pending -> Color(0xFFFFE08A) to Color(0xFF5A4300)
    OnlineOrderStatus.Accepted -> Color(0xFFCFE3FF) to Color(0xFF123A6B)
    OnlineOrderStatus.Preparing -> Color(0xFFFFD9B8) to Color(0xFF6B3200)
    OnlineOrderStatus.Ready -> Color(0xFFC8F0D2) to Color(0xFF0F4D24)
    OnlineOrderStatus.Completed -> Color(0xFFE3E3E3) to Color(0xFF333333)
    OnlineOrderStatus.Rejected, OnlineOrderStatus.Cancelled -> Color(0xFFFFD2D2) to Color(0xFF6B1111)
}

/** The button that moves an order one step forward, by the status it is in now. */
internal fun primaryStep(status: OnlineOrderStatus): Pair<OnlineOrderStatus, String>? = when (status) {
    OnlineOrderStatus.Pending -> OnlineOrderStatus.Accepted to "Accept"
    OnlineOrderStatus.Accepted -> OnlineOrderStatus.Preparing to "Start preparing"
    OnlineOrderStatus.Preparing -> OnlineOrderStatus.Ready to "Mark ready"
    OnlineOrderStatus.Ready -> OnlineOrderStatus.Completed to "Complete"
    else -> null
}

/** The step that ends an order early: Reject for a new order, Cancel after that. */
internal fun endingStep(status: OnlineOrderStatus): Pair<OnlineOrderStatus, String>? =
    when {
        OnlineOrderRules.isAllowed(status, OnlineOrderStatus.Rejected) -> OnlineOrderStatus.Rejected to "Reject"
        OnlineOrderRules.isAllowed(status, OnlineOrderStatus.Cancelled) -> OnlineOrderStatus.Cancelled to "Cancel order"
        else -> null
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineOrdersScreen(
    viewModel: OnlineOrdersViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = rememberAdaptiveTopBarScrollBehavior()
    val cupertino = LocalCupertinoColors.current
    var tab by remember { mutableStateOf(OrdersTab.Active) }
    // an order waiting for a reason before it is rejected or cancelled
    var reasonFor by remember { mutableStateOf<Pair<OnlineOrder, OnlineOrderStatus>?>(null) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    val shown = if (tab == OrdersTab.Active) state.activeOrders else state.pastOrders

    AdaptiveScaffold(
        topBar = {
            AdaptiveTopAppBar(
                title = "Online Orders",
                scrollBehavior = scrollBehavior,
                largeTitle = false,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        FluentIcon(
                            imageVector = FluentIcons.ArrowLeft,
                            contentDescription = "Go Back",
                            tint = cupertino.accent,
                            size = 24.dp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        FluentIcon(
                            imageVector = FluentIcons.Refresh,
                            contentDescription = "Refresh orders",
                            tint = cupertino.accent,
                            size = 22.dp
                        )
                    }
                }
            )
        },
        snackbarHost = { AdaptiveSnackbarHost(hostState = snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = tab == OrdersTab.Active,
                    onClick = { tab = OrdersTab.Active },
                    label = { Text("Active (${state.activeOrders.size})") }
                )
                FilterChip(
                    selected = tab == OrdersTab.Past,
                    onClick = { tab = OrdersTab.Past },
                    label = { Text("Past (${state.pastOrders.size})") }
                )
                Spacer(Modifier.weight(1f))
                state.lastLoadedAtMillis?.let {
                    Text(
                        text = "Updated ${SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date(it))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = adaptiveBodyMuted()
                    )
                }
            }
            state.loadError?.let { error ->
                Spacer(Modifier.height(8.dp))
                ErrorBanner(message = error, actionLabel = "Retry", onAction = viewModel::refresh)
            }
            Spacer(Modifier.height(8.dp))

            when {
                state.isLoading && state.orders.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                shown.isEmpty() -> EmptyState(
                    text = if (tab == OrdersTab.Active) {
                        "No online orders to handle. New orders show up here with a chime."
                    } else {
                        "Completed, rejected and cancelled orders show up here."
                    }
                )
                else -> LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(minSize = 340.dp),
                    verticalItemSpacing = 12.dp,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(shown, key = { it.reference }) { order ->
                        OrderCard(
                            order = order,
                            change = state.changes[order.reference],
                            onStep = { to -> viewModel.change(order, to) },
                            onEnd = { to -> reasonFor = order to to },
                            onRetry = { viewModel.retry(order.reference) },
                            onDismiss = { viewModel.dismiss(order.reference) }
                        )
                    }
                    if (tab == OrdersTab.Past) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Text(
                                text = "Showing the latest 100 orders.",
                                style = MaterialTheme.typography.labelSmall,
                                color = adaptiveBodyMuted(),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    reasonFor?.let { (order, to) ->
        ReasonDialog(
            order = order,
            to = to,
            onDismiss = { reasonFor = null },
            onConfirm = { reason ->
                reasonFor = null
                viewModel.change(order, to, reason)
            }
        )
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = adaptiveBodyMuted()
        )
    }
}

@Composable
private fun ErrorBanner(message: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

private val priceFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}

private fun peso(value: Double) = "₱" + priceFormat.format(value)

@Composable
private fun OrderCard(
    order: OnlineOrder,
    change: PendingChange?,
    onStep: (OnlineOrderStatus) -> Unit,
    onEnd: (OnlineOrderStatus) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val (badgeBg, badgeFg) = statusColors(order.status)
    AdaptiveGlassCard(modifier = Modifier.fillMaxWidth(), surfaceAlpha = 0.97f) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(order.reference, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        text = "${order.customerName} · ${order.customerPhone}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = adaptiveBodyMuted()
                    )
                }
                Text(
                    text = order.status.label,
                    color = badgeFg,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(badgeBg, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Text(
                text = "Placed ${SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(order.createdAtMillis))} · Pay at pickup: ${order.paymentPreference}",
                style = MaterialTheme.typography.labelMedium,
                color = adaptiveBodyMuted()
            )
            HorizontalDivider()
            order.lines.forEach { line ->
                Row(verticalAlignment = Alignment.Top) {
                    Text("${line.quantity}×", fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp))
                    Column(Modifier.weight(1f)) {
                        Text(line.itemName, fontWeight = FontWeight.Medium)
                        val detail = listOfNotNull(line.variantName.takeIf { it.isNotBlank() }, line.flavor).joinToString(" · ")
                        if (detail.isNotEmpty()) {
                            Text(detail, style = MaterialTheme.typography.bodySmall, color = adaptiveBodyMuted())
                        }
                    }
                    Text(peso(line.lineTotal), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (order.notes.isNotBlank()) {
                Text(
                    text = "Note: ${order.notes}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            if (order.status == OnlineOrderStatus.Rejected && !order.rejectionReason.isNullOrBlank()) {
                Text(
                    text = "Reason shown to the customer: ${order.rejectionReason}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Total", style = MaterialTheme.typography.labelLarge, color = adaptiveBodyMuted())
                Spacer(Modifier.weight(1f))
                Text(peso(order.subtotal), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            OrderActions(order, change, onStep, onEnd, onRetry, onDismiss)
        }
    }
}

@Composable
private fun OrderActions(
    order: OnlineOrder,
    change: PendingChange?,
    onStep: (OnlineOrderStatus) -> Unit,
    onEnd: (OnlineOrderStatus) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    if (change != null) {
        if (change.isSending) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Saving: ${change.to.label}…", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            // A change with no answer stays here until it lands or staff give up (FR-077)
            ErrorBanner(
                message = "${change.to.label} wasn't saved. ${change.failure}",
                actionLabel = "Retry",
                onAction = onRetry
            )
            TextButton(onClick = onDismiss) { Text("Discard this change") }
        }
        return
    }
    val primary = primaryStep(order.status)
    val ending = endingStep(order.status)
    if (primary == null && ending == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        if (ending != null) {
            OutlinedButton(
                onClick = { onEnd(ending.first) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
            ) { Text(ending.second) }
        }
        if (primary != null) {
            PosPrimaryButton(
                onClick = { onStep(primary.first) },
                modifier = Modifier.weight(if (ending != null) 1.4f else 1f)
            ) { Text(primary.second, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ReasonDialog(
    order: OnlineOrder,
    to: OnlineOrderStatus,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    val required = OnlineOrderRules.requiresReason(to)
    val problem = OnlineOrderRules.problem(order.status, to, reason)
    val verb = if (to == OnlineOrderStatus.Rejected) "Reject" else "Cancel"
    AdaptiveGlassDialog(onDismissRequest = onDismiss, surfaceAlpha = 0.97f) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "$verb ${order.reference}?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (required) {
                    "The customer sees this reason on their order page."
                } else {
                    "Add a reason if the customer should know why. It's optional."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = adaptiveBodyMuted()
            )
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it.take(OnlineOrderRules.REASON_MAX_LENGTH) },
                label = { Text(if (required) "Reason (required)" else "Reason (optional)") },
                placeholder = { Text("e.g., We ran out of matcha") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Keep order") }
                PosPrimaryButton(
                    onClick = { onConfirm(reason.trim().takeIf { it.isNotEmpty() }) },
                    enabled = problem == null,
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) { Text(verb, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
