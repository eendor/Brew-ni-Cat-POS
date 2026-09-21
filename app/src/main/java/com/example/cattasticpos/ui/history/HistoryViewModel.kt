package com.example.cattasticpos.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.cattasticpos.CattasticPosApp
import com.example.cattasticpos.domain.model.AppConfig
import com.example.cattasticpos.domain.model.Cashier
import com.example.cattasticpos.domain.model.Expense
import com.example.cattasticpos.domain.model.GcashAccount
import com.example.cattasticpos.domain.model.CartItem
import com.example.cattasticpos.domain.model.Item
import com.example.cattasticpos.domain.model.Order
import com.example.cattasticpos.domain.model.ZReadingSummary
import com.example.cattasticpos.domain.repository.AppConfigRepository
import com.example.cattasticpos.domain.repository.ExpenseRepository
import com.example.cattasticpos.domain.repository.OrderRepository
import com.example.cattasticpos.domain.service.ReceiptPrinterService
import com.example.cattasticpos.domain.usecase.ExportDataUseCase
import com.example.cattasticpos.domain.usecase.GetMenuUseCase
import com.example.cattasticpos.domain.usecase.UpdateOrderUseCase
import com.example.cattasticpos.domain.usecase.VoidOrderUseCase
import com.example.cattasticpos.domain.strategy.DiscountStrategy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

enum class BreakdownPeriod { WEEK, MONTH }

data class DateRangeApplyResult(
    val startMillis: Long,
    val endMillis: Long,
    val pickerStartMillis: Long,
    val pickerEndMillis: Long,
    val wasClamped: Boolean
)

