package com.example.cattasticpos.domain.model

/** The statuses an online order can be in (`public.online_orders.status`). */
enum class OnlineOrderStatus(val wire: String, val label: String) {
    Pending("pending", "New"),
    Accepted("accepted", "Accepted"),
    Rejected("rejected", "Rejected"),
    Preparing("preparing", "Preparing"),
    Ready("ready", "Ready for pickup"),
    Completed("completed", "Completed"),
    Cancelled("cancelled", "Cancelled");

    companion object {
        fun fromWire(value: String?): OnlineOrderStatus? =
            entries.firstOrNull { it.wire == value }
    }
}

/** One line of an online order, as the customer ordered it. */
data class OnlineOrderLine(
    val lineNumber: Int,
    val itemId: String,
    val itemName: String,
    val variantId: String,
    val variantName: String,
    val flavor: String?,
    val unitPrice: Double,
    val quantity: Int,
    val lineTotal: Double
)

data class OnlineOrder(
    val id: String,
    val reference: String,
    val status: OnlineOrderStatus,
    val customerName: String,
    val customerPhone: String,
    val notes: String,
    val paymentPreference: String,
    val subtotal: Double,
    val itemCount: Int,
    val rejectionReason: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val lines: List<OnlineOrderLine>
)

/**
 * The steps staff may take, the same as `private.is_allowed_shop_step()` in the database. The
 * database is the authority and refuses anything else; this list only decides which buttons the
 * screen shows, so a button never offers a step the database would turn down.
 */
object OnlineOrderRules {
    /** The longest reason the database accepts (`online_orders.rejection_reason`). */
    const val REASON_MAX_LENGTH = 300

    private val steps: Map<OnlineOrderStatus, List<OnlineOrderStatus>> = mapOf(
        OnlineOrderStatus.Pending to listOf(OnlineOrderStatus.Accepted, OnlineOrderStatus.Rejected),
        OnlineOrderStatus.Accepted to listOf(OnlineOrderStatus.Preparing, OnlineOrderStatus.Cancelled),
        OnlineOrderStatus.Preparing to listOf(OnlineOrderStatus.Ready, OnlineOrderStatus.Cancelled),
        OnlineOrderStatus.Ready to listOf(OnlineOrderStatus.Completed, OnlineOrderStatus.Cancelled)
    )

    fun nextSteps(from: OnlineOrderStatus): List<OnlineOrderStatus> = steps[from].orEmpty()

    fun isAllowed(from: OnlineOrderStatus, to: OnlineOrderStatus): Boolean =
        to in nextSteps(from)

    /** A rejection must say why; the customer sees the reason. */
    fun requiresReason(to: OnlineOrderStatus): Boolean = to == OnlineOrderStatus.Rejected

    /** Rejecting and cancelling may carry a reason; no other step may. */
    fun acceptsReason(to: OnlineOrderStatus): Boolean =
        to == OnlineOrderStatus.Rejected || to == OnlineOrderStatus.Cancelled

    fun isFinal(status: OnlineOrderStatus): Boolean = nextSteps(status).isEmpty()

    /** Orders staff still have to act on, newest work first on the screen. */
    fun isActive(status: OnlineOrderStatus): Boolean = !isFinal(status)

    /** Why a step can't be taken, or null when it can. */
    fun problem(from: OnlineOrderStatus, to: OnlineOrderStatus, reason: String?): String? {
        if (!isAllowed(from, to)) return "${from.label} orders can't move to ${to.label}."
        val trimmed = reason?.trim().orEmpty()
        if (requiresReason(to) && trimmed.isEmpty()) return "Tell the customer why the order is rejected."
        if (trimmed.isNotEmpty() && !acceptsReason(to)) return "Only a rejection or cancellation takes a reason."
        if (trimmed.length > REASON_MAX_LENGTH) return "Keep the reason under $REASON_MAX_LENGTH characters."
        return null
    }

    /**
     * Stock is taken off the tablet only when an order is completed, once per reference
     * (ADR-021). A rejected or cancelled order never changes stock.
     */
    fun deductsStock(to: OnlineOrderStatus): Boolean = to == OnlineOrderStatus.Completed

    /** How long after a change staff can still undo it; the database checks the same limit. */
    const val UNDO_WINDOW_MILLIS = 5 * 60_000L

    /**
     * Whether the card offers Undo: the last change is under five minutes old and was a step
     * forward. A new order has nothing to undo, and a rejection can't be undone because the
     * customer has already seen the reason. The database has the last word (FR-095).
     */
    fun canUndo(order: OnlineOrder, nowMillis: Long): Boolean =
        order.status != OnlineOrderStatus.Pending &&
            order.status != OnlineOrderStatus.Rejected &&
            nowMillis - order.updatedAtMillis in 0..UNDO_WINDOW_MILLIS
}

/** What the database said to a status change. */
sealed interface OnlineOrderChangeResult {
    /** The change is saved. [replayed] means an earlier try of the same change had already landed. */
    data class Changed(
        val reference: String,
        val status: OnlineOrderStatus,
        val replayed: Boolean,
        /** The POS sale the database voided when a Complete was undone. */
        val voidedSaleId: Long? = null
    ) : OnlineOrderChangeResult

    /** The database refused: `stale`, `not_allowed`, `reason_required`, `invalid` or `not_found`. */
    data class Refused(val error: String, val currentStatus: OnlineOrderStatus?) : OnlineOrderChangeResult

    /** No answer (offline, signed out, server error). The same change can be tried again. */
    data class Failed(val message: String) : OnlineOrderChangeResult
}
