package com.example.cattasticpos.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.example.cattasticpos.ui.adaptive.AdaptiveSnackbarHost
import com.example.cattasticpos.ui.adaptive.BrewNiCatBrandIcon
import com.example.cattasticpos.ui.adaptive.CollapsingGlassScaffold
import com.example.cattasticpos.ui.adaptive.CollapsingHeaderState
import com.example.cattasticpos.ui.adaptive.LocalCupertinoColors
import com.example.cattasticpos.ui.adaptive.collapsingNestedScroll
import com.example.cattasticpos.ui.adaptive.iOSSpringSpec
import com.example.cattasticpos.ui.adaptive.iOSSpringDp
import com.example.cattasticpos.ui.adaptive.iOSSpringSize
import com.example.cattasticpos.ui.adaptive.liquidSwipeTransition
import com.example.cattasticpos.ui.adaptive.rememberCollapsingHeaderState
import com.example.cattasticpos.ui.adaptive.rememberLiquidGlassHazeState
import com.example.cattasticpos.ui.adaptive.liquidGlassSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import com.example.cattasticpos.ui.adaptive.CupertinoSection
import com.example.cattasticpos.ui.adaptive.BionicHaptic
import com.example.cattasticpos.ui.adaptive.CupertinoSegmentChip
import com.example.cattasticpos.ui.adaptive.FeedbackEvent
import com.example.cattasticpos.ui.adaptive.PosSound
import com.example.cattasticpos.ui.adaptive.SelectableOptionRow
import com.example.cattasticpos.ui.adaptive.rememberPosFeedback
import com.example.cattasticpos.ui.components.unstyled.PosFilterChip
import com.example.cattasticpos.ui.icons.FluentIcon
import com.example.cattasticpos.ui.icons.FluentIcons
import com.example.cattasticpos.ui.icons.PosIconBadge
import com.example.cattasticpos.ui.icons.PosIconSize
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cattasticpos.domain.catalog.ProductAddOnCatalog
import com.example.cattasticpos.domain.model.CartItem
import com.example.cattasticpos.domain.model.CartLineSelection
import com.example.cattasticpos.domain.model.Item
import dev.chrisbanes.haze.HazeState
import com.example.cattasticpos.domain.model.Variant
import com.example.cattasticpos.domain.strategy.DiscountStrategy
import com.example.cattasticpos.domain.strategy.FreeOrderDiscountStrategy
import com.example.cattasticpos.domain.strategy.NoDiscountStrategy
import com.example.cattasticpos.domain.strategy.PercentageDiscountStrategy
import com.example.cattasticpos.domain.strategy.FivePercentDiscountStrategy
import com.example.cattasticpos.ui.components.GlassSearchBar
import androidx.compose.foundation.layout.widthIn
import com.example.cattasticpos.ui.components.SleepingCatGraphic
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.cattasticpos.ui.theme.AlabasterPalette
import com.example.cattasticpos.ui.components.unstyled.PosButtonIconLabel
import com.example.cattasticpos.ui.components.unstyled.PosPrimaryButton
import com.example.cattasticpos.ui.theme.AdaptiveAmbientGlows
import com.example.cattasticpos.ui.theme.AdaptiveGlassDialog
import com.example.cattasticpos.ui.theme.AdaptiveGlassCard
import com.example.cattasticpos.ui.theme.adaptiveGlassBrush
import com.example.cattasticpos.ui.theme.adaptiveBodyMuted
import com.example.cattasticpos.ui.theme.adaptiveGlassContentColor
import com.example.cattasticpos.ui.theme.ObsidianGlassCard
import com.example.cattasticpos.ui.theme.ObsidianGlassSurface
import com.example.cattasticpos.ui.theme.adaptiveBodyMuted
import com.example.cattasticpos.ui.theme.adaptiveGlassFill
import com.example.cattasticpos.ui.theme.specularBorderBrush

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToHistory: () -> Unit,
    onNavigateToInventory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val cartItemCount = uiState.activeCart.sumOf { it.quantity }
    // Wide screens (Redmi Pad 2 tablet) use a fixed split: catalog on top, an order panel
    // that fills the bottom slot (TabletOrderPanel) — always visible, no dead space. Phones
    // use the collapsing bottom-sheet, so this flag only drives the phone layout.
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.smallestScreenWidthDp >= 480 || configuration.screenWidthDp >= 440
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val useSideBySidePanel = isLandscape && (configuration.screenWidthDp >= 720 || configuration.smallestScreenWidthDp >= 540)
    var isCartExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(cartItemCount) {
        if (cartItemCount == 0) {
            isCartExpanded = false
        }
    }

    val performFeedback = rememberPosFeedback()

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            if (message.contains("placed successfully", ignoreCase = true)) {
                performFeedback(FeedbackEvent(BionicHaptic.Success, PosSound.Checkout))
            }
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbarMessage()
        }
    }

    val cupertino = LocalCupertinoColors.current
    val hazeState = rememberLiquidGlassHazeState()
    val headerState = rememberCollapsingHeaderState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }
    val isCatalogSearchActive = isSearchExpanded || searchQuery.isNotEmpty()

    LaunchedEffect(isCatalogSearchActive) {
        if (isCatalogSearchActive) {
            headerState.resetCollapse()
        }
    }

    CollapsingGlassScaffold(
        title = "Brew ni Cat",
        hazeState = hazeState,
        headerState = headerState,
        showBrandWordmark = true,
        snackbarHost = { AdaptiveSnackbarHost(snackbarHostState) },
        modifier = modifier,
        navigationIcon = {
            BrewNiCatBrandIcon(
                modifier = Modifier.padding(start = 8.dp)
            )
        },
        actions = {
            DashboardHeaderIconButton(
                onClick = {
                    if (isSearchExpanded) {
                        isSearchExpanded = false
                        searchQuery = ""
                    } else {
                        headerState.resetCollapse()
                        isSearchExpanded = true
                    }
                },
                icon = FluentIcons.Search,
                contentDescription = "Search menu",
                tint = cupertino.accent
            )
            DashboardHeaderIconButton(
                onClick = onNavigateToInventory,
                icon = FluentIcons.Box,
                contentDescription = "Inventory Management",
                tint = cupertino.accent
            )
            DashboardHeaderIconButton(
                onClick = { viewModel.setShowExpenseDialog(true) },
                icon = FluentIcons.Wallet,
                contentDescription = "Add Expense",
                tint = cupertino.accent
            )
            DashboardHeaderIconButton(
                onClick = { viewModel.setShowQueuesDialog(true) },
                icon = FluentIcons.Queue,
                contentDescription = "View Queues",
                tint = cupertino.accent
            )
            DashboardHeaderIconButton(
                onClick = onNavigateToHistory,
                icon = FluentIcons.History,
                contentDescription = "History",
                tint = cupertino.accent
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (!uiState.activeTableLabel.isNullOrBlank() && cartItemCount > 0) {
                Text(
                    text = "Name: ${uiState.activeTableLabel}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
            }
            val darkTheme = isSystemInDarkTheme()
            val checkoutBorder = if (darkTheme) {
                BorderStroke(1.dp, specularBorderBrush())
            } else {
                BorderStroke(1.dp, AlabasterPalette.RingBorder)
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (useSideBySidePanel) {
                    // Tablet landscape: catalog left, order panel right — both always visible.
                    // Panel is capped at 42% width so the catalog keeps at least ~3 columns.
                    val panelWidth = minOf(380.dp, maxWidth * 0.42f)
                    Row(modifier = Modifier.fillMaxSize()) {
                        StorefrontCatalogPane(
                            modifier = Modifier.weight(1f),
                            uiState = uiState,
                            hazeState = hazeState,
                            headerState = headerState,
                            searchQuery = searchQuery,
                            isSearchExpanded = isSearchExpanded,
                            onSearchQueryChange = { searchQuery = it },
                            onSearchExpandedChange = { isSearchExpanded = it },
                            onCategorySelected = { viewModel.selectCategory(it) },
                            onItemClick = { viewModel.showConfigurationSheet(it) },
                            compactGlows = true,
                            bottomContentPadding = 12.dp
                        )
                        TabletOrderPanel(
                            modifier = Modifier
                                .width(panelWidth)
                                .fillMaxHeight(),
                            uiState = uiState,
                            cartItemCount = cartItemCount,
                            checkoutBorder = checkoutBorder,
                            darkTheme = darkTheme,
                            onHoldOrder = { viewModel.setShowHoldOrderDialog(true) },
                            onPlaceOrder = { viewModel.setShowPaymentDialog(true) },
                            onQuantityChange = { id, delta -> viewModel.changeQuantity(id, delta) },
                            onSelectDiscount = { viewModel.selectDiscount(it) },
                            panelShape = RoundedCornerShape(topStart = 24.dp)
                        )
                    }
                } else if (isWideScreen && !isLandscape) {
                    // Tablet portrait: catalog on top, order panel fills bottom slot completely down to screen edge.
                    Column(modifier = Modifier.fillMaxSize()) {
                        StorefrontCatalogPane(
                            modifier = Modifier.weight(1f),
                            uiState = uiState,
                            hazeState = hazeState,
                            headerState = headerState,
                            searchQuery = searchQuery,
                            isSearchExpanded = isSearchExpanded,
                            onSearchQueryChange = { searchQuery = it },
                            onSearchExpandedChange = { isSearchExpanded = it },
                            onCategorySelected = { viewModel.selectCategory(it) },
                            onItemClick = { viewModel.showConfigurationSheet(it) },
                            compactGlows = true,
                            bottomContentPadding = 12.dp
                        )
                        TabletOrderPanel(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.85f),
                            uiState = uiState,
                            cartItemCount = cartItemCount,
                            checkoutBorder = checkoutBorder,
                            darkTheme = darkTheme,
                            onHoldOrder = { viewModel.setShowHoldOrderDialog(true) },
                            onPlaceOrder = { viewModel.setShowPaymentDialog(true) },
                            onQuantityChange = { id, delta -> viewModel.changeQuantity(id, delta) },
                            onSelectDiscount = { viewModel.selectDiscount(it) },
                            panelShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                        )
                    }
                } else {
                    // Phone (any orientation): collapsing bottom-sheet cart so the menu
                    // stays usable when the screen is small.
                    val sheetMaxHeight = maxHeight * (if (isWideScreen) 0.78f else 0.6f)
                    Column(modifier = Modifier.fillMaxSize()) {
                        StorefrontCatalogPane(
                            modifier = Modifier.weight(1f),
                            uiState = uiState,
                            hazeState = hazeState,
                            headerState = headerState,
                            searchQuery = searchQuery,
                            isSearchExpanded = isSearchExpanded,
                            onSearchQueryChange = { searchQuery = it },
                            onSearchExpandedChange = { isSearchExpanded = it },
                            onCategorySelected = { viewModel.selectCategory(it) },
                            onItemClick = { viewModel.showConfigurationSheet(it) },
                            compactGlows = true,
                            bottomContentPadding = 12.dp
                        )
                        DashboardCheckoutPanel(
                            modifier = Modifier.fillMaxWidth(),
                            uiState = uiState,
                            cartItemCount = cartItemCount,
                            isCartExpanded = isCartExpanded,
                            onCartExpandedChange = { isCartExpanded = it },
                            checkoutBorder = checkoutBorder,
                            darkTheme = darkTheme,
                            onHoldOrder = { viewModel.setShowHoldOrderDialog(true) },
                            onPlaceOrder = { viewModel.setShowPaymentDialog(true) },
                            onQuantityChange = { id, delta -> viewModel.changeQuantity(id, delta) },
                            onSelectDiscount = { viewModel.selectDiscount(it) },
                            forceExpanded = false,
                            useBottomSheetStyle = true,
                            maxSheetHeight = sheetMaxHeight
                        )
                    }
                }
            }
        }

        // Dialogs & Sheets
        if (uiState.selectedConfiguringItem != null) {
            key(uiState.selectedConfiguringItem!!.id) {
                ProductConfigBottomSheet(
                    item = uiState.selectedConfiguringItem!!,
                    onDismiss = { viewModel.hideConfigurationSheet() },
                    onAddToCart = { variant, flavor -> viewModel.addToCart(variant, flavor) }
                )
            }
        }
        if (uiState.showQueuesDialog) {
            QueuesDialog(heldQueues = uiState.heldQueues, onResume = { viewModel.resumeOrder(it) }, onDismiss = { viewModel.setShowQueuesDialog(false) })
        }
        if (uiState.showPaymentDialog) {
            PaymentCheckoutDialog(
                finalTotal = uiState.total,
                paymentState = uiState.paymentDialogState,
                gcashAccounts = uiState.gcashAccounts,
                isProcessing = uiState.isCheckoutProcessing,
                onPaymentStateChange = { viewModel.setPaymentDialogState(it) },
                onConfirmPayment = { method, ref ->
                    viewModel.confirmCheckout(method, ref)
                },
                onDismiss = { viewModel.setShowPaymentDialog(false) }
            )
        }
        if (uiState.showHoldOrderDialog) {
            HoldOrderDialog(
                onHold = { label -> viewModel.holdCurrentOrder(label) },
                onDismiss = { viewModel.setShowHoldOrderDialog(false) }
            )
        }
        if (uiState.showExpenseDialog) {
            AddExpenseDialog(
                onSave = { desc, amount, by -> viewModel.saveExpense(desc, amount, by) },
                onDismiss = { viewModel.setShowExpenseDialog(false) }
            )
        }
    }
}

