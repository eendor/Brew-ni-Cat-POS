package com.example.cattasticpos.ui.icons

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.CakeSlice
import com.composables.icons.lucide.Calendar
import com.composables.icons.lucide.Candy
import com.composables.icons.lucide.Cat
import com.composables.icons.lucide.Cherry
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.Citrus
import com.composables.icons.lucide.Coffee
import com.composables.icons.lucide.Cookie
import com.composables.icons.lucide.Croissant
import com.composables.icons.lucide.CupSoda
import com.composables.icons.lucide.Donut
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Drumstick
import com.composables.icons.lucide.Egg
import com.composables.icons.lucide.Flame
import com.composables.icons.lucide.GlassWater
import com.composables.icons.lucide.History
import com.composables.icons.lucide.IceCreamBowl
import com.composables.icons.lucide.IceCreamCone
import com.composables.icons.lucide.Layers
import com.composables.icons.lucide.LayoutList
import com.composables.icons.lucide.Leaf
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Milk
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Package
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Percent
import com.composables.icons.lucide.Pizza
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Popcorn
import com.composables.icons.lucide.Printer
import com.composables.icons.lucide.ReceiptText
import com.composables.icons.lucide.Salad
import com.composables.icons.lucide.Sandwich
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Share2
import com.composables.icons.lucide.ShoppingBag
import com.composables.icons.lucide.Soup
import com.composables.icons.lucide.Tag
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Trophy
import com.composables.icons.lucide.User
import com.composables.icons.lucide.Users
import com.composables.icons.lucide.UsersRound
import com.composables.icons.lucide.Utensils
import com.composables.icons.lucide.Wallet
import com.composables.icons.lucide.X
import com.example.cattasticpos.ui.adaptive.glassIconGradient

data class ProductIconOption(
    val key: String,
    val label: String,
    val icon: ImageVector
)

/**
 * Lucide icon registry. Default rendering uses glass gradient via [FluentIcon];
 * pass explicit [tint] + useGlassGradient=false for toolbar chrome only.
 */
object FluentIcons {
    val History: ImageVector = Lucide.History
    val Settings: ImageVector = Lucide.Settings
    val Search: ImageVector = Lucide.Search
    val Calendar: ImageVector = Lucide.Calendar
    val Box: ImageVector = Lucide.Package
    val Wallet: ImageVector = Lucide.Wallet
    val List: ImageVector = Lucide.LayoutList
    val Queue: ImageVector = Lucide.Layers
    val ArrowLeft: ImageVector = Lucide.ArrowLeft
    val Print: ImageVector = Lucide.Printer
    val ArrowDownload: ImageVector = Lucide.Download
    val Share: ImageVector = Lucide.Share2
    val Edit: ImageVector = Lucide.Pencil
    val Delete: ImageVector = Lucide.Trash2
    val ChevronUp: ImageVector = Lucide.ChevronUp
    val ChevronDown: ImageVector = Lucide.ChevronDown
    val ChevronLeft: ImageVector = Lucide.ChevronLeft
    val ChevronRight: ImageVector = Lucide.ChevronRight
    val Receipt: ImageVector = Lucide.ReceiptText
    val Trophy: ImageVector = Lucide.Trophy
    val Add: ImageVector = Lucide.Plus
    val Subtract: ImageVector = Lucide.Minus
    val Pause: ImageVector = Lucide.Pause
    val CheckmarkCircle: ImageVector = Lucide.CircleCheck
    val Close: ImageVector = Lucide.X
    val Tag: ImageVector = Lucide.Tag
    val Percent: ImageVector = Lucide.Percent
    val ShoppingBag: ImageVector = Lucide.ShoppingBag
    val Utensils: ImageVector = Lucide.Utensils

    val DrinkCoffee: ImageVector = Lucide.Coffee
    val FoodBites: ImageVector = Lucide.Cookie
    val ComboPackage: ImageVector = Lucide.Utensils

