package com.example.cattasticpos

import com.example.cattasticpos.data.remote.OnlineOrderJson
import com.example.cattasticpos.domain.model.Category
import com.example.cattasticpos.domain.model.InventoryItem
import com.example.cattasticpos.domain.model.Item
import com.example.cattasticpos.domain.model.OnlineOrder
import com.example.cattasticpos.domain.model.OnlineOrderChangeResult
import com.example.cattasticpos.domain.model.OnlineOrderLine
import com.example.cattasticpos.domain.model.OnlineOrderRules
import com.example.cattasticpos.domain.model.OnlineOrderStatus
import com.example.cattasticpos.domain.model.OnlineOrderStatus.Accepted
import com.example.cattasticpos.domain.model.OnlineOrderStatus.Cancelled
import com.example.cattasticpos.domain.model.OnlineOrderStatus.Completed
import com.example.cattasticpos.domain.model.OnlineOrderStatus.Pending
import com.example.cattasticpos.domain.model.OnlineOrderStatus.Preparing
import com.example.cattasticpos.domain.model.OnlineOrderStatus.Ready
import com.example.cattasticpos.domain.model.OnlineOrderStatus.Rejected
import com.example.cattasticpos.domain.model.RecipeMapping
import com.example.cattasticpos.domain.model.Variant
import com.example.cattasticpos.domain.repository.InventoryRepository
import com.example.cattasticpos.domain.repository.MenuRepository
import com.example.cattasticpos.domain.repository.OnlineOrdersRepository
import com.example.cattasticpos.domain.repository.RecipeRepository
import com.example.cattasticpos.domain.usecase.DeductOnlineOrderStockUseCase
import com.example.cattasticpos.domain.usecase.DeductedOrderStore
import com.example.cattasticpos.domain.usecase.LocalSaleStore
import com.example.cattasticpos.ui.orders.OnlineOrdersCoordinator
import com.example.cattasticpos.ui.orders.endingStep
import com.example.cattasticpos.ui.orders.primaryStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** TC-P6-191 to TC-P6-198: the Online orders screen's rules, parsing, retries and stock. */
class OnlineOrdersTest {

    // ---- fakes ---------------------------------------------------------------------------

    private class FakeOrders(var orders: List<OnlineOrder>) : OnlineOrdersRepository {
        data class Call(
            val reference: String,
            val from: OnlineOrderStatus,
            val to: OnlineOrderStatus,
            val reason: String?,
            val correlationId: String
        )

        val calls = mutableListOf<Call>()
        var fetchFails = false
        val answers = ArrayDeque<OnlineOrderChangeResult>()

        override suspend fun fetchOrders(): Result<List<OnlineOrder>> =
            if (fetchFails) Result.failure(IllegalStateException("offline")) else Result.success(orders)

        override suspend fun changeStatus(
            reference: String,
            from: OnlineOrderStatus,
            to: OnlineOrderStatus,
            reason: String?,
            correlationId: String
        ): OnlineOrderChangeResult {
            calls += Call(reference, from, to, reason, correlationId)
            val answer = answers.removeFirstOrNull()
                ?: OnlineOrderChangeResult.Changed(reference, to, replayed = false)
            if (answer is OnlineOrderChangeResult.Changed) {
                orders = orders.map { if (it.reference == reference) it.copy(status = answer.status) else it }
            }
            return answer
        }

        val undoCalls = mutableListOf<Pair<String, OnlineOrderStatus>>()
        val undoAnswers = ArrayDeque<OnlineOrderChangeResult>()

        override suspend fun undoStatus(
            reference: String,
            from: OnlineOrderStatus,
            correlationId: String
        ): OnlineOrderChangeResult {
            undoCalls += reference to from
            val answer = undoAnswers.removeFirst()
            if (answer is OnlineOrderChangeResult.Changed) {
                orders = orders.map { if (it.reference == reference) it.copy(status = answer.status) else it }
            }
            return answer
        }
    }

