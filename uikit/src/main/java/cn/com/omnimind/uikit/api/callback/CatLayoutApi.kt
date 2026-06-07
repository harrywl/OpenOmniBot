package cn.com.omnimind.uikit.api.callback

interface CatLayoutApi {
    fun onOpenHomeParam(path: String, needClear: Boolean)

    fun cancelScheduledTask()
}
