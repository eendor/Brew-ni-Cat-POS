package com.example.cattasticpos.data.repository

import com.example.cattasticpos.data.local.dao.MenuDao
import com.example.cattasticpos.data.local.entity.CategoryEntity
import com.example.cattasticpos.data.local.entity.ItemEntity
import com.example.cattasticpos.domain.model.Category
import com.example.cattasticpos.domain.model.Item
import com.example.cattasticpos.domain.model.Variant
import com.example.cattasticpos.domain.repository.MenuRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class MenuRepositoryImpl(
    private val menuDao: MenuDao
) : MenuRepository {

    override fun getCategories(): Flow<List<Category>> {
        return menuDao.getCategories().map { entities ->
            entities.map { Category(it.id, it.name) }
        }
    }

    override fun getItems(): Flow<List<Item>> {
        return menuDao.getItems().map { entities ->
            entities.map { entity ->
                Item(
                    id = entity.id,
                    categoryId = entity.categoryId,
                    name = entity.name,
                    flavors = if (entity.flavors.isEmpty()) emptyList() else entity.flavors.split("|").filter { it.isNotBlank() },
                    variants = parseVariants(entity.variantsJson),
                    isAvailable = entity.isAvailable,
                    icon = entity.icon?.takeIf { it.isNotBlank() }
                )
            }
        }
    }

    override suspend fun upsertCategory(category: Category) {
        menuDao.insertCategories(listOf(CategoryEntity(id = category.id.trim(), name = category.name.trim())))
    }

    override suspend fun deleteCategory(categoryId: String) {
        menuDao.deleteCategoriesByIds(listOf(categoryId))
    }

    override suspend fun upsertItem(item: Item) {
        val cleanFlavors = item.flavors.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val entity = ItemEntity(
            id = item.id.trim(),
            categoryId = item.categoryId.trim(),
            name = item.name.trim(),
            flavors = cleanFlavors.joinToString("|"),
            variantsJson = serializeVariants(item.variants),
            isAvailable = item.isAvailable,
            icon = item.icon?.trim()?.takeIf { it.isNotEmpty() }
        )
        menuDao.insertItems(listOf(entity))
    }

    override suspend fun deleteItem(itemId: String) {
        menuDao.deleteItemsByIds(listOf(itemId))
    }

    companion object {
        fun parseVariants(jsonStr: String): List<Variant> {
            val list = mutableListOf<Variant>()
            if (jsonStr.isBlank()) return list
            try {
                val jsonArray = JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.optString("id", "variant_${i + 1}").ifBlank { "variant_${i + 1}" }
                    val name = obj.optString("name", "Regular").ifBlank { "Regular" }
                    val basePrice = when {
                        obj.has("basePrice") -> obj.optDouble("basePrice", 0.0)
                        obj.has("price") -> obj.optDouble("price", 0.0)
                        else -> 0.0
                    }
                    val priceByFlavorObj = obj.optJSONObject("priceByFlavor")
                    val priceMap = mutableMapOf<String, Double>()
                    if (priceByFlavorObj != null) {
                        val keys = priceByFlavorObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            priceMap[key] = priceByFlavorObj.getDouble(key)
                        }
                    }
                    val description = if (obj.has("description") && !obj.isNull("description")) {
                        obj.optString("description", "").takeIf { it.isNotBlank() }
                    } else {
                        null
                    }
                    list.add(Variant(id, name, basePrice, priceMap, description))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return list
        }

        fun serializeVariants(variants: List<Variant>): String {
            val array = JSONArray()
            variants.forEachIndexed { index, variant ->
                val obj = JSONObject().apply {
                    put("id", variant.id.ifBlank { "variant_${index + 1}" })
                    put("name", variant.name.ifBlank { "Regular" })
                    put("basePrice", variant.basePrice)
                    val flavorPricesObj = JSONObject()
                    variant.priceByFlavor.forEach { (flavor, price) ->
                        if (flavor.isNotBlank()) {
                            flavorPricesObj.put(flavor.trim(), price)
                        }
                    }
                    put("priceByFlavor", flavorPricesObj)
                    if (!variant.description.isNullOrBlank()) {
                        put("description", variant.description.trim())
                    }
                }
                array.put(obj)
            }
            return array.toString()
        }
    }
}
