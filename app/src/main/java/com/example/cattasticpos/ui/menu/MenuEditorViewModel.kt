package com.example.cattasticpos.ui.menu

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.cattasticpos.CattasticPosApp
import com.example.cattasticpos.domain.model.Category
import com.example.cattasticpos.domain.model.Item
import com.example.cattasticpos.domain.model.Variant
import com.example.cattasticpos.domain.repository.MenuRepository
import com.example.cattasticpos.domain.repository.RecipeRepository
import com.example.cattasticpos.ui.icons.FluentIcons
import com.example.cattasticpos.worker.SyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MenuEditorUiState(
    val categories: List<Category> = emptyList(),
    val items: List<Item> = emptyList(),
    val selectedCategoryFilterId: String? = null,
    val searchQuery: String = "",
    val snackbarMessage: String? = null
) {
    val filteredItems: List<Item>
        get() = items.filter { item ->
            val matchesCategory = selectedCategoryFilterId == null || item.categoryId == selectedCategoryFilterId
            val q = searchQuery.trim()
            val matchesSearch = q.isEmpty() ||
                item.name.contains(q, ignoreCase = true) ||
                item.flavors.any { it.contains(q, ignoreCase = true) }
            matchesCategory && matchesSearch
        }
}

class MenuEditorViewModel(
    private val appContext: Context,
    private val menuRepository: MenuRepository,
    private val recipeRepository: RecipeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MenuEditorUiState())
    val uiState: StateFlow<MenuEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            menuRepository.getCategories().collect { categories ->
                _uiState.update { state ->
                    val filterStillValid = state.selectedCategoryFilterId == null ||
                        categories.any { it.id == state.selectedCategoryFilterId }
                    state.copy(
                        categories = categories,
                        selectedCategoryFilterId = if (filterStillValid) state.selectedCategoryFilterId else null
                    )
                }
            }
        }
        viewModelScope.launch {
            menuRepository.getItems().collect { items ->
                _uiState.update { it.copy(items = items) }
            }
        }
    }

    fun selectCategoryFilter(categoryId: String?) {
        _uiState.update { it.copy(selectedCategoryFilterId = categoryId) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun clearSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    /** Saves a category and returns its id, or null when the name is empty. */
    fun saveCategory(existingId: String?, name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(snackbarMessage = "Category name cannot be empty") }
            return null
        }
        val existingIds = _uiState.value.categories.map { it.id }.toSet()
        val categoryId = existingId ?: generateUniqueSlugId("cat", trimmed, existingIds)
        viewModelScope.launch {
            menuRepository.upsertCategory(Category(id = categoryId, name = trimmed))
            SyncWorker.triggerImmediateSync(appContext)
            _uiState.update {
                it.copy(snackbarMessage = if (existingId == null) "Added category \"$trimmed\"" else "Updated category \"$trimmed\"")
            }
        }
        return categoryId
    }

    fun deleteCategory(category: Category) {
        val dependentCount = _uiState.value.items.count { it.categoryId == category.id }
        if (dependentCount > 0) {
            _uiState.update {
                it.copy(snackbarMessage = "Move or delete $dependentCount product(s) in \"${category.name}\" before deleting this category")
            }
            return
        }
        viewModelScope.launch {
            menuRepository.deleteCategory(category.id)
            SyncWorker.deleteRemoteCatalogRow(appContext, "categories", category.id)
            SyncWorker.triggerImmediateSync(appContext)
            _uiState.update {
                it.copy(
                    selectedCategoryFilterId = if (it.selectedCategoryFilterId == category.id) null else it.selectedCategoryFilterId,
                    snackbarMessage = "Deleted category \"${category.name}\""
                )
            }
        }
    }

    fun toggleItemAvailability(item: Item, isAvailable: Boolean) {
        viewModelScope.launch {
            menuRepository.upsertItem(item.copy(isAvailable = isAvailable))
            SyncWorker.triggerImmediateSync(appContext)
            _uiState.update {
                it.copy(snackbarMessage = "${item.name} marked ${if (isAvailable) "available" else "sold out"}")
            }
        }
    }

    fun saveItem(
        existingId: String?,
        name: String,
        categoryId: String,
        icon: String?,
        isAvailable: Boolean,
        flavors: List<String>,
        variants: List<Variant>,
        onSuccess: () -> Unit = {}
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            _uiState.update { it.copy(snackbarMessage = "Product name cannot be empty") }
            return
        }
        if (categoryId.isBlank()) {
            _uiState.update { it.copy(snackbarMessage = "Please select a category") }
            return
        }
        val cleanVariants = variants
            .map { v ->
                v.copy(
                    name = v.name.trim(),
                    description = v.description?.trim()?.takeIf { it.isNotEmpty() }
                )
            }
            .filter { it.name.isNotEmpty() }
        if (cleanVariants.isEmpty()) {
            _uiState.update { it.copy(snackbarMessage = "Add at least one size / variant with a price") }
            return
        }
        val cleanFlavors = flavors.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val sanitizedIcon = icon?.trim()?.takeIf { key ->
            key.isNotEmpty() && FluentIcons.iconByKey(key) != null
        }

        viewModelScope.launch {
            val existingIds = _uiState.value.items.map { it.id }.toSet()
            val itemId = existingId ?: generateUniqueSlugId("item", trimmedName, existingIds)
            val updatedItem = Item(
                id = itemId,
                categoryId = categoryId,
                name = trimmedName,
                flavors = cleanFlavors,
                variants = cleanVariants,
                isAvailable = isAvailable,
                icon = sanitizedIcon
            )
            menuRepository.upsertItem(updatedItem)
            SyncWorker.triggerImmediateSync(appContext)
            onSuccess()
            _uiState.update {
                it.copy(snackbarMessage = if (existingId == null) "Added \"$trimmedName\"" else "Saved \"$trimmedName\"")
            }
        }
    }

    fun deleteItem(item: Item) {
        viewModelScope.launch {
            val mappings = recipeRepository.getMappingsForMenu(item.id).first()
            mappings.forEach { recipeRepository.deleteMapping(it) }
            menuRepository.deleteItem(item.id)
            SyncWorker.deleteRemoteCatalogRow(appContext, "items", item.id)
            SyncWorker.triggerImmediateSync(appContext)
            _uiState.update { it.copy(snackbarMessage = "Deleted \"${item.name}\"") }
        }
    }

    companion object {
        fun generateUniqueSlugId(prefix: String, rawName: String, existingIds: Set<String>): String {
            val slug = rawName
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .take(28)
                .ifEmpty { "custom" }
            val base = "${prefix}_$slug"
            if (base !in existingIds) return base
            var suffix = 2
            while ("${base}_$suffix" in existingIds) {
                suffix++
            }
            return "${base}_$suffix"
        }

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as CattasticPosApp
                return MenuEditorViewModel(
                    appContext = app.applicationContext,
                    menuRepository = app.container.menuRepository,
                    recipeRepository = app.container.recipeRepository
                ) as T
            }
        }
    }
}
