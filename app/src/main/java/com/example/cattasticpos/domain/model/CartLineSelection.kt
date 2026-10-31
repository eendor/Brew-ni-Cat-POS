package com.example.cattasticpos.domain.model

import com.example.cattasticpos.domain.catalog.ProductAddOnCatalog

/**
 * Parses cart/order flavor strings that may include coffee options (" — ") and add-ons (" + ").
 */
data class CartLineSelection(
    val baseFlavor: String?,
    val coffeeOption: String?,
    val addOnLabels: List<String>
) {
    fun encode(): String? = buildString {
        if (!baseFlavor.isNullOrBlank()) append(baseFlavor)
        if (!coffeeOption.isNullOrBlank()) {
            if (isNotEmpty()) append(COFFEE_SEPARATOR)
            append(coffeeOption)
        }
        if (addOnLabels.isNotEmpty()) {
            // The separator is the only thing marking these as extras rather than the flavor
            // itself. Omitting it when there is nothing in front — Buldak Carbo, Buldak Cheese
            // and Sedaap Original all ship with an empty flavor list — stored a bare
            // "Egg (Sunny Side Up)", which parsed straight back as the base flavor and dropped
            // the surcharge, so the sheet quoted 134 and the cart charged 119.
            append(if (isEmpty()) ADD_ON_PREFIX else ADD_ON_SEPARATOR)
            append(addOnLabels.joinToString(LABEL_SEPARATOR))
        }
    }.takeIf { it.isNotBlank() }

    fun addOnSurcharge(itemId: String): Double {
        // Known, not offered: an order placed before an option was retired must still price at
        // what the customer paid when it is reprinted or reopened in the receipt editor.
        val options = ProductAddOnCatalog.knownAddOnsForItem(itemId).associateBy { it.label }
        return addOnLabels.sumOf { label -> options[label]?.price ?: 0.0 }
    }

    companion object {
        /** Joins the flavors of a mixed order, e.g. "BBQ Scratch / Cheesy Purr". */
        const val MIX_SEPARATOR = " / "

        /** Any product with flavors can mix up to this many, at the normal size price. */
        const val MAX_MIXED_FLAVORS = 2

        /** The flavors in a base flavor string: one entry, or each part of a mix. */
        fun mixParts(baseFlavor: String?): List<String> =
            baseFlavor.orEmpty().split(MIX_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }

        /** Builds the stored base flavor for one or more picked flavors. */
        fun mixFlavors(flavors: List<String>): String? = when (flavors.size) {
            0 -> null
            1 -> flavors.first()
            else -> flavors.joinToString(MIX_SEPARATOR) { it.substringAfter(": ").trim() }
        }

        private const val COFFEE_SEPARATOR = " — "
        private const val ADD_ON_SEPARATOR = " + "
        private const val ADD_ON_PREFIX = "+ "
        private const val LABEL_SEPARATOR = ", "

        /**
         * Splits a stored flavor string into everything before the add-ons and the add-on labels.
         * Item-agnostic, so [RecipeDeductionResolver][com.example.cattasticpos.domain.usecase.RecipeDeductionResolver]
         * can pull extras out of the same string without knowing which SKU it belongs to — the two
         * used to carry their own copy of this split and could drift apart.
         */
        fun splitAddOns(flavor: String?): Pair<String, List<String>> {
            val trimmed = flavor?.trim().orEmpty()
            if (trimmed.isEmpty()) return "" to emptyList()
            val (before, addOnPart) = if (trimmed.startsWith(ADD_ON_PREFIX)) {
                "" to trimmed.removePrefix(ADD_ON_PREFIX)
            } else {
                trimmed.substringBefore(ADD_ON_SEPARATOR) to
                    trimmed.substringAfter(ADD_ON_SEPARATOR, missingDelimiterValue = "")
            }
            return before.trim() to splitLabels(addOnPart)
        }

        private fun splitLabels(text: String): List<String> =
            text.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        fun parse(flavor: String?, itemId: String): CartLineSelection {
            if (flavor.isNullOrBlank()) {
                return CartLineSelection(null, null, emptyList())
            }
            val (beforeAddOns, addOnLabels) = splitAddOns(flavor)
            if (addOnLabels.isEmpty()) {
                // Builds 10122-10125 wrote the separator-less form described in [encode]. Recover
                // those rows instead of pricing them as a flavor that never existed; the guard in
                // AddOnEncodingTest keeps a real flavor from ever matching here.
                legacyAddOnsOnly(beforeAddOns, itemId)?.let {
                    return CartLineSelection(null, null, it)
                }
            }
            val coffeeOption = beforeAddOns.substringAfter(COFFEE_SEPARATOR, missingDelimiterValue = "").trim()
                .takeIf { beforeAddOns.contains(COFFEE_SEPARATOR) }
            val baseFlavor = beforeAddOns.substringBefore(COFFEE_SEPARATOR).trim().takeIf { it.isNotBlank() }
            return CartLineSelection(baseFlavor, coffeeOption, addOnLabels)
        }

        /**
         * The whole string, with no separator of any kind, made up purely of this item's own
         * add-on labels — i.e. an extra recorded by a build that forgot the separator.
         */
        private fun legacyAddOnsOnly(text: String, itemId: String): List<String>? {
            if (text.isEmpty() || text.contains(COFFEE_SEPARATOR)) return null
            val known = ProductAddOnCatalog.knownAddOnsForItem(itemId).map { it.label }.toSet()
            if (known.isEmpty()) return null
            val labels = splitLabels(text)
            return labels.takeIf { it.isNotEmpty() && it.all { label -> label in known } }
        }

        fun encode(
            baseFlavor: String?,
            coffeeOption: String?,
            addOnIds: List<String>,
            itemId: String
        ): String? = CartLineSelection(
            baseFlavor = baseFlavor,
            coffeeOption = coffeeOption,
            addOnLabels = ProductAddOnCatalog.labelsForIds(itemId, addOnIds)
        ).encode()
    }
}
