package cn.com.omnimind.baselib.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface FavoriteRecordDao {
    @Insert
    suspend fun insert(record: FavoriteRecord): Long

    @Update
    suspend fun update(record: FavoriteRecord)

    @Query("SELECT * FROM favorite_records WHERE id = :id")
    suspend fun getById(id: Long): FavoriteRecord?

    @Query("SELECT * FROM favorite_records WHERE status = 1 ORDER BY createdAt DESC")
    suspend fun getAll(): List<FavoriteRecord>

    @Query("SELECT * FROM favorite_records WHERE type = :type AND status = 1 ORDER BY createdAt DESC")
    suspend fun getByType(type: String): List<FavoriteRecord>

    @Query("SELECT type, COUNT(*) as count FROM favorite_records WHERE status = 1 group BY type")
    suspend fun getFavoriteRecordCountByType(): List<FavoriteCount>

    @Query("UPDATE favorite_records SET title = :title WHERE id = :id")
    suspend fun updateTitleById(id: Long, title: String)

    @Query("SELECT * FROM favorite_records WHERE title = :title AND status = 1")
    suspend fun getByTitle(title: String): List<FavoriteRecord>

    @Query("DELETE FROM favorite_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    data class FavoriteCount(val type: String, val count: Int)
}