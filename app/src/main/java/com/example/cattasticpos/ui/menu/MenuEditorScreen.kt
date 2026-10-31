package com.example.cattasticpos.ui.menu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cattasticpos.domain.model.Category
import com.example.cattasticpos.domain.model.Item
import com.example.cattasticpos.domain.model.Variant
import com.example.cattasticpos.ui.adaptive.AdaptiveFloatingActionButton
import com.example.cattasticpos.ui.adaptive.AdaptiveScaffold
import com.example.cattasticpos.ui.adaptive.AdaptiveSnackbarHost
import com.example.cattasticpos.ui.adaptive.AdaptiveTopAppBar
import com.example.cattasticpos.ui.adaptive.LocalCupertinoColors
import com.example.cattasticpos.ui.adaptive.rememberAdaptiveTopBarScrollBehavior
import com.example.cattasticpos.ui.components.unstyled.PosPrimaryButton
import com.example.cattasticpos.ui.icons.FluentIcon
import com.example.cattasticpos.ui.icons.FluentIcons
import com.example.cattasticpos.ui.icons.PosIconBadge
import com.example.cattasticpos.ui.icons.PosIconSize
import com.example.cattasticpos.ui.theme.AdaptiveGlassCard
import com.example.cattasticpos.ui.theme.AdaptiveGlassDialog
import com.example.cattasticpos.ui.theme.adaptiveBodyMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuEditorScreen(
    viewModel: MenuEditorViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = rememberAdaptiveTopBarScrollBehavior()
    val cupertino = LocalCupertinoColors.current

    var showCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }

    var showProductDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<Item?>(null) }
    var confirmDeleteItem by remember { mutableStateOf<Item?>(null) }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbarMessage()
        }
    }

    val categoryById = remember(uiState.categories) {
        uiState.categories.associateBy { it.id }
    }

    AdaptiveScaffold(
        topBar = {
            AdaptiveTopAppBar(
                title = "Menu & Categories",
                scrollBehavior = scrollBehavior,
                largeTitle = false,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            FluentIcon(
                                imageVector = FluentIcons.ArrowLeft,
                                contentDescription = "Go Back",
                                tint = cupertino.accent,
                                size = 24.dp
                            )
                        }
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            editingCategory = null
                            showCategoryDialog = true
                        }
                    ) {
                        FluentIcon(
                            imageVector = FluentIcons.Add,
                            contentDescription = null,
                            tint = cupertino.accent,
                            size = 18.dp
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Category", color = cupertino.accent, fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        },
        snackbarHost = { AdaptiveSnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            AdaptiveFloatingActionButton(
                onClick = {
                    editingItem = null
                    showProductDialog = true
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FluentIcon(
                        imageVector = FluentIcons.Add,
                        contentDescription = "Add Product",
                        tint = cupertino.onAccent,
                        size = 20.dp
                    )
                    Text(
                        "Add Product",
                        color = cupertino.onAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search products or flavors...") },
                leadingIcon = {
                    FluentIcon(
                        imageVector = FluentIcons.Search,
                        contentDescription = null,
                        size = 18.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            FluentIcon(
                                imageVector = FluentIcons.Close,
                                contentDescription = "Clear search",
                                size = 16.dp
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "__all__") {
                        FilterChip(
                            selected = uiState.selectedCategoryFilterId == null,
                            onClick = { viewModel.selectCategoryFilter(null) },
                            label = { Text("All (${uiState.items.size})") }
                        )
                    }
                    items(uiState.categories, key = { it.id }) { category ->
                        val count = uiState.items.count { it.categoryId == category.id }
                        FilterChip(
                            selected = uiState.selectedCategoryFilterId == category.id,
                            onClick = { viewModel.selectCategoryFilter(category.id) },
                            label = { Text("${category.name} ($count)") }
                        )
                    }
                }

                val selectedCategory = uiState.selectedCategoryFilterId?.let { categoryById[it] }
                if (selectedCategory != null) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            editingCategory = selectedCategory
                            showCategoryDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        FluentIcon(
                            imageVector = FluentIcons.Edit,
                            contentDescription = "Edit Category",
                            size = 16.dp,
                            tint = cupertino.accent
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Edit Category", fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (uiState.filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (uiState.categories.isEmpty()) {
                            "No categories yet. Tap '+ Category' to create one."
                        } else {
                            "No matching products found. Tap '+ Add Product' to add one."
                        },
                        color = adaptiveBodyMuted(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(uiState.filteredItems, key = { it.id }) { item ->
                        MenuEditorProductCard(
                            item = item,
                            categoryName = categoryById[item.categoryId]?.name ?: item.categoryId,
                            onToggleAvailable = { available ->
                                viewModel.toggleItemAvailability(item, available)
                            },
                            onEdit = {
                                editingItem = item
                                showProductDialog = true
                            },
                            onDelete = {
                                confirmDeleteItem = item
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCategoryDialog) {
        CategoryEditorDialog(
            existingCategory = editingCategory,
            onDismiss = { showCategoryDialog = false },
            onSave = { name ->
                viewModel.saveCategory(editingCategory?.id, name)
                showCategoryDialog = false
            },
            onDelete = editingCategory?.let { cat ->
                {
                    viewModel.deleteCategory(cat)
                    showCategoryDialog = false
                }
            }
        )
    }

    if (showProductDialog) {
        ProductEditorDialog(
            existingItem = editingItem,
            categories = uiState.categories,
            defaultCategoryId = uiState.selectedCategoryFilterId
                ?: uiState.categories.firstOrNull()?.id.orEmpty(),
            onDismiss = { showProductDialog = false },
            onSave = { name, categoryId, icon, isAvailable, flavors, variants ->
                viewModel.saveItem(
                    existingId = editingItem?.id,
                    name = name,
                    categoryId = categoryId,
                    icon = icon,
                    isAvailable = isAvailable,
                    flavors = flavors,
                    variants = variants,
                    onSuccess = { showProductDialog = false }
                )
            }
        )
    }

    confirmDeleteItem?.let { itemToDelete ->
        AdaptiveGlassDialog(onDismissRequest = { confirmDeleteItem = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Delete \"${itemToDelete.name}\"?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "This removes the product and its recipe mappings from the POS menu. Past order receipts keep their recorded item names.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = adaptiveBodyMuted()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { confirmDeleteItem = null }) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.deleteItem(itemToDelete)
                            confirmDeleteItem = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.onError)
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuEditorProductCard(
    item: Item,
    categoryName: String,
    onToggleAvailable: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val cupertino = LocalCupertinoColors.current
    val variantsSummary = remember(item.variants) {
        item.variants.joinToString(" · ") { variant ->
            val price = if (variant.basePrice > 0.0) {
                variant.basePrice
            } else {
                variant.priceByFlavor.values.minOrNull() ?: variant.basePrice
            }
            "${variant.name}: ₱${String.format("%.0f", price)}"
        }
    }
    val iconOption = remember(item.icon) {
        FluentIcons.productIconOptions.firstOrNull { it.key == item.icon }
    }

    AdaptiveGlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PosIconBadge(
                imageVector = FluentIcons.menuItemIcon(item.id, item.icon),
                contentDescription = item.name,
                emphasized = item.isAvailable,
                error = !item.isAvailable,
                iconSize = PosIconSize.Medium
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        color = cupertino.accent.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = categoryName,
                            color = cupertino.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (iconOption != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = iconOption.label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    text = variantsSummary.ifEmpty { "No sizes configured" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (item.flavors.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Flavors (${item.flavors.size}): ${item.flavors.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = adaptiveBodyMuted(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (item.isAvailable) "Available" else "Sold Out",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.isAvailable) cupertino.accent else MaterialTheme.colorScheme.error
                    )
                    Switch(
                        checked = item.isAvailable,
                        onCheckedChange = onToggleAvailable
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit) {
                        FluentIcon(
                            imageVector = FluentIcons.Edit,
                            contentDescription = "Edit ${item.name}",
                            tint = cupertino.accent,
                            size = 20.dp
                        )
                    }
                    IconButton(onClick = onDelete) {
                        FluentIcon(
                            imageVector = FluentIcons.Delete,
                            contentDescription = "Delete ${item.name}",
                            tint = MaterialTheme.colorScheme.error,
                            size = 20.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryEditorDialog(
    existingCategory: Category?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember(existingCategory) { mutableStateOf(existingCategory?.name.orEmpty()) }

    AdaptiveGlassDialog(onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = if (existingCategory == null) "Add Category" else "Edit Category",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Category Name") },
                placeholder = { Text("e.g., Seasonal Drinks") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        FluentIcon(
                            imageVector = FluentIcons.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            size = 16.dp
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Delete")
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    PosPrimaryButton(
                        onClick = { onSave(name) },
                        enabled = name.trim().isNotEmpty()
                    ) {
                        Text("Save", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private data class EditableVariantDraft(
    val id: String = "",
    val name: String,
    val basePriceText: String,
    val description: String,
    val flavorPriceTexts: Map<String, String>
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ProductEditorDialog(
    existingItem: Item?,
    categories: List<Category>,
    defaultCategoryId: String,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        categoryId: String,
        icon: String?,
        isAvailable: Boolean,
        flavors: List<String>,
        variants: List<Variant>
    ) -> Unit
) {
    val cupertino = LocalCupertinoColors.current
    var name by remember(existingItem) { mutableStateOf(existingItem?.name.orEmpty()) }
    var selectedCategoryId by remember(existingItem, defaultCategoryId) {
        mutableStateOf(existingItem?.categoryId ?: defaultCategoryId)
    }
    var selectedIconKey by remember(existingItem) { mutableStateOf<String?>(existingItem?.icon) }
    var isAvailable by remember(existingItem) { mutableStateOf(existingItem?.isAvailable ?: true) }
    var flavors by remember(existingItem) { mutableStateOf(existingItem?.flavors ?: emptyList()) }
    var newFlavorInput by remember { mutableStateOf("") }
    var showFlavorOverrides by remember(existingItem) {
        mutableStateOf(existingItem?.variants?.any { it.priceByFlavor.isNotEmpty() } == true)
    }

    var variantDrafts by remember(existingItem) {
        mutableStateOf(
            if (existingItem != null && existingItem.variants.isNotEmpty()) {
                existingItem.variants.map { v ->
                    EditableVariantDraft(
                        id = v.id,
                        name = v.name,
                        basePriceText = if (v.basePrice % 1.0 == 0.0) {
                            v.basePrice.toInt().toString()
                        } else {
                            v.basePrice.toString()
                        },
                        description = v.description.orEmpty(),
                        flavorPriceTexts = v.priceByFlavor.mapValues { (_, p) ->
                            if (p % 1.0 == 0.0) p.toInt().toString() else p.toString()
                        }
                    )
                }
            } else {
                listOf(
                    EditableVariantDraft(
                        id = "size_regular",
                        name = "Regular",
                        basePriceText = "59",
                        description = "",
                        flavorPriceTexts = emptyMap()
                    )
                )
            }
        )
    }

    AdaptiveGlassDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
        ) {
            Text(
                text = if (existingItem == null) "Add Product" else "Edit Product",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product Name") },
                    placeholder = { Text("e.g., Caramel Macchiato") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Category Picker
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categories, key = { it.id }) { cat ->
                            FilterChip(
                                selected = selectedCategoryId == cat.id,
                                onClick = { selectedCategoryId = cat.id },
                                label = { Text(cat.name) }
                            )
                        }
                    }
                }

                // Availability Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Available for Sale",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isAvailable) "Shown as active on the cashier menu" else "Shown as Sold Out on the cashier menu",
                            style = MaterialTheme.typography.bodySmall,
                            color = adaptiveBodyMuted()
                        )
                    }
                    Switch(
                        checked = isAvailable,
                        onCheckedChange = { isAvailable = it }
                    )
                }

                // 30-Icon Picker
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Product Icon (30 Icons)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        val activeLabel = FluentIcons.productIconOptions
                            .firstOrNull { it.key == selectedIconKey }
                            ?.label ?: "Auto (By Product ID)"
                        Text(
                            text = activeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = cupertino.accent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedIconKey == null,
                            onClick = { selectedIconKey = null },
                            label = { Text("Auto", fontSize = 11.sp) }
                        )
                        FluentIcons.productIconOptions.forEach { option ->
                            val isSelected = selectedIconKey == option.key
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) {
                                    cupertino.accent.copy(alpha = 0.18f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                },
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) cupertino.accent else Color.Transparent
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedIconKey = option.key }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    FluentIcon(
                                        imageVector = option.icon,
                                        contentDescription = option.label,
                                        size = 16.dp,
                                        tint = if (isSelected) cupertino.accent else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = option.label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) cupertino.accent else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Sizes / Variants Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sizes / Variants & Prices",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(
                            onClick = {
                                variantDrafts = variantDrafts + EditableVariantDraft(
                                    name = "",
                                    basePriceText = "",
                                    description = "",
                                    flavorPriceTexts = emptyMap()
                                )
                            }
                        ) {
                            FluentIcon(
                                imageVector = FluentIcons.Add,
                                contentDescription = null,
                                size = 16.dp,
                                tint = cupertino.accent
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Add Size")
                        }
                    }

                    variantDrafts.forEachIndexed { index, draft ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = draft.name,
                                        onValueChange = { newName ->
                                            variantDrafts = variantDrafts.toMutableList().also {
                                                it[index] = draft.copy(name = newName)
                                            }
                                        },
                                        label = { Text("Size / Variant") },
                                        placeholder = { Text("16oz / Regular") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.2f)
                                    )

                                    OutlinedTextField(
                                        value = draft.basePriceText,
                                        onValueChange = { newPrice ->
                                            variantDrafts = variantDrafts.toMutableList().also {
                                                it[index] = draft.copy(basePriceText = newPrice)
                                            }
                                        },
                                        label = { Text("Price (₱)") },
                                        placeholder = { Text("59") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.weight(0.9f)
                                    )

                                    if (variantDrafts.size > 1) {
                                        IconButton(
                                            onClick = {
                                                variantDrafts = variantDrafts.toMutableList().also {
                                                    it.removeAt(index)
                                                }
                                            }
                                        ) {
                                            FluentIcon(
                                                imageVector = FluentIcons.Delete,
                                                contentDescription = "Remove size",
                                                tint = MaterialTheme.colorScheme.error,
                                                size = 18.dp
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = draft.description,
                                    onValueChange = { newDesc ->
                                        variantDrafts = variantDrafts.toMutableList().also {
                                            it[index] = draft.copy(description = newDesc)
                                        }
                                    },
                                    label = { Text("Subtitle / Combo Note (Optional)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (flavors.isNotEmpty() && showFlavorOverrides) {
                                    Text(
                                        text = "Per-flavor price overrides for ${draft.name.ifBlank { "this size" }} (leave blank to use base price):",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = adaptiveBodyMuted()
                                    )
                                    flavors.forEach { flavor ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = flavor,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.weight(1f)
                                            )
                                            OutlinedTextField(
                                                value = draft.flavorPriceTexts[flavor].orEmpty(),
                                                onValueChange = { priceText ->
                                                    val updatedMap = draft.flavorPriceTexts.toMutableMap()
                                                    if (priceText.isBlank()) {
                                                        updatedMap.remove(flavor)
                                                    } else {
                                                        updatedMap[flavor] = priceText
                                                    }
                                                    variantDrafts = variantDrafts.toMutableList().also {
                                                        it[index] = draft.copy(flavorPriceTexts = updatedMap)
                                                    }
                                                },
                                                label = { Text("₱") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                singleLine = true,
                                                modifier = Modifier.width(110.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Flavors Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Flavors / Options (${flavors.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (flavors.isNotEmpty()) {
                            TextButton(onClick = { showFlavorOverrides = !showFlavorOverrides }) {
                                Text(
                                    text = if (showFlavorOverrides) "Hide Flavor Prices" else "Set Flavor Prices",
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newFlavorInput,
                            onValueChange = { newFlavorInput = it },
                            label = { Text("Add Flavor") },
                            placeholder = { Text("e.g., Spanish Latte or Creamy: Carbonara") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val trimmed = newFlavorInput.trim()
                                if (trimmed.isNotEmpty() && trimmed !in flavors) {
                                    flavors = flavors + trimmed
                                    newFlavorInput = ""
                                }
                            },
                            enabled = newFlavorInput.trim().isNotEmpty()
                        ) {
                            Text("Add")
                        }
                    }

                    if (flavors.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            flavors.forEach { flavor ->
                                InputChip(
                                    selected = false,
                                    onClick = { flavors = flavors - flavor },
                                    label = { Text(flavor, fontSize = 12.sp) },
                                    trailingIcon = {
                                        FluentIcon(
                                            imageVector = FluentIcons.Close,
                                            contentDescription = "Remove $flavor",
                                            size = 14.dp
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(8.dp))
                PosPrimaryButton(
                    onClick = {
                        val builtVariants = variantDrafts.mapIndexedNotNull { index, draft ->
                            val cleanName = draft.name.trim()
                            if (cleanName.isEmpty()) return@mapIndexedNotNull null
                            val parsedBase = draft.basePriceText.trim().toDoubleOrNull() ?: 0.0
                            val parsedFlavorPrices = draft.flavorPriceTexts
                                .filterKeys { it in flavors }
                                .mapNotNull { (flavor, priceStr) ->
                                    priceStr.trim().toDoubleOrNull()?.let { flavor to it }
                                }
                                .toMap()
                            val slug = cleanName.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "size_${index + 1}" }
                            val variantId = draft.id.takeIf { it.isNotBlank() } ?: "size_$slug"
                            Variant(
                                id = variantId,
                                name = cleanName,
                                basePrice = parsedBase,
                                priceByFlavor = parsedFlavorPrices,
                                description = draft.description.trim().takeIf { it.isNotEmpty() }
                            )
                        }
                        onSave(
                            name,
                            selectedCategoryId,
                            selectedIconKey,
                            isAvailable,
                            flavors,
                            builtVariants
                        )
                    },
                    enabled = name.trim().isNotEmpty() &&
                        selectedCategoryId.isNotBlank() &&
                        variantDrafts.any { it.name.trim().isNotEmpty() }
                ) {
                    Text(
                        text = if (existingItem == null) "Create Product" else "Save Changes",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
