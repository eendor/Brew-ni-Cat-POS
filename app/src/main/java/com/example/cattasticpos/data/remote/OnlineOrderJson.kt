package com.example.cattasticpos.data.remote

import com.example.cattasticpos.domain.model.OnlineOrder
import com.example.cattasticpos.domain.model.OnlineOrderChangeResult
import com.example.cattasticpos.domain.model.OnlineOrderLine
import com.example.cattasticpos.domain.model.OnlineOrderStatus
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime

/** Reads the rows `online_orders` returns and the answer of `set_online_order_status`. */
object OnlineOrderJson {

    /** `online_orders?select=*,online_order_items(*)` */
    fun parseOrders(body: String): List<OnlineOrder> {
        val array = JSONArray(body)
        return (0 until array.length()).mapNotNull { parseOrder(array.getJSONObject(it)) }
    }

    /** Null for a row with a status this app doesn't know, so one odd row can't blank the list. */
    fun parseOrder(json: JSONObject): OnlineOrder? {
        val status = OnlineOrderStatus.fromWire(json.optString("status")) ?: return null
        val lines = json.optJSONArray("online_order_items")
        val parsedLines = if (lines == null) {
            emptyList()
        } else {
            (0 until lines.length()).map { parseLine(lines.getJSONObject(it)) }
                .sortedBy { it.lineNumber }
        }
        return OnlineOrder(
            id = json.getString("id"),
            reference = json.getString("reference"),
            status = status,
            customerName = json.optString("customer_name"),
            customerPhone = json.optString("customer_phone"),
            notes = json.optString("notes"),
            paymentPreference = json.optString("payment_preference"),
            subtotal = json.optDouble("subtotal", 0.0),
            itemCount = json.optInt("item_count", parsedLines.sumOf { it.quantity }),
            rejectionReason = json.optStringOrNull("rejection_reason"),
            createdAtMillis = parseTime(json.optString("created_at")),
            updatedAtMillis = parseTime(json.optString("updated_at")),
            lines = parsedLines
        )
    }

    private fun parseLine(json: JSONObject) = OnlineOrderLine(
        lineNumber = json.optInt("line_number"),
        itemId = json.getString("item_id"),
        itemName = json.optString("item_name"),
        variantId = json.optString("variant_id"),
        variantName = json.optString("variant_name"),
        flavor = json.optStringOrNull("flavor"),
        unitPrice = json.optDouble("unit_price", 0.0),
        quantity = json.optInt("quantity", 1),
        lineTotal = json.optDouble("line_total", 0.0)
    )

    /** `{"ok":true,...}` or `{"ok":false,"error":"stale","status":"accepted"}` */
    fun parseChange(body: String): OnlineOrderChangeResult {
        val json = JSONObject(body)
        val status = OnlineOrderStatus.fromWire(json.optString("status"))
        if (json.optBoolean("ok")) {
            return OnlineOrderChangeResult.Changed(
                reference = json.optString("reference"),
                status = status ?: return OnlineOrderChangeResult.Failed("The server's answer was not understood."),
                replayed = json.optBoolean("replayed")
            )
        }
        return OnlineOrderChangeResult.Refused(
            error = json.optString("error", "invalid"),
            currentStatus = status
        )
    }

    /** What to tell staff when the database refused a change. */
    fun refusalMessage(refused: OnlineOrderChangeResult.Refused): String = when (refused.error) {
        "stale" -> "Someone else already changed this order" +
            (refused.currentStatus?.let { " (now ${it.label})" } ?: "") + "."
        "not_allowed" -> "That step isn't allowed from this status."
        "reason_required" -> "Tell the customer why the order is rejected."
        "not_found" -> "This order can't be found any more."
        else -> "The change was refused."
    }

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }

    private fun parseTime(value: String): Long =
        runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrDefault(0L)
}
