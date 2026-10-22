package com.example.cattasticpos

import com.example.cattasticpos.domain.catalog.ProductAddOnCatalog
import com.example.cattasticpos.domain.model.CartItem
import com.example.cattasticpos.domain.model.CartKey
import com.example.cattasticpos.domain.model.CartLineSelection
import com.example.cattasticpos.domain.model.Item
import com.example.cattasticpos.domain.model.Variant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Take-out Box is a menu item under its own category, so it must not also be sold as a
 * per-item add-on — but orders taken while it *was* an add-on still have to price correctly.
 */
class AddOnCatalogTest {

    /** Every SKU the seeder ships, as (id, categoryId, name). */
    private val catalog = listOf(
        Triple("bite_takoyaki", "cat_bites", "Takoyaki (Pawsome Balls)"),
        Triple("bite_fries", "cat_bites", "Fries (Cat Claws)"),
        Triple("bite_nachos", "cat_bites", "Nachos (Kitty Litter Crisps)"),
        Triple("bite_takeout_box", "cat_takeout", "Take-out Box"),
        Triple("buldak_carbo", "cat_buldak", "Buldak Carbonara"),
        Triple("buldak_cheese", "cat_buldak", "Buldak Cheese"),
        Triple("sedaap_original", "cat_buldak", "Sedaap Original"),
        Triple("sedaap_spicy_chicken", "cat_buldak", "Sedaap Spicy Chicken"),
        Triple("combo_single_paw", "cat_combos", "Single Paw Combo"),
        Triple("combo_couple_cats", "cat_combos", "Couple Cats Combo"),
        Triple("combo_association", "cat_combos", "Association Combo"),
        Triple("drink_cat_feine", "cat_drinks", "Cat-feine"),
        Triple("drink_matcha", "cat_drinks", "Matcha"),
        Triple("drink_oreo", "cat_drinks", "Oreo"),
        Triple("drink_soda", "cat_drinks", "Soda"),
        Triple("drink_bottled_water", "cat_drinks", "Bottled Water")
    )

    @Test
    fun `no item offers Take-out Box as an add-on any more`() {
        val offenders = catalog.filter { (id, cat, name) ->
            ProductAddOnCatalog.addOnsForItem(id, cat, name).any { it.id == "takeout_box" }
        }.map { it.first }
        assertTrue("still offering Take-out Box on: $offenders", offenders.isEmpty())
    }

    @Test
    fun `an unknown item does not fall through to a Take-out Box add-on`() {
        // The catalog's `when` ends in a default branch; a brand-new SKU must not inherit it.
        val addOns = ProductAddOnCatalog.addOnsForItem("some_new_sku", "cat_new", "Mystery Item")
        assertTrue(addOns.none { it.id == "takeout_box" })
    }

    @Test
    fun `the real add-ons are untouched`() {
        val buldak = ProductAddOnCatalog.addOnsForItem("sedaap_original", "cat_buldak", "Sedaap Original")
        assertEquals(
            listOf("Egg (Sunny Side Up)", "Egg (Omelette)", "Egg (Boiled)", "3pcs Seaweed", "Hotdog", "Fries"),
            buldak.map { it.label }
        )
        val soda = ProductAddOnCatalog.addOnsForItem("drink_soda", "cat_drinks", "Soda")
        assertEquals(listOf("Nata de coco", "Rainbow Jelly"), soda.map { it.label })
    }

    @Test
    fun `the standalone Take-out Box item still adds straight to the cart`() {
        val variant = Variant("takeout_regular", "Regular", 10.0)
        val item = Item("bite_takeout_box", "cat_takeout", "Take-out Box", emptyList(), listOf(variant))
        assertTrue(ProductAddOnCatalog.isDirectAddTakeoutItem(item))
        assertFalse(ProductAddOnCatalog.supportsAddOns(item))
    }

    @Test
    fun `Bottled Water adds straight to the cart at 15 pesos with no add-ons`() {
        val entity = com.example.cattasticpos.data.local.MenuBoardCatalog.bottledWaterItem()
        assertEquals("cat_drinks", entity.categoryId)
        assertTrue(entity.variantsJson.contains("\"basePrice\":15.0"))
        val item = Item(entity.id, entity.categoryId, entity.name, emptyList(), listOf(Variant("bottle", "Regular", 15.0)))
        assertTrue(ProductAddOnCatalog.isDirectAddTakeoutItem(item))
        assertFalse(ProductAddOnCatalog.supportsAddOns(item))
    }

    @Test
    fun `an order taken before the change still prices its Take-out Box`() {
        // Historical flavor string from a receipt rung up when the add-on existed.
        val selection = CartLineSelection.parse(" + Take-out Box", "bite_takoyaki")
        assertEquals(listOf("Take-out Box"), selection.addOnLabels)
        assertEquals(10.0, selection.addOnSurcharge("bite_takoyaki"), 0.001)

        val variant = Variant("4pcs", "4pcs", 40.0)
        val item = Item("bite_takoyaki", "cat_bites", "Takoyaki", emptyList(), listOf(variant))
        val line = CartItem(
            key = CartKey.from(item, variant, " + Take-out Box"),
            item = item,
            variant = variant,
            flavor = " + Take-out Box",
            quantity = 2
        )
        assertEquals(50.0, line.unitPrice, 0.001)
        assertEquals(100.0, line.totalPrice, 0.001)
    }

    @Test
    fun `a retired add-on is still recognised but never offered`() {
        val offered = ProductAddOnCatalog.addOnsForItem("bite_fries", "cat_bites", "Fries")
        val known = ProductAddOnCatalog.knownAddOnsForItem("bite_fries", "cat_bites", "Fries")
        assertTrue(offered.none { it.id == "takeout_box" })
        assertTrue(known.any { it.id == "takeout_box" })
        assertEquals(10.0, ProductAddOnCatalog.surcharge("bite_fries", listOf("takeout_box")), 0.001)
    }
}
