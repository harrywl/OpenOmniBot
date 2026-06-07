package cn.com.omnimind.assists.util

/**
 * 轻量 XML 结构相似度比较。
 * 开源版用于页面稳定性判断，不依赖私有技能数据结构。
 */
object TreeEditDistance {
    private val tagRegex = Regex("""<\s*/?\s*([a-zA-Z0-9_.:-]+)""")
    private const val DEFAULT_THRESHOLD = 0.5

    @JvmStatic
    fun getSimilarity(xml1: String, xml2: String): Float {
        val t1 = tokenize(xml1)
        val t2 = tokenize(xml2)
        if (t1.isEmpty() && t2.isEmpty()) return 1.0f
        if (t1.isEmpty() || t2.isEmpty()) return 0f

        val freq1 = t1.groupingBy { it }.eachCount().toMutableMap()
        val freq2 = t2.groupingBy { it }.eachCount().toMutableMap()

        var common = 0
        for ((k, c1) in freq1) {
            val c2 = freq2[k] ?: 0
            common += minOf(c1, c2)
        }
        val denom = t1.size + t2.size
        if (denom == 0) return 1.0f
        return (2.0f * common.toFloat() / denom.toFloat()).coerceIn(0f, 1f)
    }

    @JvmStatic
    fun getSimilarityThreshold(): Double = DEFAULT_THRESHOLD

    private fun tokenize(xml: String): List<String> {
        if (xml.isBlank()) return emptyList()
        return tagRegex.findAll(xml).map { it.groupValues[1] }.toList()
    }
}