class HistoryViewModel(
    private val application: android.app.Application,
    private val orderRepository: OrderRepository,
    private val expenseRepository: ExpenseRepository,
    private val exportDataUseCase: ExportDataUseCase,
    private val appConfigRepository: AppConfigRepository,
    private val voidOrderUseCase: VoidOrderUseCase,
    private val updateOrderUseCase: UpdateOrderUseCase,
    private val getMenuUseCase: GetMenuUseCase,
    private val receiptPrinterService: ReceiptPrinterService
) : ViewModel() {

    private val _menuItems = MutableStateFlow<List<Item>>(emptyList())
    val menuItemsState: StateFlow<List<Item>> = _menuItems.asStateFlow()

    private val todayStart: Long
    private val todayEnd: Long
    private val allTimeStart: Long = 0L
    private val allTimeEnd: Long = Long.MAX_VALUE

    private val _startDate = MutableStateFlow(0L)
    private val _endDate = MutableStateFlow(0L)
    private val _extraOrderPages = MutableStateFlow<List<Order>>(emptyList())
    private val _showDateRangeDialog = MutableStateFlow(false)
    private val _canLoadMore = MutableStateFlow(false)
    private val _datePickerEpoch = MutableStateFlow(0)
    private val _pickerStartMillis = MutableStateFlow(0L)
    private val _pickerEndMillis = MutableStateFlow(0L)

    val showDateRangeDialog: StateFlow<Boolean> = _showDateRangeDialog.asStateFlow()
    val canLoadMore: StateFlow<Boolean> = _canLoadMore.asStateFlow()
    val datePickerEpoch: StateFlow<Int> = _datePickerEpoch.asStateFlow()
    val pickerStartMillis: StateFlow<Long> = _pickerStartMillis.asStateFlow()
    val pickerEndMillis: StateFlow<Long> = _pickerEndMillis.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val firstPageOrders = combine(_startDate, _endDate) { start, end -> start to end }
        .flatMapLatest { (start, end) ->
            orderRepository.observeOrdersPage(
                startDate = start,
                endDate = end,
                beforeTimestamp = Long.MAX_VALUE,
                limit = PAGE_SIZE
            )
        }

    val ordersState: StateFlow<List<Order>> = combine(firstPageOrders, _extraOrderPages) { first, extra ->
        first + extra
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun onHistoryScreenVisible() {
        _extraOrderPages.value = emptyList()
    }

    init {
        viewModelScope.launch {
            getMenuUseCase().collect { menu ->
                _menuItems.value = menu.items
            }
        }

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        todayStart = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        todayEnd = calendar.timeInMillis

        _startDate.value = todayStart
        _endDate.value = todayEnd
        _pickerStartMillis.value = DateRangePickerMillis.localStartOfDayToUtcPicker(todayStart)
        _pickerEndMillis.value = DateRangePickerMillis.localStartOfDayToUtcPicker(startOfDayMillis(todayEnd))

        viewModelScope.launch {
            combine(_startDate, _endDate) { _, _ -> }
                .collect { _extraOrderPages.value = emptyList() }
        }

        viewModelScope.launch {
            firstPageOrders.collect { firstPage ->
                if (_extraOrderPages.value.isEmpty()) {
                    _canLoadMore.value = firstPage.size == PAGE_SIZE
                }
            }
        }
    }

    fun loadMoreOrders() {
        if (!_canLoadMore.value) return
        viewModelScope.launch {
            val currentOrders = ordersState.value
            val beforeTimestamp = currentOrders.minOfOrNull { it.timestamp } ?: return@launch
            val nextPage = orderRepository.getOrdersPage(
                startDate = _startDate.value,
                endDate = _endDate.value,
                beforeTimestamp = beforeTimestamp,
                limit = PAGE_SIZE
            )
            if (nextPage.isEmpty()) {
                _canLoadMore.value = false
            } else {
                _extraOrderPages.value = _extraOrderPages.value + nextPage
                _canLoadMore.value = nextPage.size == PAGE_SIZE
            }
        }
    }

    fun setDateRange(start: Long?, end: Long?) {
        if (start == null && end == null) {
            _startDate.value = allTimeStart
            _endDate.value = allTimeEnd
            _pickerStartMillis.value = DateRangePickerMillis.localStartOfDayToUtcPicker(todayStart)
            _pickerEndMillis.value = DateRangePickerMillis.localStartOfDayToUtcPicker(startOfDayMillis(todayEnd))
            _datePickerEpoch.value = _datePickerEpoch.value + 1
            return
        }
        applyDateRangeFilter(start, end)
    }

    fun applyDateRangeFilter(startMillis: Long?, endMillis: Long?): DateRangeApplyResult {
        val presentBounds = presentDayBounds()
        var wasClamped = false

        var startDay = startMillis?.let { DateRangePickerMillis.utcPickerToLocalStartOfDay(it) }
        var endDay = endMillis?.let { DateRangePickerMillis.utcPickerToLocalStartOfDay(it) }

        if (startDay == null && endDay == null) {
            startDay = presentBounds.first
            endDay = presentBounds.first
            wasClamped = true
        } else if (startDay == null || endDay == null || startDay < 0L || endDay < 0L) {
            startDay = presentBounds.first
            endDay = presentBounds.first
            wasClamped = true
        }

        if (startDay > presentBounds.first) {
            startDay = presentBounds.first
            wasClamped = true
        }
        if (endDay > presentBounds.first) {
            endDay = presentBounds.first
            wasClamped = true
        }

        if (endDay < startDay) {
            val earlier = minOf(startDay, endDay)
            val later = maxOf(startDay, endDay)
            startDay = earlier
            endDay = later
            wasClamped = true
        }

        val normalizedStart = startDay
        val normalizedEnd = endOfDayMillis(endDay)

        _startDate.value = normalizedStart
        _endDate.value = normalizedEnd
        _pickerStartMillis.value = DateRangePickerMillis.localStartOfDayToUtcPicker(normalizedStart)
        // Use endDay (start-of-day of the chosen end), not startDay — otherwise reopening the
        // picker collapsed the highlighted range back to a single day.
        _pickerEndMillis.value = DateRangePickerMillis.localStartOfDayToUtcPicker(endDay)
        _datePickerEpoch.value = _datePickerEpoch.value + 1

        return DateRangeApplyResult(
            startMillis = normalizedStart,
            endMillis = normalizedEnd,
            pickerStartMillis = _pickerStartMillis.value,
            pickerEndMillis = _pickerEndMillis.value,
            wasClamped = wasClamped
        )
    }

    fun setShowDateRangeDialog(show: Boolean) {
        if (show) {
            val localPickerStart = if (_startDate.value == allTimeStart) {
                todayStart
            } else {
                startOfDayMillis(_startDate.value)
            }
            val localPickerEnd = if (_endDate.value == allTimeEnd) {
                startOfDayMillis(todayEnd)
            } else {
                startOfDayMillis(_endDate.value)
            }
            _pickerStartMillis.value = DateRangePickerMillis.localStartOfDayToUtcPicker(localPickerStart)
            _pickerEndMillis.value = DateRangePickerMillis.localStartOfDayToUtcPicker(localPickerEnd)
            _datePickerEpoch.value = _datePickerEpoch.value + 1
        }
        _showDateRangeDialog.value = show
    }

    private fun presentDayBounds(): Pair<Long, Long> {
        val start = startOfDayMillis(System.currentTimeMillis())
        val end = endOfDayMillis(start)
        return start to end
    }

    private fun startOfDayMillis(millis: Long): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = millis }
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun endOfDayMillis(dayStartMillis: Long): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = dayStartMillis }
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }

    /** Real number of orders in the selected range — [ordersState] is only the loaded pages. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val orderCountState: StateFlow<Int> = combine(_startDate, _endDate) { start, end ->
        start to end
    }.flatMapLatest { (start, end) -> orderRepository.getOrderCountForDay(start, end) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val grossSalesState: StateFlow<Double?> = combine(_startDate, _endDate) { start, end ->
        start to end
    }.flatMapLatest { (start, end) -> orderRepository.getGrossSalesForDay(start, end) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val discountsState: StateFlow<Double?> = combine(_startDate, _endDate) { start, end ->
        start to end
    }.flatMapLatest { (start, end) -> orderRepository.getDiscountsGivenForDay(start, end) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val netRevenueState: StateFlow<Double?> = combine(_startDate, _endDate) { start, end ->
        start to end
    }.flatMapLatest { (start, end) -> orderRepository.getNetRevenueForDay(start, end) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val cashSalesState: StateFlow<Double?> = combine(_startDate, _endDate) { start, end ->
        start to end
    }.flatMapLatest { (start, end) -> orderRepository.getCashSalesForDay(start, end) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val gcashSalesState: StateFlow<Double?> = combine(_startDate, _endDate) { start, end ->
        start to end
    }.flatMapLatest { (start, end) -> orderRepository.getGcashSalesForDay(start, end) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val topSellingItemState: StateFlow<Pair<String, Int>?> = combine(_startDate, _endDate) { start, end ->
        start to end
    }.flatMapLatest { (start, end) -> orderRepository.getTopSellingItemForDay(start, end) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val expensesListState: StateFlow<List<Expense>> = combine(_startDate, _endDate) { start, end ->
        start to end
    }.flatMapLatest { (start, end) -> expenseRepository.getExpensesForDay(start, end) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalExpensesState: StateFlow<Double?> = combine(_startDate, _endDate) { start, end ->
        start to end
    }.flatMapLatest { (start, end) -> expenseRepository.getTotalExpensesForDay(start, end) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val appConfigState: StateFlow<AppConfig?> = appConfigRepository.getAppConfig()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val cashierSalesTodayState: StateFlow<Map<String, Double>> =
        orderRepository.observeCashierSalesForDay(todayStart, todayEnd)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // --- Per-food sales breakdown (separate from the shop-wide Z-Reading) ---
    // The owner wants each food/drink totaled on its own, viewable per week and per month.
    // This is deliberately independent of the date-range filter above so it never touches
    // the existing history behaviour.
    private val _breakdownPeriod = MutableStateFlow(BreakdownPeriod.WEEK)
    val breakdownPeriod: StateFlow<BreakdownPeriod> = _breakdownPeriod.asStateFlow()

    fun setBreakdownPeriod(period: BreakdownPeriod) {
        _breakdownPeriod.value = period
    }

    private fun breakdownRangeFor(period: BreakdownPeriod): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val end = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        when (period) {
            BreakdownPeriod.WEEK -> {
                calendar.firstDayOfWeek = Calendar.MONDAY
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            }
            BreakdownPeriod.MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
            }
        }
        val start = calendar.timeInMillis
        return start to end
    }

    /** Start/end epoch millis of the currently selected breakdown window, for the range label. */
    val breakdownRangeState: StateFlow<Pair<Long, Long>> =
        _breakdownPeriod
            .map { breakdownRangeFor(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), breakdownRangeFor(BreakdownPeriod.WEEK))

    @OptIn(ExperimentalCoroutinesApi::class)
    val itemSalesBreakdownState: StateFlow<List<com.example.cattasticpos.domain.model.ItemSalesBreakdown>> =
        _breakdownPeriod.flatMapLatest { period ->
            val (start, end) = breakdownRangeFor(period)
            orderRepository.getItemSalesBreakdownForRange(start, end)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateConfig(targetSales: Double, startingCashFloat: Double, pinHash: String) {
        viewModelScope.launch {
            appConfigRepository.updateConfig(targetSales, startingCashFloat, pinHash)
        }
    }

    suspend fun saveBusinessGoals(targetSales: Double, startingCashFloat: Double): Boolean {
        val config = appConfigState.value
            ?: appConfigRepository.getAppConfig().first { it != null }
            ?: return false
        appConfigRepository.updateConfig(targetSales, startingCashFloat, config.pinHash)
        return true
    }

    suspend fun saveConfigWithPinVerification(
        targetSales: Double,
        startingCashFloat: Double,
        currentPin: String,
        newPin: String
    ): Boolean {
        val config = appConfigRepository.getAppConfig().first { it != null } ?: return false
        val normalizedCurrentPin = currentPin.trim()
        if (!AppConfig.verifyPin(normalizedCurrentPin, config.pinHash)) {
            return false
        }
        val finalPinHash = if (newPin.trim().length == 4) {
            AppConfig.hashPin(newPin.trim())
        } else {
            config.pinHash
        }
        appConfigRepository.updateConfig(targetSales, startingCashFloat, finalPinHash)
        return true
    }

    fun updateSyncConfig(supabaseUrl: String, supabaseAnonKey: String) {
        viewModelScope.launch {
            appConfigRepository.updateSyncConfig(supabaseUrl, supabaseAnonKey)
            com.example.cattasticpos.worker.SyncWorker.triggerImmediateSync(application)
        }
    }

    fun addCashier(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val config = appConfigState.value ?: return@launch
            val updated = config.cashiers + Cashier(
                id = Cashier.newId(),
                name = trimmed,
                pinHash = AppConfig.DEFAULT_PIN_HASH
            )
            appConfigRepository.updatePaymentConfig(updated, config.gcashAccounts)
        }
    }

    fun removeCashier(cashierId: String) {
        viewModelScope.launch {
            val config = appConfigState.value ?: return@launch
            val updated = config.cashiers.filterNot { it.id == cashierId }
            if (updated.isEmpty()) return@launch
            appConfigRepository.updatePaymentConfig(updated, config.gcashAccounts)
        }
    }

    fun selectActiveCashier(cashierId: String) {
        viewModelScope.launch {
            appConfigRepository.updateActiveCashier(cashierId)
        }
    }

    fun addGcashAccount(label: String) {
        val trimmed = label.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val config = appConfigState.value ?: return@launch
            val updated = config.gcashAccounts + GcashAccount(
                id = GcashAccount.newId(),
                label = trimmed
            )
            appConfigRepository.updatePaymentConfig(config.cashiers, updated)
        }
    }

    fun removeGcashAccount(accountId: String) {
        viewModelScope.launch {
            val config = appConfigState.value ?: return@launch
            val updated = config.gcashAccounts.filterNot { it.id == accountId }
            if (updated.isEmpty()) return@launch
            appConfigRepository.updatePaymentConfig(config.cashiers, updated)
        }
    }

    fun updateThemeAccent(themeAccentId: String) {
        viewModelScope.launch {
            appConfigRepository.updateThemeAccent(themeAccentId)
        }
    }

    suspend fun verifyAdminPin(pin: String): Boolean {
        val config = appConfigRepository.getAppConfig().first { it != null } ?: return false
        return AppConfig.verifyPin(pin.trim(), config.pinHash)
    }

    fun voidOrder(orderId: Long, reason: String) {
        viewModelScope.launch {
            val result = voidOrderUseCase(orderId, reason, cashierId = null)
            if (result.isFailure) {
                _exportMessage.value = "Void failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun toggleOrderServed(orderId: Long) {
        viewModelScope.launch {
            val order = orderRepository.getOrderById(orderId) ?: return@launch
            orderRepository.setOrderServed(orderId, !order.isServed)
            // The row is PENDING now; push it before the next catch-up download runs.
            com.example.cattasticpos.worker.SyncWorker.triggerImmediateSync(application)
        }
    }

    fun updateOrder(orderId: Long, cartItems: List<CartItem>, discountStrategy: DiscountStrategy) {
        viewModelScope.launch {
            val result = updateOrderUseCase(orderId, cartItems, discountStrategy)
            _exportMessage.value = if (result.isSuccess) {
                com.example.cattasticpos.worker.SyncWorker.triggerImmediateSync(application)
                "Receipt #${String.format("%04d", orderId)} updated."
            } else {
                "Update failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun printZReading(summary: ZReadingSummary) {
        viewModelScope.launch {
            val result = receiptPrinterService.printZReading(summary)
            _exportMessage.value = if (result.isSuccess) {
                "Z-Reading sent to printer."
            } else {
                "Z-Reading print failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage.asStateFlow()

    fun exportData() {
        viewModelScope.launch {
            val orders = ordersState.value
            val expenses = expensesListState.value
            val result = exportDataUseCase(orders, expenses)
            if (result.isSuccess) {
                _exportMessage.value = "Exported to Downloads: ${result.getOrNull()}"
            } else {
                _exportMessage.value = "Export Failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun clearExportMessage() {
        _exportMessage.value = null
    }

    companion object {
        private const val PAGE_SIZE = 50

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as CattasticPosApp
                return HistoryViewModel(
                    application,
                    application.container.orderRepository,
                    application.container.expenseRepository,
                    application.container.exportDataUseCase,
                    application.container.appConfigRepository,
                    application.container.voidOrderUseCase,
                    application.container.updateOrderUseCase,
                    application.container.getMenuUseCase,
                    application.container.receiptPrinterService
                ) as T
            }
        }
    }
}
