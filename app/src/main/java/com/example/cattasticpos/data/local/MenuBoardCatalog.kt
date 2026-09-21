package com.example.cattasticpos.data.local

import com.example.cattasticpos.data.local.entity.ItemEntity
import com.example.cattasticpos.data.local.entity.RecipeMappingEntity

/**
 * Canonical menu from Cat-Tastic menu boards (2026). Shared by fresh seed and DB patches.
 */
internal object MenuBoardCatalog {

    fun allMenuItems(): List<ItemEntity> = listOf(
        takoyakiItem(),
        friesItem(),
        nachosItem(),
        catTreatsItem(),
        takeoutBoxItem(),
        catFeineItem(),
        oreoDrinkItem(),
        matchaDrinkItem(),
        sodaItem(),
        comboSinglePawItem(),
        comboCoupleCatsItem(),
        comboAssociationItem(),
        *buldakMenuItems().toTypedArray()
    )

    fun buldakMenuItems(): List<ItemEntity> = listOf(
        buldakCarboItem(),
        buldakCheeseItem(),
        sedaapSpicyChickenItem(),
        sedaapOriginalItem()
    )

    /** Drinks + combos only — used by the section-split DB patch. */
    fun drinkAndComboMenuItems(): List<ItemEntity> = listOf(
        catFeineItem(),
        oreoDrinkItem(),
        matchaDrinkItem(),
        sodaItem(),
        comboSinglePawItem(),
        comboCoupleCatsItem(),
        comboAssociationItem()
    )

    fun shrimpTakoyakiRecipeMappings(): List<RecipeMappingEntity> = listOf(
        RecipeMappingEntity("r_shrimp_4", "bite_takoyaki", "4pcs|Shrimp Whisker", "inv_shrimp", 4.0),
        RecipeMappingEntity("r_shrimp_8", "bite_takoyaki", "8pcs|Shrimp Whisker", "inv_shrimp", 8.0),
        RecipeMappingEntity("r_shrimp_12", "bite_takoyaki", "12pcs|Shrimp Whisker", "inv_shrimp", 12.0),
        RecipeMappingEntity("r_shrimp_16", "bite_takoyaki", "16pcs|Shrimp Whisker", "inv_shrimp", 16.0)
    )

    fun coffeeCupRecipeMappings(): List<RecipeMappingEntity> = listOf(
        RecipeMappingEntity("r_coffee_cat_feine", "drink_cat_feine", null, "inv_cups", 1.0),
        RecipeMappingEntity("r_coffee_oreo", "drink_oreo", null, "inv_cups", 1.0),
        RecipeMappingEntity("r_coffee_matcha", "drink_matcha", null, "inv_cups", 1.0)
    )

    fun takoyakiItem(): ItemEntity = ItemEntity(
        id = "bite_takoyaki",
        categoryId = "cat_bites",
        name = "Takoyaki (Pawsome Balls)",
        flavors = "Veggie Whiskers|Cheesy Calico|Squid Treats|Shrimp Whisker",
        variantsJson = """
            [
              {"id":"4pcs","name":"4pcs","basePrice":0.0,"priceByFlavor":{"Veggie Whiskers":40.0,"Cheesy Calico":50.0,"Squid Treats":55.0,"Shrimp Whisker":60.0}},
              {"id":"8pcs","name":"8pcs","basePrice":0.0,"priceByFlavor":{"Veggie Whiskers":80.0,"Cheesy Calico":100.0,"Squid Treats":110.0,"Shrimp Whisker":120.0}},
              {"id":"12pcs","name":"12pcs","basePrice":0.0,"priceByFlavor":{"Veggie Whiskers":120.0,"Cheesy Calico":150.0,"Squid Treats":160.0,"Shrimp Whisker":180.0}},
              {"id":"16pcs","name":"16pcs","basePrice":0.0,"priceByFlavor":{"Veggie Whiskers":150.0,"Cheesy Calico":200.0,"Squid Treats":210.0,"Shrimp Whisker":240.0}}
            ]
        """.trimIndent()
    )

    private fun friesItem(): ItemEntity = ItemEntity(
        id = "bite_fries",
        categoryId = "cat_bites",
        name = "Fries (Cat Claws)",
        flavors = "BBQ Scratch|Cheesy Purr|Sour Cream Meow|Spicy Claw",
        variantsJson = """
            [
              {"id":"small","name":"Small","basePrice":30.0,"priceByFlavor":{}},
              {"id":"medium","name":"Medium","basePrice":50.0,"priceByFlavor":{}},
              {"id":"large","name":"Large","basePrice":70.0,"priceByFlavor":{}},
              {"id":"jumbo","name":"Jumbo","basePrice":150.0,"priceByFlavor":{}}
            ]
        """.trimIndent()
    )

