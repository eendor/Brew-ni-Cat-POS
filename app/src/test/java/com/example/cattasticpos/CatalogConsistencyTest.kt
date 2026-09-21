package com.example.cattasticpos

import com.example.cattasticpos.data.local.MenuBoardCatalog
import com.example.cattasticpos.domain.model.RecipeMapping
import com.example.cattasticpos.domain.usecase.ComboBundleResolver
import com.example.cattasticpos.domain.usecase.RecipeDeductionResolver
import org.json.JSONArray
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mechanical audit of the seeded menu. These catch the class of mistake no amount of reading
 * finds reliably: a combo pointing at a variant id where a name is expected, an expansion for a
 * combo that no longer exists, a recipe row targeting a variant that was renamed.
 */
class CatalogConsistencyTest {

    private data class VariantInfo(val id: String, val name: String)

    private val items = MenuBoardCatalog.allMenuItems()

    private val variantsByItem: Map<String, List<VariantInfo>> = items.associate { item ->
        val arr = JSONArray(item.variantsJson)
        item.id to (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            VariantInfo(o.getString("id"), o.getString("name"))
        }
    }

    private val flavorsByItem: Map<String, List<String>> = items.associate { item ->
        item.id to item.flavors.split("|").filter { it.isNotBlank() }
    }

    @Test
    fun `item ids are unique`() {
        val dupes = items.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertTrue("duplicate item ids: $dupes", dupes.isEmpty())
    }

