package com.example.cattasticpos.domain.repository

import com.example.cattasticpos.domain.model.Order
import kotlinx.coroutines.flow.Flow

interface OrderRepository {
    fun observeOrdersPage(startDate: Long, endDate: Long, beforeTimestamp: Long, limit: Int): Flow<List<Order>>
    suspend fun getOrdersPage(startDate: Long, endDate: Long, beforeTimestamp: Long, limit: Int): List<Order>
    suspend fun getOrderById(orderId: Long): Order?
    suspend fun saveOrder(order: Order): Order
    suspend fun updateOrder(order: Order): Order
    fun getTopSellingItemForDay(startOfDay: Long, endOfDay: Long): Flow<Pair<String, Int>?>
    fun getOrderCountForDay(startOfDay: Long, endOfDay: Long): Flow<Int>

    fun getGrossSalesForDay(startOfDay: Long, endOfDay: Long): Flow<Double?>
    fun getDiscountsGivenForDay(startOfDay: Long, endOfDay: Long): Flow<Double?>
    fun getNetRevenueForDay(startOfDay: Long, endOfDay: Long): Flow<Double?>
    fun getCashSalesForDay(startOfDay: Long, endOfDay: Long): Flow<Double?>
    fun getGcashSalesForDay(startOfDay: Long, endOfDay: Long): Flow<Double?>
    fun observeCashierSalesForDay(startOfDay: Long, endOfDay: Long): Flow<Map<String, Double>>
    fun getItemSalesBreakdownForRange(startOfDay: Long, endOfDay: Long): Flow<List<com.example.cattasticpos.domain.model.ItemSalesBreakdown>>
    suspend fun deleteOrder(orderId: Long)
    suspend fun setOrderServed(orderId: Long, isServed: Boolean)
}
