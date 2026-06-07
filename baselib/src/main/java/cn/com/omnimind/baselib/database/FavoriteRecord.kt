package cn.com.omnimind.baselib.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "favorite_records")
data class FavoriteRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val desc: String,
    val type: String,
    val imagePath: String,
    val packageName: String = "",  // 添加默认值
    val status: Int = 0, // 0为识别中,1为识别成功,2为识别失败
    val createdAt: Long = Date().time,
    val updatedAt: Long = Date().time
)