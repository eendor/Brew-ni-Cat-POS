package com.example.cattasticpos.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.example.cattasticpos.data.local.entity.OrderEntity
import com.example.cattasticpos.data.local.entity.OrderItemEntity
import kotlinx.coroutines.flow.Flow

data class OrderWithItems(
    @Embedded val order: OrderEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "orderId"
    )
    val items: List<OrderItemEntity>
)

data class TopSellingItemResult(
    val itemName: String,
    val totalQuantity: Int
)

data class CashierSalesResult(
    val cashierId: String?,
    val totalSales: Double?
)

data class ItemSalesBreakdownResult(
    val itemName: String,
    val categoryName: String?,
    val totalQuantity: Int,
    val totalSales: Double
)

@Dao
interface OrderDao {
    @Insert
    suspend fun insertOrder(order: OrderEntity): Long

    @Insert
    suspend fun insertOrderItems(items: List<OrderItemEntity>)

    @Transaction
    suspend fun insertOrderWithItems(order: OrderEntity, items: List<OrderItemEntity>): Long {
        val orderId = insertOrder(order)
        if (items.isNotEmpty()) {
            insertOrderItems(items.map { it.copy(orderId = orderId) })
        }
        return orderId
    }

    @Transaction
    @Query(
        """
        SELECT * FROM orders
        WHERE timestamp >= :startDate AND timestamp <= :endDate
        AND timestamp < :beforeTimestamp
        AND isVoided = 0
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    fun observeOrdersPage(startDate: Long, endDate: Long, beforeTimestamp: Long, limit: Int): Flow<List<OrderWithItems>>

    @Transaction
    @Query(
        """
        SELECT * FROM orders
        WHERE timestamp >= :startDate AND timestamp <= :endDate
        AND timestamp < :beforeTimestamp
        AND isVoided = 0
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    suspend fun getOrdersPage(startDate: Long, endDate: Long, beforeTimestamp: Long, limit: Int): List<OrderWithItems>

    @Transaction
    @Query("SELECT * FROM orders WHERE id = :orderId")
    suspend fun getOrderWithItems(orderId: Long): OrderWithItems?

    @Transaction
    @Query("SELECT * FROM orders WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getOrderByRemoteId(remoteId: Long): OrderWithItems?

    @Query("UPDATE orders SET remoteId = :remoteId WHERE id = :id")
    suspend fun setRemoteId(id: Long, remoteId: Long)

    /**
     * Orders awaiting upload. Unlike [getOrdersPage] this intentionally does NOT filter out
     * voided rows, so a locally-voided order still propagates its void to the cloud.
     */
    @Transaction
    @Query("SELECT * FROM orders WHERE syncStatus = 'PENDING'")
    suspend fun getPendingSyncOrders(): List<OrderWithItems>

    @Query("SELECT itemName, SUM(quantity) as totalQuantity FROM order_items JOIN orders ON order_items.orderId = orders.id WHERE orders.timestamp >= :startOfDay AND orders.timestamp <= :endOfDay AND orders.isVoided = 0 GROUP BY itemName ORDER BY totalQuantity DESC LIMIT 1")
    fun getTopSellingItemForDay(startOfDay: Long, endOfDay: Long): Flow<TopSellingItemResult?>

    /**
     * Counts every order in the range. The history list is paginated ([observeOrdersPage]), so
     * its size only ever reflects the pages loaded so far — the Z-Reading needs the real total.
     */
    @Query("SELECT COUNT(*) FROM orders WHERE timestamp >= :startOfDay AND timestamp <= :endOfDay AND isVoided = 0")
    fun getOrderCountForDay(startOfDay: Long, endOfDay: Long): Flow<Int>

    @Query("SELECT SUM(subtotal) FROM orders WHERE timestamp >= :startOfDay AND timestamp <= :endOfDay AND isVoided = 0")
    fun getGrossSalesForDay(startOfDay: Long, endOfDay: Long): Flow<Double?>

    @Query("SELECT SUM(discountDeduction) FROM orders WHERE timestamp >= :startOfDay AND timestamp <= :endOfDay AND isVoided = 0")
    fun getDiscountsGivenForDay(startOfDay: Long, endOfDay: Long): Flow<Double?>

    @Query("SELECT SUM(total) FROM orders WHERE timestamp >= :startOfDay AND timestamp <= :endOfDay AND isVoided = 0")
    fun getNetRevenueForDay(startOfDay: Long, endOfDay: Long): Flow<Double?>

    @Query("SELECT SUM(total) FROM orders WHERE timestamp >= :startOfDay AND timestamp <= :endOfDay AND paymentMethod = 'CASH' AND isVoided = 0")
    fun getCashSalesForDay(startOfDay: Long, endOfDay: Long): Flow<Double?>

    @Query("SELECT SUM(total) FROM orders WHERE timestamp >= :startOfDay AND timestamp <= :endOfDay AND paymentMethod = 'GCASH' AND isVoided = 0")
    fun getGcashSalesForDay(startOfDay: Long, endOfDay: Long): Flow<Double?>

    @Query(
        """
        SELECT cashierId, SUM(total) as totalSales FROM orders
        WHERE timestamp >= :startOfDay AND timestamp <= :endOfDay AND isVoided = 0
        GROUP BY cashierId
        """
    )
    fun observeCashierSalesForDay(startOfDay: Long, endOfDay: Long): Flow<List<CashierSalesResult>>

    /**
     * Per-food sales totals for the History food/drink breakdown. Groups active order lines by
     * item name so each food (Takoyaki, Fries, every Buldak variant, every drink) carries its own
     * running total, independent of the shop-wide Z-Reading. Category name is resolved via a LEFT
     * JOIN so lines whose item was later removed from the catalog still appear.
     */
    @Query(
        """
        SELECT oi.itemName AS itemName,
               c.name AS categoryName,
               SUM(oi.quantity) AS totalQuantity,
               SUM(oi.totalPrice) AS totalSales
        FROM order_items oi
        JOIN orders o ON oi.orderId = o.id
        LEFT JOIN items i ON oi.itemId = i.id
        LEFT JOIN categories c ON i.categoryId = c.id
        WHERE o.timestamp >= :startOfDay AND o.timestamp <= :endOfDay AND o.isVoided = 0
        GROUP BY oi.itemName, c.name
        ORDER BY totalSales DESC
        """
    )
    fun getItemSalesBreakdownForRange(startOfDay: Long, endOfDay: Long): Flow<List<ItemSalesBreakdownResult>>

    @Query("DELETE FROM orders WHERE id = :orderId")
    suspend fun deleteOrderEntity(orderId: Long)

    @Query("DELETE FROM order_items WHERE orderId = :orderId")
    suspend fun deleteOrderItemsForOrder(orderId: Long)

    @Transaction
    suspend fun deleteOrderWithItems(orderId: Long) {
        deleteOrderItemsForOrder(orderId)
        deleteOrderEntity(orderId)
    }

    /**
     * Also re-queues the row for upload. Without the PENDING flip the served state stayed
     * local forever, and the next catch-up download reconciled it straight back to the
     * cloud's stale is_served value.
     */
    @Query("UPDATE orders SET isServed = :isServed, syncStatus = 'PENDING' WHERE id = :orderId")
    suspend fun setOrderServed(orderId: Long, isServed: Boolean)

    @Update
    suspend fun updateOrderEntity(order: OrderEntity)

    @Transaction
    suspend fun replaceOrderWithItems(order: OrderEntity, items: List<OrderItemEntity>) {
        updateOrderEntity(order)
        deleteOrderItemsForOrder(order.id)
        if (items.isNotEmpty()) {
            insertOrderItems(items.map { it.copy(orderId = order.id) })
        }
    }
}