    private class FakeMenu(private val items: List<Item>) : MenuRepository {
        override fun getCategories(): Flow<List<Category>> = flowOf(emptyList())
        override fun getItems(): Flow<List<Item>> = flowOf(items)
        override suspend fun upsertCategory(category: Category) = Unit
        override suspend fun deleteCategory(categoryId: String) = Unit
        override suspend fun upsertItem(item: Item) = Unit
        override suspend fun deleteItem(itemId: String) = Unit
    }

    private class FakeRecipes(private val mappings: List<RecipeMapping>) : RecipeRepository {
        override fun getAllMappings(): Flow<List<RecipeMapping>> = flowOf(mappings)
        override suspend fun getAllMappingsOnce(): List<RecipeMapping> = mappings
        override fun getMappingsForMenu(menuItemId: String): Flow<List<RecipeMapping>> = flowOf(emptyList())
        override suspend fun getMappingsForCheckout(menuItemId: String, variantName: String?) = emptyList<RecipeMapping>()
        override suspend fun resolveCheckoutMappings(menuItemId: String, sizeVariantName: String?, flavor: String?) =
            emptyList<RecipeMapping>()
        override suspend fun insertMapping(mapping: RecipeMapping) = Unit
        override suspend fun insertMappings(mappings: List<RecipeMapping>) = Unit
        override suspend fun deleteMapping(mapping: RecipeMapping) = Unit
        override suspend fun deleteMappingsForInventory(inventoryItemId: String) = Unit
    }

    private class FakeInventory : InventoryRepository {
        val stock = mutableMapOf("cups" to 100.0, "matcha" to 50.0)
        override fun getAllInventory(): Flow<List<InventoryItem>> = flowOf(emptyList())
        override suspend fun getInventoryItemById(itemId: String): InventoryItem? = null
        override suspend fun insertInventoryItems(items: List<InventoryItem>) = Unit
        override suspend fun updateInventoryItem(item: InventoryItem) = Unit
        override suspend fun decrementStock(inventoryId: String, amount: Double) {
            stock[inventoryId] = (stock[inventoryId] ?: 0.0) - amount
        }
        override suspend fun restockItem(itemId: String, addedAmount: Double) {
            stock[itemId] = (stock[itemId] ?: 0.0) + addedAmount
        }
        override suspend fun deleteInventoryItem(itemId: String) = Unit
    }

    private class MemoryStore : DeductedOrderStore {
        private val done = mutableSetOf<String>()
        override fun claim(reference: String) = done.add(reference)
        override fun release(reference: String) = done.remove(reference)
    }

    private class FakeSales(val voided: MutableSet<Long> = mutableSetOf()) : LocalSaleStore {
        override suspend fun markVoided(remoteId: Long) = voided.add(remoteId)
        override suspend fun isVoided(remoteId: Long) = remoteId in voided
    }

    private val matcha = Item(
        id = "drink_matcha",
        categoryId = "cat_drinks",
        name = "Matcha",
        flavors = emptyList(),
        variants = listOf(Variant(id = "16oz", name = "16oz", basePrice = 49.0))
    )

    private fun line(quantity: Int = 2) = OnlineOrderLine(
        lineNumber = 1, itemId = "drink_matcha", itemName = "Matcha", variantId = "16oz",
        variantName = "16oz", flavor = null, unitPrice = 49.0, quantity = quantity,
        lineTotal = 49.0 * quantity
    )

    private fun order(reference: String = "BNC-AAAAAA", status: OnlineOrderStatus = Pending) = OnlineOrder(
        id = "id-$reference", reference = reference, status = status, customerName = "Ana",
        customerPhone = "+639171234567", notes = "", paymentPreference = "Cash", subtotal = 98.0,
        itemCount = 2, rejectionReason = null, createdAtMillis = 1_000L, updatedAtMillis = 2_000L,
        lines = listOf(line())
    )

    private class Rig(
        val orders: FakeOrders,
        val inventory: FakeInventory,
        val coordinator: OnlineOrdersCoordinator,
        val sales: FakeSales
    )

