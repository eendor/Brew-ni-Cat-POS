package com.example.cattasticpos

import com.example.cattasticpos.domain.model.CartLineSelection
import com.example.cattasticpos.domain.model.RecipeMapping
import com.example.cattasticpos.domain.model.Variant
import com.example.cattasticpos.domain.usecase.RecipeDeductionResolver
import org.junit.Assert.assertEquals
import org.junit.Test

/** Mixed flavors (TC-P4-252 to TC-P4-254): two flavors on one line at the size price. */
class MixedFlavorTest {

    @Test
    fun mixIsStoredAndParsedWithAddOns() {
        val base = CartLineSelection.mixFlavors(listOf("BBQ Scratch", "Cheesy Purr"))
        assertEquals("BBQ Scratch / Cheesy Purr", base)
        val stored = CartLineSelection(base, null, listOf("Extra Cheese")).encode()
        val parsed = CartLineSelection.parse(stored, "item_fries")
        assertEquals(listOf("BBQ Scratch", "Cheesy Purr"), CartLineSelection.mixParts(parsed.baseFlavor))
        assertEquals(listOf("Extra Cheese"), parsed.addOnLabels)
        assertEquals("Mocha", CartLineSelection.mixFlavors(listOf("Mocha")))
    }

    @Test
    fun mixCostsTheSizePrice() {
        val medium = Variant("size_medium", "Medium", 50.0)
        assertEquals(50.0, medium.getPrice("BBQ Scratch / Cheesy Purr"), 0.0)
        // A size that prices flavors differently charges the dearest flavor in the mix.
        val soda = Variant("size_12oz", "12oz", 0.0, mapOf("Yogurt Yarn" to 39.0, "Honey Peach Paws" to 45.0))
        assertEquals(45.0, soda.getPrice("Yogurt Yarn / Honey Peach Paws"), 0.0)
    }

    @Test
    fun mixDeductsEachFlavorsStock() {
        val bbq = RecipeMapping("m1", "item_fries", "BBQ Scratch", "inv_bbq", 1.0)
        val cheese = RecipeMapping("m2", "item_fries", "Cheesy Purr", "inv_cheese", 1.0)
        val sour = RecipeMapping("m3", "item_fries", "Sour Cream", "inv_sour", 1.0)
        val result = RecipeDeductionResolver.resolve(listOf(bbq, cheese, sour), "Medium", "BBQ Scratch / Cheesy Purr")
        assertEquals(setOf("m1", "m2"), result.map { it.id }.toSet())
    }
}
