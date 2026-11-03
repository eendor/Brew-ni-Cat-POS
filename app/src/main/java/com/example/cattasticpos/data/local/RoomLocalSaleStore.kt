package com.example.cattasticpos.data.local

import com.example.cattasticpos.data.local.dao.OrderDao
import com.example.cattasticpos.domain.usecase.LocalSaleStore

/** [LocalSaleStore] over the tablet's Room orders table. */
class RoomLocalSaleStore(private val orderDao: OrderDao) : LocalSaleStore {
    override suspend fun markVoided(remoteId: Long): Boolean {
        val local = orderDao.getOrderByRemoteId(remoteId)?.order ?: return false
        if (local.isVoided) return false
        // The cloud row is already voided, so there is nothing left to upload.
        orderDao.updateOrderEntity(local.copy(isVoided = true, syncStatus = "SYNCED"))
        return true
    }

    override suspend fun isVoided(remoteId: Long): Boolean =
        orderDao.getOrderByRemoteId(remoteId)?.order?.isVoided == true
}