    @Test
    fun `variant ids and names are unique within each item`() {
        val problems = mutableListOf<String>()
        variantsByItem.forEach { (itemId, variants) ->
            variants.groupBy { it.id }.filterValues { it.size > 1 }.keys
                .forEach { problems += "$itemId has duplicate variant id '$it'" }
            variants.groupBy { it.name }.filterValues { it.size > 1 }.keys
                .forEach { problems += "$itemId has duplicate variant name '$it'" }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `every item belongs to a seeded category`() {
        val seeded = setOf("cat_bites", "cat_treats", "cat_drinks", "combos", "cat_buldak", "cat_takeout")
        val orphans = items.filter { it.categoryId !in seeded }.map { "${it.id} -> ${it.categoryId}" }
        assertTrue("items in a category that is never seeded: $orphans", orphans.isEmpty())
    }

    @Test
    fun `every combo expansion points at a real item and a real variant NAME`() {
        val problems = mutableListOf<String>()
        for (comboItem in items.filter { it.categoryId == "combos" }) {
            for (variant in variantsByItem.getValue(comboItem.id)) {
                val components = ComboBundleResolver.expand(
                    menuItemId = comboItem.id,
                    variantId = variant.id,
                    sizeVariantName = variant.name,
                    flavor = null,
                    orderQuantity = 1
                )
                if (components.isEmpty()) {
                    problems += "${comboItem.id}/${variant.id} (${variant.name}) expands to nothing"
                    continue
                }
                for (c in components) {
                    val target = variantsByItem[c.menuItemId]
                    if (target == null) {
                        problems += "${variant.name}: component '${c.menuItemId}' is not a menu item"
                        continue
                    }
                    val size = c.sizeVariantName ?: continue
                    if (target.none { it.name == size }) {
                        val hint = target.firstOrNull { it.id == size }
                            ?.let { " (that is the variant ID; the name is '${it.name}')" } ?: ""
                        problems += "${variant.name}: '${c.menuItemId}' has no variant named '$size'$hint"
                    }
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `every combo variant actually deducts something`() {
        // A combo whose components resolve to no recipe rows would sell without touching stock.
        val recipes = allSeededRecipes()
        val problems = mutableListOf<String>()
        for (comboItem in items.filter { it.categoryId == "combos" }) {
            for (variant in variantsByItem.getValue(comboItem.id)) {
                val components = ComboBundleResolver.expand(
                    comboItem.id, variant.id, variant.name, null, 1
                )
                val deducted = components.sumOf { c ->
                    RecipeDeductionResolver.resolve(
                        recipes.filter { it.menuItemId == c.menuItemId },
                        c.sizeVariantName,
                        c.flavor
                    ).size
                }
                if (deducted == 0) problems += "${variant.name} (${variant.id}) deducts no inventory"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `every recipe mapping targets something that exists`() {
        val problems = mutableListOf<String>()
        for (m in allSeededRecipes()) {
            val variants = variantsByItem[m.menuItemId]
            if (variants == null) {
                problems += "${m.id}: menu item '${m.menuItemId}' does not exist"
                continue
            }
            val target = m.variantName ?: continue // base mappings always apply
            val flavors = flavorsByItem[m.menuItemId].orEmpty()
            val parts = target.split("|", limit = 2)
            val ok = if (parts.size == 2) {
                variants.any { it.name == parts[0].trim() } && flavors.any { it == parts[1].trim() }
            } else {
                variants.any { it.name == target } || flavors.any { it == target } ||
                    knownAddOnLabels.contains(target)
            }
            if (!ok) problems += "${m.id}: '${m.menuItemId}' has no variant/flavor named '$target'"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `no seeded raw material starts with less stock than a single serving`() {
        // A starting stock below one serving sends the very first sale negative and pins a
        // low-stock badge on the product from day one.
        val stock = mapOf(
            "inv_cups" to 100.0, "inv_takoyaki" to 100.0, "inv_shrimp" to 100.0,
            "inv_fries" to 5000.0, "inv_nachos" to 5000.0, "inv_nata_coco" to 100.0,
            "inv_rainbow_jelly" to 100.0, "inv_buldak_carbo" to 100.0,
            "inv_buldak_cheese" to 100.0, "inv_sedaap_spicy" to 100.0, "inv_sedaap_orig" to 100.0
        )
        val problems = allSeededRecipes().mapNotNull { m ->
            val have = stock[m.inventoryItemId] ?: return@mapNotNull "${m.id}: unknown inventory '${m.inventoryItemId}'"
            if (m.deductionQuantity > have) "${m.inventoryItemId}: starts at $have but one serving takes ${m.deductionQuantity}" else null
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private val knownAddOnLabels = setOf(
        "Nata de coco", "Rainbow Jelly", "Take-out Box",
        "Egg (Sunny Side Up)", "Egg (Omelette)", "Egg (Boiled)",
        "3pcs Seaweed", "Hotdog", "Fries"
    )

    /** Mirrors what DatabaseSeeder + MenuBoardCatalog write on a fresh install. */
    private fun allSeededRecipes(): List<RecipeMapping> {
        fun r(id: String, item: String, target: String?, inv: String, qty: Double) =
            RecipeMapping(id, item, target, inv, qty)
        return listOf(
            r("r_tako_4", "bite_takoyaki", "4pcs", "inv_takoyaki", 4.0),
            r("r_tako_8", "bite_takoyaki", "8pcs", "inv_takoyaki", 8.0),
            r("r_tako_12", "bite_takoyaki", "12pcs", "inv_takoyaki", 12.0),
            r("r_tako_16", "bite_takoyaki", "16pcs", "inv_takoyaki", 16.0),
            r("r_fries_all", "bite_fries", null, "inv_fries", 150.0),
            r("r_nachos_all", "bite_nachos", null, "inv_nachos", 150.0),
            r("r_soda_all", "drink_soda", null, "inv_cups", 1.0),
            r("r_soda_nata", "drink_soda", "Nata de coco", "inv_nata_coco", 1.0),
            r("r_soda_rainbow", "drink_soda", "Rainbow Jelly", "inv_rainbow_jelly", 1.0)
        ) + MenuBoardCatalog.shrimpTakoyakiRecipeMappings().map {
            r(it.id, it.menuItemId, it.variantName, it.inventoryItemId, it.deductionQuantity)
        } + MenuBoardCatalog.coffeeCupRecipeMappings().map {
            r(it.id, it.menuItemId, it.variantName, it.inventoryItemId, it.deductionQuantity)
        } + MenuBoardCatalog.buldakRecipeMappings().map {
            r(it.id, it.menuItemId, it.variantName, it.inventoryItemId, it.deductionQuantity)
        }
    }
}
