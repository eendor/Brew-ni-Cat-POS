package com.example.cattasticpos

import com.example.cattasticpos.domain.catalog.CategoryOrder
import com.example.cattasticpos.domain.model.Category
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryOrderTest {
    @Test
    fun catTreatsGoesLastAndTheOthersKeepTheirOrder() {
        val list = listOf(
            Category("cat_bites", "Cat-Tastic Bites"),
            Category("cat_treats", "Cat Treats"),
            Category("cat_drinks", "Cat-Tastic Drinks"),
            Category("combos", "Combos & Packages"),
            Category("cat_buldak", "Buldak & Sedaap"),
        )
        assertEquals(
            listOf("cat_bites", "cat_drinks", "combos", "cat_buldak", "cat_treats"),
            CategoryOrder.catTreatsLast(list).map { it.id },
        )
    }

    @Test
    fun matchesCatTreatsAddedWithAnotherId() {
        val list = listOf(Category("cat_cat-treats", " cat treats "), Category("combos", "Combos"))
        assertEquals(listOf("combos", "cat_cat-treats"), CategoryOrder.catTreatsLast(list).map { it.id })
    }
}
