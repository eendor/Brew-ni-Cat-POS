package com.example.cattasticpos

import com.example.cattasticpos.data.local.DatabaseSeeder
import com.example.cattasticpos.data.local.MenuContentUpdater
import com.example.cattasticpos.data.local.dao.AppConfigDao
import com.example.cattasticpos.data.local.dao.InventoryDao
import com.example.cattasticpos.data.local.dao.MenuDao
import com.example.cattasticpos.data.local.dao.RecipeDao
import com.example.cattasticpos.data.local.entity.AppConfigEntity
import com.example.cattasticpos.data.local.entity.CategoryEntity
import com.example.cattasticpos.data.local.entity.InventoryEntity
import com.example.cattasticpos.data.local.entity.ItemEntity
import com.example.cattasticpos.data.local.entity.RecipeMappingEntity
import com.example.cattasticpos.data.repository.MenuRepositoryImpl
import com.example.cattasticpos.domain.model.Category
import com.example.cattasticpos.domain.model.Item
import com.example.cattasticpos.domain.model.Variant
import com.example.cattasticpos.ui.icons.FluentIcons
import com.example.cattasticpos.ui.menu.MenuEditorViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuCatalogEditorTest {

    @Test
    fun `productIconOptions exposes 30 unique DB-valid icon keys and resolves them`() {
        val options = FluentIcons.productIconOptions
        assertEquals(30, options.size)

        val keyPattern = Regex("^[a-z0-9_]{1,40}$")
        val seenKeys = mutableSetOf<String>()
        val seenLabels = mutableSetOf<String>()

        for (opt in options) {
            assertTrue("Invalid icon key '${opt.key}'", keyPattern.matches(opt.key))
            assertTrue("Duplicate icon key '${opt.key}'", seenKeys.add(opt.key))
            assertTrue("Duplicate icon label '${opt.label}'", seenLabels.add(opt.label))
            assertSame(opt.icon, FluentIcons.iconByKey(opt.key))
            assertSame(opt.icon, FluentIcons.menuItemIcon("custom_item_1", opt.key))
        }

        // Null, blank, or unknown icon keys gracefully fall back to the itemId-based icon
        val expectedMatchaIcon = FluentIcons.iconByKey("leaf")
        assertSame(expectedMatchaIcon, FluentIcons.menuItemIcon("drink_matcha", null))
        assertSame(expectedMatchaIcon, FluentIcons.menuItemIcon("drink_matcha", ""))
        assertSame(expectedMatchaIcon, FluentIcons.menuItemIcon("drink_matcha", "not_a_real_key"))
    }

    @Test
    fun `MenuRepositoryImpl round-trips categories and products with icon, availability, flavors, and variants`() = runBlocking {
        val menuDao = FakeMenuDao()
        val repository = MenuRepositoryImpl(menuDao)

        repository.upsertCategory(Category(id = "cat_seasonal", name = "Seasonal Specials"))
        val categories = repository.getCategories().first()
        assertEquals(listOf(Category(id = "cat_seasonal", name = "Seasonal Specials")), categories)

        val customItem = Item(
            id = "item_strawberry_cloud",
            categoryId = "cat_seasonal",
            name = "Strawberry Cloud Latte",
            flavors = listOf("Sweetened", "Oat Milk"),
            variants = listOf(
                Variant(
                    id = "size_16oz",
                    name = "16oz",
                    basePrice = 89.0,
                    priceByFlavor = mapOf("Oat Milk" to 99.0),
                    description = "Iced 16oz cup"
                ),
                Variant(
                    id = "size_22oz",
                    name = "22oz",
                    basePrice = 109.0,
                    priceByFlavor = mapOf("Oat Milk" to 119.0)
                )
            ),
            isAvailable = false,
            icon = "cherry"
        )
        repository.upsertItem(customItem)

        val storedItems = repository.getItems().first()
        assertEquals(1, storedItems.size)
        val loaded = storedItems.single()
        assertEquals("item_strawberry_cloud", loaded.id)
        assertEquals("cat_seasonal", loaded.categoryId)
        assertEquals("Strawberry Cloud Latte", loaded.name)
        assertEquals(listOf("Sweetened", "Oat Milk"), loaded.flavors)
        assertFalse(loaded.isAvailable)
        assertEquals("cherry", loaded.icon)
        assertEquals(2, loaded.variants.size)
        assertEquals(89.0, loaded.variants[0].getPrice("Sweetened"), 0.001)
        assertEquals(99.0, loaded.variants[0].getPrice("Oat Milk"), 0.001)
        assertEquals("Iced 16oz cup", loaded.variants[0].description)
        assertEquals(119.0, loaded.variants[1].getPrice("Oat Milk"), 0.001)

        // Delete item and category
        repository.deleteItem("item_strawberry_cloud")
        assertTrue(repository.getItems().first().isEmpty())

        repository.deleteCategory("cat_seasonal")
        assertTrue(repository.getCategories().first().isEmpty())
    }

    @Test
    fun `safe seeding never overwrites owner-edited items or restores owner-deleted items on subsequent launches`() = runBlocking {
        val menuDao = FakeMenuDao()
        val inventoryDao = FakeInventoryDao()
        val recipeDao = FakeRecipeDao()
        val appConfigDao = FakeAppConfigDao()
        val repository = MenuRepositoryImpl(menuDao)

        // 1. Initial app launch: seed defaults & run MenuContentUpdater
        DatabaseSeeder.seedDefaultsIfMissing(menuDao, inventoryDao, recipeDao, appConfigDao)
        MenuContentUpdater.applyPendingUpdates(menuDao, inventoryDao, recipeDao, appConfigDao)

        val initialConfig = appConfigDao.getAppConfigOnce()
        assertNotNull(initialConfig)
        assertEquals(MenuContentUpdater.CURRENT_CATALOG_VERSION, initialConfig!!.catalogVersion)

        // 2. Owner edits an existing seeded item (buldak_carbo) and a takeout item (bite_takeout_box)
        val buldak = repository.getItems().first().first { it.id == "buldak_carbo" }
        val editedBuldak = buldak.copy(
            name = "Carbonara Buldak Deluxe",
            variants = listOf(Variant(id = "size_1pc", name = "1-pc", basePrice = 155.0)),
            isAvailable = false,
            icon = "flame"
        )
        repository.upsertItem(editedBuldak)

        // 3. Owner deletes seeded items that MenuContentUpdater previously resurrected
        repository.deleteItem("drink_bottled_water")
        repository.deleteItem("bite_cat_treats")
        repository.deleteItem("bite_takeout_box")

        // 4. Simulate subsequent app launches / database open callbacks
        DatabaseSeeder.seedDefaultsIfMissing(menuDao, inventoryDao, recipeDao, appConfigDao)
        MenuContentUpdater.applyPendingUpdates(menuDao, inventoryDao, recipeDao, appConfigDao)

        val itemsAfterRelaunch = repository.getItems().first().associateBy { it.id }

        // Edited item keeps the owner's name, price, availability, and custom icon
        val reloadedBuldak = itemsAfterRelaunch["buldak_carbo"]
        assertNotNull(reloadedBuldak)
        assertEquals("Carbonara Buldak Deluxe", reloadedBuldak!!.name)
        assertEquals(155.0, reloadedBuldak.startingPrice, 0.001)
        assertFalse(reloadedBuldak.isAvailable)
        assertEquals("flame", reloadedBuldak.icon)

        // Deleted items stay deleted
        assertNull(itemsAfterRelaunch["drink_bottled_water"])
        assertNull(itemsAfterRelaunch["bite_cat_treats"])
        assertNull(itemsAfterRelaunch["bite_takeout_box"])
    }

    @Test
    fun `generateUniqueSlugId creates clean non-colliding IDs`() {
        val existing = setOf("cat_drinks", "item_matcha_latte", "item_matcha_latte_2")
        assertEquals("cat_seasonal_menu", MenuEditorViewModel.generateUniqueSlugId("cat", "Seasonal Menu!", existing))
        assertEquals("item_matcha_latte_3", MenuEditorViewModel.generateUniqueSlugId("item", "Matcha Latte", existing))
    }

    private class FakeMenuDao : MenuDao {
        private val categoriesState = MutableStateFlow<List<CategoryEntity>>(emptyList())
        private val itemsState = MutableStateFlow<List<ItemEntity>>(emptyList())

        override fun getCategories(): Flow<List<CategoryEntity>> = categoriesState
        override fun getItems(): Flow<List<ItemEntity>> = itemsState

        override suspend fun getCategoryCount(): Int = categoriesState.value.size
        override suspend fun getCategoryById(categoryId: String): CategoryEntity? =
            categoriesState.value.firstOrNull { it.id == categoryId }
        override suspend fun getItemById(itemId: String): ItemEntity? =
            itemsState.value.firstOrNull { it.id == itemId }

        override suspend fun insertCategories(categories: List<CategoryEntity>) {
            val map = categoriesState.value.associateBy { it.id }.toMutableMap()
            categories.forEach { map[it.id] = it }
            categoriesState.value = map.values.toList()
        }

        override suspend fun insertItems(items: List<ItemEntity>) {
            val map = itemsState.value.associateBy { it.id }.toMutableMap()
            items.forEach { map[it.id] = it }
            itemsState.value = map.values.toList()
        }

        override suspend fun deleteCategoriesByIds(ids: List<String>) {
            val set = ids.toSet()
            categoriesState.value = categoriesState.value.filterNot { it.id in set }
        }

        override suspend fun deleteItemsByIds(ids: List<String>) {
            val set = ids.toSet()
            itemsState.value = itemsState.value.filterNot { it.id in set }
        }
    }

    private class FakeInventoryDao : InventoryDao {
        private val state = MutableStateFlow<List<InventoryEntity>>(emptyList())
        override fun getAllInventory(): Flow<List<InventoryEntity>> = state
        override suspend fun getInventoryCount(): Int = state.value.size
        override suspend fun getInventoryItemById(itemId: String): InventoryEntity? =
            state.value.firstOrNull { it.id == itemId }
        override suspend fun insertInventoryItems(items: List<InventoryEntity>) {
            val map = state.value.associateBy { it.id }.toMutableMap()
            items.forEach { map[it.id] = it }
            state.value = map.values.toList()
        }
        override suspend fun updateInventoryItem(item: InventoryEntity) {
            insertInventoryItems(listOf(item))
        }
        override suspend fun decrementStock(inventoryId: String, amount: Double) {}
        override suspend fun restockItem(itemId: String, addedAmount: Double) {}
        override suspend fun deleteInventoryItem(itemId: String) {
            state.value = state.value.filterNot { it.id == itemId }
        }
        override suspend fun deleteInventoryItemsByIds(ids: List<String>) {
            val set = ids.toSet()
            state.value = state.value.filterNot { it.id in set }
        }
    }

    private class FakeRecipeDao : RecipeDao {
        private val state = MutableStateFlow<List<RecipeMappingEntity>>(emptyList())
        override fun getAllMappings(): Flow<List<RecipeMappingEntity>> = state
        override suspend fun getAllMappingsOnce(): List<RecipeMappingEntity> = state.value
        override suspend fun getMappingCount(): Int = state.value.size
        override fun getMappingsForMenu(menuItemId: String): Flow<List<RecipeMappingEntity>> =
            state.map { list -> list.filter { it.menuItemId == menuItemId } }
        override suspend fun getMappingsForMenuOnce(menuItemId: String): List<RecipeMappingEntity> =
            state.value.filter { it.menuItemId == menuItemId }
        override suspend fun getMappingsForCheckout(menuItemId: String, variantName: String?): List<RecipeMappingEntity> =
            state.value.filter { it.menuItemId == menuItemId && (it.variantName == variantName || it.variantName == null) }
        override suspend fun insertMapping(mapping: RecipeMappingEntity) {
            insertMappings(listOf(mapping))
        }
        override suspend fun insertMappings(mappings: List<RecipeMappingEntity>) {
            val map = state.value.associateBy { it.id }.toMutableMap()
            mappings.forEach { map[it.id] = it }
            state.value = map.values.toList()
        }
        override suspend fun deleteMapping(mapping: RecipeMappingEntity) {
            state.value = state.value.filterNot { it.id == mapping.id }
        }
        override suspend fun deleteMappingsForInventory(inventoryItemId: String) {
            state.value = state.value.filterNot { it.inventoryItemId == inventoryItemId }
        }
        override suspend fun deleteMappingsForMenuItem(menuItemId: String) {
            state.value = state.value.filterNot { it.menuItemId == menuItemId }
        }
        override suspend fun deleteMappingsByIds(ids: List<String>) {
            val set = ids.toSet()
            state.value = state.value.filterNot { it.id in set }
        }
    }

    private class FakeAppConfigDao : AppConfigDao {
        private val state = MutableStateFlow<AppConfigEntity?>(null)
        override fun getAppConfig(): Flow<AppConfigEntity?> = state
        override suspend fun getAppConfigOnce(): AppConfigEntity? = state.value
        override suspend fun insertConfig(config: AppConfigEntity) {
            state.value = config
        }
    }
}
