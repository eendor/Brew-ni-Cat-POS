package com.example.cattasticpos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.cattasticpos.data.local.entity.CategoryEntity
import com.example.cattasticpos.data.local.entity.ItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MenuDao {
    @Query("SELECT * FROM categories")
    fun getCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM items")
    fun getItems(): Flow<List<ItemEntity>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCategoryCount(): Int

    @Query("SELECT * FROM categories WHERE id = :categoryId LIMIT 1")
    suspend fun getCategoryById(categoryId: String): CategoryEntity?

    @Query("SELECT * FROM items WHERE id = :itemId LIMIT 1")
    suspend fun getItemById(itemId: String): ItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ItemEntity>)

    @Query("DELETE FROM items WHERE id IN (:ids)")
    suspend fun deleteItemsByIds(ids: List<String>)

    @Query("DELETE FROM categories WHERE id IN (:ids)")
    suspend fun deleteCategoriesByIds(ids: List<String>)
}
