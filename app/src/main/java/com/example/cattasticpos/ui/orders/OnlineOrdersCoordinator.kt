package com.example.cattasticpos.ui.orders

import com.example.cattasticpos.domain.model.OnlineOrder
import com.example.cattasticpos.domain.model.OnlineOrderChangeResult
import com.example.cattasticpos.domain.model.OnlineOrderRules
import com.example.cattasticpos.domain.model.OnlineOrderStatus
import com.example.cattasticpos.domain.model.ShopClosure
import com.example.cattasticpos.domain.repository.OnlineOrdersRepository
import com.example.cattasticpos.domain.usecase.DeductOnlineOrderStockUseCase
import com.example.cattasticpos.domain.usecase.LocalSaleStore
import com.example.cattasticpos.data.remote.OnlineOrderJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** A status change staff asked for that hasn't landed yet. */
data class PendingChange(
    val from: OnlineOrderStatus,
    val to: OnlineOrderStatus,
    val reason: String?,
    /** Kept across retries so the database can tell a retry from a second change. */
    val correlationId: String,
    /** Null while it is being sent, the reason it didn't land once it failed. */
    val failure: String? = null,
    /** An undo of the last change; [to] is then the status being undone (FR-095). */
    val undo: Boolean = false
) {
    /** What the card says while this change is saving or after it failed. */
    val label: String get() = if (undo) "Undo ${to.label}" else to.label

    val isSending: Boolean get() = failure == null
}

data class OnlineOrdersUiState(
    val orders: List<OnlineOrder> = emptyList(),
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val lastLoadedAtMillis: Long? = null,
    /** Changes sending or failed, by order reference. A failed one stays until retried or dismissed. */
    val changes: Map<String, PendingChange> = emptyMap(),
    val snackbarMessage: String? = null,
    /** Orders whose last change can still be undone, by reference (FR-095). */
    val undoable: Set<String> = emptySet(),
    /** The "Closed today" switch; null until it is read, or when the server can't give it. */
    val closure: ShopClosure? = null,
    val closureSaving: Boolean = false
) {
    /** Orders waiting for staff to accept or reject them. */
    val newCount: Int get() = orders.count { it.status == OnlineOrderStatus.Pending }
    val activeOrders: List<OnlineOrder> get() = orders.filter { OnlineOrderRules.isActive(it.status) }
    val pastOrders: List<OnlineOrder> get() = orders.filter { OnlineOrderRules.isFinal(it.status) }
}

/**
 * Keeps the online orders list, moves orders through their statuses, and tells the screen when
 * a new order needs a sound. The screen and the dashboard badge both read [state].
 *
 * A change is never assumed to have landed: the order only changes on screen when the database
 * answers and the list is read again. A change that gets no answer stays visible with a retry
 * (FR-077), and the retry reuses its correlation id, so an answer lost on the way back can't
 * move the order twice.
 */
