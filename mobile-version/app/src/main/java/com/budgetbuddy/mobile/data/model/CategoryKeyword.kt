package com.budgetbuddy.mobile.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "category_keywords",
    indices = [Index(value = ["keyword"], unique = true), Index(value = ["categoryName"])]
)
data class CategoryKeyword(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val categoryName: String,
    
    val keyword: String,
    
    val categoriesFor: String? // Field for AI categorisation taxonomy
)

