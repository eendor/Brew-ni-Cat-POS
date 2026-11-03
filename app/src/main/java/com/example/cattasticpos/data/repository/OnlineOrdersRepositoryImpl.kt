package com.example.cattasticpos.data.repository

import android.util.Log
import com.example.cattasticpos.data.local.dao.AppConfigDao
import com.example.cattasticpos.data.remote.OnlineOrderJson
import com.example.cattasticpos.domain.model.OnlineOrder
import com.example.cattasticpos.domain.model.OnlineOrderChangeResult
import com.example.cattasticpos.domain.model.OnlineOrderStatus
import com.example.cattasticpos.domain.repository.OnlineOrdersRepository
import com.example.cattasticpos.service.SupabaseAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Reads online orders and changes their status through Supabase with the signed-in store
 * account. The table has no write access: `public.set_online_order_status()` and
 * `public.undo_online_order_status()` are the only ways to change an order, and they also add the
 * status history row and the audit row.
 */
class OnlineOrdersRepositoryImpl(
    private val appConfigDao: AppConfigDao,
    private val authManager: SupabaseAuthManager
) : OnlineOrdersRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private companion object {
        const val TAG = "OnlineOrdersRepo"
        const val RECENT_ORDERS = 100
        val JSON = "application/json; charset=utf-8".toMediaType()
    }

    override suspend fun fetchOrders(): Result<List<OnlineOrder>> = withContext(Dispatchers.IO) {
        runCatching {
            val (url, key) = config() ?: error("Cloud sync is not set up on this device.")
            val token = authManager.getValidAccessToken() ?: error("Sign in with the store account to see online orders.")
            val request = Request.Builder()
                .url(
                    "$url/rest/v1/online_orders?select=*,online_order_items(*)" +
                        "&order=created_at.desc&limit=$RECENT_ORDERS"
                )
                .get()
                .header("apikey", key)
                .header("Authorization", "Bearer $token")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "Fetching online orders failed: ${response.code}")
                    error("Couldn't load online orders (${response.code}).")
                }
                OnlineOrderJson.parseOrders(body)
            }
        }.onFailure { Log.w(TAG, "fetchOrders: ${it.message}") }
    }

    override suspend fun changeStatus(
        reference: String,
        from: OnlineOrderStatus,
        to: OnlineOrderStatus,
        reason: String?,
        correlationId: String
    ): OnlineOrderChangeResult = rpc(
        "set_online_order_status",
        JSONObject().apply {
            put("p_reference", reference)
            put("p_from_status", from.wire)
            put("p_to_status", to.wire)
            put("p_reason", reason?.trim()?.takeIf { it.isNotEmpty() } ?: JSONObject.NULL)
            put("p_correlation_id", correlationId)
        }
    )

    override suspend fun undoStatus(
        reference: String,
        from: OnlineOrderStatus,
        correlationId: String
    ): OnlineOrderChangeResult = rpc(
        "undo_online_order_status",
        JSONObject().apply {
            put("p_reference", reference)
            put("p_from_status", from.wire)
            put("p_correlation_id", correlationId)
        }
    )

    private suspend fun rpc(function: String, payload: JSONObject): OnlineOrderChangeResult =
        withContext(Dispatchers.IO) {
            try {
                val (url, key) = config()
                    ?: return@withContext OnlineOrderChangeResult.Failed("Cloud sync is not set up on this device.")
                val token = authManager.getValidAccessToken()
                    ?: return@withContext OnlineOrderChangeResult.Failed("Sign in with the store account first.")
                val request = Request.Builder()
                    .url("$url/rest/v1/rpc/$function")
                    .post(payload.toString().toRequestBody(JSON))
                    .header("apikey", key)
                    .header("Authorization", "Bearer $token")
                    .header("Content-Type", "application/json")
                    .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    when {
                        response.isSuccessful -> OnlineOrderJson.parseChange(body)
                        response.code == 401 || response.code == 403 ->
                            OnlineOrderChangeResult.Failed("This account can't change online orders. Sign in with the store account.")
                        else -> OnlineOrderChangeResult.Failed("The server didn't save the change (${response.code}). Try again.")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "$function: ${e.message}")
                OnlineOrderChangeResult.Failed("Couldn't reach the server. Check the internet connection and try again.")
            }
        }

    private suspend fun config(): Pair<String, String>? {
        val config = appConfigDao.getAppConfigOnce() ?: return null
        val url = config.supabaseUrl.trim()
        val key = config.supabaseAnonKey.trim()
        return if (url.isEmpty() || key.isEmpty()) null else url to key
    }
}
