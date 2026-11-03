package com.example.cattasticpos.ui.dashboard

import com.example.cattasticpos.domain.model.CartItem
import com.example.cattasticpos.domain.model.Cashier
import com.example.cattasticpos.domain.model.GcashAccount
import com.example.cattasticpos.domain.model.Category
import com.example.cattasticpos.domain.model.Item
import com.example.cattasticpos.domain.strategy.DiscountStrategy
import com.example.cattasticpos.domain.strategy.NoDiscountStrategy
import com.example.cattasticpos.domain.model.InventoryItem
import com.example.cattasticpos.domain.model.RecipeMapping

data class HeldQueue(
    val id: String,
    val timestamp: Long,
    val items: List<CartItem>,
    val tableLabel: String? = null
)

enum class OrderServiceType {
    DINE_IN,
    TAKE_OUT
}

data class PaymentDialogState(
    val selectedTabIndex: Int = 0,
    val amountTenderedStr: String = "",
    /** Split tab (FR-096): the GCash part; the cash part is the rest of the total. */
    val splitGcashStr: String = "",
    val gcashReference: String = "",
    val receivingAccount: String = "",
    val serviceType: OrderServiceType = OrderServiceType.DINE_IN,
    val tableNumber: String = ""
)

data class DashboardUiState(
    val categories: List<Category> = emptyList(),
    val allMenuItems: List<Item> = emptyList(),
    val menuItems: List<Item> = emptyList(),
    val selectedCategoryId: String = "",
    val activeCart: List<CartItem> = emptyList(),
    val selectedDiscountStrategy: DiscountStrategy = NoDiscountStrategy(),
    val subtotal: Double = 0.0,
    val discountDeduction: Double = 0.0,
    val discountLabel: String = "None",
    val total: Double = 0.0,
    val selectedConfiguringItem: Item? = null,
    val checkoutSuccessEvent: String? = null,
    val snackbarMessage: String? = null,
    val heldQueues: List<HeldQueue> = emptyList(),
    val showQueuesDialog: Boolean = false,
    val showHoldOrderDialog: Boolean = false,
    val currentQueueId: String? = null,
    val activeTableLabel: String? = null,
    val cashiers: List<Cashier> = emptyList(),
    val gcashAccounts: List<GcashAccount> = emptyList(),
    val selectedCashierId: String = "cashier_default",
    val showPaymentDialog: Boolean = false,
    val isCheckoutProcessing: Boolean = false,
    val paymentDialogState: PaymentDialogState = PaymentDialogState(),
    val showExpenseDialog: Boolean = false,
    val inventory: List<InventoryItem> = emptyList(),
    val recipeMappings: List<RecipeMapping> = emptyList(),
    // Menu item ids whose recipe uses an inventory item at/below its reorder threshold.
    // Precomputed when inventory/mappings change so catalog cards do a Set lookup instead
    // of scanning both lists on every recomposition.
    val lowStockItemIds: Set<String> = emptySet()
)
