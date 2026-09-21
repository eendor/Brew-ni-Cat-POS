package com.example.cattasticpos.data.local

import com.example.cattasticpos.data.local.dao.AppConfigDao
import com.example.cattasticpos.data.local.dao.InventoryDao
import com.example.cattasticpos.data.local.dao.MenuDao
import com.example.cattasticpos.data.local.dao.RecipeDao
import com.example.cattasticpos.data.local.entity.AppConfigEntity
import com.example.cattasticpos.data.local.entity.CategoryEntity
import com.example.cattasticpos.data.local.entity.InventoryEntity
import com.example.cattasticpos.data.local.entity.ItemEntity
import com.example.cattasticpos.data.local.entity.RecipeMappingEntity

internal object DatabaseSeeder {

    suspend fun seedDefaultsIfMissing(
        menuDao: MenuDao,
        inventoryDao: InventoryDao,
        recipeDao: RecipeDao,
        appConfigDao: AppConfigDao
    ) {
        if (menuDao.getCategoryCount() == 0) {
            seedMenu(menuDao)
        }
        if (inventoryDao.getInventoryCount() == 0) {
            seedInventory(inventoryDao)
        }
        if (recipeDao.getMappingCount() == 0) {
            seedRecipes(recipeDao)
        }
        val existingConfig = appConfigDao.getAppConfigOnce()
        if (existingConfig == null) {
            seedAppConfig(appConfigDao)
        } else if (existingConfig.supabaseUrl.isBlank() || existingConfig.supabaseAnonKey.isBlank()) {
            try {
                appConfigDao.insertConfig(
                    existingConfig.copy(
                        supabaseUrl = "https://hyeotyohpdpmmvquotnd.supabase.co",
                        supabaseAnonKey = "sb_publishable_orak9Nk7HGB_qFHgXMdIzA_11T8NfYQ",
                        deviceId = existingConfig.deviceId.ifBlank { java.util.UUID.randomUUID().toString() }
                    )
                )
            } catch (e: Exception) {
                android.util.Log.e("DatabaseSeeder", "Error patching existing app config with Supabase defaults", e)
            }
        }
    }

    private suspend fun seedAppConfig(appConfigDao: AppConfigDao) {
        try {
            appConfigDao.insertConfig(
                AppConfigEntity(
                    id = 1,
                    targetSales = AppConfigEntity.DEFAULT_TARGET_SALES,
                    startingCashFloat = AppConfigEntity.DEFAULT_STARTING_CASH_FLOAT,
                    pinHash = "otCBSIxSZkk6vcF7SKwqCw==:Seyex1KVzCA7gLC3+1Vi8AHYtjU7A168GCGRihADbp0=",
                    cashiersJson = AppConfigEntity.DEFAULT_CASHIERS_JSON,
                    supabaseUrl = "https://hyeotyohpdpmmvquotnd.supabase.co",
                    supabaseAnonKey = "sb_publishable_orak9Nk7HGB_qFHgXMdIzA_11T8NfYQ",
                    deviceId = java.util.UUID.randomUUID().toString()
                )
            )
        } catch (e: Exception) {
            android.util.Log.e("DatabaseSeeder", "Error seeding app config", e)
        }
    }

    private suspend fun seedMenu(menuDao: MenuDao) {
        try {
            menuDao.insertCategories(
                listOf(
                    CategoryEntity("cat_bites", "Cat-Tastic Bites"),
                    CategoryEntity("cat_treats", "Cat Treats"),
                    CategoryEntity("cat_drinks", "Cat-Tastic Drinks"),
                    CategoryEntity("combos", "Combos & Packages"),
                    CategoryEntity("cat_buldak", "Buldak & Sedaap"),
                    CategoryEntity("cat_takeout", "Take-out Box")
                )
            )
            menuDao.insertItems(defaultMenuItems())
        } catch (e: Exception) {
            android.util.Log.e("DatabaseSeeder", "Error seeding menu", e)
        }
    }

    private suspend fun seedInventory(inventoryDao: InventoryDao) {
        try {
            inventoryDao.insertInventoryItems(
                listOf(
                    InventoryEntity("inv_cups", "Cups", "pcs", 100.0, 20.0),
                    InventoryEntity("inv_takoyaki", "Takoyaki Balls", "pcs", 100.0, 20.0),
                    InventoryEntity("inv_shrimp", "Shrimp Takoyaki", "pcs", 100.0, 20.0),
                    // Gram-based stock, so the placeholder 100.0 used by the pcs/pack items makes
                    // no sense here: one serving deducts 150 g, which drove a brand-new install
                    // straight to negative stock and a permanent low-stock badge on the very
                    // first fries or nachos sale. Seeded at 5 kg with a 1 kg reorder point;
                    // the owner sets real figures on the Inventory screen.
                    InventoryEntity("inv_fries", "Potato Fries", "grams", 5000.0, 1000.0),
                    InventoryEntity("inv_nachos", "Nacho Chips", "grams", 5000.0, 1000.0),
                    InventoryEntity("inv_nata_coco", "Nata de coco", "pcs", 100.0, 20.0),
                    InventoryEntity("inv_rainbow_jelly", "Rainbow Jelly", "pcs", 100.0, 20.0),
                    InventoryEntity("inv_buldak_carbo", "Buldak Carbo Samyang", "pack", 100.0, 20.0),
                    InventoryEntity("inv_buldak_cheese", "Buldak Cheese Samyang", "pack", 100.0, 20.0),
                    InventoryEntity("inv_sedaap_spicy", "Sedaap Spicy Chicken", "pack", 100.0, 20.0),
                    InventoryEntity("inv_sedaap_orig", "Sedaap Original", "pack", 100.0, 20.0)
                )
            )
        } catch (e: Exception) {
            android.util.Log.e("DatabaseSeeder", "Error seeding inventory", e)
        }
    }

    private suspend fun seedRecipes(recipeDao: RecipeDao) {
        try {
            recipeDao.insertMappings(
                listOf(
                    RecipeMappingEntity("r_tako_4", "bite_takoyaki", "4pcs", "inv_takoyaki", 4.0),
                    RecipeMappingEntity("r_tako_8", "bite_takoyaki", "8pcs", "inv_takoyaki", 8.0),
                    RecipeMappingEntity("r_tako_12", "bite_takoyaki", "12pcs", "inv_takoyaki", 12.0),
                    RecipeMappingEntity("r_tako_16", "bite_takoyaki", "16pcs", "inv_takoyaki", 16.0),
                    *MenuBoardCatalog.shrimpTakoyakiRecipeMappings().toTypedArray(),
                    RecipeMappingEntity("r_fries_all", "bite_fries", null, "inv_fries", 150.0),
                    RecipeMappingEntity("r_nachos_all", "bite_nachos", null, "inv_nachos", 150.0),
                    RecipeMappingEntity("r_soda_all", "drink_soda", null, "inv_cups", 1.0),
                    RecipeMappingEntity("r_soda_nata", "drink_soda", "Nata de coco", "inv_nata_coco", 1.0),
                    RecipeMappingEntity("r_soda_rainbow", "drink_soda", "Rainbow Jelly", "inv_rainbow_jelly", 1.0),
                    *MenuBoardCatalog.coffeeCupRecipeMappings().toTypedArray(),
                    *MenuBoardCatalog.buldakRecipeMappings().toTypedArray()
                )
            )
        } catch (e: Exception) {
            android.util.Log.e("DatabaseSeeder", "Error seeding recipe mappings", e)
        }
    }

    private fun defaultMenuItems(): List<ItemEntity> = MenuBoardCatalog.allMenuItems()
}