    val productIconOptions: List<ProductIconOption> = listOf(
        ProductIconOption("coffee", "Coffee", Lucide.Coffee),
        ProductIconOption("cup_soda", "Iced Cup", Lucide.CupSoda),
        ProductIconOption("glass_water", "Water Glass", Lucide.GlassWater),
        ProductIconOption("milk", "Milk / Shake", Lucide.Milk),
        ProductIconOption("leaf", "Matcha / Tea", Lucide.Leaf),
        ProductIconOption("cookie", "Cookie", Lucide.Cookie),
        ProductIconOption("circle_dot", "Takoyaki", Lucide.CircleDot),
        ProductIconOption("flame", "Fries / Spicy", Lucide.Flame),
        ProductIconOption("sandwich", "Nachos / Sub", Lucide.Sandwich),
        ProductIconOption("buldak", "Buldak Ramen", NoodleIcons.BuldakSpicyNoodle),
        ProductIconOption("sedaap", "Classic Noodle", NoodleIcons.SedaapNoodle),
        ProductIconOption("soup", "Soup Bowl", Lucide.Soup),
        ProductIconOption("pizza", "Pizza", Lucide.Pizza),
        ProductIconOption("croissant", "Croissant", Lucide.Croissant),
        ProductIconOption("donut", "Donut", Lucide.Donut),
        ProductIconOption("cake_slice", "Cake Slice", Lucide.CakeSlice),
        ProductIconOption("ice_cream_cone", "Ice Cream", Lucide.IceCreamCone),
        ProductIconOption("ice_cream_bowl", "Dessert Bowl", Lucide.IceCreamBowl),
        ProductIconOption("candy", "Candy", Lucide.Candy),
        ProductIconOption("popcorn", "Popcorn", Lucide.Popcorn),
        ProductIconOption("drumstick", "Chicken", Lucide.Drumstick),
        ProductIconOption("egg", "Egg", Lucide.Egg),
        ProductIconOption("salad", "Salad", Lucide.Salad),
        ProductIconOption("cherry", "Berry", Lucide.Cherry),
        ProductIconOption("citrus", "Citrus", Lucide.Citrus),
        ProductIconOption("cat", "Cat Treat", Lucide.Cat),
        ProductIconOption("user", "Solo Combo", Lucide.User),
        ProductIconOption("users", "Duo Combo", Lucide.Users),
        ProductIconOption("users_round", "Group Combo", Lucide.UsersRound),
        ProductIconOption("package", "Take-out Box", Lucide.Package)
    )

    private val productIconsByKey: Map<String, ImageVector> =
        productIconOptions.associate { it.key to it.icon }

    fun iconByKey(iconKey: String?): ImageVector? {
        val clean = iconKey?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return productIconsByKey[clean]
    }

    fun categoryIcon(categoryId: String): ImageVector = when (categoryId) {
        "cat_drinks" -> DrinkCoffee
        "combos" -> ComboPackage
        "cat_buldak" -> NoodleIcons.NoodleCategory
        "cat_takeout" -> Lucide.Package
        else -> FoodBites
    }

    fun menuItemIcon(itemId: String, iconKey: String? = null): ImageVector {
        iconByKey(iconKey)?.let { return it }
        val id = itemId.lowercase()
        return when {
            id == "drink_cat_feine" -> DrinkCoffee
            id == "drink_oreo" -> Lucide.Cookie
            id == "drink_matcha" -> Lucide.Leaf
            id == "drink_soda" -> Lucide.CupSoda
            id == "drink_bottled_water" -> Lucide.GlassWater
            id == "drink_coffee" -> DrinkCoffee
            id == "bite_takoyaki" -> Lucide.CircleDot
            id == "bite_fries" -> Lucide.Flame
            id == "bite_nachos" -> Lucide.Sandwich
            id == "bite_takeout_box" -> Lucide.Package
            id == "combo_single_paw" || id == "combo_meals" -> Lucide.User
            id == "combo_couple_cats" -> Lucide.Users
            id == "combo_association" -> Lucide.UsersRound
            id.contains("buldak") -> NoodleIcons.BuldakSpicyNoodle
            id.contains("sedaap") -> NoodleIcons.SedaapNoodle
            else -> categoryIcon(
                when {
                    id.startsWith("drink_") -> "cat_drinks"
                    id.startsWith("combo_") -> "combos"
                    id.startsWith("buldak_") || id.startsWith("sedaap_") -> "cat_buldak"
                    else -> "cat_bites"
                }
            )
        }
    }
}

@Composable
fun FluentIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    size: Dp = PosIconSize.Default,
    useGlassGradient: Boolean = true
) {
    val useGradient = useGlassGradient && tint == null
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = if (useGradient) {
            modifier.size(size).glassIconGradient()
        } else {
            modifier.size(size)
        },
        tint = if (useGradient) {
            Color.Unspecified
        } else {
            tint ?: MaterialTheme.colorScheme.primary
        }
    )
}
