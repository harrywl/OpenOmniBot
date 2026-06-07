package cn.com.omnimind.baselib.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface StudyRecordDao {
    @Insert
    suspend fun insert(record: StudyRecord): Long

    @Update
    suspend fun update(record: StudyRecord)

    @Query("SELECT * FROM study_records ORDER BY createdAt DESC")
    suspend fun getAll(): List<StudyRecord>
    @Query("SELECT * FROM study_records WHERE id = :id")
    suspend fun getById(id: Long): StudyRecord
    @Query("SELECT * FROM study_records WHERE appName = :appName ORDER BY createdAt DESC")
    suspend fun getByAppName(appName: String): List<StudyRecord>

    @Query("SELECT appName,packageName, COUNT(*) as count FROM study_records GROUP BY appName")
    suspend fun getNumGroupByAppName(): List<AppNameCount>

    @Query("UPDATE study_records SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateIsFavoriteById(id: Long, isFavorite: Boolean)

    @Query("UPDATE study_records SET title = :title WHERE id = :id")
    suspend fun updateTitleById(id: Long, title: String)

    @Query("SELECT * FROM study_records WHERE title = :title")
    suspend fun getByTitle(title: String): List<StudyRecord>

    @Query("DELETE FROM study_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    data class AppNameCount(val appName: String, val packageName: String,val count: Int)
}