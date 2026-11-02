package com.example.cattasticpos.service

import android.content.Context
import android.util.Log
import com.example.cattasticpos.CattasticPosApp
import com.example.cattasticpos.data.local.entity.OrderEntity
import com.example.cattasticpos.data.local.entity.OrderItemEntity
import com.example.cattasticpos.worker.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import androidx.room.withTransaction

class SupabaseRealtimeManager(private val context: Context) {

    companion object {
        private const val TAG = "RealtimeManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var isClosed = false
    private var refCount = 1

    fun start() {
        isClosed = false
        connect()
    }

    fun stop() {
        isClosed = true
        webSocket?.close(1000, "App closed")
    }

    private fun connect() {
        val app = context.applicationContext as CattasticPosApp
        scope.launch {
            val config = app.container.database.appConfigDao().getAppConfigOnce() ?: return@launch
            val url = config.supabaseUrl.trim()
            val key = config.supabaseAnonKey.trim()

            if (url.isEmpty() || key.isEmpty()) {
                Log.d(TAG, "Supabase config missing. Not starting Realtime WebSocket.")
                return@launch
            }

            // Realtime change events are filtered by row-level security, so the
            // subscription must carry the authenticated session token. Without a
            // session (fresh install before sign-in) keep retrying until one exists.
            val accessToken = app.container.supabaseAuthManager.getValidAccessToken()
            if (accessToken == null) {
                Log.w(TAG, "No authenticated Supabase session. Retrying Realtime connect later.")
                reconnect()
                return@launch
            }

            val wsUrl = url.replace("https://", "wss://") + "/realtime/v1/websocket?apikey=$key&vsn=1.0.0"
            val request = Request.Builder().url(wsUrl).build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.i(TAG, "Supabase Realtime WebSocket Connected.")
                    startHeartbeat(webSocket)
                    joinChannel(webSocket, accessToken)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleMessage(text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.w(TAG, "Supabase Realtime WebSocket Closing: $code / $reason")
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.w(TAG, "Supabase Realtime WebSocket Closed. Reconnecting in 10s...")
                    reconnect()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "Supabase Realtime WebSocket Failure. Reconnecting in 10s...", t)
                    reconnect()
                }
            })
        }
    }

    private fun reconnect() {
        if (isClosed) return
        scope.launch {
            delay(10000)
            if (!isClosed) connect()
        }
    }

    private fun startHeartbeat(webSocket: WebSocket) {
        scope.launch {
            val app = context.applicationContext as CattasticPosApp
            var lastToken: String? = null
            while (!isClosed) {
                delay(30000)
                try {
                    val ref = refCount++
                    val heartbeat = JSONObject().apply {
                        put("topic", "phoenix")
                        put("event", "heartbeat")
                        put("payload", JSONObject())
                        put("ref", ref.toString())
                    }
                    webSocket.send(heartbeat.toString())

                    // Access tokens expire hourly; push the refreshed one to the
                    // channel so the server keeps delivering RLS-checked events.
                    val token = app.container.supabaseAuthManager.getValidAccessToken()
                    if (token != null && token != lastToken) {
                        lastToken = token
                        val tokenMessage = JSONObject().apply {
                            put("topic", "realtime:public:orders")
                            put("event", "access_token")
                            put("payload", JSONObject().apply { put("access_token", token) })
                            put("ref", (refCount++).toString())
                        }
                        webSocket.send(tokenMessage.toString())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Heartbeat failed", e)
                }
            }
        }
    }

    private fun joinChannel(webSocket: WebSocket, accessToken: String) {
        try {
            val ref = refCount++
            val joinMessage = JSONObject().apply {
                put("topic", "realtime:public:orders")
                put("event", "phx_join")
                put("payload", JSONObject().apply {
                    put("access_token", accessToken)
                    put("config", JSONObject().apply {
                        put("postgres_changes", JSONArray().apply {
                            put(JSONObject().apply {
                                put("event", "INSERT")
                                put("schema", "public")
                                put("table", "orders")
                            })
                            put(JSONObject().apply {
                                put("event", "UPDATE")
                                put("schema", "public")
                                put("table", "orders")
                            })
                        })
                    })
                })
                put("ref", ref.toString())
            }
            webSocket.send(joinMessage.toString())
            Log.i(TAG, "Sent join channel message.")
            joinOnlineOrders(webSocket, accessToken)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to join realtime channel", e)
        }
    }

    /**
     * Online orders ride the same socket on their own channel (FR-080). Row-level security lets
     * the store account see every order, so a new one reaches the tablet the moment it's placed.
     */
    private fun joinOnlineOrders(webSocket: WebSocket, accessToken: String) {
        try {
            val joinMessage = JSONObject().apply {
                put("topic", "realtime:public:online_orders")
                put("event", "phx_join")
                put("payload", JSONObject().apply {
                    put("access_token", accessToken)
                    put("config", JSONObject().apply {
                        put("postgres_changes", JSONArray().apply {
                            for (change in listOf("INSERT", "UPDATE")) {
                                put(JSONObject().apply {
                                    put("event", change)
                                    put("schema", "public")
                                    put("table", "online_orders")
                                })
                            }
                        })
                    })
                })
                put("ref", (refCount++).toString())
            }
            webSocket.send(joinMessage.toString())
            // The screen reads the list again after a reconnect, in case orders came in meanwhile.
            OnlineOrderEvents.notifyChanged()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to join the online orders channel", e)
        }
    }

    private fun handleMessage(text: String) {
        scope.launch {
            try {
                val json = JSONObject(text)
                val event = json.optString("event")
                if (event != "postgres_changes") return@launch

                val payload = json.optJSONObject("payload") ?: return@launch
                val data = payload.optJSONObject("data") ?: return@launch
                if (data.optString("table") == "online_orders") {
                    OnlineOrderEvents.notifyChanged()
                    return@launch
                }
                val type = data.optString("type") // "INSERT" or "UPDATE"
                val record = data.optJSONObject("record") ?: return@launch

                val remoteOrderId = record.getLong("id")
                val remoteDeviceId = record.getString("device_id")

                val app = context.applicationContext as CattasticPosApp
                val database = app.container.database
                val config = database.appConfigDao().getAppConfigOnce() ?: return@launch
                val localDeviceId = config.deviceId.trim()

                // Note: we intentionally do NOT skip events whose device_id matches this device.
                // Another terminal can void/serve an order that originated here, and we must react
                // to that in real time. Merging our own echo is a harmless no-op (the merger
                // reconciles to cloud state and does nothing when it already matches).
                Log.d(TAG, "Received remote change event $type for order $remoteOrderId (origin $remoteDeviceId).")
                pullAndMergeOrder(remoteOrderId, remoteDeviceId, localDeviceId)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling message", e)
            }
        }
    }

    private fun pullAndMergeOrder(remoteOrderId: Long, remoteDeviceId: String, localDeviceId: String) {
        scope.launch {
            val app = context.applicationContext as CattasticPosApp
            val database = app.container.database
            val config = database.appConfigDao().getAppConfigOnce() ?: return@launch
            val url = config.supabaseUrl.trim()
            val key = config.supabaseAnonKey.trim()
            val accessToken = app.container.supabaseAuthManager.getValidAccessToken() ?: return@launch

            try {
                // Fetch order along with order_items
                val restUrl = "$url/rest/v1/orders?select=*,order_items(*)&id=eq.$remoteOrderId"
                val request = Request.Builder()
                    .url(restUrl)
                    .get()
                    .header("apikey", key)
                    .header("Authorization", "Bearer $accessToken")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    response.close()
                    return@launch
                }

                val body = response.body?.string() ?: "[]"
                response.close()

                val jsonArray = JSONArray(body)
                if (jsonArray.length() == 0) return@launch

                // Live event: route through the shared merger so the local-id mapping matches the
                // historical-pull and periodic catch-up paths exactly.
                //
                // Inventory is NOT restored here. The device that performed the void already
                // restocked locally and pushed the corrected levels up; restoring again on every
                // other terminal double-counted the stock, and whichever tablet uploaded last
                // wrote that inflated number back to the cloud. Correct levels arrive with the
                // regular inventory sync instead — the same reasoning the bulk paths already use.
                com.example.cattasticpos.data.sync.OrderSyncMerger.mergeRemoteOrder(
                    database = database,
                    recipeRepository = app.container.recipeRepository,
                    inventoryRepository = app.container.inventoryRepository,
                    orderJson = jsonArray.getJSONObject(0),
                    localDeviceId = localDeviceId,
                    restoreInventoryOnVoid = false
                )
                Log.i(TAG, "Successfully merged remote order change for remoteId=$remoteOrderId.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pull and merge remote order", e)
            }
        }
    }
}
