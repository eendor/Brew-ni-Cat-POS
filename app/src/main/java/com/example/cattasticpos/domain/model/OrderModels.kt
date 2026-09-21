package com.example.cattasticpos.domain.model

import java.util.Locale

data class Order(
    val id: Long = 0,
    val timestamp: Long,
    val subtotal: Double,
    val discountDeduction: Double,
    val discountLabel: String,
    val total: Double,
    val paymentMethod: String,
    val paymentReference: String?,
    val cashierId: String? = null,
    val cashierName: String? = null,
    val tableLabel: String? = null,
    val isServed: Boolean = false,
    val deviceId: String = "",
    val syncStatus: String = "PENDING",
    val isVoided: Boolean = false,
    val lastSyncedAt: Long = 0,
    val remoteId: Long? = null,
    val items: List<OrderItem>
) {
    val receiptNumber: String
        get() {
            val targetId = remoteId ?: id
            if (targetId <= 0L) return "----"
            val displayId = if (targetId >= 1_000_000_000L) targetId % 1_000_000_000L else targetId
            return String.format(Locale.US, "%04d", displayId)
        }
}

data class OrderItem(
    val id: Long,
    val orderId: Long,
    val itemId: String,
    val itemName: String,
    val variantId: String,
    val variantName: String,
    val flavor: String?,
    val quantity: Int,
    val unitPrice: Double,
    val totalPrice: Double
)

/**
 * Per-food sales total for the food/drink breakdown in History. Each menu item (Takoyaki, Fries,
 * every Buldak variant, every drink, combos) becomes its own row with its own running total,
 * kept entirely separate from the shop-wide Z-Reading figures.
 */
data class ItemSalesBreakdown(
    val itemName: String,
    val categoryName: String?,
    val totalQuantity: Int,
    val totalSales: Double
)
