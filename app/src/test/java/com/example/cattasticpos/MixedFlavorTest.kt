package com.example.cattasticpos

import com.example.cattasticpos.domain.catalog.ProductAddOnCatalog
import com.example.cattasticpos.domain.model.CartLineSelection
import com.example.cattasticpos.domain.model.RecipeMapping
import com.example.cattasticpos.domain.model.Variant
import com.example.cattasticpos.domain.usecase.RecipeDeductionResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mixed flavors (TC-P4-252 to TC-P4-254, TC-P6-221): up to three flavors on one line at the size price. */
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

    @Test
    fun `mixing is offered on fries only`() {
        // TC-P4-257
        assertTrue(ProductAddOnCatalog.allowsFlavorMix("bite_fries", "Fries (Cat Claws)"))
        assertFalse(ProductAddOnCatalog.allowsFlavorMix("bite_takoyaki", "Takoyaki (Pawsome Balls)"))
        assertFalse(ProductAddOnCatalog.allowsFlavorMix("drink_soda", "Soda (Fizzy Felines)"))
        assertFalse(ProductAddOnCatalog.allowsFlavorMix("sedaap_spicy_chicken", "Sedaap Spicy Chicken"))
    }

    @Test
    fun `fries can mix up to three flavors`() {
        // TC-P6-221
        val base = CartLineSelection.mixFlavors(listOf("BBQ Scratch", "Cheesy Purr", "Sour Cream"))
        assertEquals("BBQ Scratch / Cheesy Purr / Sour Cream", base)
        val parsed = CartLineSelection.parse(CartLineSelection(base, null, emptyList()).encode(), "item_fries")
        assertEquals(listOf("BBQ Scratch", "Cheesy Purr", "Sour Cream"), CartLineSelection.mixParts(parsed.baseFlavor))

        // 2 or 3 picks can go to the cart, and a 4th pick replaces the oldest.
        assertFalse(CartLineSelection.canFinishMix(1))
        assertTrue(CartLineSelection.canFinishMix(2))
        assertTrue(CartLineSelection.canFinishMix(3))
        assertFalse(CartLineSelection.canFinishMix(4))
        var picks = emptyList<String>()
        listOf("BBQ Scratch", "Cheesy Purr", "Sour Cream", "Spicy Claw").forEach {
            picks = CartLineSelection.toggleMixPick(picks, it)
        }
        assertEquals(listOf("Cheesy Purr", "Sour Cream", "Spicy Claw"), picks)
        assertEquals(listOf("Cheesy Purr", "Spicy Claw"), CartLineSelection.toggleMixPick(picks, "Sour Cream"))

        // Same price and stock rules as a 2-flavor mix.
        assertEquals(50.0, Variant("size_medium", "Medium", 50.0).getPrice(base), 0.0)
        val soda = Variant("size_12oz", "12oz", 0.0, mapOf("Yogurt Yarn" to 39.0, "Honey Peach Paws" to 45.0, "Lychee" to 42.0))
        assertEquals(45.0, soda.getPrice("Yogurt Yarn / Lychee / Honey Peach Paws"), 0.0)
        val recipes = listOf(
            RecipeMapping("m1", "item_fries", "BBQ Scratch", "inv_bbq", 1.0),
            RecipeMapping("m2", "item_fries", "Cheesy Purr", "inv_cheese", 1.0),
            RecipeMapping("m3", "item_fries", "Sour Cream", "inv_sour", 1.0),
            RecipeMapping("m4", "item_fries", "Spicy Claw", "inv_spicy", 1.0)
        )
        assertEquals(setOf("m1", "m2", "m3"), RecipeDeductionResolver.resolve(recipes, "Medium", base).map { it.id }.toSet())
    }
}