    private fun nachosItem(): ItemEntity = ItemEntity(
        id = "bite_nachos",
        categoryId = "cat_bites",
        name = "Nachos (Kitty Litter Crisps)",
        flavors = "",
        variantsJson = """
            [
              {"id":"nachos_veggies_meat","name":"Nachos+Veggies+Meat","basePrice":99.0,"priceByFlavor":{}},
              {"id":"nachos_fries_meat","name":"Nachos+Fries+Meat","basePrice":119.0,"priceByFlavor":{}},
              {"id":"nachos_fries_meat_veggies","name":"Nachos+Fries+Meat+Veggies","basePrice":129.0,"priceByFlavor":{}}
            ]
        """.trimIndent()
    )

    fun catTreatsItem(): ItemEntity = ItemEntity(
        id = "bite_cat_treats",
        categoryId = "cat_treats",
        name = "Cat Treats",
        flavors = "",
        variantsJson = """
            [
              {"id":"regular","name":"Regular","basePrice":5.0,"priceByFlavor":{}}
            ]
        """.trimIndent()
    )

    fun takeoutBoxItem(): ItemEntity = ItemEntity(
        id = "bite_takeout_box",
        categoryId = "cat_takeout",
        name = "Take-out Box",
        flavors = "",
        variantsJson = """
            [
              {"id":"box","name":"Regular","basePrice":10.0,"priceByFlavor":{}}
            ]
        """.trimIndent()
    )

    private fun coffeeSizeVariantsJson(): String = """
        [
          {"id":"12oz","name":"12oz","basePrice":49.0,"priceByFlavor":{}},
          {"id":"16oz","name":"16oz","basePrice":59.0,"priceByFlavor":{}},
          {"id":"22oz","name":"22oz","basePrice":79.0,"priceByFlavor":{}}
        ]
    """.trimIndent()

    private fun catFeineItem(): ItemEntity = ItemEntity(
        id = "drink_cat_feine",
        categoryId = "cat_drinks",
        name = "Cat-Feine (Classic Coffee)",
        flavors = "Salted Caramel Tail|Vanilla Iced Whisker|Caramel Meow-chiato|Hazelnut Hairball|Salted Caramel Paws|Siamese Latte",
        variantsJson = coffeeSizeVariantsJson()
    )

    private fun oreoDrinkItem(): ItemEntity = ItemEntity(
        id = "drink_oreo",
        categoryId = "cat_drinks",
        name = "Oreo (The Tuxedo Cat)",
        flavors = "Caramel Oreo Kitten|Oreo Iced Paw-tte|Vanilla Oreo Whisker",
        variantsJson = coffeeSizeVariantsJson()
    )

    private fun matchaDrinkItem(): ItemEntity = ItemEntity(
        id = "drink_matcha",
        categoryId = "cat_drinks",
        name = "Matcha (The Lucky Green Neko)",
        flavors = "Dirty Neko|Vanilla Matcha Meow|Caramel Matcha Tail|Salted Caramel Meow-cha",
        variantsJson = coffeeSizeVariantsJson()
    )

    private fun sodaItem(): ItemEntity = ItemEntity(
        id = "drink_soda",
        categoryId = "cat_drinks",
        name = "Soda (Fizzy Felines)",
        flavors = "Yogurt Yarn|Honey Peach Paws|Passion Fruit Purr|Kiwi Kitten|Strawberry Scratch|Lychee Litter|Blueberry Bite|Grumpy Grapes|Green Apple Alley Cat",
        variantsJson = """
            [
              {"id":"12oz","name":"12oz","basePrice":39.0,"priceByFlavor":{}},
              {"id":"16oz","name":"16oz","basePrice":49.0,"priceByFlavor":{}},
              {"id":"22oz","name":"22oz","basePrice":69.0,"priceByFlavor":{}}
            ]
        """.trimIndent()
    )

    private fun comboSinglePawItem(): ItemEntity = ItemEntity(
        id = "combo_single_paw",
        categoryId = "combos",
        name = "Single-Paw-rtner Combos",
        flavors = "",
        variantsJson = """
            [
              {"id":"combo_1","name":"Classy Cat Combo","basePrice":105.0,"priceByFlavor":{},"description":"4pcs Cheese Takoyaki + 16oz coffee (Any flavor)"},
              {"id":"combo_2","name":"Fizzy Kitten Combo","basePrice":65.0,"priceByFlavor":{},"description":"Small Fries (Any flavor) + 12oz soda (Any flavor)"},
              {"id":"combo_3","name":"Persian Combo","basePrice":175.0,"priceByFlavor":{},"description":"Nachos + Veggies + Meat + 16oz coffee (Any flavor)"},
              {"id":"combo_4","name":"Kasper Combo","basePrice":90.0,"priceByFlavor":{},"description":"4pcs Squid Takoyaki + 12oz soda (Any flavor)"}
            ]
        """.trimIndent()
    )

