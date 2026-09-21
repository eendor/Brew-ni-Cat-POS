package com.example.cattasticpos.data.repository

import androidx.room.withTransaction
import com.example.cattasticpos.data.local.PosDatabase
import com.example.cattasticpos.data.local.dao.OrderDao
import com.example.cattasticpos.data.local.dao.OrderWithItems
import com.example.cattasticpos.data.local.entity.OrderEntity
import com.example.cattasticpos.data.local.entity.OrderItemEntity
import com.example.cattasticpos.domain.model.Order
import com.example.cattasticpos.domain.model.OrderItem
import com.example.cattasticpos.domain.repository.OrderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class OrderRepositoryImpl(
    private val database: PosDatabase,
    private val authManager: com.example.cattasticpos.service.SupabaseAuthManager? = null,
    // Application-lifetime scope for the fire-and-forget void PATCH. Previously each void
    // spun up a throwaway CoroutineScope, which is never cancelled and leaks its job.
    private val externalScope: kotlinx.coroutines.CoroutineScope =
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
        )
) : OrderRepository {

    private val orderDao: OrderDao = database.orderDao()

    override fun observeOrdersPage(
        startDate: Long,
        endDate: Long,
        beforeTimestamp: Long,
        limit: Int
    ): Flow<List<Order>> {
        return orderDao.observeOrdersPage(startDate, endDate, beforeTimestamp, limit)
            .map { page -> page.map { it.toDomain() } }
    }

    override suspend fun getOrdersPage(
        startDate: Long,
        endDate: Long,
        beforeTimestamp: Long,
        limit: Int
    ): List<Order> {
        return orderDao.getOrdersPage(startDate, endDate, beforeTimestamp, limit).map { it.toDomain() }
    }

    override suspend fun getOrderById(orderId: Long): Order? {
        return orderDao.getOrderWithItems(orderId)?.toDomain()
    }

    override suspend fun updateOrder(order: Order): Order {
        val existing = orderDao.getOrderWithItems(order.id)
        val targetDeviceId = existing?.order?.deviceId ?: order.deviceId.takeIf { it.isNotEmpty() } ?: ""
        val lastSyncedAt = existing?.order?.lastSyncedAt ?: 0L
        val orderEntity = OrderEntity(
            id = order.id,
            timestamp = order.timestamp,
            subtotal = order.subtotal,
            discountDeduction = order.discountDeduction,
            discountLabel = order.discountLabel,
            total = order.total,
            paymentMethod = order.paymentMethod,
            paymentReference = order.paymentReference,
            cashierId = order.cashierId,
            cashierName = order.cashierName,
            tableLabel = order.tableLabel,
            isServed = order.isServed,
            deviceId = targetDeviceId,
            syncStatus = "PENDING",
            isVoided = order.isVoided,
            lastSyncedAt = lastSyncedAt,
            remoteId = existing?.order?.remoteId
        )
        val itemEntities = order.items.map { item ->
            OrderItemEntity(
                orderId = order.id,
                itemId = item.itemId,
                itemName = item.itemName,
                variantId = item.variantId,
                variantName = item.variantName,
                flavor = item.flavor,
                quantity = item.quantity,
                unitPrice = item.unitPrice,
                totalPrice = item.totalPrice
            )
        }
        database.withTransaction {
            orderDao.replaceOrderWithItems(orderEntity, itemEntities)
        }
        return order
    }

    override suspend fun saveOrder(order: Order): Order {
        val appConfig = database.appConfigDao().getAppConfigOnce()
        val localDeviceId = appConfig?.deviceId.orEmpty()
        val orderEntity = OrderEntity(
            id = 0,
            timestamp = order.timestamp,
            subtotal = order.subtotal,
            discountDeduction = order.discountDeduction,
            discountLabel = order.discountLabel,
            total = order.total,
            paymentMethod = order.paymentMethod,
            paymentReference = order.paymentReference,
            cashierId = order.cashierId,
            cashierName = order.cashierName,
            tableLabel = order.tableLabel,
            isServed = order.isServed,
            deviceId = localDeviceId,
            syncStatus = "PENDING"
        )
        val itemEntities = order.items.map { item ->
            OrderItemEntity(
                orderId = 0,
                itemId = item.itemId,
                itemName = item.itemName,
                variantId = item.variantId,
                variantName = item.variantName,
                flavor = item.flavor,
                quantity = item.quantity,
                unitPrice = item.unitPrice,
                totalPrice = item.totalPrice
            )
        }
        val newOrderId = database.withTransaction {
            orderDao.insertOrderWithItems(orderEntity, itemEntities)
        }
        return order.copy(
            id = newOrderId,
            items = order.items.map { it.copy(orderId = newOrderId) }
        )
    }

    override fun getTopSellingItemForDay(startOfDay: Long, endOfDay: Long): Flow<Pair<String, Int>?> {
        return orderDao.getTopSellingItemForDay(startOfDay, endOfDay).map { result ->
            result?.let { Pair(it.itemName, it.totalQuantity) }
        }
    }

    override fun getOrderCountForDay(startOfDay: Long, endOfDay: Long): Flow<Int> {
        return orderDao.getOrderCountForDay(startOfDay, endOfDay)
    }

    override fun getGrossSalesForDay(startOfDay: Long, endOfDay: Long): Flow<Double?> {
        return orderDao.getGrossSalesForDay(startOfDay, endOfDay)
    }

    override fun getDiscountsGivenForDay(startOfDay: Long, endOfDay: Long): Flow<Double?> {
        return orderDao.getDiscountsGivenForDay(startOfDay, endOfDay)
    }

    override fun getNetRevenueForDay(startOfDay: Long, endOfDay: Long): Flow<Double?> {
        return orderDao.getNetRevenueForDay(startOfDay, endOfDay)
    }

    override fun getCashSalesForDay(startOfDay: Long, endOfDay: Long): Flow<Double?> {
        return orderDao.getCashSalesForDay(startOfDay, endOfDay)
    }

    override fun getGcashSalesForDay(startOfDay: Long, endOfDay: Long): Flow<Double?> {
        return orderDao.getGcashSalesForDay(startOfDay, endOfDay)
    }

    override fun observeCashierSalesForDay(startOfDay: Long, endOfDay: Long): Flow<Map<String, Double>> {
        return orderDao.observeCashierSalesForDay(startOfDay, endOfDay).map { rows ->
            rows.mapNotNull { row ->
                val id = row.cashierId ?: return@mapNotNull null
                id to (row.totalSales ?: 0.0)
            }.toMap()
        }
    }

    override fun getItemSalesBreakdownForRange(
        startOfDay: Long,
        endOfDay: Long
    ): Flow<List<com.example.cattasticpos.domain.model.ItemSalesBreakdown>> {
        return orderDao.getItemSalesBreakdownForRange(startOfDay, endOfDay).map { rows ->
            rows.map { row ->
                com.example.cattasticpos.domain.model.ItemSalesBreakdown(
                    itemName = row.itemName,
                    categoryName = row.categoryName,
                    totalQuantity = row.totalQuantity,
                    totalSales = row.totalSales
                )
            }
        }
    }

    override suspend fun deleteOrder(orderId: Long) {
        // Soft delete: mark voided (kept for audit, hidden from lists) and queue for sync.
        val existingOrder = database.orderDao().getOrderWithItems(orderId) ?: return
        database.orderDao().updateOrderEntity(
            existingOrder.order.copy(
                isVoided = true,
                syncStatus = "PENDING",
                lastSyncedAt = System.currentTimeMillis()
            )
        )

        // Propagate the void immediately using the order's GLOBAL id. This is correct even when
        // voiding an order that originated on another device. Orders not yet uploaded
        // (remoteId == null) carry is_voided=true on their first POST via SyncWorker instead.
        val remoteId = existingOrder.order.remoteId ?: return
        externalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val config = database.appConfigDao().getAppConfigOnce() ?: return@launch
                val supabaseUrl = config.supabaseUrl.trim()
                val supabaseKey = config.supabaseAnonKey.trim()
                // No session -> skip the instant patch; the order is already PENDING so
                // SyncWorker propagates the void once signed in.
                val accessToken = authManager?.getValidAccessToken() ?: return@launch
                if (supabaseUrl.isNotEmpty() && supabaseKey.isNotEmpty()) {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .build()

                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val body = "{\"is_voided\": true}".toRequestBody(mediaType)
                    val patchOrderReq = okhttp3.Request.Builder()
                        .url("$supabaseUrl/rest/v1/orders?id=eq.$remoteId")
                        .patch(body)
                        .header("apikey", supabaseKey)
                        .header("Authorization", "Bearer $accessToken")
                        .header("Content-Type", "application/json")
                        .build()
                    client.newCall(patchOrderReq).execute().close()

                    android.util.Log.i("OrderRepositoryImpl", "Marked order (remote $remoteId) as voided on Supabase.")
                }
            } catch (e: Exception) {
                android.util.Log.e("OrderRepositoryImpl", "Failed to mark order $orderId voided on Supabase", e)
            }
        }
    }

    override suspend fun setOrderServed(orderId: Long, isServed: Boolean) {
        orderDao.setOrderServed(orderId, isServed)
    }

    private fun OrderWithItems.toDomain(): Order {
        return Order(
            id = order.id,
            timestamp = order.timestamp,
            subtotal = order.subtotal,
            discountDeduction = order.discountDeduction,
            discountLabel = order.discountLabel,
            total = order.total,
            paymentMethod = order.paymentMethod,
            paymentReference = order.paymentReference,
            cashierId = order.cashierId,
            cashierName = order.cashierName,
            tableLabel = order.tableLabel,
            isServed = order.isServed,
            deviceId = order.deviceId,
            syncStatus = order.syncStatus,
            isVoided = order.isVoided,
            lastSyncedAt = order.lastSyncedAt,
            remoteId = order.remoteId,
            items = items.map { item ->
                OrderItem(
                    id = item.id,
                    orderId = item.orderId,
                    itemId = item.itemId,
                    itemName = item.itemName,
                    variantId = item.variantId,
                    variantName = item.variantName,
                    flavor = item.flavor,
                    quantity = item.quantity,
                    unitPrice = item.unitPrice,
                    totalPrice = item.totalPrice
                )
            }
        )
    }
}
