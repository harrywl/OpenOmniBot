package cn.com.omnimind.baselib.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cache_suggestion")
data class CacheSuggestion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val suggestionId: String,
    val packageName: String,
    val indexNum: Int
)