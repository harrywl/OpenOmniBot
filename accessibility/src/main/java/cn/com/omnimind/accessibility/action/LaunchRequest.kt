package cn.com.omnimind.accessibility.action

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import cn.com.omnimind.baselib.R
import cn.com.omnimind.baselib.util.MobileManufacturer
import cn.com.omnimind.baselib.util.MobileManufacturerUtil


/**
 *
 * 通过启动一个透明的Activity来处理打开应用,规避荣耀不能在桌面频繁打开应用的问题
 */
class LaunchRequest : Activity() {

    companion object {

        /**
         * 打开三方应用
         * @param context 上下文
         * @param packageName 包名
         */
        fun requestLaunch(
            context: Context, packageName: String
        ) {
            val intent =
                Intent(context, LaunchRequest::class.java).putExtra("packageName", packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 设置透明主题

        setTheme(R.style.Theme_OmnibotApp_Permission)

        val packageName = intent.getStringExtra("packageName")
        if (packageName == null || packageName.isEmpty()) {
            Handler().postDelayed({
                finish()
            }, 1000)
            return
        }
        var lIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (lIntent != null) {
            lIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // 清除原有任务栈
            if (MobileManufacturerUtil.getDeviceManufacturer() == MobileManufacturer.HONOR) {
                lIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK); // 重置任务栈
            } else {
                lIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            startActivity(lIntent)
            finish()
        } else {
            finish()
        }

    }


}