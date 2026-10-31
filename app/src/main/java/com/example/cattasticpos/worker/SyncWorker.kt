package com.example.cattasticpos.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.cattasticpos.CattasticPosApp
import com.example.cattasticpos.data.local.entity.OrderEntity
import com.example.cattasticpos.data.local.entity.OrderItemEntity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "cattastic_sync_worker"
        private const val TAG = "SyncWorker"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /**
         * Number of device buckets. Capped at 1e6 on purpose: [getSupabaseOrderItemId] multiplies
         * the order id by another 1000, so `buckets * 1e9 * 1000` has to stay inside Long —
         * widening this overflows every order-item id.
         */
        private const val DEVICE_BUCKETS = 1_000_000L

        /**
         * Globally-unique cloud id for a local order: `deviceBucket * 1e9 + localId`.
         *
         * Folds the device id with 64-bit FNV-1a instead of [String.hashCode]. The old version
         * used `hashCode().absoluteValue`, which returns a *negative* number for
         * [Int.MIN_VALUE] and would have minted a negative order id. [OrderSyncMerger] recovers
         * the local id with `remoteId % 1e9`, so the multiplier must not change.
         */
        fun getSupabaseOrderId(deviceId: String, localId: Long): Long {
            var hash = -0x340d631b7bdddcdbL // FNV-1a 64-bit offset basis
            for (char in deviceId) {
                hash = hash xor char.code.toLong()
                hash *= 0x100000001b3L // FNV-1a 64-bit prime
            }
            val deviceBucket = Math.floorMod(hash, DEVICE_BUCKETS)
            return (deviceBucket * 1_000_000_000L) + localId
        }

        fun getSupabaseOrderItemId(supabaseOrderId: Long, itemIndex: Int): Long {
            return (supabaseOrderId * 1000L) + itemIndex
        }

        fun triggerImmediateSync(context: Context) {
            val request = androidx.work.OneTimeWorkRequestBuilder<SyncWorker>().build()
            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                "immediate_sync_upload",
                androidx.work.ExistingWorkPolicy.REPLACE,
                request
            )
        }

        suspend fun deleteRemoteCatalogRow(context: Context, table: String, id: String) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val app = context.applicationContext as? CattasticPosApp ?: return@withContext
                    val config = app.container.database.appConfigDao().getAppConfigOnce() ?: return@withContext
                    val supabaseUrl = config.supabaseUrl.trim()
                    val supabaseKey = config.supabaseAnonKey.trim()
                    if (supabaseUrl.isEmpty() || supabaseKey.isEmpty()) return@withContext
                    val accessToken = app.container.supabaseAuthManager.getValidAccessToken() ?: return@withContext
                    val encodedId = java.net.URLEncoder.encode(id, "UTF-8")
                    val deleteClient = OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .build()
                    if (table == "items") {
                        val recipeDeleteReq = Request.Builder()
                            .url("$supabaseUrl/rest/v1/recipe_mappings?menu_item_id=eq.$encodedId")
                            .delete()
                            .header("apikey", supabaseKey)
                            .header("Authorization", "Bearer $accessToken")
                            .build()
                        deleteClient.newCall(recipeDeleteReq).execute().close()
                    }
                    val deleteReq = Request.Builder()
                        .url("$supabaseUrl/rest/v1/$table?id=eq.$encodedId")
                        .delete()
                        .header("apikey", supabaseKey)
                        .header("Authorization", "Bearer $accessToken")
                        .build()
                    deleteClient.newCall(deleteReq).execute().close()
                } catch (e: Exception) {
                    Log.w(TAG, "Remote delete from $table ($id) skipped or failed", e)
                }
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Upserts [payload] into [table]. Returns false (and logs the body) on any non-2xx.
     *
     * The catalog download phase delete-mirrors the cloud onto Room, so a silently-swallowed
     * upload failure used to delete freshly-added local menu rows on the very next pass. Every
     * caller now gates its delete-mirror on this result.
     */
    private fun upsertCatalog(
        supabaseUrl: String,
        supabaseKey: String,
        accessToken: String,
        table: String,
        payload: JSONArray
    ): Boolean {
        if (payload.length() == 0) return true
        val request = Request.Builder()
            .url("$supabaseUrl/rest/v1/$table")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("apikey", supabaseKey)
            .header("Authorization", "Bearer $accessToken")
            .header("Prefer", "resolution=merge-duplicates")
            .header("Content-Type", "application/json")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    if (table == "items" && response.code == 400 && errorBody.contains("icon")) {
                        val strippedPayload = JSONArray()
                        for (i in 0 until payload.length()) {
                            val copy = JSONObject(payload.getJSONObject(i).toString())
                            copy.remove("icon")
                            strippedPayload.put(copy)
                        }
                        val fallbackReq = Request.Builder()
                            .url("$supabaseUrl/rest/v1/$table")
                            .post(strippedPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                            .header("apikey", supabaseKey)
                            .header("Authorization", "Bearer $accessToken")
                            .header("Prefer", "resolution=merge-duplicates")
                            .header("Content-Type", "application/json")
                            .build()
                        return client.newCall(fallbackReq).execute().use { fallbackRes ->
                            if (!fallbackRes.isSuccessful) {
                                Log.e(TAG, "Upload to $table fallback failed: ${fallbackRes.code} - ${fallbackRes.body?.string().orEmpty()}")
                                false
                            } else {
                                true
                            }
                        }
                    }
                    Log.e(TAG, "Upload to $table failed: ${response.code} - $errorBody")
                    false
                } else {
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload to $table errored", e)
            false
        }
    }

    /**
     * Deletes the cloud line items for [supabaseOrderId] and re-uploads [items] in their place.
     * Returns false when any leg fails so the caller can leave the order PENDING and retry.
     */
    private fun replaceRemoteOrderItems(
        supabaseUrl: String,
        supabaseKey: String,
        accessToken: String,
        supabaseOrderId: Long,
        items: List<OrderItemEntity>
    ): Boolean {
        val deleteRequest = Request.Builder()
            .url("$supabaseUrl/rest/v1/order_items?order_id=eq.$supabaseOrderId")
            .delete()
            .header("apikey", supabaseKey)
            .header("Authorization", "Bearer $accessToken")
            .build()
        client.newCall(deleteRequest).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to clear order_items for $supabaseOrderId: ${response.code}")
                return false
            }
        }

        if (items.isEmpty()) return true

        val itemsArray = JSONArray()
        items.forEachIndexed { index, item ->
            itemsArray.put(
                JSONObject().apply {
                    put("id", getSupabaseOrderItemId(supabaseOrderId, index))
                    put("order_id", supabaseOrderId)
                    put("item_id", item.itemId)
                    put("item_name", item.itemName)
                    put("variant_id", item.variantId)
                    put("variant_name", item.variantName)
                    put("flavor", item.flavor ?: JSONObject.NULL)
                    put("quantity", item.quantity)
                    put("unit_price", item.unitPrice)
                    put("total_price", item.totalPrice)
                }
            )
        }
        val insertRequest = Request.Builder()
            .url("$supabaseUrl/rest/v1/order_items")
            .post(itemsArray.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("apikey", supabaseKey)
            .header("Authorization", "Bearer $accessToken")
            .header("Prefer", "resolution=merge-duplicates")
            .header("Content-Type", "application/json")
            .build()
        client.newCall(insertRequest).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to re-upload order_items for $supabaseOrderId: ${response.code}")
                return false
            }
        }
        return true
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as CattasticPosApp
        val database = app.container.database
        val config = database.appConfigDao().getAppConfigOnce() ?: return Result.success()

        val supabaseUrl = config.supabaseUrl.trim()
        val supabaseKey = config.supabaseAnonKey.trim()
        val deviceId = config.deviceId.trim()

        if (supabaseUrl.isEmpty() || supabaseKey.isEmpty() || deviceId.isEmpty()) {
            Log.d(TAG, "Supabase sync not configured. Skipping.")
            return Result.success()
        }

        // Cloud access requires the authenticated POS session (RLS rejects the anon key).
        // Not signed in yet / token refresh failed -> keep everything local and retry on
        // the next periodic cycle; the POS itself keeps working offline.
        val accessToken = app.container.supabaseAuthManager.getValidAccessToken()
        if (accessToken == null) {
            Log.w(TAG, "No authenticated Supabase session. Skipping sync until sign-in.")
            return Result.success()
        }

        try {
            // Sync Catalog: Categories, Items, Inventory, and Recipe Mappings (Two-Way Sync)
            try {
                // 1. Upload Local Categories
                val categories = database.menuDao().getCategories().first()
                val catArray = JSONArray()
                categories.forEach { cat ->
                    catArray.put(
                        JSONObject().apply {
                            put("id", cat.id)
                            put("name", cat.name)
                        }
                    )
                }
                val categoriesUploaded = upsertCatalog(supabaseUrl, supabaseKey, accessToken, "categories", catArray)

                // 2. Upload Local Items
                val items = database.menuDao().getItems().first()
                val itemsArray = JSONArray()
                items.forEach { item ->
                    itemsArray.put(
                        JSONObject().apply {
                            put("id", item.id)
                            put("category_id", item.categoryId)
                            put("name", item.name)
                            put("flavors", item.flavors)
                            put("variants_json", item.variantsJson)
                            put("is_available", item.isAvailable)
                            put("icon", item.icon ?: JSONObject.NULL)
                        }
                    )
                }
                val itemsUploaded = upsertCatalog(supabaseUrl, supabaseKey, accessToken, "items", itemsArray)

                // 3. Upload Local Inventory
                val inventory = database.inventoryDao().getAllInventory().first()
                val invArray = JSONArray()
                inventory.forEach { inv ->
                    invArray.put(
                        JSONObject().apply {
                            put("id", inv.id)
                            put("item_name", inv.itemName)
                            put("unit", inv.unit)
                            put("current_stock", inv.currentStock)
                            put("reorder_threshold", inv.reorderThreshold)
                        }
                    )
                }
                val inventoryUploaded = upsertCatalog(supabaseUrl, supabaseKey, accessToken, "inventory", invArray)

                // 4. Upload Local Recipe Mappings
                val recipes = database.recipeDao().getAllMappingsOnce()
                val recArray = JSONArray()
                recipes.forEach { rec ->
                    recArray.put(
                        JSONObject().apply {
                            put("id", rec.id)
                            put("menu_item_id", rec.menuItemId)
                            put("size_variant_name", rec.variantName ?: JSONObject.NULL)
                            put("inventory_item_id", rec.inventoryItemId)
                            put("deduction_quantity", rec.deductionQuantity)
                        }
                    )
                }
                val recipesUploaded = upsertCatalog(supabaseUrl, supabaseKey, accessToken, "recipe_mappings", recArray)

                // 5. Upload Local Expenses (recorded on cashier terminals)
                val expenses = database.expenseDao().getAllExpensesOnce()
                val expArray = JSONArray()
                expenses.forEach { exp ->
                    expArray.put(
                        JSONObject().apply {
                            put("id", exp.id)
                            put("timestamp", exp.timestamp)
                            put("description", exp.description)
                            put("amount", exp.amount)
                            put("recorded_by", exp.recordedBy)
                            put("device_id", deviceId)
                        }
                    )
                }
                upsertCatalog(supabaseUrl, supabaseKey, accessToken, "expenses", expArray)

                // ==========================================
                // DOWNLOAD & SYNC CLOUD CHANGES TO DEVICE
                // ==========================================

                // A. Download & Sync Categories
                val getCatRequest = Request.Builder()
                    .url("$supabaseUrl/rest/v1/categories")
                    .get()
                    .header("apikey", supabaseKey)
                    .header("Authorization", "Bearer $accessToken")
                    .build()
                client.newCall(getCatRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val arr = JSONArray(body)
                            val downloadedList = mutableListOf<com.example.cattasticpos.data.local.entity.CategoryEntity>()
                            val downloadedIds = mutableSetOf<String>()
                            for (i in 0 until arr.length()) {
                                val obj = arr.getJSONObject(i)
                                val id = obj.getString("id")
                                val name = obj.getString("name")
                                downloadedList.add(com.example.cattasticpos.data.local.entity.CategoryEntity(id, name))
                                downloadedIds.add(id)
                            }
                            if (downloadedList.isNotEmpty()) {
                                // Write only rows that are new or actually changed, so an
                                // unchanged catalog doesn't invalidate the Flow and flicker the UI.
                                val localById = database.menuDao().getCategories().first().associateBy { it.id }
                                val changed = downloadedList.filter { localById[it.id] != it }
                                if (changed.isNotEmpty()) {
                                    database.menuDao().insertCategories(changed)
                                }
                                // Only mirror deletions when our own rows definitely reached the
                                // cloud; otherwise "absent upstream" just means the upload failed.
                                val toDelete = if (categoriesUploaded) {
                                    localById.keys.filter { it !in downloadedIds }
                                } else {
                                    emptyList()
                                }
                                if (toDelete.isNotEmpty()) {
                                    database.menuDao().deleteCategoriesByIds(toDelete)
                                }
                            }
                        }
                    }
                }

                // B. Download & Sync Items
                val getItemsRequest = Request.Builder()
                    .url("$supabaseUrl/rest/v1/items")
                    .get()
                    .header("apikey", supabaseKey)
                    .header("Authorization", "Bearer $accessToken")
                    .build()
                client.newCall(getItemsRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val arr = JSONArray(body)
                            val downloadedList = mutableListOf<com.example.cattasticpos.data.local.entity.ItemEntity>()
                            val downloadedIds = mutableSetOf<String>()
                            for (i in 0 until arr.length()) {
                                val obj = arr.getJSONObject(i)
                                val id = obj.getString("id")
                                val categoryId = obj.getString("category_id")
                                val name = obj.getString("name")
                                val flavors = obj.optString("flavors", "")
                                val variantsJson = obj.optString("variants_json", "[]")
                                val isAvailable = obj.optBoolean("is_available", true)
                                val icon = if (obj.isNull("icon")) null else obj.optString("icon", "").takeIf { it.isNotBlank() }
                                downloadedList.add(
                                    com.example.cattasticpos.data.local.entity.ItemEntity(
                                        id = id,
                                        categoryId = categoryId,
                                        name = name,
                                        flavors = flavors,
                                        variantsJson = variantsJson,
                                        isAvailable = isAvailable,
                                        icon = icon
                                    )
                                )
                                downloadedIds.add(id)
                            }
                            if (downloadedList.isNotEmpty()) {
                                val localById = database.menuDao().getItems().first().associateBy { it.id }
                                val changed = downloadedList.filter { localById[it.id] != it }
                                if (changed.isNotEmpty()) {
                                    database.menuDao().insertItems(changed)
                                }
                                val toDelete = if (itemsUploaded) {
                                    localById.keys.filter { it !in downloadedIds }
                                } else {
                                    emptyList()
                                }
                                if (toDelete.isNotEmpty()) {
                                    database.menuDao().deleteItemsByIds(toDelete)
                                }
                            }
                        }
                    }
                }

                // C. Download & Sync Inventory (Stock)
                val getInvRequest = Request.Builder()
                    .url("$supabaseUrl/rest/v1/inventory")
                    .get()
                    .header("apikey", supabaseKey)
                    .header("Authorization", "Bearer $accessToken")
                    .build()
                client.newCall(getInvRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val arr = JSONArray(body)
                            val downloadedList = mutableListOf<com.example.cattasticpos.data.local.entity.InventoryEntity>()
                            val downloadedIds = mutableSetOf<String>()
                            for (i in 0 until arr.length()) {
                                val obj = arr.getJSONObject(i)
                                val id = obj.getString("id")
                                val itemName = obj.getString("item_name")
                                val unit = obj.getString("unit")
                                val currentStock = obj.getDouble("current_stock")
                                val reorderThreshold = obj.getDouble("reorder_threshold")
                                downloadedList.add(com.example.cattasticpos.data.local.entity.InventoryEntity(id, itemName, unit, currentStock, reorderThreshold))
                                downloadedIds.add(id)
                            }
                            if (downloadedList.isNotEmpty()) {
                                val localById = database.inventoryDao().getAllInventory().first().associateBy { it.id }
                                val changed = downloadedList.filter { localById[it.id] != it }
                                if (changed.isNotEmpty()) {
                                    database.inventoryDao().insertInventoryItems(changed)
                                }
                                val toDelete = if (inventoryUploaded) {
                                    localById.keys.filter { it !in downloadedIds }
                                } else {
                                    emptyList()
                                }
                                if (toDelete.isNotEmpty()) {
                                    database.inventoryDao().deleteInventoryItemsByIds(toDelete)
                                }
                            }
                        }
                    }
                }

                // D. Download & Sync Recipe Mappings
                val getRecRequest = Request.Builder()
                    .url("$supabaseUrl/rest/v1/recipe_mappings")
                    .get()
                    .header("apikey", supabaseKey)
                    .header("Authorization", "Bearer $accessToken")
                    .build()
                client.newCall(getRecRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val arr = JSONArray(body)
                            val downloadedList = mutableListOf<com.example.cattasticpos.data.local.entity.RecipeMappingEntity>()
                            val downloadedIds = mutableSetOf<String>()
                            for (i in 0 until arr.length()) {
                                val obj = arr.getJSONObject(i)
                                val id = obj.getString("id")
                                val menuItemId = obj.getString("menu_item_id")
                                val sizeVariantName = if (obj.isNull("size_variant_name")) null else obj.getString("size_variant_name")
                                val inventoryItemId = obj.getString("inventory_item_id")
                                val deductionQuantity = obj.getDouble("deduction_quantity")
                                downloadedList.add(com.example.cattasticpos.data.local.entity.RecipeMappingEntity(id, menuItemId, sizeVariantName, inventoryItemId, deductionQuantity))
                                downloadedIds.add(id)
                            }
                            if (downloadedList.isNotEmpty()) {
                                val localById = database.recipeDao().getAllMappingsOnce().associateBy { it.id }
                                val changed = downloadedList.filter { localById[it.id] != it }
                                if (changed.isNotEmpty()) {
                                    database.recipeDao().insertMappings(changed)
                                }
                                val toDelete = if (recipesUploaded) {
                                    localById.keys.filter { it !in downloadedIds }
                                } else {
                                    emptyList()
                                }
                                if (toDelete.isNotEmpty()) {
                                    database.recipeDao().deleteMappingsByIds(toDelete)
                                }
                            }
                        }
                    }
                }

                // E. Download & Sync Expenses. Expenses are permanent records that
                // accumulate from every device, so we only insert new/changed rows
                // and never delete-mirror (cloud is a superset of any one device).
                val getExpRequest = Request.Builder()
                    .url("$supabaseUrl/rest/v1/expenses")
                    .get()
                    .header("apikey", supabaseKey)
                    .header("Authorization", "Bearer $accessToken")
                    .build()
                client.newCall(getExpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val arr = JSONArray(body)
                            val downloadedList = mutableListOf<com.example.cattasticpos.data.local.entity.ExpenseEntity>()
                            val deletedIds = mutableListOf<String>()
                            for (i in 0 until arr.length()) {
                                val obj = arr.getJSONObject(i)
                                // Tombstoned on the web dashboard: remove the local copy too,
                                // otherwise the next upload pass resurrects it in the cloud.
                                if (obj.optBoolean("is_deleted", false)) {
                                    deletedIds.add(obj.getString("id"))
                                    continue
                                }
                                downloadedList.add(
                                    com.example.cattasticpos.data.local.entity.ExpenseEntity(
                                        id = obj.getString("id"),
                                        timestamp = obj.getLong("timestamp"),
                                        description = obj.getString("description"),
                                        amount = obj.getDouble("amount"),
                                        recordedBy = obj.optString("recorded_by", "")
                                    )
                                )
                            }
                            if (deletedIds.isNotEmpty()) {
                                database.expenseDao().deleteExpensesByIds(deletedIds)
                            }
                            if (downloadedList.isNotEmpty()) {
                                val localById = database.expenseDao().getAllExpensesOnce().associateBy { it.id }
                                val changed = downloadedList.filter { localById[it.id] != it }
                                if (changed.isNotEmpty()) {
                                    database.expenseDao().insertExpenses(changed)
                                }
                            }
                        }
                    }
                }
            } catch (ce: Exception) {
                Log.e(TAG, "Catalog sync error", ce)
            }

            // ==========================================
            // UPLOAD: Push Local Pending Orders to Cloud
            // ==========================================
            try {
                val pendingOrders = database.orderDao().getPendingSyncOrders()

                if (pendingOrders.isNotEmpty()) {
                    Log.i(TAG, "Found ${pendingOrders.size} pending orders to upload.")

                    for (orderWithItems in pendingOrders) {
                        val order = orderWithItems.order
                        val items = orderWithItems.items

                        if (order.remoteId != null) {
                            // Already present in the cloud. For a FOREIGN order only the status
                            // fields may be touched — the owning device stays authoritative for
                            // its totals and line items. For one of OUR OWN orders the totals can
                            // legitimately have changed (receipt editor), and pushing status only
                            // meant those edits never left the tablet.
                            val isOwnOrder = order.deviceId == deviceId
                            val patchJson = JSONObject().apply {
                                put("is_voided", order.isVoided)
                                put("is_served", order.isServed)
                                if (isOwnOrder) {
                                    put("subtotal", order.subtotal)
                                    put("discount_deduction", order.discountDeduction)
                                    put("discount_label", order.discountLabel)
                                    put("total", order.total)
                                    put("payment_method", order.paymentMethod)
                                    put("payment_reference", order.paymentReference ?: JSONObject.NULL)
                                    put("table_label", order.tableLabel ?: JSONObject.NULL)
                                }
                            }
                            val patchRequest = Request.Builder()
                                .url("$supabaseUrl/rest/v1/orders?id=eq.${order.remoteId}")
                                .patch(patchJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                                .header("apikey", supabaseKey)
                                .header("Authorization", "Bearer $accessToken")
                                .header("Content-Type", "application/json")
                                .build()
                            val patchResponse = client.newCall(patchRequest).execute()
                            if (!patchResponse.isSuccessful) {
                                val body = patchResponse.body?.string() ?: ""
                                Log.e(TAG, "Failed to patch order ${order.id} (remote ${order.remoteId}): ${patchResponse.code} - $body")
                                patchResponse.close()
                                continue
                            }
                            patchResponse.close()

                            // Line items are replaced wholesale: an edit can drop items, and the
                            // index-derived ids would otherwise leave the removed rows orphaned.
                            if (isOwnOrder && !replaceRemoteOrderItems(
                                    supabaseUrl = supabaseUrl,
                                    supabaseKey = supabaseKey,
                                    accessToken = accessToken,
                                    supabaseOrderId = order.remoteId,
                                    items = items
                                )
                            ) {
                                Log.e(TAG, "Failed to replace order items for ${order.id} (remote ${order.remoteId}).")
                                continue
                            }

                            database.orderDao().updateOrderEntity(
                                order.copy(syncStatus = "SYNCED", lastSyncedAt = System.currentTimeMillis())
                            )
                            Log.d(TAG, "Patched order ${order.id} to cloud (remote ${order.remoteId}, own=$isOwnOrder).")
                            continue
                        }

                        // Brand-new own order -> POST full record under a globally-unique id.
                        val supabaseOrderId = getSupabaseOrderId(deviceId, order.id)

                        val orderJson = JSONObject().apply {
                            put("id", supabaseOrderId)
                            put("device_id", deviceId)
                            put("timestamp", order.timestamp)
                            put("subtotal", order.subtotal)
                            put("discount_deduction", order.discountDeduction)
                            put("discount_label", order.discountLabel)
                            put("total", order.total)
                            put("payment_method", order.paymentMethod)
                            put("payment_reference", order.paymentReference ?: JSONObject.NULL)
                            put("cashier_id", order.cashierId ?: JSONObject.NULL)
                            put("cashier_name", order.cashierName ?: JSONObject.NULL)
                            put("table_label", order.tableLabel ?: JSONObject.NULL)
                            put("is_served", order.isServed)
                            put("is_voided", order.isVoided)
                        }

                        val orderRequest = Request.Builder()
                            .url("$supabaseUrl/rest/v1/orders")
                            .post(orderJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                            .header("apikey", supabaseKey)
                            .header("Authorization", "Bearer $accessToken")
                            .header("Prefer", "resolution=merge-duplicates")
                            .header("Content-Type", "application/json")
                            .build()

                        val orderResponse = client.newCall(orderRequest).execute()
                        if (!orderResponse.isSuccessful) {
                            val body = orderResponse.body?.string() ?: ""
                            Log.e(TAG, "Failed to upload order header ${order.id}: ${orderResponse.code} - $body")
                            orderResponse.close()
                            continue
                        }
                        orderResponse.close()

                        if (items.isNotEmpty()) {
                            val itemsArray = JSONArray()
                            items.forEachIndexed { index, item ->
                                val itemJson = JSONObject().apply {
                                    put("id", getSupabaseOrderItemId(supabaseOrderId, index))
                                    put("order_id", supabaseOrderId)
                                    put("item_id", item.itemId)
                                    put("item_name", item.itemName)
                                    put("variant_id", item.variantId)
                                    put("variant_name", item.variantName)
                                    put("flavor", item.flavor ?: JSONObject.NULL)
                                    put("quantity", item.quantity)
                                    put("unit_price", item.unitPrice)
                                    put("total_price", item.totalPrice)
                                }
                                itemsArray.put(itemJson)
                            }

                            val itemsRequest = Request.Builder()
                                .url("$supabaseUrl/rest/v1/order_items")
                                .post(itemsArray.toString().toRequestBody(JSON_MEDIA_TYPE))
                                .header("apikey", supabaseKey)
                                .header("Authorization", "Bearer $accessToken")
                                .header("Prefer", "resolution=merge-duplicates")
                                .header("Content-Type", "application/json")
                                .build()

                            val itemsResponse = client.newCall(itemsRequest).execute()
                            if (!itemsResponse.isSuccessful) {
                                val body = itemsResponse.body?.string() ?: ""
                                Log.e(TAG, "Failed to upload order items for order ${order.id}: ${itemsResponse.code} - $body")
                                itemsResponse.close()
                                continue
                            }
                            itemsResponse.close()
                        }

                        // Mark SYNCED and remember the global id so future syncs PATCH, not re-POST.
                        database.orderDao().updateOrderEntity(
                            order.copy(
                                syncStatus = "SYNCED",
                                lastSyncedAt = System.currentTimeMillis(),
                                remoteId = supabaseOrderId
                            )
                        )
                        Log.d(TAG, "Uploaded new order ${order.id} (Supabase ID: $supabaseOrderId)")
                    }
                } else {
                    Log.d(TAG, "No pending orders to upload.")
                }
            } catch (oe: Exception) {
                Log.e(TAG, "Order upload error", oe)
            }

            // ==========================================
            // DOWNLOAD: Catch-up sync for orders created/voided on other devices
            // (or while this device was offline). Merges through OrderSyncMerger so the
            // local-id mapping is identical to the realtime and historical-pull paths.
            // ==========================================
            try {
                Log.d(TAG, "Starting catch-up order sync from Supabase...")
                val getOrdersRequest = Request.Builder()
                    .url("$supabaseUrl/rest/v1/orders?select=*,order_items(*)&order=timestamp.desc&limit=500")
                    .get()
                    .header("apikey", supabaseKey)
                    .header("Authorization", "Bearer $accessToken")
                    .build()

                client.newCall(getOrdersRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val arr = JSONArray(body)
                            Log.d(TAG, "Catch-up: examining ${arr.length()} cloud orders")
                            val recipeRepository = app.container.recipeRepository
                            val inventoryRepository = app.container.inventoryRepository
                            for (i in 0 until arr.length()) {
                                com.example.cattasticpos.data.sync.OrderSyncMerger.mergeRemoteOrder(
                                    database = database,
                                    recipeRepository = recipeRepository,
                                    inventoryRepository = inventoryRepository,
                                    orderJson = arr.getJSONObject(i),
                                    localDeviceId = deviceId,
                                    restoreInventoryOnVoid = false
                                )
                            }
                        }
                    } else {
                        Log.e(TAG, "Failed to download orders: ${response.code}")
                    }
                }
            } catch (he: Exception) {
                Log.e(TAG, "Catch-up sync error", he)
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync error", e)
            return Result.retry()
        }
    }
}