    private fun rig(vararg initial: OnlineOrder): Rig {
        val sales = FakeSales()
        val orders = FakeOrders(initial.toList())
        val inventory = FakeInventory()
        val deduct = DeductOnlineOrderStockUseCase(
            menuRepository = FakeMenu(listOf(matcha)),
            recipeRepository = FakeRecipes(
                listOf(
                    RecipeMapping("r1", "drink_matcha", null, "cups", 1.0),
                    RecipeMapping("r2", "drink_matcha", null, "matcha", 5.0)
                )
            ),
            inventoryRepository = inventory,
            store = MemoryStore()
        )
        var next = 0
        val coordinator = OnlineOrdersCoordinator(
            scope = CoroutineScope(Dispatchers.Unconfined),
            repository = orders,
            deductStock = deduct,
            localSales = sales,
            newCorrelationId = { "corr-${++next}" },
            now = { 5_000L }
        )
        return Rig(orders, inventory, coordinator, sales)
    }

    // ---- status rules --------------------------------------------------------------------

    @Test
    fun `TC-P6-191 the allowed steps are exactly the database's`() {
        // the pairs of private.is_allowed_shop_step() in 20261028153552_online_order_status.sql
        val database = setOf(
            Pending to Accepted, Pending to Rejected,
            Accepted to Preparing, Accepted to Cancelled,
            Preparing to Ready, Preparing to Cancelled,
            Ready to Completed, Ready to Cancelled
        )
        val app = OnlineOrderStatus.entries.flatMap { from ->
            OnlineOrderStatus.entries.filter { to -> OnlineOrderRules.isAllowed(from, to) }
                .map { to -> from to to }
        }.toSet()
        assertEquals(database, app)
        assertTrue(OnlineOrderRules.isFinal(Completed))
        assertTrue(OnlineOrderRules.isFinal(Rejected))
        assertTrue(OnlineOrderRules.isFinal(Cancelled))
        assertFalse(OnlineOrderRules.isFinal(Ready))
    }

    @Test
    fun `TC-P6-192 every button the screen shows is an allowed step`() {
        OnlineOrderStatus.entries.forEach { status ->
            primaryStep(status)?.let { assertTrue("$status primary", OnlineOrderRules.isAllowed(status, it.first)) }
            endingStep(status)?.let { assertTrue("$status ending", OnlineOrderRules.isAllowed(status, it.first)) }
            // a finished order has no buttons, an active one always has one
            val hasButton = primaryStep(status) != null || endingStep(status) != null
            assertEquals(status.name, !OnlineOrderRules.isFinal(status), hasButton)
        }
        assertEquals(Rejected, endingStep(Pending)?.first)
        assertEquals(Cancelled, endingStep(Preparing)?.first)
    }

    @Test
    fun `TC-P6-193 a rejection needs a reason, a skip or a repeat is refused, and only completing takes stock`() {
        assertNotNull(OnlineOrderRules.problem(Pending, Rejected, null))
        assertNotNull(OnlineOrderRules.problem(Pending, Rejected, "   "))
        assertNull(OnlineOrderRules.problem(Pending, Rejected, "Out of matcha"))
        assertNull(OnlineOrderRules.problem(Accepted, Cancelled, null))
        assertNull(OnlineOrderRules.problem(Accepted, Cancelled, "Customer called"))
        assertNotNull(OnlineOrderRules.problem(Pending, Preparing, null)) // skips Accepted
        assertNotNull(OnlineOrderRules.problem(Completed, Ready, null)) // backwards
        assertNotNull(OnlineOrderRules.problem(Pending, Accepted, "a reason")) // only reject/cancel take one
        assertNotNull(OnlineOrderRules.problem(Pending, Rejected, "x".repeat(301)))
        assertTrue(OnlineOrderRules.deductsStock(Completed))
        OnlineOrderStatus.entries.filter { it != Completed }.forEach {
            assertFalse("$it must not take stock", OnlineOrderRules.deductsStock(it))
        }
    }

    // ---- reading the database ------------------------------------------------------------

