package com.example.cattasticpos.domain.repository

import com.example.cattasticpos.domain.model.OnlineOrder
import com.example.cattasticpos.domain.model.OnlineOrderChangeResult
import com.example.cattasticpos.domain.model.OnlineOrderStatus
import com.example.cattasticpos.domain.model.ShopClosure

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

    /**
     * Asks the database to undo the order's last change, made from [from]. Undoing a Complete
     * voids its sale. The same [correlationId] on a retry answers `replayed` (FR-095).
     */
    suspend fun undoStatus(
        reference: String,
        from: OnlineOrderStatus,
        correlationId: String
    ): OnlineOrderChangeResult

    /** The "Closed today" switch; a failure when it can't be read or isn't set up yet. */
    suspend fun fetchClosure(): Result<ShopClosure> =
        Result.failure(UnsupportedOperationException("Closed today isn't available."))

    /** Saves the "Closed today" switch and answers what the database saved. */
    suspend fun saveClosure(closure: ShopClosure): Result<ShopClosure> =
        Result.failure(UnsupportedOperationException("Closed today isn't available."))
}
