package com.example.cattasticpos.domain.model

data class Category(
    val id: String,
    val name: String
)

data class Variant(
    val id: String,
    val name: String,
    val basePrice: Double,
    val priceByFlavor: Map<String, Double> = emptyMap(),
    val description: String? = null
) {
    fun getPrice(flavor: String?): Double {
        if (flavor != null) {
            val price = priceByFlavor[flavor]
            if (price != null) return price
            
            // Handle cases where flavor has sub-category prefix (e.g. "Classic: Americano" matching "Americano")
            val cleanFlavor = flavor.substringAfter(": ").trim()
            val cleanPrice = priceByFlavor[cleanFlavor]
            if (cleanPrice != null) return cleanPrice
        }
        if (basePrice == 0.0 && priceByFlavor.isNotEmpty()) {
            throw IllegalStateException("Invalid flavor selected or flavor price missing for zero-base item.")
        }
        return basePrice
    }
}

data class Item(
    val id: String,
    val categoryId: String,
    val name: String,
    val flavors: List<String>,
    val variants: List<Variant>,
    val isAvailable: Boolean = true,
    val icon: String? = null
) {
    // Helper to get starting price
    val startingPrice: Double
        get() {
            if (variants.isEmpty()) return 0.0
            return variants.minOf { variant ->
                if (variant.priceByFlavor.isNotEmpty()) {
                    variant.priceByFlavor.values.minOrNull() ?: variant.basePrice
                } else {
                    variant.basePrice
                }
            }
        }

    /** All recipe-mapping targets: size variant names plus flavor option strings. */
    fun allRecipeTargets(): List<String> = variants.map { it.name } + flavors

    /** Grouped recipe-mapping targets for inventory UI (sizes vs flavors). */
    fun recipeTargetGroups(): List<Pair<String, List<String>>> = buildList {
        if (variants.isNotEmpty()) add("Sizes" to variants.map { it.name })
        if (flavors.isNotEmpty()) add("Flavors" to flavors)
    }

    fun filteredRecipeTargetGroups(query: String): List<Pair<String, List<String>>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return recipeTargetGroups()
        return recipeTargetGroups().mapNotNull { (groupLabel, targets) ->
            val filtered = targets.filter { target ->
                target.contains(trimmed, ignoreCase = true) ||
                    target.substringAfter(": ").trim().contains(trimmed, ignoreCase = true)
            }
            if (filtered.isEmpty()) null else groupLabel to filtered
        }
    }
}