    @Test
    fun `TC-P6-194 orders and answers from the database are read, and an unknown status is skipped`() {
        val body = """
            [{"id":"a","reference":"BNC-AAAAAA","status":"pending","customer_name":"Ana",
              "customer_phone":"+639171234567","notes":"","payment_preference":"GCash","subtotal":98.00,
              "item_count":2,"rejection_reason":null,"created_at":"2026-11-02T01:00:00+00:00",
              "updated_at":"2026-11-02T01:00:00+00:00",
              "online_order_items":[
                {"line_number":2,"item_id":"bite_fries","item_name":"Fries","variant_id":"small",
                 "variant_name":"Small","flavor":"Cheese","unit_price":30.0,"quantity":1,"line_total":30.0},
                {"line_number":1,"item_id":"drink_matcha","item_name":"Matcha","variant_id":"16oz",
                 "variant_name":"16oz","flavor":null,"unit_price":49.0,"quantity":2,"line_total":98.0}]},
             {"id":"b","reference":"BNC-BBBBBB","status":"mystery","customer_name":"X","customer_phone":"",
              "notes":"","payment_preference":"Cash","subtotal":1,"item_count":1,
              "created_at":"2026-11-02T01:00:00Z","updated_at":"2026-11-02T01:00:00Z","online_order_items":[]}]
        """.trimIndent()
        val parsed = OnlineOrderJson.parseOrders(body)
        assertEquals(1, parsed.size)
        val first = parsed.single()
        assertEquals(Pending, first.status)
        assertEquals("GCash", first.paymentPreference)
        assertEquals(listOf(1, 2), first.lines.map { it.lineNumber }) // sorted by line
        assertNull(first.lines[0].flavor)
        assertEquals("Cheese", first.lines[1].flavor)
        assertNull(first.rejectionReason)
        assertEquals(1_793_581_200_000L, first.createdAtMillis)

        val changed = OnlineOrderJson.parseChange("""{"ok":true,"replayed":true,"reference":"BNC-AAAAAA","status":"completed"}""")
        assertEquals(OnlineOrderChangeResult.Changed("BNC-AAAAAA", Completed, replayed = true), changed)
        val stale = OnlineOrderJson.parseChange("""{"ok":false,"error":"stale","status":"accepted"}""")
        assertEquals(OnlineOrderChangeResult.Refused("stale", Accepted), stale)
        assertTrue(
            OnlineOrderJson.refusalMessage(stale as OnlineOrderChangeResult.Refused).contains("Accepted")
        )
    }

    // ---- the coordinator -----------------------------------------------------------------

    @Test
    fun `TC-P6-195 moving an order forward sends one change and shows the new status`() {
        val r = rig(order())
        r.coordinator.refresh()
        r.coordinator.change(r.coordinator.state.value.orders.single(), Accepted)
        assertEquals(1, r.orders.calls.size)
        assertEquals(Pending, r.orders.calls.single().from)
        assertEquals(Accepted, r.orders.calls.single().to)
        assertEquals(Accepted, r.coordinator.state.value.orders.single().status)
        assertTrue(r.coordinator.state.value.changes.isEmpty())
        // accepting never touches stock
        assertEquals(100.0, r.inventory.stock["cups"]!!, 0.0)
    }

    @Test
    fun `TC-P6-196 a change with no answer stays visible and the retry reuses its correlation id`() {
        val r = rig(order())
        r.coordinator.refresh()
        r.orders.answers += OnlineOrderChangeResult.Failed("Couldn't reach the server.")
        r.coordinator.change(r.coordinator.state.value.orders.single(), Accepted)

        val failed = r.coordinator.state.value.changes["BNC-AAAAAA"]!!
        assertEquals("Couldn't reach the server.", failed.failure)
        assertFalse(failed.isSending)
        assertEquals(Pending, r.coordinator.state.value.orders.single().status) // not assumed to have landed

        // pressing the button again while the failure is showing doesn't start a second change
        r.coordinator.change(r.coordinator.state.value.orders.single(), Rejected, "no")
        assertEquals(1, r.orders.calls.size)

        r.coordinator.retry("BNC-AAAAAA")
        assertEquals(2, r.orders.calls.size)
        assertEquals(r.orders.calls[0].correlationId, r.orders.calls[1].correlationId)
        assertEquals(Accepted, r.coordinator.state.value.orders.single().status)
        assertTrue(r.coordinator.state.value.changes.isEmpty())
    }

