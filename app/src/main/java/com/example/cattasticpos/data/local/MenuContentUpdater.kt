package com.example.cattasticpos.data.local

import com.example.cattasticpos.data.local.dao.InventoryDao
import com.example.cattasticpos.data.local.dao.MenuDao
import com.example.cattasticpos.data.local.dao.RecipeDao
import com.example.cattasticpos.data.local.entity.InventoryEntity
import com.example.cattasticpos.data.local.entity.ItemEntity
import org.json.JSONArray

/**
 * Idempotent menu/inventory patches for installs that already have seeded data.
 */
internal object MenuContentUpdater {

    suspend fun applyPendingUpdates(
        menuDao: MenuDao,
        inventoryDao: InventoryDao,
        recipeDao: RecipeDao
    ) {
        ensureShrimpInfrastructure(inventoryDao, recipeDao)
        ensureAddOnInfrastructure(inventoryDao, recipeDao)
        applyMenuBoard2026Patch(menuDao, recipeDao)
        applyMenuSectionSplitPatch(menuDao, recipeDao)
        ensureCatTreatsInfrastructure(menuDao)
        ensureBuldakInfrastructure(menuDao, inventoryDao, recipeDao)
        ensureTakeoutBoxInfrastructure(menuDao)
    }

    private suspend fun ensureBuldakInfrastructure(
        menuDao: MenuDao,
        inventoryDao: InventoryDao,
        recipeDao: RecipeDao
    ) {
        val buldakCat = com.example.cattasticpos.data.local.entity.CategoryEntity("cat_buldak", "Buldak & Sedaap")
        menuDao.insertCategories(listOf(buldakCat))

        val buldakItems = MenuBoardCatalog.buldakMenuItems()
        menuDao.insertItems(buldakItems)

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

        menuDao.insertItems(MenuBoardCatalog.allMenuItems())
        recipeDao.insertMappings(MenuBoardCatalog.shrimpTakoyakiRecipeMappings())
    }

    private suspend fun applyMenuSectionSplitPatch(menuDao: MenuDao, recipeDao: RecipeDao) {
        val splitApplied = menuDao.getItemById("drink_cat_feine") != null &&
            menuDao.getItemById("drink_coffee") == null &&
            menuDao.getItemById("combo_meals") == null
        if (splitApplied) return

        menuDao.insertItems(MenuBoardCatalog.drinkAndComboMenuItems())
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
        val takeoutCat = com.example.cattasticpos.data.local.entity.CategoryEntity("cat_takeout", "Take-out Box")
        menuDao.insertCategories(listOf(takeoutCat))
        menuDao.insertItems(listOf(MenuBoardCatalog.takeoutBoxItem()))
    }

    private suspend fun ensureCatTreatsInfrastructure(menuDao: MenuDao) {
        val existing = menuDao.getItemById("bite_cat_treats")
        if (existing != null) return
        val treatsCat = com.example.cattasticpos.data.local.entity.CategoryEntity("cat_treats", "Cat Treats")
        menuDao.insertCategories(listOf(treatsCat))
        menuDao.insertItems(listOf(MenuBoardCatalog.catTreatsItem()))
    }
}
