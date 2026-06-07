package cn.com.omnimind.assists.util

/**
 * 时间工具类
 */
object TimeUtil {
    /**
     * 获取当前时间字符串
     * @return 格式：yyyy-MM-dd HH:mm 星期X（例如：2026-01-08 15:30 星期三）
     */
    fun getCurrentTimeString(): String {
        val calendar = java.util.Calendar.getInstance()
        val year = calendar.get(java.util.Calendar.YEAR)
        val month = String.format(java.util.Locale.US, "%02d", calendar.get(java.util.Calendar.MONTH) + 1)
        val day = String.format(java.util.Locale.US, "%02d", calendar.get(java.util.Calendar.DAY_OF_MONTH))
        val hour = String.format(java.util.Locale.US, "%02d", calendar.get(java.util.Calendar.HOUR_OF_DAY))
        val minute = String.format(java.util.Locale.US, "%02d", calendar.get(java.util.Calendar.MINUTE))

        // 获取星期几
        val dayOfWeek = when (calendar.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.SUNDAY -> "星期日"
            java.util.Calendar.MONDAY -> "星期一"
            java.util.Calendar.TUESDAY -> "星期二"
            java.util.Calendar.WEDNESDAY -> "星期三"
            java.util.Calendar.THURSDAY -> "星期四"
            java.util.Calendar.FRIDAY -> "星期五"
            java.util.Calendar.SATURDAY -> "星期六"
            else -> ""
        }

        return "$year-$month-$day $hour:$minute $dayOfWeek"
    }
}
