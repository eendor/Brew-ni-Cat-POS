package com.example.cattasticpos.ui.orders

import com.example.cattasticpos.domain.model.OnlineOrder
import com.example.cattasticpos.domain.model.OnlineOrderChangeResult
import com.example.cattasticpos.domain.model.OnlineOrderRules
import com.example.cattasticpos.domain.model.OnlineOrderStatus
import com.example.cattasticpos.domain.repository.OnlineOrdersRepository
import com.example.cattasticpos.domain.usecase.DeductOnlineOrderStockUseCase
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
    val failure: String? = null
) {
    val isSending: Boolean get() = failure == null
}

data class OnlineOrdersUiState(
    val orders: List<OnlineOrder> = emptyList(),
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val lastLoadedAtMillis: Long? = null,
    /** Changes sending or failed, by order reference. A failed one stays until retried or dismissed. */
    val changes: Map<String, PendingChange> = emptyMap(),
    val snackbarMessage: String? = null
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

    fun refresh() {
        scope.launch { load() }
    }

    private suspend fun load() = refreshLock.withLock {
        repository.fetchOrders()
            .onSuccess { orders ->
                val fresh = orders.filter {
                    it.status == OnlineOrderStatus.Pending && announced.add(it.reference)
                }
                _state.update {
                    it.copy(orders = orders, isLoading = false, loadError = null, lastLoadedAtMillis = now())
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

    /** Tries a failed change again with the same correlation id. */
    fun retry(reference: String) {
        val order = _state.value.orders.firstOrNull { it.reference == reference } ?: return
        val change = _state.value.changes[reference] ?: return
        if (change.isSending) return
        send(order, change.copy(failure = null))
    }

    /** Gives up on a failed change and shows the order as the database has it. */
    fun dismiss(reference: String) {
        _state.update { it.copy(changes = it.changes - reference) }
        refresh()
    }

    private fun send(order: OnlineOrder, change: PendingChange) {
        _state.update { it.copy(changes = it.changes + (order.reference to change)) }
        scope.launch {
            val result = repository.changeStatus(
                reference = order.reference,
                from = change.from,
                to = change.to,
                reason = change.reason,
                correlationId = change.correlationId
            )
            when (result) {
                is OnlineOrderChangeResult.Changed -> {
                    var message: String? = null
                    if (OnlineOrderRules.deductsStock(change.to)) {
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

    fun clearSnackbar() {
        _state.update { it.copy(snackbarMessage = null) }
    }
}
