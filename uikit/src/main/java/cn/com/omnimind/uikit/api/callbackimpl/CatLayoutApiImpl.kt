package cn.com.omnimind.uikit.api.callbackimpl

import cn.com.omnimind.assists.AssistsCore
import cn.com.omnimind.uikit.UIKit
import cn.com.omnimind.uikit.api.callback.CatLayoutApi
import cn.com.omnimind.uikit.loader.FloatingHalfScreenLoader

class CatLayoutApiImpl : CatLayoutApi {

    override fun onOpenHomeParam(path: String, needClear: Boolean) {
        FloatingHalfScreenLoader.destroyInstance()
        UIKit.halfScreenApi?.onNeedOpenAppMainParam(path, needClear)
    }

    override fun cancelScheduledTask() {
        AssistsCore.cancelScheduleTask()
    }
}