@Composable
private fun DashboardHeaderIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp)
    ) {
        FluentIcon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            size = 22.dp
        )
    }
}

@Composable
private fun StorefrontCatalogPane(
    uiState: DashboardUiState,
    hazeState: HazeState,
    headerState: CollapsingHeaderState,
    searchQuery: String,
    isSearchExpanded: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    onCategorySelected: (String) -> Unit,
    onItemClick: (Item) -> Unit,
    modifier: Modifier = Modifier,
    compactGlows: Boolean = false,
    bottomContentPadding: Dp = 0.dp
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .liquidGlassSource(hazeState)
    ) {
        // Columns follow the pane's own width, not the screen's — in the landscape split the
        // catalog only gets the space left of the order panel. ~230dp keeps cards a comfy
        // tap size on the Redmi Pad 2 without stretching on phones.
        val catalogColumns = (maxWidth / 230.dp).toInt().coerceIn(2, 5)
        AdaptiveAmbientGlows(
            modifier = Modifier.fillMaxSize(),
            compactLayout = compactGlows
        )
        val isSearching = searchQuery.isNotEmpty()
        val searchResults = remember(searchQuery, uiState.allMenuItems) {
            val query = searchQuery.trim()
            if (query.isEmpty()) {
                emptyList()
            } else {
                uiState.allMenuItems.mapNotNull { it.toCatalogSearchHit(query) }
            }
        }
        val browseGroupedItems = remember(
            uiState.menuItems,
            uiState.selectedCategoryId,
            uiState.categories
        ) {
            if (uiState.menuItems.isEmpty()) {
                emptyList()
            } else {
                val categoryName = uiState.categories
                    .find { it.id == uiState.selectedCategoryId }
                    ?.name
                    .orEmpty()
                listOf(categoryName to uiState.menuItems)
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            if (isSearchExpanded || isSearching) {
                GlassSearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    placeholder = "Search menu...",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    onClose = {
                        onSearchQueryChange("")
                        onSearchExpandedChange(false)
                    }
                )
            }
            if (!isSearching) {
                CategorySelector(
                    categories = uiState.categories,
                    selectedCategoryId = uiState.selectedCategoryId,
                    onCategorySelected = onCategorySelected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            val catalogListModifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .then(
                    if (isSearchExpanded || isSearching) {
                        Modifier
                    } else {
                        Modifier.collapsingNestedScroll(headerState)
                    }
                )

            LazyColumn(
                modifier = catalogListModifier,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = bottomContentPadding)
            ) {
                if (isSearching) {
                    if (searchResults.isEmpty()) {
                        item(key = "search_empty") {
                            Text(
                                text = "No menu items match your search.",
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(32.dp)
                            )
                        }
                    } else {
                        items(
                            items = searchResults.chunked(catalogColumns),
                            key = { row -> "search_${row.joinToString("_") { it.item.id }}" }
                        ) { rowHits ->
                            CatalogSearchResultRow(
                                hits = rowHits,
                                columns = catalogColumns,
                                lowStockItemIds = uiState.lowStockItemIds,
                                onItemClick = onItemClick
                            )
                        }
                    }
                } else {
                    if (browseGroupedItems.isEmpty()) {
                        item(key = "browse_empty") {
                            Text(
                                text = "No items in this category.",
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(32.dp)
                            )
                        }
                    } else {
                        browseGroupedItems.forEach { (categoryName, categoryItems) ->
                            items(
                                items = categoryItems.chunked(catalogColumns),
                                key = { row -> "browse_${categoryName}_${row.joinToString("_") { it.id }}" }
                            ) { rowProducts ->
                                CatalogProductRow(
                                    products = rowProducts,
                                    columns = catalogColumns,
                                    lowStockItemIds = uiState.lowStockItemIds,
                                    onItemClick = onItemClick
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class CatalogSearchHit(
    val item: Item,
    val matchLabel: String?
)

@Composable
private fun CatalogSearchResultRow(
    hits: List<CatalogSearchHit>,
    columns: Int,
    lowStockItemIds: Set<String>,
    onItemClick: (Item) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        hits.forEach { hit ->
            ItemCard(
                item = hit.item,
                isLowStock = hit.item.id in lowStockItemIds,
                searchMatchLabel = hit.matchLabel,
                onClick = { onItemClick(hit.item) },
                modifier = Modifier.weight(1f)
            )
        }
        // Keep cards their natural width on a short final row instead of stretching.
        repeat(columns - hits.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun CatalogProductRow(
    products: List<Item>,
    columns: Int,
    lowStockItemIds: Set<String>,
    onItemClick: (Item) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        products.forEach { item ->
            ItemCard(
                item = item,
                isLowStock = item.id in lowStockItemIds,
                onClick = { onItemClick(item) },
                modifier = Modifier.weight(1f)
            )
        }
        repeat(columns - products.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

private fun Item.toCatalogSearchHit(query: String): CatalogSearchHit? {
    val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return null

    val flavorMatch = flavors.firstOrNull { flavor ->
        val flavorText = flavor.substringAfter(": ").trim().ifEmpty { flavor }
        tokens.all { token ->
            flavor.contains(token, ignoreCase = true) ||
                flavorText.contains(token, ignoreCase = true)
        }
    }
    if (flavorMatch != null) {
        val label = flavorMatch.substringAfter(": ").trim().ifEmpty { flavorMatch }
        return CatalogSearchHit(this, label)
    }

    val variantMatch = variants.firstOrNull { variant ->
        tokens.all { token -> variant.name.contains(token, ignoreCase = true) }
    }?.name
    if (variantMatch != null) {
        return CatalogSearchHit(this, variantMatch)
    }

    if (tokens.all { token -> name.contains(token, ignoreCase = true) }) {
        return CatalogSearchHit(this, matchLabel = null)
    }

    return null
}

@Composable
private fun DashboardCheckoutPanel(
    uiState: DashboardUiState,
    cartItemCount: Int,
    isCartExpanded: Boolean,
    onCartExpandedChange: (Boolean) -> Unit,
    checkoutBorder: BorderStroke,
    darkTheme: Boolean,
    onHoldOrder: () -> Unit,
    onPlaceOrder: () -> Unit,
    onQuantityChange: (String, Int) -> Unit,
    onSelectDiscount: (DiscountStrategy) -> Unit,
    modifier: Modifier = Modifier,
    forceExpanded: Boolean = false,
    useBottomSheetStyle: Boolean = true,
    onCollapseCart: (() -> Unit)? = null,
    maxSheetHeight: Dp = Dp.Unspecified
) {
    if (useBottomSheetStyle) {
        val cartBarShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        val labelColor = adaptiveGlassContentColor(darkTheme)
        val chevronRotation by animateFloatAsState(
            targetValue = if (isCartExpanded) 180f else 0f,
            animationSpec = iOSSpringSpec,
            label = "cartChevronRotation"
        )
        val performFeedback = rememberPosFeedback()
        val toggleCart: () -> Unit = {
            performFeedback(FeedbackEvent(BionicHaptic.Selection, PosSound.Select))
            onCartExpandedChange(!isCartExpanded)
        }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = iOSSpringSize)
                .clip(cartBarShape)
                .background(adaptiveGlassBrush(darkTheme), cartBarShape)
                .border(checkoutBorder.width, checkoutBorder.brush, cartBarShape)
                .shadow(8.dp, cartBarShape, clip = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = toggleCart),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        AnimatedContent(
                            targetState = cartItemCount,
                            transitionSpec = {
                                fadeIn(animationSpec = iOSSpringSpec) togetherWith fadeOut(animationSpec = iOSSpringSpec)
                            },
                            label = "cartCount"
                        ) { count ->
                            Text(
                                "Current Order ($count)",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = labelColor
                            )
                        }
                        AnimatedVisibility(
                            visible = cartItemCount > 0,
                            enter = expandVertically(animationSpec = iOSSpringSize) + fadeIn(animationSpec = iOSSpringSpec),
                            exit = shrinkVertically(animationSpec = iOSSpringSize) + fadeOut(animationSpec = iOSSpringSpec)
                        ) {
                            Text(
                                "Total: ₱${String.format("%.0f", uiState.total)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = onHoldOrder,
                            enabled = uiState.activeCart.isNotEmpty(),
                            modifier = Modifier.height(40.dp)
                        ) {
                            FluentIcon(
                                imageVector = FluentIcons.Pause,
                                contentDescription = null,
                                size = 14.dp,
                                useGlassGradient = false
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Hold", fontSize = 12.sp)
                        }
                        IconButton(onClick = toggleCart) {
                            FluentIcon(
                                imageVector = FluentIcons.ChevronUp,
                                contentDescription = if (isCartExpanded) {
                                    "Collapse order panel"
                                } else {
                                    "Expand order panel"
                                },
                                useGlassGradient = false,
                                modifier = Modifier.graphicsLayer { rotationZ = chevronRotation }
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = isCartExpanded,
                    enter = expandVertically(animationSpec = iOSSpringSize) + fadeIn(animationSpec = iOSSpringSpec),
                    exit = shrinkVertically(animationSpec = iOSSpringSize) + fadeOut(animationSpec = iOSSpringSpec)
                ) {
                    // Cap the expanded order to ~half the screen and scroll within it, so the
                    // menu stays visible above and Place Order is always reachable. A single
                    // scroll here (list itself unbounded) avoids nested-scroll conflicts.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (maxSheetHeight != Dp.Unspecified) {
                                    Modifier.heightIn(max = maxSheetHeight)
                                } else {
                                    Modifier
                                }
                            )
                            .verticalScroll(rememberScrollState())
                    ) {
                        DashboardCheckoutBody(
                            uiState = uiState,
                            onQuantityChange = onQuantityChange,
                            onSelectDiscount = onSelectDiscount,
                            onPlaceOrder = onPlaceOrder,
                            listMaxHeight = null
                        )
                    }
                }
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onCollapseCart != null) {
                        IconButton(
                            onClick = onCollapseCart,
                            modifier = Modifier.size(44.dp)
                        ) {
                            FluentIcon(
                                imageVector = FluentIcons.Close,
                                contentDescription = "Collapse Cart",
                                useGlassGradient = false,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        "Current Order ($cartItemCount)",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                TextButton(
                    onClick = onHoldOrder,
                    enabled = uiState.activeCart.isNotEmpty(),
                    modifier = Modifier.height(40.dp)
                ) {
                    FluentIcon(
                        imageVector = FluentIcons.Pause,
                        contentDescription = null,
                        size = 14.dp,
                        useGlassGradient = false
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Hold", fontSize = 12.sp)
                }
            }
            DashboardCheckoutBody(
                uiState = uiState,
                onQuantityChange = onQuantityChange,
                onSelectDiscount = onSelectDiscount,
                onPlaceOrder = onPlaceOrder,
                listMaxHeight = null
            )
        }
    }
}

@Composable
private fun DashboardCheckoutBody(
    uiState: DashboardUiState,
    onQuantityChange: (String, Int) -> Unit,
    onSelectDiscount: (DiscountStrategy) -> Unit,
    onPlaceOrder: () -> Unit,
    listMaxHeight: Dp? = 150.dp
) {
    val bodyColor = adaptiveGlassContentColor()
    val mutedColor = adaptiveBodyMuted()
    Spacer(modifier = Modifier.height(8.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (listMaxHeight != null) {
                    Modifier.heightIn(max = listMaxHeight).verticalScroll(rememberScrollState())
                } else {
                    Modifier
                }
            ),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (uiState.activeCart.isEmpty()) {
            Text(
                "No items yet",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                color = mutedColor,
                textAlign = TextAlign.Center
            )
        } else {
            uiState.activeCart.forEach { cartItem ->
                CartItemRow(
                    cartItem = cartItem,
                    onQuantityChange = onQuantityChange
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "Subtotal: ₱${String.format("%.0f", uiState.subtotal)}",
                fontSize = 12.sp,
                color = mutedColor
            )
            if (uiState.discountDeduction > 0) {
                Text(
                    "Disc (${uiState.discountLabel}): -₱${String.format("%.0f", uiState.discountDeduction)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Total: ", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = bodyColor)
            Text(
                "₱${String.format("%.0f", uiState.total)}",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DiscountButton("None", uiState.selectedDiscountStrategy is NoDiscountStrategy, { onSelectDiscount(NoDiscountStrategy()) }, Modifier.weight(1f))
        DiscountButton("5%", uiState.selectedDiscountStrategy is FivePercentDiscountStrategy, { onSelectDiscount(FivePercentDiscountStrategy()) }, Modifier.weight(1f))
        DiscountButton("10%", uiState.selectedDiscountStrategy is PercentageDiscountStrategy && (uiState.selectedDiscountStrategy as PercentageDiscountStrategy).pct == 10.0, { onSelectDiscount(PercentageDiscountStrategy(10.0)) }, Modifier.weight(1f))
        DiscountButton("20%", uiState.selectedDiscountStrategy is PercentageDiscountStrategy && (uiState.selectedDiscountStrategy as PercentageDiscountStrategy).pct == 20.0, { onSelectDiscount(PercentageDiscountStrategy(20.0)) }, Modifier.weight(1f))
        DiscountButton("Free", uiState.selectedDiscountStrategy is FreeOrderDiscountStrategy, { onSelectDiscount(FreeOrderDiscountStrategy()) }, Modifier.weight(1.2f))
    }
    Spacer(modifier = Modifier.height(12.dp))
    val btnInteractionSource = remember { MutableInteractionSource() }
    val btnIsPressed by btnInteractionSource.collectIsPressedAsState()
    val btnScale by animateFloatAsState(
        targetValue = if (btnIsPressed) 0.96f else 1f,
        animationSpec = iOSSpringSpec,
        label = "btnScale"
    )
    Button(
        onClick = onPlaceOrder,
        interactionSource = btnInteractionSource,
        enabled = uiState.activeCart.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().scale(btnScale),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = "Place Order",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}

/**
 * Wide-screen (tablet) order panel. Fills the fixed bottom slot it is given, so its
 * background always reaches the screen edge — no dead space below it. Header is fixed, the
 * item list scrolls in the middle, and the footer (totals + discounts + Place Order) is
 * pinned to the bottom edge.
 */
@Composable
private fun TabletOrderPanel(
    uiState: DashboardUiState,
    cartItemCount: Int,
    checkoutBorder: BorderStroke,
    darkTheme: Boolean,
    onHoldOrder: () -> Unit,
    onPlaceOrder: () -> Unit,
    onQuantityChange: (String, Int) -> Unit,
    onSelectDiscount: (DiscountStrategy) -> Unit,
    modifier: Modifier = Modifier,
    panelShape: RoundedCornerShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
) {
    val labelColor = adaptiveGlassContentColor(darkTheme)
    val bodyColor = adaptiveGlassContentColor()
    val mutedColor = adaptiveBodyMuted()
    Column(
        modifier = modifier
            .clip(panelShape)
            .background(adaptiveGlassBrush(darkTheme), panelShape)
            .border(checkoutBorder.width, checkoutBorder.brush, panelShape)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Header (fixed)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Current Order ($cartItemCount)",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = labelColor
                )
                if (cartItemCount > 0) {
                    Text(
                        "Total: ₱${String.format("%.0f", uiState.total)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            TextButton(
                onClick = onHoldOrder,
                enabled = uiState.activeCart.isNotEmpty(),
                modifier = Modifier.height(40.dp)
            ) {
                FluentIcon(
                    imageVector = FluentIcons.Pause,
                    contentDescription = null,
                    size = 14.dp,
                    useGlassGradient = false
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Hold", fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Item list (fills the middle, scrolls)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (uiState.activeCart.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No items yet", color = mutedColor)
                }
            } else {
                uiState.activeCart.forEach { cartItem ->
                    CartItemRow(cartItem = cartItem, onQuantityChange = onQuantityChange)
                }
            }
        }
        // Footer (pinned)
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Subtotal: ₱${String.format("%.0f", uiState.subtotal)}",
                    fontSize = 12.sp,
                    color = mutedColor
                )
                if (uiState.discountDeduction > 0) {
                    Text(
                        "Disc (${uiState.discountLabel}): -₱${String.format("%.0f", uiState.discountDeduction)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Total: ", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = bodyColor)
                Text(
                    "₱${String.format("%.0f", uiState.total)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DiscountButton("None", uiState.selectedDiscountStrategy is NoDiscountStrategy, { onSelectDiscount(NoDiscountStrategy()) }, Modifier.weight(1f))
            DiscountButton("5%", uiState.selectedDiscountStrategy is FivePercentDiscountStrategy, { onSelectDiscount(FivePercentDiscountStrategy()) }, Modifier.weight(1f))
            DiscountButton("10%", uiState.selectedDiscountStrategy is PercentageDiscountStrategy && (uiState.selectedDiscountStrategy as PercentageDiscountStrategy).pct == 10.0, { onSelectDiscount(PercentageDiscountStrategy(10.0)) }, Modifier.weight(1f))
            DiscountButton("20%", uiState.selectedDiscountStrategy is PercentageDiscountStrategy && (uiState.selectedDiscountStrategy as PercentageDiscountStrategy).pct == 20.0, { onSelectDiscount(PercentageDiscountStrategy(20.0)) }, Modifier.weight(1f))
            DiscountButton("Free", uiState.selectedDiscountStrategy is FreeOrderDiscountStrategy, { onSelectDiscount(FreeOrderDiscountStrategy()) }, Modifier.weight(1.2f))
        }
        Spacer(modifier = Modifier.height(12.dp))
        val btnInteractionSource = remember { MutableInteractionSource() }
        val btnIsPressed by btnInteractionSource.collectIsPressedAsState()
        val btnScale by animateFloatAsState(
            targetValue = if (btnIsPressed) 0.96f else 1f,
            animationSpec = iOSSpringSpec,
            label = "tabletBtnScale"
        )
        Button(
            onClick = onPlaceOrder,
            interactionSource = btnInteractionSource,
            enabled = uiState.activeCart.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().scale(btnScale),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Place Order",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Shared Components
// ─────────────────────────────────────────────────────────────
@Composable
fun CategorySelector(categories: List<com.example.cattasticpos.domain.model.Category>, selectedCategoryId: String, onCategorySelected: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier) {
        items(categories, key = { it.id }) { category ->
            PosFilterChip(
                selected = category.id == selectedCategoryId,
                onClick = { onCategorySelected(category.id) },
                label = category.name,
                icon = FluentIcons.categoryIcon(category.id)
            )
        }
    }
}

@Composable
fun ItemCard(
    item: Item,
    isLowStock: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    searchMatchLabel: String? = null
) {
    val performFeedback = rememberPosFeedback()

    ObsidianGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .height(128.dp),
        onClick = {
            performFeedback(FeedbackEvent(BionicHaptic.Selection, PosSound.Tap))
            onClick()
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                PosIconBadge(
                    imageVector = FluentIcons.menuItemIcon(item.id),
                    contentDescription = null,
                    emphasized = false,
                    error = isLowStock,
                    iconSize = PosIconSize.Medium
                )
                Column {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                    if (!searchMatchLabel.isNullOrBlank()) {
                        Text(
                            text = searchMatchLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "₱${String.format("%.0f", item.startingPrice)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            if (isLowStock) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(Color(0xFFFF6B6B).copy(alpha = 0.2f), RoundedCornerShape(bottomStart = 12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Low Stock", color = Color(0xFFFF8A8A), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CartItemRow(cartItem: CartItem, onQuantityChange: (String, Int) -> Unit) {
    val lineColor = adaptiveGlassContentColor()
    val performFeedback = rememberPosFeedback()
    ObsidianGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val selection = CartLineSelection.parse(cartItem.flavor, cartItem.item.id)
                val flavorLabel = selection.baseFlavor?.substringAfter(": ")?.trim()
                    ?: selection.baseFlavor
                val addOnText = selection.addOnLabels.takeIf { it.isNotEmpty() }?.joinToString(", ")
                val variantFlavorText = when {
                    flavorLabel.isNullOrBlank() && addOnText.isNullOrBlank() -> cartItem.variant.name
                    addOnText.isNullOrBlank() -> "${cartItem.variant.name}/$flavorLabel"
                    flavorLabel.isNullOrBlank() -> "${cartItem.variant.name}/+$addOnText"
                    else -> "${cartItem.variant.name}/$flavorLabel + $addOnText"
                }
                Text(
                    "${cartItem.quantity}x ${cartItem.item.name} ($variantFlavorText) - ₱${String.format("%.0f", cartItem.totalPrice)}",
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = lineColor,
                    lineHeight = 16.sp
                )
            }
            // Quantity steppers: 44.dp touch targets (Fitts's Law — was 24.dp, half the
            // 48.dp minimum) with haptic + audio feedback so every tap confirms itself.
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        performFeedback(FeedbackEvent(BionicHaptic.Light, PosSound.Tap))
                        onQuantityChange(cartItem.id, -1)
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    FluentIcon(imageVector = FluentIcons.Subtract, contentDescription = "Decrease quantity", size = 18.dp, useGlassGradient = false)
                }
                IconButton(
                    onClick = {
                        performFeedback(FeedbackEvent(BionicHaptic.Light, PosSound.Tap))
                        onQuantityChange(cartItem.id, 1)
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    FluentIcon(imageVector = FluentIcons.Add, contentDescription = "Increase quantity", tint = MaterialTheme.colorScheme.primary, size = 18.dp)
                }
                // Separate the destructive "remove line" action from the +/- steppers so a
                // mis-tap can't wipe a line item (error prevention — Norman's constraints).
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .height(24.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                )
                IconButton(
                    onClick = {
                        // Snap haptic gives the removal a distinct, stronger tactile cue, but
                        // keep a neutral Tap sound — Error is the app's failure chime and this
                        // is a deliberate, valid action, not a failure.
                        performFeedback(FeedbackEvent(BionicHaptic.Snap, PosSound.Tap))
                        onQuantityChange(cartItem.id, -cartItem.quantity)
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    FluentIcon(imageVector = FluentIcons.Delete, contentDescription = "Remove item", tint = MaterialTheme.colorScheme.error, size = 18.dp)
                }
            }
        }
    }
}

@Composable
private fun QuickTenderButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.height(40.dp)
    ) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

@Composable
fun DiscountButton(label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary),
        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.height(40.dp)
    ) { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentCheckoutDialog(
    finalTotal: Double,
    paymentState: PaymentDialogState,
    gcashAccounts: List<com.example.cattasticpos.domain.model.GcashAccount>,
    isProcessing: Boolean = false,
    onPaymentStateChange: (PaymentDialogState) -> Unit,
    onConfirmPayment: (String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var simDropdownExpanded by remember { mutableStateOf(false) }
    val simOptions = gcashAccounts.map { it.label }

    val amountTendered = paymentState.amountTenderedStr.toDoubleOrNull() ?: 0.0
    val changeDue = amountTendered - finalTotal
    val isCash = paymentState.selectedTabIndex == 0
    val isReady = if (isCash) amountTendered >= finalTotal else paymentState.receivingAccount.isNotBlank()

    AdaptiveGlassDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        surfaceAlpha = 0.93f,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Payment Checkout", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "₱${String.format("%.0f", finalTotal)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isProcessing) { Text("Cancel") }
        },
        confirmButton = {
            PosPrimaryButton(
                onClick = {
                    if (isCash) {
                        onConfirmPayment("CASH", null)
                    } else {
                        val ref = buildString {
                            append("account=${paymentState.receivingAccount}")
                            if (paymentState.gcashReference.isNotBlank()) {
                                append("|ref=${paymentState.gcashReference.trim()}")
                            }
                        }
                        onConfirmPayment("GCASH", ref)
                    }
                },
                enabled = isReady && !isProcessing
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Confirm & Pay",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        content = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Order Type",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CupertinoSegmentChip(
                        selected = paymentState.serviceType == OrderServiceType.DINE_IN,
                        onClick = { onPaymentStateChange(paymentState.copy(serviceType = OrderServiceType.DINE_IN)) },
                        label = "Dine In",
                        icon = FluentIcons.Utensils,
                        modifier = Modifier.weight(1f)
                    )
                    CupertinoSegmentChip(
                        selected = paymentState.serviceType == OrderServiceType.TAKE_OUT,
                        onClick = {
                            onPaymentStateChange(
                                paymentState.copy(
                                    serviceType = OrderServiceType.TAKE_OUT,
                                    tableNumber = ""
                                )
                            )
                        },
                        label = "Take Out",
                        icon = FluentIcons.ShoppingBag,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (paymentState.serviceType == OrderServiceType.DINE_IN) {
                    OutlinedTextField(
                        value = paymentState.tableNumber,
                        onValueChange = { onPaymentStateChange(paymentState.copy(tableNumber = it)) },
                        label = { Text("Name (optional)") },
                        placeholder = { Text("e.g. Juan") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    "Total Due",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CupertinoSegmentChip(
                        selected = paymentState.selectedTabIndex == 0,
                        onClick = { onPaymentStateChange(paymentState.copy(selectedTabIndex = 0)) },
                        label = "Cash",
                        icon = FluentIcons.Wallet,
                        modifier = Modifier.weight(1f)
                    )
                    CupertinoSegmentChip(
                        selected = paymentState.selectedTabIndex == 1,
                        onClick = { onPaymentStateChange(paymentState.copy(selectedTabIndex = 1)) },
                        label = "GCash",
                        icon = FluentIcons.Receipt,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (isCash) {
                    OutlinedTextField(
                        value = paymentState.amountTenderedStr,
                        onValueChange = { onPaymentStateChange(paymentState.copy(amountTenderedStr = it)) },
                        label = { Text("Amount Tendered (₱)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    // One-tap tender for the common cases — exact payment or a single bill —
                    // so the cashier types nothing at the busiest moment of the flow.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        QuickTenderButton(
                            label = "Exact",
                            onClick = {
                                onPaymentStateChange(
                                    paymentState.copy(amountTenderedStr = String.format("%.0f", finalTotal))
                                )
                            },
                            modifier = Modifier.weight(1.2f)
                        )
                        listOf(100, 200, 500, 1000).forEach { bill ->
                            QuickTenderButton(
                                label = "₱$bill",
                                onClick = {
                                    onPaymentStateChange(
                                        paymentState.copy(amountTenderedStr = bill.toString())
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    AdaptiveGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Change Due", fontWeight = FontWeight.Medium)
                            Text(
                                if (changeDue >= 0) "₱${String.format("%.0f", changeDue)}" else "---",
                                fontWeight = FontWeight.Bold,
                                color = if (changeDue >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                } else {
                    ExposedDropdownMenuBox(expanded = simDropdownExpanded, onExpandedChange = { simDropdownExpanded = it }) {
                        OutlinedTextField(
                            value = paymentState.receivingAccount,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Receiving SIM") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = simDropdownExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(expanded = simDropdownExpanded, onDismissRequest = { simDropdownExpanded = false }) {
                            simOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        onPaymentStateChange(paymentState.copy(receivingAccount = option))
                                        simDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = paymentState.gcashReference,
                        onValueChange = { onPaymentStateChange(paymentState.copy(gcashReference = it)) },
                        label = { Text("GCash Reference No. (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }
    )
}

private enum class ProductConfigStep {
    FlavorGroup,
    Flavor,
    Size,
    AddOns,
    CoffeeOption
}

private val COFFEE_DRINK_IDS = setOf(
    "drink_cat_feine",
    "drink_oreo",
    "drink_matcha",
    "drink_coffee"
)

private fun isCoffeeItem(item: Item): Boolean = item.id in COFFEE_DRINK_IDS

private fun hasAddOnStep(item: Item): Boolean = ProductAddOnCatalog.supportsAddOns(item)

private fun buildCartFlavor(
    itemId: String,
    baseFlavor: String?,
    coffeeOption: String?,
    addOnIds: List<String>
): String? = CartLineSelection.encode(baseFlavor, coffeeOption, addOnIds, itemId)

private fun itemHasGroupedFlavors(item: Item): Boolean =
    item.flavors.any { it.contains(":") }

private fun itemFlavorGroups(item: Item): Map<String, List<String>> =
    item.flavors.groupBy { flavor ->
        if (flavor.contains(":")) flavor.substringBefore(":").trim() else "Flavors"
    }

private fun initialProductConfigStep(item: Item): ProductConfigStep = when {
    itemHasGroupedFlavors(item) -> ProductConfigStep.FlavorGroup
    item.flavors.isNotEmpty() -> ProductConfigStep.Flavor
    item.variants.size > 1 -> ProductConfigStep.Size
    hasAddOnStep(item) -> ProductConfigStep.AddOns
    isCoffeeItem(item) -> ProductConfigStep.CoffeeOption
    else -> ProductConfigStep.Size
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductConfigBottomSheet(item: Item, onDismiss: () -> Unit, onAddToCart: (Variant, String?) -> Unit) {
    val performFeedback = rememberPosFeedback()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentStep by remember(item.id) { mutableStateOf(initialProductConfigStep(item)) }
    var selectedFlavorGroup by remember(item.id) { mutableStateOf<String?>(null) }
    var selectedVariant by remember(item.id) {
        mutableStateOf<Variant?>(if (item.variants.size == 1) item.variants.firstOrNull() else null)
    }
    var selectedFlavor by remember(item.id) { mutableStateOf<String?>(null) }
    var selectedCoffeeOption by remember(item.id) { mutableStateOf<String?>(null) }
    // Optional extras stay unselected until the cashier taps them. Take-out Box is no longer
    // among them — it is its own menu item under the Take-out Box category.
    var selectedAddOnIds by remember(item.id) { mutableStateOf<List<String>>(emptyList()) }
    val isCoffee = isCoffeeItem(item)
    val hasAddOns = hasAddOnStep(item)
    val hasComboDescriptions = item.variants.any { !it.description.isNullOrBlank() }
    val showCheckoutFooter = when {
        isCoffee -> currentStep == ProductConfigStep.CoffeeOption
        hasAddOns -> currentStep == ProductConfigStep.AddOns
        else -> currentStep == ProductConfigStep.Size
    }
    val displayPrice = run {
        val variant = selectedVariant ?: return@run 0.0
        val base = if (selectedFlavor == null && variant.basePrice == 0.0) {
            0.0
        } else {
            try {
                variant.getPrice(selectedFlavor)
            } catch (_: Exception) {
                0.0
            }
        }
        base + ProductAddOnCatalog.surcharge(item, selectedAddOnIds)
    }
    val scrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val maxBodyHeight = (configuration.screenHeightDp * 0.72f).dp

    LaunchedEffect(currentStep, item.id) {
        scrollState.scrollTo(0)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxBodyHeight)
                    .verticalScroll(scrollState)
            ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    liquidSwipeTransition(forward = targetState.ordinal > initialState.ordinal)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds(),
                label = "ProductConfigStep"
            ) { step ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (step) {
                        ProductConfigStep.FlavorGroup -> {
                            ProductConfigStepHeader(
                                title = item.name,
                                subtitle = "Choose a style"
                            )
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemFlavorGroups(item).forEach { (group, _) ->
                                    key(group) {
                                        FlavorOptionRow(
                                            label = group,
                                            isSelected = selectedFlavorGroup == group,
                                            leadingIcon = FluentIcons.menuItemIcon(item.id),
                                            onSelect = {
                                                selectedFlavorGroup = group
                                                selectedFlavor = null
                                                currentStep = ProductConfigStep.Flavor
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        ProductConfigStep.Flavor -> {
                            val flavorsForStep = if (selectedFlavorGroup != null) {
                                itemFlavorGroups(item)[selectedFlavorGroup].orEmpty()
                            } else {
                                item.flavors
                            }
                            ProductConfigStepHeader(
                                title = item.name,
                                subtitle = selectedFlavorGroup ?: "Choose a flavor",
                                onBack = {
                                    if (itemHasGroupedFlavors(item)) {
                                        selectedFlavor = null
                                        selectedFlavorGroup = null
                                        currentStep = ProductConfigStep.FlavorGroup
                                    } else {
                                        onDismiss()
                                    }
                                }
                            )
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                flavorsForStep.forEach { flavor ->
                                    key(flavor) {
                                        FlavorOptionRow(
                                            label = flavor.substringAfter(": ").trim(),
                                            isSelected = selectedFlavor == flavor,
                                            leadingIcon = FluentIcons.menuItemIcon(item.id),
                                            onSelect = {
                                                selectedFlavor = flavor
                                                selectedVariant = if (item.variants.size == 1) item.variants.firstOrNull() else null
                                                currentStep = when {
                                                    item.variants.size > 1 -> ProductConfigStep.Size
                                                    isCoffee -> ProductConfigStep.CoffeeOption
                                                    hasAddOns -> ProductConfigStep.AddOns
                                                    else -> ProductConfigStep.Size
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Tap a flavor to continue",
                                modifier = Modifier.fillMaxWidth(),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }

                        ProductConfigStep.Size -> {
                            ProductConfigStepHeader(
                                title = item.name,
                                subtitle = selectedFlavor?.substringAfter(": ")?.trim()
                                    ?: if (item.flavors.isEmpty()) "Choose an option" else "Choose a size",
                                onBack = {
                                    if (item.flavors.isNotEmpty()) {
                                        selectedFlavor = null
                                        selectedVariant = null
                                        selectedCoffeeOption = null
                                        currentStep = ProductConfigStep.Flavor
                                    } else {
                                        onDismiss()
                                    }
                                }
                            )
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item.variants.forEach { variant ->
                                    key(variant.id) {
                                        VariantOptionRow(
                                            variant = variant,
                                            item = item,
                                            selectedFlavor = selectedFlavor,
                                            isSelected = selectedVariant?.id == variant.id,
                                            onSelect = {
                                                selectedVariant = variant
                                                selectedCoffeeOption = null
                                                selectedAddOnIds = emptyList()
                                                currentStep = when {
                                                    isCoffee -> ProductConfigStep.CoffeeOption
                                                    hasAddOns -> ProductConfigStep.AddOns
                                                    else -> ProductConfigStep.Size
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            if (hasComboDescriptions) {
                                Spacer(modifier = Modifier.height(12.dp))
                                val panelTextColor = adaptiveGlassContentColor()
                                AdaptiveGlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            "Included in this combo:",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = panelTextColor
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = selectedVariant?.description ?: "Select an option to view combo details.",
                                            fontSize = 12.sp,
                                            color = panelTextColor,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }

                        ProductConfigStep.AddOns -> {
                            val addOnOptions = ProductAddOnCatalog.addOnsForItem(item)
                            ProductConfigStepHeader(
                                title = item.name,
                                subtitle = listOfNotNull(
                                    selectedFlavor?.substringAfter(": ")?.trim() ?: selectedFlavor,
                                    selectedVariant?.name
                                ).joinToString(" · ").ifBlank { "Add-ons (optional)" },
                                onBack = {
                                    selectedAddOnIds = emptyList()
                                    currentStep = if (item.flavors.isNotEmpty() && item.variants.size <= 1) {
                                        ProductConfigStep.Flavor
                                    } else {
                                        ProductConfigStep.Size
                                    }
                                }
                            )
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                addOnOptions.forEach { addOn ->
                                    key(addOn.id) {
                                        val isSelected = selectedAddOnIds.contains(addOn.id)
                                        FlavorOptionRow(
                                            label = "${addOn.label} (+₱${String.format("%.0f", addOn.price)})",
                                            isSelected = isSelected,
                                            onSelect = {
                                                selectedAddOnIds = if (ProductAddOnCatalog.allowsMultiple(item)) {
                                                    if (isSelected) {
                                                        selectedAddOnIds - addOn.id
                                                    } else {
                                                        selectedAddOnIds + addOn.id
                                                    }
                                                } else {
                                                    if (isSelected) emptyList() else listOf(addOn.id)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Tap to add optional extras, or add to order below",
                                modifier = Modifier.fillMaxWidth(),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }

                        ProductConfigStep.CoffeeOption -> {
                            ProductConfigStepHeader(
                                title = item.name,
                                subtitle = listOfNotNull(
                                    selectedFlavor?.substringAfter(": ")?.trim(),
                                    selectedVariant?.name
                                ).joinToString(" · ").ifBlank { "Coffee option" },
                                onBack = {
                                    selectedCoffeeOption = null
                                    currentStep = ProductConfigStep.Size
                                }
                            )
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("With Coffee", "Without Coffee").forEach { option ->
                                    key(option) {
                                        FlavorOptionRow(
                                            label = option,
                                            isSelected = selectedCoffeeOption == option,
                                            onSelect = { selectedCoffeeOption = option }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Tap an option to continue",
                                modifier = Modifier.fillMaxWidth(),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            }

            Crossfade(
                targetState = showCheckoutFooter,
                animationSpec = tween(durationMillis = 140),
                label = "ProductConfigFooter"
            ) { showCheckoutFooter ->
                if (showCheckoutFooter) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, bottom = 24.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Price Summary", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                Text(
                                    "₱${String.format("%.0f", displayPrice)}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            PosPrimaryButton(
                                onClick = {
                                    val variant = selectedVariant ?: return@PosPrimaryButton
                                    performFeedback(FeedbackEvent(BionicHaptic.Add, PosSound.AddToCart))
                                    onAddToCart(
                                        variant,
                                        buildCartFlavor(item.id, selectedFlavor, selectedCoffeeOption, selectedAddOnIds)
                                    )
                                },
                                enabled = selectedVariant != null &&
                                    !(item.flavors.isNotEmpty() && selectedFlavor == null) &&
                                    (!isCoffee || selectedCoffeeOption != null),
                                modifier = Modifier.defaultMinSize(minWidth = 0.dp)
                            ) {
                                PosButtonIconLabel(
                                    icon = {
                                        FluentIcon(
                                            imageVector = FluentIcons.Add,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            size = 24.dp
                                        )
                                    },
                                    label = "Add to Order",
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ProductConfigStepHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                FluentIcon(
                    imageVector = FluentIcons.ArrowLeft,
                    contentDescription = "Back",
                    useGlassGradient = false
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (onBack == null) 0.dp else 4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun FlavorOptionRow(
    label: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    SelectableOptionRow(
        label = label,
        isSelected = isSelected,
        onSelect = onSelect,
        leadingIcon = leadingIcon
    )
}

@Composable
private fun VariantOptionRow(
    variant: Variant,
    item: Item,
    selectedFlavor: String?,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val priceLabel = formatVariantPriceLabel(variant, item, selectedFlavor)
    SelectableOptionRow(
        label = variant.name,
        isSelected = isSelected,
        onSelect = onSelect,
        leadingIcon = FluentIcons.menuItemIcon(item.id),
        trailing = priceLabel
    )
}

private fun formatVariantPriceLabel(variant: Variant, item: Item, selectedFlavor: String?): String {
    if (item.flavors.isNotEmpty() && selectedFlavor == null && variant.priceByFlavor.isNotEmpty()) {
        return "Select flavor"
    }
    val price = try {
        variant.getPrice(selectedFlavor)
    } catch (_: Exception) {
        return "—"
    }
    return "₱${String.format("%.0f", price)}"
}

@Composable
fun QueuesDialog(heldQueues: List<HeldQueue>, onResume: (String) -> Unit, onDismiss: () -> Unit) {
    val panelTextColor = adaptiveGlassContentColor()
    val mutedTextColor = adaptiveBodyMuted()
    AdaptiveGlassDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FluentIcon(
                    imageVector = FluentIcons.Queue,
                    contentDescription = null,
                    size = 20.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Held Orders Queue", fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        content = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                if (heldQueues.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No held orders in queue.", color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        items(heldQueues, key = { it.id }) { queue ->
                            AdaptiveGlassCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        val labelText = queue.tableLabel?.let { " [$it]" } ?: ""
                                        Text(
                                            "Queue #${queue.id}$labelText",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = panelTextColor
                                        )
                                        val timeStr = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                                            .format(java.util.Date(queue.timestamp))
                                        Text(
                                            "Held at: $timeStr",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            "${queue.items.sumOf { it.quantity }} items • ₱${String.format("%.0f", queue.items.sumOf { it.totalPrice })}",
                                            fontSize = 12.sp,
                                            color = mutedTextColor
                                        )
                                    }
                                    PosPrimaryButton(
                                        onClick = { onResume(queue.id) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "Resume",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun HoldOrderDialog(onHold: (String?) -> Unit, onDismiss: () -> Unit) {
    var tableLabel by remember { mutableStateOf("") }

    AdaptiveGlassDialog(
        onDismissRequest = onDismiss,
        surfaceAlpha = 0.93f,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FluentIcon(
                    imageVector = FluentIcons.Pause,
                    contentDescription = null,
                    size = 20.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Hold Order", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            PosPrimaryButton(onClick = { onHold(tableLabel.takeIf { it.isNotBlank() }) }) {
                PosButtonIconLabel(
                    icon = {
                        FluentIcon(
                            imageVector = FluentIcons.Pause,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            size = 18.dp
                        )
                    },
                    label = "Hold Order",
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Park this order and resume it later from the queue.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = tableLabel,
                    onValueChange = { tableLabel = it },
                    label = { Text("Name (Optional)") },
                    placeholder = { Text("e.g. Juan") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

@Composable
fun AddExpenseDialog(onSave: (String, Double, String) -> Unit, onDismiss: () -> Unit) {
    var description by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var recordedBy by remember { mutableStateOf("") }
    val amount = amountStr.toDoubleOrNull()
    val isReady = description.isNotBlank() && amount != null && amount > 0 && recordedBy.isNotBlank()

    AdaptiveGlassDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Expense (from Cash Drawer)", fontWeight = FontWeight.Bold) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            Button(
                onClick = { if (isReady) onSave(description, amount!!, recordedBy) },
                enabled = isReady
            ) { Text("Save Expense") }
        },
        content = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description (e.g. Supplies: Ice)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = amountStr, onValueChange = { amountStr = it }, label = { Text("Amount (₱)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = recordedBy, onValueChange = { recordedBy = it }, label = { Text("Recorded By (Name)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }
    )
}



