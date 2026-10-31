package com.example.cattasticpos.domain.usecase

import com.example.cattasticpos.domain.model.CartLineSelection
import com.example.cattasticpos.domain.model.RecipeMapping

/**
 * Resolves which recipe BOM rows apply at checkout for a size + optional flavor selection.
 * Base mappings (variantName = null) always apply; size and flavor mappings stack additively.
 * Composite targets like "4pcs|Shrimp" match size + flavor together and replace size-only rows.
 */
object RecipeDeductionResolver {

    /** Mappings visible in the recipe editor for the currently selected target chip. */
    fun forRecipeEditorTarget(
        mappings: List<RecipeMapping>,
        selectedVariantName: String?
    ): List<RecipeMapping> =
        if (selectedVariantName == null) {
            mappings.filter { it.variantName == null }
        } else {
            mappings.filter { mapping ->
                mapping.variantName == null || mapping.variantName == selectedVariantName
            }
        }

    fun resolve(
        mappings: List<RecipeMapping>,
        sizeVariantName: String?,
        flavor: String?
    ): List<RecipeMapping> {
        // Shared with CartLineSelection so pricing and stock always read a flavor string the same
        // way; each used to split it by hand and an extra with no flavor in front of it stopped
        // deducting here at the same time as it stopped being charged for.
        val (beforeAddOns, addOnLabels) = CartLineSelection.splitAddOns(flavor)

        val flavorKey = beforeAddOns
            .substringBefore(" —")
            .trim()
            .takeIf { it.isNotEmpty() }

        val compositeMatches = mappings.filter { mapping ->
            val target = mapping.variantName
            target != null && target.contains('|') && compositeTargetMatches(target, sizeVariantName, flavorKey)
        }
        val baseMatches = if (compositeMatches.isNotEmpty()) {
            compositeMatches
        } else {
            mappings.filter { mapping ->
                when (val target = mapping.variantName) {
                    null -> true
                    else -> !target.contains('|') && targetMatches(target, sizeVariantName, flavorKey)
                }
            }
        }

        if (addOnLabels.isEmpty()) return baseMatches

        val addOnMappings = mappings.filter { mapping ->
            val target = mapping.variantName ?: return@filter false
            if (target.contains('|')) return@filter false
            addOnLabels.any { label -> flavorMatches(target, label) }
        }
        return baseMatches + addOnMappings
    }

    private fun compositeTargetMatches(
        mappingTarget: String,
        sizeVariantName: String?,
        flavor: String?
    ): Boolean {
        val parts = mappingTarget.split("|", limit = 2)
        if (parts.size != 2) return false
        return sizeVariantName == parts[0].trim() && flavorMatches(parts[1].trim(), flavor)
    }

    private fun targetMatches(
        mappingTarget: String,
        sizeVariantName: String?,
        flavor: String?
    ): Boolean {
        if (sizeVariantName != null && mappingTarget == sizeVariantName) return true
        return flavorMatches(mappingTarget, flavor)
    }

    private fun flavorMatches(mappingTarget: String, flavor: String?): Boolean {
        if (flavor == null) return false
        if (flavor.contains(CartLineSelection.MIX_SEPARATOR)) {
            return CartLineSelection.mixParts(flavor).any { flavorMatches(mappingTarget, it) }
        }
        if (mappingTarget == flavor) return true
        val cleanMapping = mappingTarget.substringAfter(": ").trim().ifEmpty { mappingTarget }
        val cleanFlavor = flavor.substringAfter(": ").trim().ifEmpty { flavor }
        return cleanMapping == cleanFlavor
    }
}
