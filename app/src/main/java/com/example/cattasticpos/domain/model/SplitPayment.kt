package com.example.cattasticpos.domain.model

import java.util.Locale

/**
 * One sale paid partly in cash and partly by GCash (FR-096). The sale keeps one total; its
 * payment reference records the cash part first, so the daily cash and GCash totals can count
 * each part under its own method: `cash=50.00|gcash=100.00|account=Personal|ref=123`.
 */
object SplitPayment {
    const val METHOD = "SPLIT"

    data class Parts(val cash: Double, val gcash: Double, val account: String?, val reference: String?)

    /** The two parts must both be above zero and add up to the total, to the centavo. */
    fun isValid(total: Double, cash: Double, gcash: Double): Boolean =
        cash > 0.0 && gcash > 0.0 && toCentavos(cash) + toCentavos(gcash) == toCentavos(total)

    fun encode(cash: Double, gcash: Double, account: String, reference: String?): String = buildString {
        append("cash=").append(money(cash))
        append("|gcash=").append(money(gcash))
        append("|account=").append(account.replace("|", " "))
        val ref = reference?.trim()?.replace("|", " ")
        if (!ref.isNullOrEmpty()) append("|ref=").append(ref)
    }

    fun parse(paymentReference: String?): Parts? {
        if (paymentReference.isNullOrBlank()) return null
        val fields = paymentReference.split("|").mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
        }.toMap()
        val cash = fields["cash"]?.toDoubleOrNull() ?: return null
        val gcash = fields["gcash"]?.toDoubleOrNull() ?: return null
        return Parts(cash, gcash, fields["account"], fields["ref"])
    }

    /** What receipts and the history list show for a sale's payment. */
    fun describe(paymentMethod: String, paymentReference: String?): String {
        if (paymentMethod != METHOD) return paymentMethod
        val parts = parse(paymentReference) ?: return "CASH + GCASH"
        return "CASH ₱${money(parts.cash)} + GCASH ₱${money(parts.gcash)}"
    }

    private fun toCentavos(amount: Double): Long = Math.round(amount * 100)

    private fun money(amount: Double): String = String.format(Locale.US, "%.2f", amount)
}