    @Test
    fun `TC-P6-197 a stale or refused change is explained, cleared and the list is read again`() {
        val r = rig(order())
        r.coordinator.refresh()
        val shown = r.coordinator.state.value.orders.single()
        // another device accepted it first; the database now holds Accepted
        r.orders.orders = listOf(shown.copy(status = Accepted))
        r.orders.answers += OnlineOrderChangeResult.Refused("stale", Accepted)
        r.coordinator.change(shown, Rejected, "Out of matcha")

        val state = r.coordinator.state.value
        assertTrue(state.snackbarMessage!!.contains("Accepted"))
        assertTrue(state.changes.isEmpty())
        assertEquals(Accepted, state.orders.single().status)

        // rejecting with no reason never reaches the database
        val before = r.orders.calls.size
        r.coordinator.change(state.orders.single(), Rejected, null)
        assertEquals(before, r.orders.calls.size)
        assertTrue(r.coordinator.state.value.snackbarMessage!!.isNotBlank())
    }

    @Test
    fun `TC-P6-198 completing takes stock once per reference, and reject or cancel never does`() {
        val r = rig(order(status = Ready), order("BNC-BBBBBB", Pending), order("BNC-CCCCCC", Accepted))
        r.coordinator.refresh()
        val ready = r.coordinator.state.value.orders.first { it.reference == "BNC-AAAAAA" }
        r.coordinator.change(ready, Completed)
        // 2 matcha: 2 cups and 10 of the matcha ingredient
        assertEquals(98.0, r.inventory.stock["cups"]!!, 0.0)
        assertEquals(40.0, r.inventory.stock["matcha"]!!, 0.0)

        // the answer was lost and the same completion is sent again: the database says replayed,
        // and the tablet must not take the same ingredients off a second time
        r.orders.answers += OnlineOrderChangeResult.Changed("BNC-AAAAAA", Completed, replayed = true)
        r.coordinator.change(ready.copy(status = Ready), Completed)
        assertEquals(98.0, r.inventory.stock["cups"]!!, 0.0)
        assertEquals(40.0, r.inventory.stock["matcha"]!!, 0.0)

        val fresh = r.coordinator.state.value.orders
        r.coordinator.change(fresh.first { it.reference == "BNC-BBBBBB" }, Rejected, "Closed early")
        r.coordinator.change(fresh.first { it.reference == "BNC-CCCCCC" }, Cancelled)
        assertEquals(98.0, r.inventory.stock["cups"]!!, 0.0)
        assertEquals(40.0, r.inventory.stock["matcha"]!!, 0.0)
        assertEquals(Rejected, r.coordinator.state.value.orders.first { it.reference == "BNC-BBBBBB" }.status)
    }

    @Test
    fun `TC-P6-199 a new order chimes once and the new count follows the list`() {
        val r = rig(order())
        val alerts = mutableListOf<Int>()
        val job = CoroutineScope(Dispatchers.Unconfined).launchCollect(r.coordinator.newOrderAlerts) { alerts += it }

        r.coordinator.refresh()
        r.coordinator.refresh() // the same order read again is not a new order
        assertEquals(listOf(1), alerts)
        assertEquals(1, r.coordinator.state.value.newCount)

        r.orders.orders = listOf(order("BNC-DDDDDD"), order())
        r.coordinator.refresh()
        assertEquals(listOf(1, 1), alerts)
        assertEquals(2, r.coordinator.state.value.newCount)

        // a read that fails keeps the list on screen and says why
        r.orders.fetchFails = true
        r.coordinator.refresh()
        assertEquals(2, r.coordinator.state.value.orders.size)
        assertEquals("offline", r.coordinator.state.value.loadError)
        job.cancel()
    }

