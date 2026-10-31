package com.example.cattasticpos.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["categoryId"])]
)
data class ItemEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val name: String,
    val flavors: String,      // Comma or pipe separated list of flavors, e.g. "Veggie Whiskers|Cheesy Calico|Octo-Paws"
    val variantsJson: String, // JSON structure detailing variants (size and pricing configurations)
    val isAvailable: Boolean = true,
    val icon: String? = null
)
