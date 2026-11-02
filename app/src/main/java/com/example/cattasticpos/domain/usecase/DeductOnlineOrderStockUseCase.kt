package com.example.cattasticpos.domain.usecase

import com.example.cattasticpos.domain.model.CartItem
import com.example.cattasticpos.domain.model.Item
import com.example.cattasticpos.domain.model.Order
import com.example.cattasticpos.domain.model.OnlineOrder
import com.example.cattasticpos.domain.model.OrderItem
import com.example.cattasticpos.domain.repository.InventoryRepository
import com.example.cattasticpos.domain.repository.MenuRepository
import com.example.cattasticpos.domain.repository.RecipeRepository
import kotlinx.coroutines.flow.first

/** Remembers which online orders already took their stock off this tablet. */
interface DeductedOrderStore {
    /** True the first time a reference is claimed on this tablet, false every time after. */
    fun claim(reference: String): Boolean
}

/**
 * The tablet that completes an online order takes its ingredients off the stock, once per order
 * reference (ADR-021). The database adds the sale, but the stock is counted on the tablet and
 * uploaded from it, so a deduction made in the database would be overwritten by the next upload.
 *
 * The reference is claimed before the stock moves. If the tablet fails halfway the stock stays
 * high until the next count, which is the trade the ADR accepts; the other way round, a retry
 * could take the same order's ingredients off twice.
 */
class DeductOnlineOrderStockUseCase(
    private val menuRepository: MenuRepository,
    private val recipeRepository: RecipeRepository,
    private val inventoryRepository: InventoryRepository,
    private val store: DeductedOrderStore
) {
    /** @return true when stock was taken off now, false when this order was already done. */
    suspend operator fun invoke(order: OnlineOrder): Boolean {
        if (!store.claim(order.reference)) return false

        val menu = menuRepository.getItems().first()
        val cartItems = toCartItems(order, menu)
        val mappingsByMenu = recipeRepository.getAllMappingsOnce().groupBy { it.menuItemId }
        cartItems.forEach { cartItem ->
            ComboBundleResolver.expandFromCartItem(cartItem).forEach { component ->
                val mappings = RecipeDeductionResolver.resolve(
                    mappingsByMenu[component.menuItemId].orEmpty(),
                    component.sizeVariantName,
                    component.flavor
                )
                mappings.forEach { mapping ->
                    val amount = mapping.deductionQuantity * component.quantity
                    if (amount > 0) inventoryRepository.decrementStock(mapping.inventoryItemId, amount)
                }
            }
        }
        return true
    }

    private fun toCartItems(order: OnlineOrder, menu: List<Item>): List<CartItem> {
        val sale = Order(
            timestamp = order.updatedAtMillis,
            subtotal = order.subtotal,
            discountDeduction = 0.0,
            discountLabel = "None",
            total = order.subtotal,
            paymentMethod = order.paymentPreference,
            paymentReference = order.reference,
            items = order.lines.map { line ->
                OrderItem(
                    id = 0L,
                    orderId = 0L,
                    itemId = line.itemId,
                    itemName = line.itemName,
                    variantId = line.variantId,
                    variantName = line.variantName,
                    flavor = line.flavor,
                    quantity = line.quantity,
                    unitPrice = line.unitPrice,
                    totalPrice = line.lineTotal
                )
            }
        )
        return OrderCartMapper.orderToCartItems(sale, menu)
    }
}
