package com.example.cattasticpos.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.cattasticpos.CattasticPosApp
import com.example.cattasticpos.domain.catalog.ProductAddOnCatalog
import com.example.cattasticpos.domain.model.CartItem
import com.example.cattasticpos.domain.model.CartKey
import com.example.cattasticpos.domain.model.CartLineSelection
import com.example.cattasticpos.domain.model.Item
import com.example.cattasticpos.domain.model.Variant
import com.example.cattasticpos.domain.strategy.DiscountStrategy
import com.example.cattasticpos.domain.strategy.NoDiscountStrategy
import java.util.Locale
import com.example.cattasticpos.domain.usecase.CalculateCartUseCase
import com.example.cattasticpos.domain.usecase.CheckoutUseCase
import com.example.cattasticpos.domain.usecase.GetMenuUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.example.cattasticpos.domain.repository.AppConfigRepository
import com.example.cattasticpos.domain.repository.ExpenseRepository
import com.example.cattasticpos.domain.repository.InventoryRepository
import com.example.cattasticpos.domain.repository.RecipeRepository
import com.example.cattasticpos.domain.service.ReceiptPrinterService
import com.example.cattasticpos.domain.model.Expense
import java.util.UUID

class DashboardViewModel(
    private val application: android.app.Application,
    private val getMenuUseCase: GetMenuUseCase,
    private val calculateCartUseCase: CalculateCartUseCase,
    private val checkoutUseCase: CheckoutUseCase,
    private val expenseRepository: ExpenseRepository,
    private val inventoryRepository: InventoryRepository,
    private val receiptPrinterService: ReceiptPrinterService,
    private val recipeRepository: RecipeRepository,
    private val appConfigRepository: AppConfigRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var allItems: List<Item> = emptyList()

    init {
        viewModelScope.launch {
            inventoryRepository.getAllInventory().collect { invList ->
                _uiState.update {
                    it.copy(
                        inventory = invList,
                        lowStockItemIds = computeLowStockItemIds(invList, it.recipeMappings)
                    )
                }
            }
        }
        viewModelScope.launch {
            recipeRepository.getAllMappings().collect { mappings ->
                _uiState.update {
                    it.copy(
                        recipeMappings = mappings,
                        lowStockItemIds = computeLowStockItemIds(it.inventory, mappings)
                    )
                }
            }
        }
        viewModelScope.launch {
            getMenuUseCase().collect { menuResult ->
                val categories = menuResult.categories
                allItems = menuResult.items
                
                _uiState.update { state ->
                    val selectedCatId = state.selectedCategoryId
                        .takeIf { id -> categories.any { it.id == id } }
                        ?: categories.firstOrNull()?.id.orEmpty()
                    state.copy(
                        categories = categories,
                        allMenuItems = allItems,
                        selectedCategoryId = selectedCatId,
                        menuItems = filterItemsByCategoryId(allItems, selectedCatId)
                    )
                }
            }
        }
        viewModelScope.launch {
            appConfigRepository.getAppConfig().collect { config ->
                val cashiers = config?.cashiers.orEmpty()
                val gcashAccounts = config?.gcashAccounts.orEmpty()
                _uiState.update { state ->
                    val activeFromConfig = config?.activeCashierId
                    val selected = when {
                        !activeFromConfig.isNullOrBlank() && cashiers.any { it.id == activeFromConfig } -> activeFromConfig
                        state.selectedCashierId.isNotBlank() && cashiers.any { it.id == state.selectedCashierId } -> state.selectedCashierId
                        else -> cashiers.firstOrNull()?.id ?: "cashier_default"
                    }
                    val defaultGcash = gcashAccounts.firstOrNull()?.label.orEmpty()
                    val paymentState = if (state.showPaymentDialog) {
                        val currentAccount = state.paymentDialogState.receivingAccount
                        val validAccount = gcashAccounts.any { it.label == currentAccount }
                        state.paymentDialogState.copy(
                            receivingAccount = when {
                                currentAccount.isNotBlank() && validAccount -> currentAccount
                                defaultGcash.isNotBlank() -> defaultGcash
                                else -> currentAccount
                            }
                        )
                    } else {
                        state.paymentDialogState
                    }
                    state.copy(
                        cashiers = cashiers,
                        gcashAccounts = gcashAccounts,
                        selectedCashierId = if (cashiers.any { it.id == selected }) {
                            selected
                        } else {
                            cashiers.firstOrNull()?.id ?: "cashier_default"
                        },
                        paymentDialogState = paymentState
                    )
                }
            }
        }
    }

    private fun computeLowStockItemIds(
        inventory: List<com.example.cattasticpos.domain.model.InventoryItem>,
        mappings: List<com.example.cattasticpos.domain.model.RecipeMapping>
    ): Set<String> {
        if (inventory.isEmpty() || mappings.isEmpty()) return emptySet()
        val lowInventoryIds = inventory
            .asSequence()
            .filter { it.currentStock <= it.reorderThreshold }
            .map { it.id }
            .toSet()
        if (lowInventoryIds.isEmpty()) return emptySet()
        return mappings
            .asSequence()
            .filter { it.inventoryItemId in lowInventoryIds }
            .map { it.menuItemId }
            .toSet()
    }

    private fun filterItemsByCategoryId(items: List<Item>, categoryId: String): List<Item> {
        val filtered = if (categoryId.isBlank()) items else items.filter { it.categoryId == categoryId }
        return filtered.sortedBy { item -> menuItemSortIndex(item.id) }
    }

    private fun menuItemSortIndex(itemId: String): Int {
        val order = listOf(
            "bite_takoyaki",
            "bite_fries",
            "bite_nachos",
            "drink_cat_feine",
            "drink_oreo",
            "drink_matcha",
            "drink_soda",
            "combo_single_paw",
            "combo_couple_cats",
            "combo_association"
        )
        val index = order.indexOf(itemId)
        return if (index >= 0) index else order.size
    }

    fun selectCategory(categoryId: String) {
        _uiState.update { state ->
            state.copy(
                selectedCategoryId = categoryId,
                menuItems = filterItemsByCategoryId(allItems, categoryId)
            )
        }
    }

    fun showConfigurationSheet(item: Item) {
        if (ProductAddOnCatalog.isDirectAddTakeoutItem(item)) {
            val variant = item.variants.firstOrNull() ?: return
            applyCartMutation(item, variant, flavor = null)
            return
        }
        _uiState.update { state ->
            state.copy(selectedConfiguringItem = item)
        }
    }

    fun hideConfigurationSheet() {
        _uiState.update { state ->
            state.copy(selectedConfiguringItem = null)
        }
    }

    fun addToCart(variant: Variant, flavor: String?) {
        val currentItem = _uiState.value.selectedConfiguringItem ?: return
        // Never auto-inject optional add-ons (e.g. Take-out Box +₱10); only keep what the sheet encoded.
        applyCartMutation(currentItem, variant, flavor)
    }

    private fun applyCartMutation(item: Item, variant: Variant, flavor: String?) {
        _uiState.update { state ->
            val sanitizedFlavor = sanitizeFlavorAddOns(item, flavor)
            val cartKey = CartKey.from(item, variant, sanitizedFlavor)
            val existingIndex = state.activeCart.indexOfFirst { it.key == cartKey }

            val tentativeCart = if (existingIndex != -1) {
                state.activeCart.mapIndexed { index, cartItem ->
                    if (index == existingIndex) {
                        cartItem.copy(quantity = cartItem.quantity + 1)
                    } else {
                        cartItem
                    }
                }
            } else {
                state.activeCart + CartItem(
                    key = cartKey,
                    item = item,
                    variant = variant,
                    flavor = sanitizedFlavor,
                    quantity = 1
                )
            }

            val updatedCart = sanitizeCart(tentativeCart)
            val calculation = calculateCartUseCase(updatedCart, state.selectedDiscountStrategy)
            state.copy(
                activeCart = updatedCart,
                subtotal = calculation.subtotal,
                discountDeduction = calculation.discountDeduction,
                discountLabel = calculation.discountLabel,
                total = calculation.total,
                selectedConfiguringItem = null,
                snackbarMessage = "${item.name} added to cart!"
            )
        }
    }

    /**
     * Drops unrequested Take-out Box add-on labels from non-standalone lines and
     * recomputes cart keys so totals never include a phantom +₱10 packaging fee.
     * Standalone direct-add SKUs like Take-out Box and Cat Treats are kept.
     */
    private fun sanitizeCart(cart: List<CartItem>): List<CartItem> {
        return cart.mapNotNull { cartItem ->
            if (ProductAddOnCatalog.isDirectAddTakeoutItem(cartItem.item) ||
                cartItem.item.id.equals("bite_takeout_box", ignoreCase = true) ||
                cartItem.item.categoryId.equals("cat_takeout", ignoreCase = true) ||
                cartItem.item.id.equals("bite_cat_treats", ignoreCase = true) ||
                cartItem.item.categoryId.equals("cat_treats", ignoreCase = true)
            ) {
                return@mapNotNull cartItem
            }
            val cleanedFlavor = sanitizeFlavorAddOns(cartItem.item, cartItem.flavor)
            if (cleanedFlavor == cartItem.flavor) {
                cartItem
            } else {
                cartItem.copy(
                    key = CartKey.from(cartItem.item, cartItem.variant, cleanedFlavor),
                    flavor = cleanedFlavor
                )
            }
        }
    }

    /**
     * Flavor strings only retain add-ons the cashier explicitly chose in the config sheet.
     * This never injects Take-out Box; it only strips a stale " + Take-out Box" if present
     * without a matching catalog selection path (defensive — sheet starts unselected).
     */
    private fun sanitizeFlavorAddOns(item: Item, flavor: String?): String? {
        if (flavor.isNullOrBlank()) return flavor
        val selection = CartLineSelection.parse(flavor, item.id)
        if (selection.addOnLabels.isEmpty()) return flavor
        // Known, not offered — a held order or a reopened receipt carrying a retired add-on must
        // keep it (and its price) instead of having the label quietly stripped out.
        val allowedLabels = ProductAddOnCatalog.knownAddOnsForItem(item).map { it.label }.toSet()
        // Keep only labels that exist as optional choices for this item (never invent new ones).
        val kept = selection.addOnLabels.filter { it in allowedLabels }
        if (kept == selection.addOnLabels) return flavor
        return CartLineSelection(selection.baseFlavor, selection.coffeeOption, kept).encode()
    }

    fun clearSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun changeQuantity(cartItemId: String, delta: Int) {
        _uiState.update { state ->
            val tentativeCart = state.activeCart.mapNotNull { cartItem ->
                if (cartItem.id == cartItemId) {
                    val newQty = cartItem.quantity + delta
                    if (newQty <= 0) null else cartItem.copy(quantity = newQty)
                } else {
                    cartItem
                }
            }

            val updatedCart = sanitizeCart(tentativeCart)
            val calculation = calculateCartUseCase(updatedCart, state.selectedDiscountStrategy)
            val cartCleared = updatedCart.isEmpty()
            state.copy(
                activeCart = updatedCart,
                activeTableLabel = if (cartCleared) null else state.activeTableLabel,
                currentQueueId = if (cartCleared) null else state.currentQueueId,
                subtotal = calculation.subtotal,
                discountDeduction = calculation.discountDeduction,
                discountLabel = calculation.discountLabel,
                total = calculation.total
            )
        }
    }

    fun selectDiscount(strategy: DiscountStrategy) {
        _uiState.update { state ->
            val sanitized = sanitizeCart(state.activeCart)
            val calculation = calculateCartUseCase(sanitized, strategy)
            state.copy(
                activeCart = sanitized,
                selectedDiscountStrategy = strategy,
                subtotal = calculation.subtotal,
                discountDeduction = calculation.discountDeduction,
                discountLabel = calculation.discountLabel,
                total = calculation.total
            )
        }
    }

    fun confirmCheckout(paymentMethod: String, paymentReference: String?) {
        val sanitizedCart = sanitizeCart(_uiState.value.activeCart)
        val currentStrategy = _uiState.value.selectedDiscountStrategy
        if (sanitizedCart.isEmpty() || _uiState.value.isCheckoutProcessing) return

        viewModelScope.launch {
            val state = _uiState.value
            val cashierName = state.cashiers.find { it.id == state.selectedCashierId }?.name
            val calculation = calculateCartUseCase(sanitizedCart, currentStrategy)
            _uiState.update {
                it.copy(
                    isCheckoutProcessing = true,
                    activeCart = sanitizedCart,
                    subtotal = calculation.subtotal,
                    discountDeduction = calculation.discountDeduction,
                    discountLabel = calculation.discountLabel,
                    total = calculation.total
                )
            }

            val result = withContext(Dispatchers.IO) {
                checkoutUseCase(
                    sanitizedCart,
                    currentStrategy,
                    paymentMethod,
                    paymentReference,
                    cashierId = state.selectedCashierId,
                    cashierName = cashierName,
                    tableLabel = resolveCheckoutTableLabel(state.activeTableLabel, state.paymentDialogState)
                )
            }

            if (result.isSuccess) {
                result.getOrNull()?.let { order ->
                    com.example.cattasticpos.worker.SyncWorker.triggerImmediateSync(application)
                    _uiState.update { ui ->
                        val freshCalculation = calculateCartUseCase(emptyList(), ui.selectedDiscountStrategy)
                        ui.copy(
                            isCheckoutProcessing = false,
                            activeCart = emptyList(),
                            currentQueueId = null,
                            selectedDiscountStrategy = NoDiscountStrategy(),
                            subtotal = freshCalculation.subtotal,
                            discountDeduction = freshCalculation.discountDeduction,
                            discountLabel = freshCalculation.discountLabel,
                            total = freshCalculation.total,
                            showPaymentDialog = false,
                            paymentDialogState = PaymentDialogState(),
                            activeTableLabel = null,
                            snackbarMessage = "Order #${order.receiptNumber} placed successfully!"
                        )
                    }
                    viewModelScope.launch {
                        val printerResult = receiptPrinterService.printReceipt(order)
                        if (printerResult.isFailure) {
                            _uiState.update {
                                it.copy(
                                    snackbarMessage = "Printer: ${printerResult.exceptionOrNull()?.message}"
                                )
                            }
                        }
                    }
                }
            } else {
                _uiState.update {
                    it.copy(
                        isCheckoutProcessing = false,
                        snackbarMessage = "Checkout failed: ${result.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }

    fun clearCheckoutEvent() {
        // Kept for API compatibility; checkout now uses snackbarMessage only.
    }

    private fun resolveCheckoutTableLabel(
        heldLabel: String?,
        payment: PaymentDialogState
    ): String? {
        heldLabel?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        return when (payment.serviceType) {
            OrderServiceType.DINE_IN -> {
                val name = payment.tableNumber.trim()
                if (name.isNotEmpty()) name else "Dine In"
            }
            OrderServiceType.TAKE_OUT -> "Take Out"
        }
    }

    // Removed int queueCounter

    fun holdCurrentOrder(tableLabel: String?) {
        val currentCart = _uiState.value.activeCart
        if (currentCart.isEmpty()) return
        
        _uiState.update { state ->
            val queueId = state.currentQueueId ?: UUID.randomUUID().toString().substring(0, 8).uppercase()
            val label = tableLabel?.trim()?.takeIf { it.isNotBlank() }
            val newQueue = HeldQueue(
                id = queueId,
                timestamp = System.currentTimeMillis(),
                items = currentCart,
                tableLabel = label
            )
            val updatedQueues = state.heldQueues.filter { it.id != queueId } + newQueue
            val freshCalculation = calculateCartUseCase(emptyList(), state.selectedDiscountStrategy)
            state.copy(
                heldQueues = updatedQueues,
                currentQueueId = null,
                activeTableLabel = null,
                activeCart = emptyList(),
                subtotal = freshCalculation.subtotal,
                discountDeduction = freshCalculation.discountDeduction,
                discountLabel = freshCalculation.discountLabel,
                total = freshCalculation.total,
                showHoldOrderDialog = false
            )
        }
    }

    fun setShowHoldOrderDialog(show: Boolean) {
        _uiState.update { it.copy(showHoldOrderDialog = show) }
    }

    fun selectCashier(cashierId: String) {
        _uiState.update { it.copy(selectedCashierId = cashierId) }
        viewModelScope.launch {
            appConfigRepository.updateActiveCashier(cashierId)
        }
    }

    fun resumeOrder(queueId: String) {
        _uiState.update { state ->
            val queueToResume = state.heldQueues.find { it.id == queueId } ?: return@update state
            val resumedCart = queueToResume.items
            val updatedQueues = state.heldQueues.filter { it.id != queueId }
            val calculation = calculateCartUseCase(resumedCart, state.selectedDiscountStrategy)
            state.copy(
                heldQueues = updatedQueues,
                currentQueueId = queueId,
                activeTableLabel = queueToResume.tableLabel,
                activeCart = resumedCart,
                subtotal = calculation.subtotal,
                discountDeduction = calculation.discountDeduction,
                discountLabel = calculation.discountLabel,
                total = calculation.total,
                showQueuesDialog = false
            )
        }
    }

    fun setShowQueuesDialog(show: Boolean) {
        _uiState.update { state ->
            state.copy(showQueuesDialog = show)
        }
    }

    fun setShowPaymentDialog(show: Boolean) {
        _uiState.update { state ->
            val defaultAccount = state.gcashAccounts.firstOrNull()?.label.orEmpty()
            state.copy(
                showPaymentDialog = show,
                paymentDialogState = if (show) {
                    val currentAccount = state.paymentDialogState.receivingAccount
                    state.paymentDialogState.copy(
                        receivingAccount = when {
                            currentAccount.isNotBlank() && state.gcashAccounts.any { it.label == currentAccount } -> currentAccount
                            defaultAccount.isNotBlank() -> defaultAccount
                            else -> currentAccount
                        }
                    )
                } else {
                    PaymentDialogState()
                }
            )
        }
    }

    fun updatePaymentDialogState(update: PaymentDialogState.() -> PaymentDialogState) {
        _uiState.update { state ->
            state.copy(paymentDialogState = state.paymentDialogState.update())
        }
    }

    fun setPaymentDialogState(state: PaymentDialogState) {
        _uiState.update { it.copy(paymentDialogState = state) }
    }

    fun setShowExpenseDialog(show: Boolean) {
        _uiState.update { state ->
            state.copy(showExpenseDialog = show)
        }
    }

    fun saveExpense(description: String, amount: Double, recordedBy: String) {
        viewModelScope.launch {
            val expense = Expense(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                description = description,
                amount = amount,
                recordedBy = recordedBy
            )
            expenseRepository.saveExpense(expense)
            _uiState.update { state ->
                state.copy(showExpenseDialog = false)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as CattasticPosApp
                return DashboardViewModel(
                    application,
                    application.container.getMenuUseCase,
                    application.container.calculateCartUseCase,
                    application.container.checkoutUseCase,
                    application.container.expenseRepository,
                    application.container.inventoryRepository,
                    application.container.receiptPrinterService,
                    application.container.recipeRepository,
                    application.container.appConfigRepository
                ) as T
            }
        }
    }
}