    // ---- undo (FR-095) ------------------------------------------------------------------

    @Test
    fun `TC-P6-205 Undo shows for five minutes after a step forward, never for new or rejected orders`() {
        val recent = order("BNC-AAAAAA", Accepted).copy(updatedAtMillis = 4_000L)
        val old = order("BNC-BBBBBB", Ready).copy(updatedAtMillis = 5_000L - OnlineOrderRules.UNDO_WINDOW_MILLIS - 1)
        val fresh = order("BNC-CCCCCC", Pending).copy(updatedAtMillis = 4_000L)
        val rejected = order("BNC-DDDDDD", Rejected).copy(updatedAtMillis = 4_000L)
        val r = rig(recent, old, fresh, rejected)
        r.coordinator.refresh()
        assertEquals(setOf("BNC-AAAAAA"), r.coordinator.state.value.undoable)

        r.coordinator.undo(old)
        assertTrue(r.orders.undoCalls.isEmpty())
        assertEquals("This change can't be undone any more.", r.coordinator.state.value.snackbarMessage)
    }

    @Test
    fun `TC-P6-206 undoing a Complete voids the local sale and puts the stock back once`() {
        val done = order("BNC-AAAAAA", Ready).copy(updatedAtMillis = 4_000L)
        val r = rig(done)
        r.coordinator.refresh()
        r.coordinator.change(done, Completed)
        assertEquals(98.0, r.inventory.stock["cups"]!!, 0.0)

        r.orders.orders = r.orders.orders.map { it.copy(updatedAtMillis = 4_500L) }
        r.coordinator.refresh()
        r.orders.undoAnswers += OnlineOrderChangeResult.Changed("BNC-AAAAAA", Ready, replayed = false, voidedSaleId = 2_000_000_000_000_001L)
        r.coordinator.undo(r.orders.orders.single())
        assertEquals(listOf("BNC-AAAAAA" to Completed), r.orders.undoCalls)
        assertEquals(100.0, r.inventory.stock["cups"]!!, 0.0)
        assertEquals(50.0, r.inventory.stock["matcha"]!!, 0.0)
        assertTrue(2_000_000_000_000_001L in r.sales.voided)
        // the undone order offers no second Undo
        assertFalse("BNC-AAAAAA" in r.coordinator.state.value.undoable)

        // completing it again takes the stock off again
        r.coordinator.change(r.orders.orders.single(), Completed)
        assertEquals(98.0, r.inventory.stock["cups"]!!, 0.0)
    }

    @Test
    fun `TC-P6-207 no double restock when a sync already voided the sale, and a refused undo is explained`() {
        val done = order("BNC-AAAAAA", Ready).copy(updatedAtMillis = 4_000L)
        val r = rig(done)
        r.coordinator.refresh()
        r.coordinator.change(done, Completed)
        r.sales.voided += 7L // a live sync voided the copy and restocked already
        r.coordinator.refresh()
        r.orders.undoAnswers += OnlineOrderChangeResult.Changed("BNC-AAAAAA", Ready, replayed = false, voidedSaleId = 7L)
        r.coordinator.undo(r.orders.orders.single())
        assertEquals(98.0, r.inventory.stock["cups"]!!, 0.0)

        val other = order("BNC-BBBBBB", Preparing).copy(updatedAtMillis = 4_000L)
        val r2 = rig(other)
        r2.coordinator.refresh()
        r2.orders.undoAnswers += OnlineOrderChangeResult.Refused("too_late", Preparing)
        r2.coordinator.undo(other)
        assertEquals("A change can only be undone within 5 minutes.", r2.coordinator.state.value.snackbarMessage)
        assertNull(r2.coordinator.state.value.changes["BNC-BBBBBB"])
    }

    private fun <T> CoroutineScope.launchCollect(flow: kotlinx.coroutines.flow.SharedFlow<T>, onEach: (T) -> Unit) =
        this.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { flow.collect { onEach(it) } }
}
