package cn.com.omnimind.baselib.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface CacheSuggestionDao {
    @Insert
    suspend fun insert(cacheSuggestion: CacheSuggestion): Long

    @Update
    suspend fun update(cacheSuggestion: CacheSuggestion)

    @Query("SELECT * FROM cache_suggestion WHERE packageName = :packageName ORDER BY indexNum ASC")
    suspend fun getListByPackageName(packageName: String): List<CacheSuggestion>

    @Query("DELETE FROM cache_suggestion WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)

    @Query("DELETE FROM cache_suggestion WHERE suggestionId = :suggestionId")
    suspend fun deleteBySuggestionId(suggestionId: String)

    @Insert
    suspend fun insertList(cacheSuggestions: List<CacheSuggestion>)
}