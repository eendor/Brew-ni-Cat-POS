package com.example.cattasticpos.data.local

import com.example.cattasticpos.data.local.dao.AppConfigDao
import com.example.cattasticpos.data.local.dao.InventoryDao
import com.example.cattasticpos.data.local.dao.MenuDao
import com.example.cattasticpos.data.local.dao.RecipeDao
import com.example.cattasticpos.data.local.entity.CategoryEntity
import com.example.cattasticpos.data.local.entity.InventoryEntity
import com.example.cattasticpos.data.local.entity.ItemEntity
import org.json.JSONArray
import java.util.Collections
import java.util.WeakHashMap

/**
 * One-time menu/inventory patches for installs upgrading from older catalog versions.
 *
 * Once applied (`catalogVersion >= CURRENT_CATALOG_VERSION`), this updater never runs again on
 * that database so owner edits (name, price, icon, availability) and owner deletions are never
 * overwritten or restored on restart.
 */
internal object MenuContentUpdater {

    const val CURRENT_CATALOG_VERSION = 1

    private val patchedMenuDaos = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<MenuDao, Boolean>())
    )

    suspend fun applyPendingUpdates(
        menuDao: MenuDao,
        inventoryDao: InventoryDao,
        recipeDao: RecipeDao,
        appConfigDao: AppConfigDao? = null
    ) {
        val currentConfig = appConfigDao?.getAppConfigOnce()
        if (currentConfig != null && currentConfig.catalogVersion >= CURRENT_CATALOG_VERSION) {
            patchedMenuDaos.add(menuDao)
            return
        }
        if (currentConfig == null && patchedMenuDaos.contains(menuDao)) {
            return
        }

        ensureShrimpInfrastructure(inventoryDao, recipeDao)
        ensureAddOnInfrastructure(inventoryDao, recipeDao)
        applyMenuBoard2026Patch(menuDao, recipeDao)
        applyMenuSectionSplitPatch(menuDao, recipeDao)
        ensureCatTreatsInfrastructure(menuDao)
        ensureBuldakInfrastructure(menuDao, inventoryDao, recipeDao)
        ensureTakeoutBoxInfrastructure(menuDao)
        ensureBottledWaterItem(menuDao)

        patchedMenuDaos.add(menuDao)
        if (currentConfig != null) {
            appConfigDao?.insertConfig(currentConfig.copy(catalogVersion = CURRENT_CATALOG_VERSION))
        }
    }

    private suspend fun insertMissingCategories(menuDao: MenuDao, categories: List<CategoryEntity>) {
        val missing = categories.filter { menuDao.getCategoryById(it.id) == null }
        if (missing.isNotEmpty()) {
            menuDao.insertCategories(missing)
        }
    }

    private suspend fun insertMissingItems(menuDao: MenuDao, items: List<ItemEntity>) {
        val missing = items.filter { menuDao.getItemById(it.id) == null }
        if (missing.isNotEmpty()) {
            menuDao.insertItems(missing)
        }
    }

    private suspend fun ensureBuldakInfrastructure(
        menuDao: MenuDao,
        inventoryDao: InventoryDao,
        recipeDao: RecipeDao
    ) {
        insertMissingCategories(menuDao, listOf(CategoryEntity("cat_buldak", "Buldak & Sedaap")))
        insertMissingItems(menuDao, MenuBoardCatalog.buldakMenuItems())

        val newInventory = listOf(
            InventoryEntity("inv_buldak_carbo", "Buldak Carbo Samyang", "pack", 100.0, 20.0),
            InventoryEntity("inv_buldak_cheese", "Buldak Cheese Samyang", "pack", 100.0, 20.0),
            InventoryEntity("inv_sedaap_spicy", "Sedaap Spicy Chicken", "pack", 100.0, 20.0),
            InventoryEntity("inv_sedaap_orig", "Sedaap Original", "pack", 100.0, 20.0)
        )
        val missingInv = newInventory.filter { inventoryDao.getInventoryItemById(it.id) == null }
        if (missingInv.isNotEmpty()) {
            inventoryDao.insertInventoryItems(missingInv)
        }

        recipeDao.insertMappings(MenuBoardCatalog.buldakRecipeMappings())
    }

    private suspend fun ensureAddOnInfrastructure(
        inventoryDao: InventoryDao,
        recipeDao: RecipeDao
    ) {
        if (inventoryDao.getInventoryCount() == 0) return
        val addOnItems = listOf(
            InventoryEntity("inv_nata_coco", "Nata de coco", "pcs", 100.0, 20.0),
            InventoryEntity("inv_rainbow_jelly", "Rainbow Jelly", "pcs", 100.0, 20.0)
        )
        val missing = addOnItems.filter { inventoryDao.getInventoryItemById(it.id) == null }
        if (missing.isNotEmpty()) {
            inventoryDao.insertInventoryItems(missing)
        }
        recipeDao.insertMappings(
            listOf(
                com.example.cattasticpos.data.local.entity.RecipeMappingEntity(
                    "r_soda_nata", "drink_soda", "Nata de coco", "inv_nata_coco", 1.0
                ),
                com.example.cattasticpos.data.local.entity.RecipeMappingEntity(
                    "r_soda_rainbow", "drink_soda", "Rainbow Jelly", "inv_rainbow_jelly", 1.0
                )
            )
        )
    }

    private suspend fun ensureShrimpInfrastructure(
        inventoryDao: InventoryDao,
        recipeDao: RecipeDao
    ) {
        if (inventoryDao.getInventoryCount() == 0) return
        val hasShrimp = inventoryDao.getInventoryItemById("inv_shrimp") != null
        if (!hasShrimp) {
            inventoryDao.insertInventoryItems(
                listOf(
                    InventoryEntity(
                        id = "inv_shrimp",
                        itemName = "Shrimp Takoyaki",
                        unit = "pcs",
                        currentStock = 100.0,
                        reorderThreshold = 20.0
                    )
                )
            )
        }
        recipeDao.insertMappings(MenuBoardCatalog.shrimpTakoyakiRecipeMappings())
    }

    private suspend fun applyMenuBoard2026Patch(menuDao: MenuDao, recipeDao: RecipeDao) {
        val combo = menuDao.getItemById("combo_meals") ?: return
        if (isMenuBoard2026Applied(combo)) return

        insertMissingItems(menuDao, MenuBoardCatalog.allMenuItems())
        recipeDao.insertMappings(MenuBoardCatalog.shrimpTakoyakiRecipeMappings())
    }

    private suspend fun applyMenuSectionSplitPatch(menuDao: MenuDao, recipeDao: RecipeDao) {
        val legacyPresent = menuDao.getItemById("drink_coffee") != null ||
            menuDao.getItemById("combo_meals") != null
        if (!legacyPresent) return

        insertMissingItems(menuDao, MenuBoardCatalog.drinkAndComboMenuItems())
        menuDao.deleteItemsByIds(listOf("drink_coffee", "combo_meals"))
        recipeDao.deleteMappingsForMenuItem("drink_coffee")
        recipeDao.insertMappings(MenuBoardCatalog.coffeeCupRecipeMappings())
    }

    private fun isMenuBoard2026Applied(combo: ItemEntity): Boolean {
        return try {
            val variants = JSONArray(combo.variantsJson)
            for (i in 0 until variants.length()) {
                val variant = variants.getJSONObject(i)
                if (variant.getString("id") == "combo_1") {
                    return variant.getString("name") == "Classy Cat Combo" &&
                        variant.getDouble("basePrice") == 105.0
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun ensureTakeoutBoxInfrastructure(menuDao: MenuDao) {
        insertMissingCategories(menuDao, listOf(CategoryEntity("cat_takeout", "Take-out Box")))
        insertMissingItems(menuDao, listOf(MenuBoardCatalog.takeoutBoxItem()))
    }

    private suspend fun ensureBottledWaterItem(menuDao: MenuDao) {
        insertMissingItems(menuDao, listOf(MenuBoardCatalog.bottledWaterItem()))
    }

    private suspend fun ensureCatTreatsInfrastructure(menuDao: MenuDao) {
        insertMissingCategories(menuDao, listOf(CategoryEntity("cat_treats", "Cat Treats")))
        insertMissingItems(menuDao, listOf(MenuBoardCatalog.catTreatsItem()))
    }
}
