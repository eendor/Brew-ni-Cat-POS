package com.example.cattasticpos.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.cattasticpos.CattasticPosApp
import com.example.cattasticpos.data.local.DeductedOrderPreferences
import com.example.cattasticpos.data.local.RoomLocalSaleStore
import com.example.cattasticpos.data.repository.OnlineOrdersRepositoryImpl
import com.example.cattasticpos.domain.model.OnlineOrder
import com.example.cattasticpos.domain.model.OnlineOrderStatus
import com.example.cattasticpos.domain.repository.OnlineOrdersRepository
import com.example.cattasticpos.domain.usecase.DeductOnlineOrderStockUseCase
import com.example.cattasticpos.domain.usecase.LocalSaleStore
import com.example.cattasticpos.service.OnlineOrderEvents
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Lives as long as the main screen, so the dashboard badge and the sound work from the first
 * moment the app is open, not only once the Online orders screen has been visited.
 */
class OnlineOrdersViewModel(
    repository: OnlineOrdersRepository,
    deductStock: DeductOnlineOrderStockUseCase,
    localSales: LocalSaleStore? = null
) : ViewModel() {

    private val coordinator = OnlineOrdersCoordinator(viewModelScope, repository, deductStock, localSales)

    val uiState = coordinator.state
    val newOrderAlerts = coordinator.newOrderAlerts

    init {
        coordinator.refresh()
        // Realtime says something changed; a burst of events becomes one read.
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        OnlineOrderEvents.changes
            .debounce(400)
            .onEach { coordinator.refresh() }
            .launchIn(viewModelScope)
        // A safety net for a Realtime connection that dropped without telling anyone.
        viewModelScope.launch {
            while (true) {
                delay(POLL_MILLIS)
                coordinator.refresh()
            }
        }
    }

    fun refresh() = coordinator.refresh()
    fun change(order: OnlineOrder, to: OnlineOrderStatus, reason: String? = null) =
        coordinator.change(order, to, reason)
    fun undo(order: OnlineOrder) = coordinator.undo(order)
    fun retry(reference: String) = coordinator.retry(reference)
    fun dismiss(reference: String) = coordinator.dismiss(reference)
    fun clearSnackbar() = coordinator.clearSnackbar()

    companion object {
        private const val POLL_MILLIS = 30_000L

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as CattasticPosApp
                val container = app.container
                return OnlineOrdersViewModel(
                    repository = OnlineOrdersRepositoryImpl(
                        appConfigDao = container.database.appConfigDao(),
                        authManager = container.supabaseAuthManager
                    ),
                    deductStock = DeductOnlineOrderStockUseCase(
                        menuRepository = container.menuRepository,
                        recipeRepository = container.recipeRepository,
                        inventoryRepository = container.inventoryRepository,
                        store = DeductedOrderPreferences(app)
                    ),
                    localSales = RoomLocalSaleStore(container.database.orderDao())
                ) as T
            }
        }
    }
}
