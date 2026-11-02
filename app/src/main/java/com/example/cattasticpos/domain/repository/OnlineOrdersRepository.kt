package com.example.cattasticpos.domain.repository

import com.example.cattasticpos.domain.model.OnlineOrder
import com.example.cattasticpos.domain.model.OnlineOrderChangeResult
import com.example.cattasticpos.domain.model.OnlineOrderStatus

interface OnlineOrdersRepository {
    /** The latest online orders with their lines, newest first. */
    suspend fun fetchOrders(): Result<List<OnlineOrder>>

    /**
     * Asks the database to move an order from [from] to [to]. The same [correlationId] on a retry
     * makes the database answer `replayed` instead of changing the order twice.
     */
    suspend fun changeStatus(
        reference: String,
        from: OnlineOrderStatus,
        to: OnlineOrderStatus,
        reason: String?,
        correlationId: String
    ): OnlineOrderChangeResult
}