    private fun comboCoupleCatsItem(): ItemEntity = ItemEntity(
        id = "combo_couple_cats",
        categoryId = "combos",
        name = "Couple of Cats",
        flavors = "",
        variantsJson = """
            [
              {"id":"combo_5","name":"Two Tail","basePrice":205.0,"priceByFlavor":{},"description":"8pcs Squid Takoyaki + 2pcs 16oz soda (Any flavor)"},
              {"id":"combo_6","name":"Two Paw","basePrice":145.0,"priceByFlavor":{},"description":"Medium Fries (Any flavor) + 2pcs 12oz coffee (Any flavor)"},
              {"id":"combo_7","name":"Latte & Luna","basePrice":175.0,"priceByFlavor":{},"description":"Nachos + meat + veggies + 2pcs 12oz soda (Any flavor)"},
              {"id":"combo_8","name":"Sweet Kittens","basePrice":205.0,"priceByFlavor":{},"description":"8pcs Cheese Takoyaki + 1pc 16oz soda (Any flavor) + 1pc 16oz coffee (Any flavor)"},
              {"id":"combo_9","name":"Duo Siamese","basePrice":195.0,"priceByFlavor":{},"description":"8pcs Veggie Takoyaki + 2pcs 16oz coffee (Any flavor)"}
            ]
        """.trimIndent()
    )

    private fun comboAssociationItem(): ItemEntity = ItemEntity(
        id = "combo_association",
        categoryId = "combos",
        name = "Cat Association",
        flavors = "",
        variantsJson = """
            [
              {"id":"combo_10","name":"Scaredy Cats","basePrice":450.0,"priceByFlavor":{},"description":"16pcs Veggie Takoyaki + Jumbo Fries (Any flavor) + 4pcs 12oz soda"},
              {"id":"combo_11","name":"Lazy Cats","basePrice":340.0,"priceByFlavor":{},"description":"12pcs Cheese Takoyaki + 2pcs 16oz soda (Any flavor) + 2pcs 16oz coffee (Any flavor)"},
              {"id":"combo_12","name":"Grumpy Cats","basePrice":480.0,"priceByFlavor":{},"description":"Jumbo fries (Any flavor) + nachos + meat + veggies + 4pcs 16oz coffee (Any flavor)"},
              {"id":"combo_13","name":"Funny Cats","basePrice":470.0,"priceByFlavor":{},"description":"Jumbo Fries (Any flavor) + 3pcs 16oz coffee (Any flavor) + 3pcs 16oz soda (Any flavor)"},
              {"id":"combo_14","name":"Super Cats","basePrice":590.0,"priceByFlavor":{},"description":"Jumbo Fries (Any flavor) + 16pcs Veggie Takoyaki + 2pcs 22oz soda (Any flavor) + 2pcs 22oz coffee (Any flavor)"}
            ]
        """.trimIndent()
    )

    fun buldakCarboItem(): ItemEntity = ItemEntity(
        id = "buldak_carbo",
        categoryId = "cat_buldak",
        name = "Buldak Carbo",
        flavors = "",
        variantsJson = """
            [
              {"id":"plain_carbo","name":"Plain Carbo Samyang","basePrice":119.0,"priceByFlavor":{}},
              {"id":"carbo_milk","name":"Carbo Samyang w/ Milk","basePrice":129.0,"priceByFlavor":{}},
              {"id":"carbo_egg","name":"Carbo Samyang w/ Egg","basePrice":135.0,"priceByFlavor":{}}
            ]
        """.trimIndent()
    )

    fun buldakCheeseItem(): ItemEntity = ItemEntity(
        id = "buldak_cheese",
        categoryId = "cat_buldak",
        name = "Buldak Cheese",
        flavors = "",
        variantsJson = """
            [
              {"id":"plain_cheese","name":"Plain Cheese Samyang","basePrice":109.0,"priceByFlavor":{}},
              {"id":"cheese_milk","name":"Cheese Samyang w/ Milk","basePrice":119.0,"priceByFlavor":{}},
              {"id":"cheese_egg","name":"Cheese Samyang w/ Egg","basePrice":130.0,"priceByFlavor":{}}
            ]
        """.trimIndent()
    )

    fun sedaapSpicyChickenItem(): ItemEntity = ItemEntity(
        id = "sedaap_spicy_chicken",
        categoryId = "cat_buldak",
        name = "Sedaap Spicy Chicken",
        flavors = "1 Paw (Mild)|2 Paws (Medium)|3 Paws (Extra Hot)",
        variantsJson = """
            [
              {"id":"regular","name":"Regular","basePrice":69.0,"priceByFlavor":{}}
            ]
        """.trimIndent()
    )

    fun sedaapOriginalItem(): ItemEntity = ItemEntity(
        id = "sedaap_original",
        categoryId = "cat_buldak",
        name = "Sedaap Original",
        flavors = "",
        variantsJson = """
            [
              {"id":"regular","name":"Regular","basePrice":65.0,"priceByFlavor":{}}
            ]
        """.trimIndent()
    )

    fun buldakRecipeMappings(): List<RecipeMappingEntity> = listOf(
        RecipeMappingEntity("r_buldak_carbo", "buldak_carbo", null, "inv_buldak_carbo", 1.0),
        RecipeMappingEntity("r_buldak_cheese", "buldak_cheese", null, "inv_buldak_cheese", 1.0),
        RecipeMappingEntity("r_sedaap_spicy", "sedaap_spicy_chicken", null, "inv_sedaap_spicy", 1.0),
        RecipeMappingEntity("r_sedaap_orig", "sedaap_original", null, "inv_sedaap_orig", 1.0)
    )
}
