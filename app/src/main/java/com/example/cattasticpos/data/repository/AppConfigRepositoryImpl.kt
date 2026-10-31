package com.example.cattasticpos.data.repository

import com.example.cattasticpos.BuildConfig
import com.example.cattasticpos.data.local.dao.AppConfigDao
import com.example.cattasticpos.data.local.entity.AppConfigEntity
import com.example.cattasticpos.domain.model.AppConfig
import com.example.cattasticpos.domain.model.AppThemeAccent
import com.example.cattasticpos.domain.model.Cashier
import com.example.cattasticpos.domain.model.GcashAccount
import com.example.cattasticpos.domain.model.PaymentConfigJson
import com.example.cattasticpos.domain.repository.AppConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

class AppConfigRepositoryImpl(
    private val dao: AppConfigDao
) : AppConfigRepository {

    /** One candidate device id per process, shared by every collector of [getAppConfig]. */
    private val generatedDeviceId = java.util.concurrent.atomic.AtomicReference<String?>(null)

    override fun getAppConfig(): Flow<AppConfig?> {
        return dao.getAppConfig().map { entity ->
            entity?.let { toAppConfig(it) }
        }
    }

    /**
     * Row id=1 is written with REPLACE, so every writer must carry the WHOLE row forward.
     * Rebuilding the entity from scratch here silently reset supabaseUrl / supabaseAnonKey /
     * deviceId to their "" defaults, which knocked the tablet off cloud sync and minted a new
     * deviceId on the next read — every time a cashier was switched or the theme was changed.
     */
    private fun baseEntity(existing: AppConfigEntity?): AppConfigEntity =
        existing ?: AppConfigEntity(
            id = 1,
            targetSales = AppConfigEntity.DEFAULT_TARGET_SALES,
            startingCashFloat = AppConfigEntity.DEFAULT_STARTING_CASH_FLOAT,
            pinHash = AppConfig.DEFAULT_PIN_HASH
        )

    override suspend fun updateConfig(targetSales: Double, startingCashFloat: Double, pinHash: String) {
        val existing = dao.getAppConfigOnce()
        val paymentConfig = PaymentConfigJson.fromStoredJson(existing?.cashiersJson.orEmpty())
        dao.insertConfig(
            baseEntity(existing).copy(
                targetSales = targetSales,
                startingCashFloat = startingCashFloat,
                pinHash = pinHash,
                cashiersJson = paymentConfig.toStoredJson()
            )
        )
    }

    override suspend fun updatePaymentConfig(cashiers: List<Cashier>, gcashAccounts: List<GcashAccount>) {
        val existing = dao.getAppConfigOnce()
        val paymentConfig = PaymentConfigJson.fromStoredJson(existing?.cashiersJson.orEmpty())
        val activeId = paymentConfig.activeCashierId?.takeIf { id -> cashiers.any { it.id == id } }
            ?: cashiers.firstOrNull()?.id
        dao.insertConfig(
            baseEntity(existing).copy(
                cashiersJson = PaymentConfigJson.toStoredJson(
                    cashiers = cashiers,
                    gcashAccounts = gcashAccounts,
                    themeAccentId = paymentConfig.themeAccentId,
                    activeCashierId = activeId
                )
            )
        )
    }

    override suspend fun updateThemeAccent(themeAccentId: String) {
        val existing = dao.getAppConfigOnce()
        val paymentConfig = PaymentConfigJson.fromStoredJson(existing?.cashiersJson.orEmpty())
        dao.insertConfig(
            baseEntity(existing).copy(
                cashiersJson = paymentConfig.copy(themeAccentId = themeAccentId).toStoredJson()
            )
        )
    }

    override suspend fun updateActiveCashier(activeCashierId: String) {
        val existing = dao.getAppConfigOnce()
        val paymentConfig = PaymentConfigJson.fromStoredJson(existing?.cashiersJson.orEmpty())
        if (paymentConfig.cashiers.none { it.id == activeCashierId }) return
        dao.insertConfig(
            baseEntity(existing).copy(
                cashiersJson = paymentConfig.copy(activeCashierId = activeCashierId).toStoredJson()
            )
        )
    }

    override suspend fun updateSyncConfig(supabaseUrl: String, supabaseAnonKey: String) {
        val existing = dao.getAppConfigOnce()
        if (existing != null) {
            dao.insertConfig(
                existing.copy(
                    supabaseUrl = supabaseUrl.trim(),
                    supabaseAnonKey = supabaseAnonKey.trim()
                )
            )
        }
    }

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    private fun toAppConfig(entity: AppConfigEntity): AppConfig {
        val paymentConfig = PaymentConfigJson.fromStoredJson(entity.cashiersJson)
        val resolvedActiveId = paymentConfig.activeCashierId?.takeIf { id ->
            paymentConfig.cashiers.any { it.id == id }
        } ?: paymentConfig.cashiers.firstOrNull()?.id

        val defaultUrl = BuildConfig.SUPABASE_URL
        val defaultKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        
        var shouldUpdate = false
        var updatedEntity = entity
        
        val actualDeviceId = if (entity.deviceId.isBlank()) {
            // Several screens collect this Flow at once. Minting a UUID per collector let two
            // of them race to persist different device ids, which re-partitions the cloud order
            // id space mid-shift; memoize so the whole process agrees on one candidate.
            val newUuid = generatedDeviceId.updateAndGet { it ?: java.util.UUID.randomUUID().toString() }!!
            updatedEntity = updatedEntity.copy(deviceId = newUuid)
            shouldUpdate = true
            newUuid
        } else {
            entity.deviceId
        }

        val actualUrl = if (entity.supabaseUrl.isBlank() && defaultUrl.isNotBlank()) {
            updatedEntity = updatedEntity.copy(supabaseUrl = defaultUrl)
            shouldUpdate = true
            defaultUrl
        } else {
            entity.supabaseUrl
        }

        val actualKey = if (entity.supabaseAnonKey.isBlank() && defaultKey.isNotBlank()) {
            updatedEntity = updatedEntity.copy(supabaseAnonKey = defaultKey)
            shouldUpdate = true
            defaultKey
        } else {
            entity.supabaseAnonKey
        }

        if (shouldUpdate) {
            val patched = updatedEntity
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    // Re-read: another collector (or a settings write) may already have filled
                    // these in between the map() above and this coroutine running.
                    val current = dao.getAppConfigOnce() ?: patched
                    val merged = current.copy(
                        deviceId = current.deviceId.ifBlank { patched.deviceId },
                        supabaseUrl = current.supabaseUrl.ifBlank { patched.supabaseUrl },
                        supabaseAnonKey = current.supabaseAnonKey.ifBlank { patched.supabaseAnonKey }
                    )
                    if (merged != current) {
                        dao.insertConfig(merged)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AppConfigRepositoryImpl", "Failed to update configs", e)
                }
            }
        }

        return AppConfig(
            targetSales = entity.targetSales,
            startingCashFloat = entity.startingCashFloat,
            pinHash = entity.pinHash,
            cashiers = paymentConfig.cashiers,
            gcashAccounts = paymentConfig.gcashAccounts,
            themeAccentId = paymentConfig.themeAccentId,
            activeCashierId = resolvedActiveId,
            supabaseUrl = actualUrl,
            supabaseAnonKey = actualKey,
            deviceId = actualDeviceId
        )
    }

    private fun PaymentConfigJson.toStoredJson(): String {
        return PaymentConfigJson.toStoredJson(
            cashiers = cashiers,
            gcashAccounts = gcashAccounts,
            themeAccentId = themeAccentId,
            activeCashierId = activeCashierId
        )
    }
}
