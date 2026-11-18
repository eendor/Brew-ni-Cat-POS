package com.example.cattasticpos.domain.catalog

import com.example.cattasticpos.domain.model.Category

/**
 * The order the category chips are shown in. Cat Treats is what customers buy
 * to feed the café cats, not food for them, so it always comes last (owner's
 * request, 2026-11-18). Every other category keeps its order.
 */
object CategoryOrder {
    fun isCatTreats(category: Category): Boolean =
        category.id == "cat_treats" || category.name.trim().equals("Cat Treats", ignoreCase = true)

    fun catTreatsLast(categories: List<Category>): List<Category> {
        val (treats, others) = categories.partition(::isCatTreats)
        return others + treats
    }
}
