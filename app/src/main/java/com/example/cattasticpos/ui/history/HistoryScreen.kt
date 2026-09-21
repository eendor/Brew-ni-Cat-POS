package com.example.cattasticpos.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import android.content.Context
import androidx.core.app.ShareCompat
import com.example.cattasticpos.domain.model.ZReadingSummary
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import com.example.cattasticpos.ui.icons.FluentIcon
import com.example.cattasticpos.ui.icons.FluentIcons
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import com.example.cattasticpos.domain.model.AppConfig
import com.example.cattasticpos.domain.model.AppThemeAccent
import com.example.cattasticpos.domain.model.Cashier
import com.example.cattasticpos.domain.model.GcashAccount
import com.example.cattasticpos.ui.adaptive.AdaptiveSnackbarHost
import com.example.cattasticpos.ui.adaptive.CollapsingGlassScaffold
import com.example.cattasticpos.ui.adaptive.CupertinoFormRow
import com.example.cattasticpos.ui.adaptive.CupertinoSection
import com.example.cattasticpos.ui.adaptive.LocalCupertinoColors
import com.example.cattasticpos.ui.adaptive.collapsingNestedScroll
import com.example.cattasticpos.ui.adaptive.iOSSpringSpec
import com.example.cattasticpos.ui.adaptive.rememberCollapsingHeaderState
import com.example.cattasticpos.ui.adaptive.rememberLiquidGlassHazeState
import com.example.cattasticpos.ui.adaptive.liquidGlassSource
import com.example.cattasticpos.ui.theme.AdaptiveAmbientGlows
import com.example.cattasticpos.ui.theme.AdaptiveGlassDialog
import com.example.cattasticpos.ui.components.ReceiptEditorDialog
import com.example.cattasticpos.ui.components.formatReceiptShareText
import androidx.compose.material3.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cattasticpos.domain.model.Order
import com.example.cattasticpos.domain.model.OrderItem
import com.example.cattasticpos.domain.model.Expense
import com.example.cattasticpos.ui.components.SleepingCatGraphic
import com.example.cattasticpos.ui.theme.ObsidianGlassCard
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val orders by viewModel.ordersState.collectAsStateWithLifecycle()
    val orderCount by viewModel.orderCountState.collectAsStateWithLifecycle()
    val grossSales by viewModel.grossSalesState.collectAsStateWithLifecycle()
    val discounts by viewModel.discountsState.collectAsStateWithLifecycle()
    val netRevenue by viewModel.netRevenueState.collectAsStateWithLifecycle()
    val cashSales by viewModel.cashSalesState.collectAsStateWithLifecycle()
    val gcashSales by viewModel.gcashSalesState.collectAsStateWithLifecycle()
    val topSellingItem by viewModel.topSellingItemState.collectAsStateWithLifecycle()
    val expensesList by viewModel.expensesListState.collectAsStateWithLifecycle()
    val totalExpenses by viewModel.totalExpensesState.collectAsStateWithLifecycle()
    val exportMessage by viewModel.exportMessage.collectAsStateWithLifecycle()
    val appConfig by viewModel.appConfigState.collectAsStateWithLifecycle()
    val cashierSalesToday by viewModel.cashierSalesTodayState.collectAsStateWithLifecycle()
    val itemSalesBreakdown by viewModel.itemSalesBreakdownState.collectAsStateWithLifecycle()
    val breakdownPeriod by viewModel.breakdownPeriod.collectAsStateWithLifecycle()
    val breakdownRange by viewModel.breakdownRangeState.collectAsStateWithLifecycle()
    val showDateRangeDialog by viewModel.showDateRangeDialog.collectAsStateWithLifecycle()
    val canLoadMore by viewModel.canLoadMore.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var receiptPreviewOrder by remember { mutableStateOf<Order?>(null) }
    val menuItems by viewModel.menuItemsState.collectAsStateWithLifecycle()
    LaunchedEffect(exportMessage) {
        exportMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearExportMessage()
        }
    }

    val cupertino = LocalCupertinoColors.current
    val hazeState = rememberLiquidGlassHazeState()
    val headerState = rememberCollapsingHeaderState()
    var showConfigDialog by remember { mutableStateOf(false) }

    CollapsingGlassScaffold(
        title = "Order History Log",
        hazeState = hazeState,
        headerState = headerState,
        snackbarHost = { AdaptiveSnackbarHost(hostState = snackbarHostState) },
        modifier = modifier,
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
            IconButton(onClick = { viewModel.setShowDateRangeDialog(true) }) {
                FluentIcon(
                    imageVector = FluentIcons.Calendar,
                    contentDescription = "Filter by Date",
                    tint = cupertino.accent,
                    size = 24.dp
                )
            }
            IconButton(onClick = { showConfigDialog = true }) {
                FluentIcon(
                    imageVector = FluentIcons.Settings,
                    contentDescription = "Settings",
                    tint = cupertino.accent,
                    size = 24.dp
                )
            }
        }
    ) { innerPadding ->
        val context = LocalContext.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .background(MaterialTheme.colorScheme.background)
        ) {
            AdaptiveAmbientGlows(Modifier.fillMaxSize())
            var isZReadingExpanded by remember { mutableStateOf(false) }
            var isExpenseTimelineExpanded by remember { mutableStateOf(true) }
            var isOrderTimelineExpanded by remember { mutableStateOf(true) }
            var startAnimation by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                startAnimation = true
                viewModel.onHistoryScreenVisible()
            }

            val todayStart = remember {
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            val todayOrders = remember(orders) {
                orders.filter { it.timestamp >= todayStart }
            }
            val totalRevenueToday = remember(todayOrders) {
                todayOrders.sumOf { it.total }
            }
            val totalOrdersProcessed = orderCount

            val startingFloat = appConfig?.startingCashFloat
                ?: com.example.cattasticpos.data.local.entity.AppConfigEntity.DEFAULT_STARTING_CASH_FLOAT
            val targetSales = appConfig?.targetSales
                ?: com.example.cattasticpos.data.local.entity.AppConfigEntity.DEFAULT_TARGET_SALES
            val totalCash = cashSales ?: 0.0
            val totalGcash = gcashSales ?: 0.0
            val totalSales = grossSales ?: 0.0
            val expenses = totalExpenses ?: 0.0
            // Net has to start from revenue actually collected (SUM of order totals), not gross
            // subtotals — starting from gross silently added every discount back into profit.
            val netSales = netRevenue ?: 0.0
            val profits = netSales - expenses
            val cashDrawer = startingFloat + totalCash - expenses

            val zReadingInteractionSource = remember { MutableInteractionSource() }
            val zReadingPressed by zReadingInteractionSource.collectIsPressedAsState()
            val zReadingScale by animateFloatAsState(
                targetValue = if (zReadingPressed) 0.96f else 1f,
                animationSpec = iOSSpringSpec,
                label = "zReadingScale"
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .liquidGlassSource(hazeState)
                    .collapsingNestedScroll(headerState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
            ObsidianGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .scale(zReadingScale),
                onClick = { isZReadingExpanded = !isZReadingExpanded }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "End of Day Report (Z-Reading)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!isZReadingExpanded) {
                                Text(
                                    text = "Net: ₱${String.format("%.0f", profits)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        
                        OutlinedButton(
                            onClick = {
                                viewModel.printZReading(
                                    ZReadingSummary(
                                        grossSales = totalSales,
                                        discounts = discounts ?: 0.0,
                                        netRevenue = netRevenue ?: 0.0,
                                        cashSales = totalCash,
                                        gcashSales = totalGcash,
                                        totalExpenses = expenses,
                                        startingCashFloat = startingFloat,
                                        cashDrawer = cashDrawer,
                                        profits = profits,
                                        topSellingItem = topSellingItem,
                                        orderCount = orderCount
                                    )
                                )
                            },
                            modifier = Modifier.padding(end = 8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            FluentIcon(
                                imageVector = FluentIcons.Print,
                                contentDescription = "Print Z-Reading",
                                size = 16.dp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Print Z", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { viewModel.exportData() },
                            modifier = Modifier.padding(end = 8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            FluentIcon(
                                imageVector = FluentIcons.ArrowDownload,
                                contentDescription = "Export CSV",
                                size = 16.dp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export", fontSize = 12.sp)
                        }
                        
                        FluentIcon(
                            imageVector = if (isZReadingExpanded) FluentIcons.ChevronUp else FluentIcons.ChevronDown,
                            contentDescription = "Expand/Collapse",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = isZReadingExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val animatedTotalSales by animateFloatAsState(
                                targetValue = if (startAnimation) totalSales.toFloat() else 0f,
                                animationSpec = iOSSpringSpec,
                                label = "salesAnim"
                            )
                            val animatedExpenses by animateFloatAsState(
                                targetValue = if (startAnimation) expenses.toFloat() else 0f,
                                animationSpec = iOSSpringSpec,
                                label = "expensesAnim"
                            )
                            val animatedProfits by animateFloatAsState(
                                targetValue = if (startAnimation) profits.toFloat() else 0f,
                                animationSpec = iOSSpringSpec,
                                label = "profitsAnim"
                            )
                            val targetProgress = if (targetSales > 0) (totalSales / targetSales).toFloat().coerceIn(0f, 1f) else 0f
                            val animatedProgress by animateFloatAsState(
                                targetValue = if (startAnimation) targetProgress else 0f,
                                animationSpec = iOSSpringSpec,
                                label = "progressAnim"
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Total Sales (Gross)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                                    Text("₱${String.format("%.0f", animatedTotalSales)}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Total Expenses", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                                    Text("-₱${String.format("%.0f", animatedExpenses)}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                            
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Goal Progress", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                                    Text("${(animatedProgress * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier.fillMaxWidth().height(8.dp),
                                    color = MaterialTheme.colorScheme.secondary,
                                    trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.2f)
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Profits (Net Cash Flow)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                                    Text("₱${String.format("%.0f", animatedProfits)}", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = MaterialTheme.colorScheme.secondary)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Cash Drawer Status", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                                    Text("₱${String.format("%.0f", cashDrawer)}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("(Float: ₱${String.format("%.0f", startingFloat)})", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                }
                            }

                            // Payment Mode Visualization
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Payment Modes", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val totalCollected = totalCash + totalGcash
                            val targetCashWeight = if (totalCollected > 0) (totalCash / totalCollected).toFloat() else 0.5f
                            val targetGcashWeight = if (totalCollected > 0) (totalGcash / totalCollected).toFloat() else 0.5f
                            val cashWeight by animateFloatAsState(
                                targetValue = if (startAnimation) targetCashWeight else 0.5f,
                                animationSpec = iOSSpringSpec,
                                label = "cashWeight"
                            )
                            val gcashWeight by animateFloatAsState(
                                targetValue = if (startAnimation) targetGcashWeight else 0.5f,
                                animationSpec = iOSSpringSpec,
                                label = "gcashWeight"
                            )
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(16.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (cashWeight > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .weight(cashWeight)
                                            .fillMaxHeight()
                                            .background(
                                                MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(
                                                    topStart = 8.dp,
                                                    bottomStart = 8.dp,
                                                    topEnd = if (gcashWeight == 0f) 8.dp else 0.dp,
                                                    bottomEnd = if (gcashWeight == 0f) 8.dp else 0.dp
                                                )
                                            )
                                    )
                                }
                                if (gcashWeight > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .weight(gcashWeight)
                                            .fillMaxHeight()
                                            .background(
                                                MaterialTheme.colorScheme.secondary,
                                                RoundedCornerShape(
                                                    topEnd = 8.dp,
                                                    bottomEnd = 8.dp,
                                                    topStart = if (cashWeight == 0f) 8.dp else 0.dp,
                                                    bottomStart = if (cashWeight == 0f) 8.dp else 0.dp
                                                )
                                            )
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "CASH: ₱${String.format("%.0f", totalCash)} (${(cashWeight * 100).toInt()}%)",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "GCASH: ₱${String.format("%.0f", totalGcash)} (${(gcashWeight * 100).toInt()}%)",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (topSellingItem != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        FluentIcon(
                                            imageVector = FluentIcons.Trophy,
                                            contentDescription = null,
                                            size = 16.dp,
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Best Seller: ${topSellingItem!!.first} - ${topSellingItem!!.second} units",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
                }

                item {
                    FoodSalesBreakdownCard(
                        breakdown = itemSalesBreakdown,
                        period = breakdownPeriod,
                        rangeStart = breakdownRange.first,
                        rangeEnd = breakdownRange.second,
                        onPeriodChange = { viewModel.setBreakdownPeriod(it) }
                    )
                }

            if (orders.isEmpty() && expensesList.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SleepingCatGraphic(
                            modifier = Modifier.size(64.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No orders found in database.",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                if (expensesList.isNotEmpty()) {
                    item {
                        CollapsibleTimelineHeader(
                            title = "Expense Timeline",
                            expanded = isExpenseTimelineExpanded,
                            onToggle = { isExpenseTimelineExpanded = !isExpenseTimelineExpanded }
                        )
                    }
                    if (isExpenseTimelineExpanded) {
                        items(expensesList, key = { "exp_${it.id}" }) { expense ->
                            Row(
                                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp)).padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(expense.description, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                                    Text("By: ${expense.recordedBy}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f))
                                }
                                Text("- ₱${String.format("%.0f", expense.amount)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    if (orders.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider()
                            CollapsibleTimelineHeader(
                                title = "Order Timeline",
                                expanded = isOrderTimelineExpanded,
                                onToggle = { isOrderTimelineExpanded = !isOrderTimelineExpanded }
                            )
                        }
                    }
                } else if (orders.isNotEmpty()) {
                    item {
                        CollapsibleTimelineHeader(
                            title = "Order Timeline",
                            expanded = isOrderTimelineExpanded,
                            onToggle = { isOrderTimelineExpanded = !isOrderTimelineExpanded }
                        )
                    }
                }

                if (isOrderTimelineExpanded) {
                    items(orders, key = { "order_${it.id}" }) { order ->
                        OrderHistoryCard(
                            order = order,
                            onShare = { shareOrderReceipt(context, order) },
                            onEdit = { receiptPreviewOrder = order }
                        )
                    }
                }

                if (isOrderTimelineExpanded && canLoadMore) {
                    item {
                        OutlinedButton(
                            onClick = { viewModel.loadMoreOrders() },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                        ) {
                            Text("Load More Orders")
                        }
                    }
                }
            }
            }

            if (showDateRangeDialog) {
                val datePickerEpoch by viewModel.datePickerEpoch.collectAsStateWithLifecycle()
                val pickerStart by viewModel.pickerStartMillis.collectAsStateWithLifecycle()
                val pickerEnd by viewModel.pickerEndMillis.collectAsStateWithLifecycle()
                key(datePickerEpoch, pickerStart, pickerEnd) {
                    val todayUtcPickerMillis = DateRangePickerMillis.localTodayStartUtcPicker()
                    val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                    val dateRangePickerState = rememberDateRangePickerState(
                        initialSelectedStartDateMillis = pickerStart,
                        initialSelectedEndDateMillis = pickerEnd,
                        selectableDates = object : SelectableDates {
                            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                                return utcTimeMillis <= todayUtcPickerMillis
                            }

                            override fun isSelectableYear(year: Int): Boolean {
                                return year <= currentYear
                            }
                        }
                    )
                    AdaptiveGlassDialog(
                        onDismissRequest = { viewModel.setShowDateRangeDialog(false) },
                        fixedFooter = true,
                        contentMaxHeight = 480.dp,
                        title = { Text("Filter Orders", fontWeight = FontWeight.Bold) },
                        dismissButton = {
                            Row {
                                TextButton(onClick = {
                                    viewModel.setDateRange(null, null)
                                    viewModel.setShowDateRangeDialog(false)
                                }) { Text("Clear Filter") }
                                TextButton(onClick = { viewModel.setShowDateRangeDialog(false) }) { Text("Cancel") }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.applyDateRangeFilter(
                                    startMillis = dateRangePickerState.selectedStartDateMillis,
                                    endMillis = dateRangePickerState.selectedEndDateMillis
                                )
                                viewModel.setShowDateRangeDialog(false)
                            }) { Text("Apply") }
                        },
                        content = {
                            Text(
                                text = "Select Date Range",
                                modifier = Modifier.padding(bottom = 8.dp),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 480.dp),
                                color = Color.Transparent,
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp
                            ) {
                                DateRangePicker(
                                    state = dateRangePickerState,
                                    showModeToggle = false,
                                    colors = DatePickerDefaults.colors(
                                        containerColor = Color.Transparent
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    )
                }
            }

            if (showConfigDialog) {
                EditConfigDialog(
                    initialTarget = appConfig?.targetSales
                        ?: com.example.cattasticpos.data.local.entity.AppConfigEntity.DEFAULT_TARGET_SALES,
                    initialFloat = appConfig?.startingCashFloat
                        ?: com.example.cattasticpos.data.local.entity.AppConfigEntity.DEFAULT_STARTING_CASH_FLOAT,
                    cashiers = appConfig?.cashiers.orEmpty(),
                    activeCashierId = appConfig?.activeCashierId,
                    cashierSalesToday = cashierSalesToday,
                    gcashAccounts = appConfig?.gcashAccounts.orEmpty(),
                    initialThemeAccentId = appConfig?.themeAccentId ?: AppThemeAccent.DEFAULT_ID,
                    initialSupabaseUrl = appConfig?.supabaseUrl.orEmpty(),
                    initialSupabaseAnonKey = appConfig?.supabaseAnonKey.orEmpty(),
                    onDismiss = { showConfigDialog = false },
                    onSaveWithPin = { target, float, currentPin, newPin ->
                        viewModel.saveConfigWithPinVerification(target, float, currentPin, newPin)
                    },
                    onSaveSyncConfig = { url, key ->
                        viewModel.updateSyncConfig(url, key)
                    },
                    onThemeAccentChange = { viewModel.updateThemeAccent(it) },
                    onSelectActiveCashier = { viewModel.selectActiveCashier(it) },
                    onAddCashier = { viewModel.addCashier(it) },
                    onRemoveCashier = { viewModel.removeCashier(it) },
                    onAddGcashAccount = { viewModel.addGcashAccount(it) },
                    onRemoveGcashAccount = { viewModel.removeGcashAccount(it) }
                )
            }


            receiptPreviewOrder?.let { order ->
                ReceiptEditorDialog(
                    order = order,
                    menuItems = menuItems,
                    onDismiss = { receiptPreviewOrder = null },
                    onSave = { cartItems, discountStrategy ->
                        viewModel.updateOrder(order.id, cartItems, discountStrategy)
                        receiptPreviewOrder = null
                    },
                    onShare = { previewOrder ->
                        shareOrderReceipt(context, previewOrder)
                    }
                )
            }
        }
    }
}

@Composable
private fun FoodSalesBreakdownCard(
    breakdown: List<com.example.cattasticpos.domain.model.ItemSalesBreakdown>,
    period: BreakdownPeriod,
    rangeStart: Long,
    rangeEnd: Long,
    onPeriodChange: (BreakdownPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(true) }
    val grandTotal = remember(breakdown) { breakdown.sumOf { it.totalSales } }
    val rangeLabel = remember(rangeStart, rangeEnd) {
        val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
        "${fmt.format(Date(rangeStart))} – ${fmt.format(Date(rangeEnd))}"
    }
    // Group by category so foods and drinks read as separate sections, each food its own total.
    val grouped = remember(breakdown) {
        breakdown.groupBy { it.categoryName ?: "Other" }
            .toList()
            .sortedByDescending { (_, rows) -> rows.sumOf { it.totalSales } }
    }

    ObsidianGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Food & Drink Totals",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Per item · ${if (period == BreakdownPeriod.WEEK) "This Week" else "This Month"} ($rangeLabel)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                FluentIcon(
                    imageVector = if (expanded) FluentIcons.ChevronUp else FluentIcons.ChevronDown,
                    contentDescription = "Expand/Collapse",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Week / Month toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        BreakdownPeriod.entries.forEach { p ->
                            val selected = p == period
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.secondary
                                        else Color.Transparent
                                    )
                                    .clickable { onPeriodChange(p) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (p == BreakdownPeriod.WEEK) "Per Week" else "Per Month",
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) MaterialTheme.colorScheme.onSecondary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (breakdown.isEmpty()) {
                        Text(
                            text = "No sales recorded for this ${if (period == BreakdownPeriod.WEEK) "week" else "month"} yet.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        grouped.forEach { (categoryName, rows) ->
                            val categoryTotal = rows.sumOf { it.totalSales }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = categoryName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = "₱${String.format(Locale.US, "%.0f", categoryTotal)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            rows.sortedByDescending { it.totalSales }.forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = row.displayLabel,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${row.totalQuantity} sold",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                    Text(
                                        text = "₱${String.format(Locale.US, "%.0f", row.totalSales)}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "All Food & Drinks",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "₱${String.format(Locale.US, "%.0f", grandTotal)}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollapsibleTimelineHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        FluentIcon(
            imageVector = if (expanded) FluentIcons.ChevronUp else FluentIcons.ChevronDown,
            contentDescription = if (expanded) "Collapse $title" else "Expand $title",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            size = 20.dp,
            useGlassGradient = false
        )
    }
}

@Composable
fun OrderHistoryCard(
    order: Order,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val actionWidth = 104.dp
    val actionWidthPx = with(density) { actionWidth.toPx() }
    val cardShape = RoundedCornerShape(22.dp)
    val swipeSnapSpec = remember { spring<Float>(dampingRatio = 0.92f, stiffness = 720f) }
    val offsetAnim = remember(order.id) { Animatable(0f) }
    var dragOffset by remember(order.id) { mutableFloatStateOf(0f) }
    var isDragging by remember(order.id) { mutableStateOf(false) }
    val displayOffset = if (isDragging) dragOffset else offsetAnim.value
    val revealProgress = (displayOffset / actionWidthPx).coerceIn(0f, 1f)
 
    fun closeReveal() {
        scope.launch { offsetAnim.animateTo(0f, swipeSnapSpec) }
    }
 
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(actionWidth)
                .fillMaxHeight()
                .graphicsLayer { alpha = revealProgress }
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OrderSwipeActionIcon(
                icon = FluentIcons.Share,
                contentDescription = "Share receipt",
                onClick = {
                    closeReveal()
                    onShare()
                }
            )
            OrderSwipeActionIcon(
                icon = FluentIcons.Edit,
                contentDescription = "Edit receipt",
                onClick = {
                    closeReveal()
                    onEdit()
                }
            )
        }

        OrderHistoryCardContent(
            order = order,
            cardShape = cardShape,
            modifier = Modifier
                .fillMaxWidth()
                .offset {
                    IntOffset(-displayOffset.coerceIn(0f, actionWidthPx).roundToInt(), 0)
                }
                .pointerInput(order.id, actionWidthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            isDragging = true
                            dragOffset = offsetAnim.value
                        },
                        onDragCancel = {
                            isDragging = false
                            scope.launch {
                                val target = if (dragOffset >= actionWidthPx * 0.35f) actionWidthPx else 0f
                                offsetAnim.snapTo(dragOffset)
                                offsetAnim.animateTo(target, swipeSnapSpec)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragOffset = (dragOffset - dragAmount).coerceIn(0f, actionWidthPx)
                        },
                        onDragEnd = {
                            isDragging = false
                            scope.launch {
                                val target = if (dragOffset >= actionWidthPx * 0.35f) actionWidthPx else 0f
                                offsetAnim.snapTo(dragOffset)
                                offsetAnim.animateTo(target, swipeSnapSpec)
                            }
                        }
                    )
                }
        )
    }
}

@Composable
private fun OrderSwipeActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color? = null
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp)
    ) {
        FluentIcon(
            imageVector = icon,
            contentDescription = contentDescription,
            size = 20.dp,
            tint = tint,
            useGlassGradient = tint == null
        )
    }
}

@Composable
private fun OrderHistoryCardContent(
    order: Order,
    cardShape: RoundedCornerShape,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember(order.id) { mutableStateOf(false) }
    val darkTheme = isSystemInDarkTheme()
    val dateFormatter = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }
    val dateStr = remember(order.timestamp) {
        dateFormatter.format(Date(order.timestamp))
    }

    val cardColor = if (darkTheme) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.background
    }
    val expandInteractionSource = remember(order.id) { MutableInteractionSource() }

    Surface(
        modifier = modifier,
        shape = cardShape,
        color = cardColor,
        shadowElevation = if (darkTheme) 0.dp else 2.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (darkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = expandInteractionSource,
                        indication = null,
                        onClick = { isExpanded = !isExpanded }
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FluentIcon(
                        imageVector = if (isExpanded) FluentIcons.ChevronUp else FluentIcons.ChevronDown,
                        contentDescription = if (isExpanded) "Collapse order" else "Expand order",
                        size = 18.dp,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Order #${order.receiptNumber}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        if (!order.cashierName.isNullOrBlank()) {
                            Text(
                                text = "Cashier: ${order.cashierName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = dateStr,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    val badgeColor = if (order.paymentMethod == "GCASH") {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                    Box(
                        modifier = Modifier
                            .background(badgeColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = order.paymentMethod,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "₱${String.format("%.0f", order.total)}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Text(
                        text = "Items Sold:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        order.items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val flavorText = if (item.flavor.isNullOrBlank()) {
                                    ""
                                } else {
                                    " (${item.flavor.substringAfter(": ").trim()})"
                                }
                                Text(
                                    text = "${item.quantity} x ${item.itemName} (${item.variantName}$flavorText)",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "₱${String.format("%.0f", item.totalPrice)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Discount Type:",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = order.discountLabel.ifBlank { "None" },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (order.discountDeduction > 0) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Subtotal: ₱${String.format("%.0f", order.subtotal)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            if (order.discountDeduction > 0) {
                                Text(
                                    text = "Discount: -₱${String.format("%.0f", order.discountDeduction)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun shareOrderReceipt(context: android.content.Context, order: com.example.cattasticpos.domain.model.Order) {
    val text = formatReceiptShareText(order)

    val intent = ShareCompat.IntentBuilder(context)
        .setType("text/plain")
        .setText(text)
        .intent
    context.startActivity(android.content.Intent.createChooser(intent, "Share Receipt"))
}



@Composable
fun EditConfigDialog(
    initialTarget: Double,
    initialFloat: Double,
    cashiers: List<Cashier>,
    activeCashierId: String?,
    cashierSalesToday: Map<String, Double>,
    gcashAccounts: List<GcashAccount>,
    initialThemeAccentId: String,
    initialSupabaseUrl: String,
    initialSupabaseAnonKey: String,
    onDismiss: () -> Unit,
    onSaveWithPin: suspend (Double, Double, String, String) -> Boolean,
    onSaveSyncConfig: (String, String) -> Unit,
    onThemeAccentChange: (String) -> Unit,
    onSelectActiveCashier: (String) -> Unit,
    onAddCashier: (String) -> Unit,
    onRemoveCashier: (String) -> Unit,
    onAddGcashAccount: (String) -> Unit,
    onRemoveGcashAccount: (String) -> Unit
) {
    var targetStr by remember { mutableStateOf(if (initialTarget % 1.0 == 0.0) initialTarget.toInt().toString() else initialTarget.toString()) }
    var floatStr by remember { mutableStateOf(if (initialFloat % 1.0 == 0.0) initialFloat.toInt().toString() else initialFloat.toString()) }
    var supabaseUrl by remember { mutableStateOf(initialSupabaseUrl) }
    var supabaseAnonKey by remember { mutableStateOf(initialSupabaseAnonKey) }
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var isPinError by remember { mutableStateOf(false) }
    var cashiersExpanded by remember { mutableStateOf(false) }
    var gcashExpanded by remember { mutableStateOf(false) }
    var newCashierName by remember { mutableStateOf("") }
    var newGcashLabel by remember { mutableStateOf("") }
    val selectedAccent = AppThemeAccent.fromId(initialThemeAccentId)
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val feedbackPrefs = remember {
        (context.applicationContext as com.example.cattasticpos.CattasticPosApp).container.feedbackPreferences
    }
    var hapticsEnabled by remember { mutableStateOf(feedbackPrefs.hapticsEnabled) }
    var soundsEnabled by remember { mutableStateOf(feedbackPrefs.soundsEnabled) }

    AdaptiveGlassDialog(
        onDismissRequest = onDismiss,
        surfaceAlpha = 0.92f,
        fixedFooter = true,
        contentMaxHeight = 600.dp,
        contentPadding = 24.dp,
        title = { Text("App Settings", fontWeight = FontWeight.Bold) },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        val t = targetStr.toDoubleOrNull() ?: initialTarget
                        val f = floatStr.toDoubleOrNull() ?: initialFloat
                        val goalsChanged = t != initialTarget || f != initialFloat
                        val pinChangeRequested = newPin.length == 4

                        onSaveSyncConfig(supabaseUrl, supabaseAnonKey)

                        if (!goalsChanged && !pinChangeRequested) {
                            onDismiss()
                            return@launch
                        }
                        if (newPin.isNotEmpty() && newPin.length < 4) {
                            isPinError = true
                            return@launch
                        }
                        if (currentPin.isBlank()) {
                            isPinError = true
                            return@launch
                        }
                        if (onSaveWithPin(t, f, currentPin, newPin)) {
                            onDismiss()
                        } else {
                            isPinError = true
                        }
                    }
                }
            ) {
                Text("Save")
            }
        },
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CupertinoSection(header = "Software Updates") {
                    var isCheckingUpdate by remember { mutableStateOf(false) }
                    val updateManager = remember { com.example.cattasticpos.service.AppUpdateManager(context) }
                    CupertinoFormRow(label = "Brew ni Cat POS v${com.example.cattasticpos.BuildConfig.VERSION_NAME}") {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isCheckingUpdate = true
                                    val update = updateManager.checkForUpdate()
                                    isCheckingUpdate = false
                                    if (update == null) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Brew ni Cat POS is up to date (v${com.example.cattasticpos.BuildConfig.VERSION_NAME})",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        android.widget.Toast.makeText(
                                            context,
                                            "New update v${update.versionName} found!",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            },
                            enabled = !isCheckingUpdate
                        ) {
                            Text(if (isCheckingUpdate) "Checking..." else "Check Now")
                        }
                    }
                }
                CupertinoSection(header = "Feedback") {
                    CupertinoFormRow(label = "Haptic feedback") {
                        Switch(
                            checked = hapticsEnabled,
                            onCheckedChange = {
                                hapticsEnabled = it
                                feedbackPrefs.hapticsEnabled = it
                            }
                        )
                    }
                    CupertinoFormRow(label = "Sound effects", showDivider = false) {
                        Switch(
                            checked = soundsEnabled,
                            onCheckedChange = {
                                soundsEnabled = it
                                feedbackPrefs.soundsEnabled = it
                            }
                        )
                    }
                }

                CupertinoSection(header = "Business Goals") {
                    CupertinoFormRow(label = "Target Sales") {
                        OutlinedTextField(
                            value = targetStr,
                            onValueChange = { targetStr = it },
                            label = { Text("₱") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    CupertinoFormRow(label = "Starting Cash Float", showDivider = false) {
                        OutlinedTextField(
                            value = floatStr,
                            onValueChange = { floatStr = it },
                            label = { Text("₱") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                CupertinoSection(header = "Security Setup") {
                    CupertinoFormRow(label = "Current PIN") {
                        OutlinedTextField(
                            value = currentPin,
                            onValueChange = { value ->
                                currentPin = value.filter { it.isDigit() }.take(4)
                                isPinError = false
                            },
                            label = { Text("Required to save goals or change PIN") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            isError = isPinError,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    CupertinoFormRow(label = "New PIN", showDivider = false) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(
                                value = newPin,
                                onValueChange = { value ->
                                    newPin = value.filter { it.isDigit() }.take(4)
                                    isPinError = false
                                },
                                label = { Text("Optional — leave blank to keep current PIN") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            if (isPinError) {
                                Text(
                                    "Incorrect current PIN or incomplete new PIN",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp
                                )
                            } else if (newPin.isNotEmpty() && newPin.length < 4) {
                                Text(
                                    "PIN must be 4 digits",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                CupertinoSection(header = "System Theme Accent") {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AppThemeAccent.entries.forEach { accent ->
                                val isSelected = accent.id == initialThemeAccentId
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(accent.swatch)
                                        .then(
                                            if (isSelected) {
                                                Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .clickable { onThemeAccentChange(accent.id) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.9f))
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            text = selectedAccent.label,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                CashierRosterSection(
                    cashiers = cashiers,
                    activeCashierId = activeCashierId,
                    salesByCashier = cashierSalesToday,
                    expanded = cashiersExpanded,
                    onToggle = { cashiersExpanded = !cashiersExpanded },
                    onSelect = onSelectActiveCashier,
                    onRemove = onRemoveCashier,
                    newValue = newCashierName,
                    onNewValueChange = { newCashierName = it },
                    onAdd = {
                        onAddCashier(newCashierName)
                        newCashierName = ""
                    }
                )

                ConfigListSection(
                    title = "GCash SIM Accounts",
                    expanded = gcashExpanded,
                    onToggle = { gcashExpanded = !gcashExpanded },
                    items = gcashAccounts.map { it.label },
                    onRemove = { index -> onRemoveGcashAccount(gcashAccounts[index].id) },
                    newValue = newGcashLabel,
                    onNewValueChange = { newGcashLabel = it },
                    addLabel = "Account Label (e.g. Main GCash 0917...)",
                    onAdd = {
                        onAddGcashAccount(newGcashLabel)
                        newGcashLabel = ""
                    }
                )

            }
        }
    )
}

@Composable
private fun CashierRosterSection(
    cashiers: List<Cashier>,
    activeCashierId: String?,
    salesByCashier: Map<String, Double>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    newValue: String,
    onNewValueChange: (String) -> Unit,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Cashiers", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        FluentIcon(
            imageVector = if (expanded) FluentIcons.ChevronUp else FluentIcons.ChevronDown,
            contentDescription = null
        )
    }

    AnimatedVisibility(visible = expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            cashiers.forEach { cashier ->
                val sales = salesByCashier[cashier.id] ?: 0.0
                val isActive = cashier.id == activeCashierId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(cashier.id) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isActive,
                        onClick = { onSelect(cashier.id) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.secondary
                        )
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${cashier.name} (₱${String.format("%.0f", sales)} sales)",
                            fontSize = 13.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            color = if (isActive) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        if (isActive) {
                            Text(
                                text = "Active operator",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                        }
                    }
                    if (isActive) {
                        FluentIcon(
                            imageVector = FluentIcons.CheckmarkCircle,
                            contentDescription = "Active cashier",
                            size = 18.dp,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    TextButton(onClick = { onRemove(cashier.id) }) {
                        Text("Remove", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newValue,
                    onValueChange = onNewValueChange,
                    label = { Text("Cashier Name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onAdd,
                    enabled = newValue.isNotBlank(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("Add")
                }
            }
        }
    }
}

@Composable
private fun ConfigListSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    items: List<String>,
    onRemove: (Int) -> Unit,
    newValue: String,
    onNewValueChange: (String) -> Unit,
    addLabel: String,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        FluentIcon(
            imageVector = if (expanded) FluentIcons.ChevronUp else FluentIcons.ChevronDown,
            contentDescription = null
        )
    }

    AnimatedVisibility(visible = expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEachIndexed { index, label ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onRemove(index) }) {
                        Text("Remove", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newValue,
                    onValueChange = onNewValueChange,
                    label = { Text(addLabel) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onAdd,
                    enabled = newValue.isNotBlank(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("Add")
                }
            }
        }
    }
}