class OnlineOrdersCoordinator(
    private val scope: CoroutineScope,
    private val repository: OnlineOrdersRepository,
    private val deductStock: DeductOnlineOrderStockUseCase,
    private val localSales: LocalSaleStore? = null,
    private val newCorrelationId: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> Long = System::currentTimeMillis
) {
    private val _state = MutableStateFlow(OnlineOrdersUiState())
    val state: StateFlow<OnlineOrdersUiState> = _state.asStateFlow()

    private val _newOrderAlerts = MutableSharedFlow<Int>(extraBufferCapacity = 4)

    /** Emits how many orders are new each time orders the staff haven't heard about arrive. */
    val newOrderAlerts: SharedFlow<Int> = _newOrderAlerts.asSharedFlow()

    private val refreshLock = Mutex()
    private val announced = mutableSetOf<String>()

    /** Orders undone on this tablet: an undo can't be undone, so they offer no Undo until moved on. */
    private val undone = mutableMapOf<String, OnlineOrderStatus>()

    fun refresh() {
        scope.launch { load() }
    }

    private suspend fun load() = refreshLock.withLock {
        repository.fetchClosure().onSuccess { closure ->
            _state.update { if (it.closureSaving) it else it.copy(closure = closure) }
        }
        repository.fetchOrders()
            .onSuccess { orders ->
                val fresh = orders.filter {
                    it.status == OnlineOrderStatus.Pending && announced.add(it.reference)
                }
                val time = now()
                // A forward step after the undo makes the order undoable again.
                undone.entries.removeAll { (reference, status) ->
                    orders.none { it.reference == reference && it.status == status }
                }
                val undoable = orders.filter {
                    OnlineOrderRules.canUndo(it, time) && it.reference !in undone
                }.map { it.reference }.toSet()
                _state.update {
                    it.copy(
                        orders = orders, isLoading = false, loadError = null,
                        lastLoadedAtMillis = time, undoable = undoable
                    )
                }
                if (fresh.isNotEmpty()) _newOrderAlerts.tryEmit(fresh.size)
            }
            .onFailure { error ->
                _state.update {
                    it.copy(isLoading = false, loadError = error.message ?: "Couldn't load online orders.")
                }
            }
    }

    /** Staff pressed a status button. [reason] is for rejecting and cancelling. */
    fun change(order: OnlineOrder, to: OnlineOrderStatus, reason: String? = null) {
        val existing = _state.value.changes[order.reference]
        if (existing != null) return // one change per order at a time; retry or dismiss the old one
        val problem = OnlineOrderRules.problem(order.status, to, reason)
        if (problem != null) {
            _state.update { it.copy(snackbarMessage = problem) }
            return
        }
        val change = PendingChange(
            from = order.status,
            to = to,
            reason = reason?.trim()?.takeIf { it.isNotEmpty() },
            correlationId = newCorrelationId()
        )
        send(order, change)
    }

    /** Staff pressed Undo: moves the order back to the status before its last change (FR-095). */
    fun undo(order: OnlineOrder) {
        if (_state.value.changes[order.reference] != null) return
        if (order.reference !in _state.value.undoable) {
            _state.update { it.copy(snackbarMessage = "This change can't be undone any more.") }
            return
        }
        send(order, PendingChange(from = order.status, to = order.status, reason = null, correlationId = newCorrelationId(), undo = true))
    }

    /** Tries a failed change again with the same correlation id. */
    fun retry(reference: String) {
        val order = _state.value.orders.firstOrNull { it.reference == reference } ?: return
        val change = _state.value.changes[reference] ?: return
        if (change.isSending) return
        send(order, change.copy(failure = null))
    }

    /** Gives up on a failed change and shows the order as the database has it. */
    /**
     * Turns "Closed today" on or off (Connect FR-111). The switch only moves when the database
     * answers, so the screen never shows a state the website doesn't have.
     */
    fun setClosed(closed: Boolean, note: String = "") {
        if (_state.value.closureSaving) return
        _state.update { it.copy(closureSaving = true) }
        scope.launch {
            repository.saveClosure(ShopClosure(closed, if (closed) note.trim() else ""))
                .onSuccess { saved ->
                    _state.update {
                        it.copy(
                            // The save answers with the switch only; keep the hours we have.
                            closure = saved.copy(hours = it.closure?.hours ?: emptyMap()),
                            closureSaving = false,
                            snackbarMessage = if (saved.closed) {
                                "The shop is closed for today. Online orders are paused until tomorrow."
                            } else {
                                "The shop is open again. Online orders follow the opening hours."
                            }
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            closureSaving = false,
                            snackbarMessage = error.message ?: "Couldn't save Closed today. Try again."
                        )
                    }
                }
        }
    }

    fun dismiss(reference: String) {
        _state.update { it.copy(changes = it.changes - reference) }
        refresh()
    }

    private fun send(order: OnlineOrder, change: PendingChange) {
        _state.update { it.copy(changes = it.changes + (order.reference to change)) }
        scope.launch {
            val result = if (change.undo) {
                repository.undoStatus(order.reference, change.from, change.correlationId)
            } else {
                repository.changeStatus(
                    reference = order.reference,
                    from = change.from,
                    to = change.to,
                    reason = change.reason,
                    correlationId = change.correlationId
                )
            }
            when (result) {
                is OnlineOrderChangeResult.Changed -> {
                    var message: String? = null
                    if (change.undo) {
                        undone[order.reference] = result.status
                        message = if (change.from == OnlineOrderStatus.Completed) {
                            restoreMessage(order, result.voidedSaleId)
                        } else {
                            "${order.reference} is back to ${result.status.label}."
                        }
                    } else if (OnlineOrderRules.deductsStock(change.to)) {
                        message = stockMessage(order)
                    }
                    _state.update {
                        it.copy(changes = it.changes - order.reference, snackbarMessage = message ?: it.snackbarMessage)
                    }
                    load()
                }
                is OnlineOrderChangeResult.Refused -> {
                    _state.update {
                        it.copy(
                            changes = it.changes - order.reference,
                            snackbarMessage = OnlineOrderJson.refusalMessage(result)
                        )
                    }
                    load()
                }
                is OnlineOrderChangeResult.Failed -> {
                    _state.update {
                        it.copy(changes = it.changes + (order.reference to change.copy(failure = result.message)))
                    }
                }
            }
        }
    }

    /** Takes the order's ingredients off the stock; returns a message only when that failed. */
    private suspend fun stockMessage(order: OnlineOrder): String? = try {
        deductStock(order)
        null
    } catch (e: Exception) {
        "${order.reference} is completed, but its stock couldn't be updated. Adjust the stock by hand."
    }

    /** Puts back an undone Complete's stock and says what happened. */
    private suspend fun restoreMessage(order: OnlineOrder, voidedSaleId: Long?): String = try {
        deductStock.restore(order, voidedSaleId, localSales)
        "${order.reference} is back to Ready. Its sale is voided and the stock is back."
    } catch (e: Exception) {
        "${order.reference} is back to Ready and its sale is voided, but the stock couldn't be put back. Adjust the stock by hand."
    }

    fun clearSnackbar() {
        _state.update { it.copy(snackbarMessage = null) }
    }
}
