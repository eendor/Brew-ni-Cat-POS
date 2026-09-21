package com.example.cattasticpos.domain.catalog

import com.example.cattasticpos.domain.model.Item

data class AddOnOption(
    val id: String,
    val label: String,
    val price: Double
)

object ProductAddOnCatalog {
    private const val ADD_ON_PRICE = 10.0

    /**
     * Retired. Take-out Box used to be offered as a +PHP 10 extra on every food item, but it is
     * now a menu item of its own under the "Take-out Box" category, so the add-on duplicated a
     * SKU the cashier can ring up directly. It stays defined — and stays visible to
     * [knownAddOnsForItem] — purely so orders taken before the change still price and reprint
     * correctly; it is deliberately absent from every offered list below.
     */
    private val retiredAddOns = listOf(
        AddOnOption("takeout_box", "Take-out Box", 10.0)
    )

    private val sodaAddOns = listOf(
        AddOnOption("nata", "Nata de coco", ADD_ON_PRICE),
        AddOnOption("rainbow", "Rainbow Jelly", ADD_ON_PRICE)
    )

    private val takoyakiAddOns = emptyList<AddOnOption>()

    private val biteAddOns = emptyList<AddOnOption>()

    private val comboAddOns = emptyList<AddOnOption>()

    private val buldakAddOns = listOf(
        AddOnOption("egg_sunny", "Egg (Sunny Side Up)", 15.0),
        AddOnOption("egg_omelette", "Egg (Omelette)", 15.0),
        AddOnOption("egg_boiled", "Egg (Boiled)", 15.0),
        AddOnOption("seaweed", "3pcs Seaweed", 25.0),
        AddOnOption("hotdog", "Hotdog", 20.0),
        AddOnOption("fries", "Fries", 30.0)
    )

    private val defaultFoodAddOns = emptyList<AddOnOption>()

    fun addOnsForItem(itemId: String, categoryId: String? = null, itemName: String? = null): List<AddOnOption> {
        val id = itemId.lowercase()
        val cat = categoryId?.lowercase().orEmpty()
        val name = itemName?.lowercase().orEmpty()

        return when {
            id == "bite_takeout_box" || cat == "cat_takeout" -> emptyList()
            id == "drink_soda" || id.contains("soda") || name.contains("soda") -> sodaAddOns
            id == "bite_takoyaki" || id.contains("takoyaki") || name.contains("takoyaki") -> takoyakiAddOns
            id.startsWith("buldak") || id.startsWith("sedaap") ||
                id.contains("buldak") || id.contains("sedaap") ||
                cat == "cat_buldak" || cat.contains("buldak") || cat.contains("sedaap") ||
                name.contains("buldak") || name.contains("sedaap") || name.contains("samyang") -> buldakAddOns
            cat == "cat_bites" || id.startsWith("bite_") || id.contains("fries") || id.contains("nachos") || name.contains("fries") || name.contains("nachos") -> biteAddOns
            // The combo category really is seeded as "combos" (no cat_ prefix, unlike every other
            // category); "cat_combos" was never a real id and matched nothing.
            cat == "combos" || id.startsWith("combo_") || id.contains("combo") || name.contains("combo") -> comboAddOns
            else -> defaultFoodAddOns
        }
    }

    fun addOnsForItem(item: Item): List<AddOnOption> =
        addOnsForItem(item.id, item.categoryId, item.name)

    /**
     * Everything currently offered for this item, plus any retired option, so an order taken
     * before an option was withdrawn still prices, re-prints and re-opens at the amount the
     * customer actually paid. Use this for money and for parsing stored flavor strings; use
     * [addOnsForItem] for anything the cashier picks from.
     */
    fun knownAddOnsForItem(itemId: String, categoryId: String? = null, itemName: String? = null): List<AddOnOption> =
        addOnsForItem(itemId, categoryId, itemName) + retiredAddOns

    fun knownAddOnsForItem(item: Item): List<AddOnOption> =
        knownAddOnsForItem(item.id, item.categoryId, item.name)

    fun supportsAddOns(itemId: String, categoryId: String? = null, itemName: String? = null): Boolean =
        addOnsForItem(itemId, categoryId, itemName).isNotEmpty()

    fun supportsAddOns(item: Item): Boolean =
        addOnsForItem(item).isNotEmpty()

    fun allowsMultiple(itemId: String, categoryId: String? = null, itemName: String? = null): Boolean {
        return true
    }

    fun allowsMultiple(item: Item): Boolean =
        allowsMultiple(item.id, item.categoryId, item.name)

    fun surcharge(itemId: String, selectedAddOnIds: List<String>, categoryId: String? = null, itemName: String? = null): Double {
        val options = knownAddOnsForItem(itemId, categoryId, itemName).associateBy { it.id }
        return selectedAddOnIds.sumOf { options[it]?.price ?: 0.0 }
    }

    fun surcharge(item: Item, selectedAddOnIds: List<String>): Double =
        surcharge(item.id, selectedAddOnIds, item.categoryId, item.name)

    fun labelsForIds(itemId: String, selectedAddOnIds: List<String>, categoryId: String? = null, itemName: String? = null): List<String> {
        val options = knownAddOnsForItem(itemId, categoryId, itemName).associateBy { it.id }
        return selectedAddOnIds.mapNotNull { options[it]?.label }
    }

    fun labelsForIds(item: Item, selectedAddOnIds: List<String>): List<String> =
        labelsForIds(item.id, selectedAddOnIds, item.categoryId, item.name)

    /**
     * Standalone single-variant SKUs that should add straight to cart with no configuration sheet.
     * Today that includes Take-out Box and Cat Treats.
     */
    fun isDirectAddTakeoutItem(item: Item): Boolean {
        val isDirectAddSku =
            item.id.equals("bite_takeout_box", ignoreCase = true) ||
                item.categoryId.equals("cat_takeout", ignoreCase = true) ||
                item.id.equals("bite_cat_treats", ignoreCase = true) ||
                item.categoryId.equals("cat_treats", ignoreCase = true)
        return isDirectAddSku &&
            item.variants.size == 1 &&
            !supportsAddOns(item)
    }
}
