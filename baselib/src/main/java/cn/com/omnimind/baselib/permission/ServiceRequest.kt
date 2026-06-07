package cn.com.omnimind.baselib.permission

import android.app.Activity
import android.app.ComponentCaller
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.util.SparseArray
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import cn.com.omnimind.baselib.R


/**
 * 透明Activity方式实现权限请求工具类
 * 通过启动一个透明的Activity来处理权限请求，避免在业务Activity中处理复杂的权限逻辑
 */
class ServiceRequest : Activity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val EXTRA_SERVICE = "extra_service"

        // 用于存储请求回调的静态变量
        private val requestCallbacks = SparseArray<(
            resultCode: Int,
            data: Intent?
        ) -> Unit>()
        private var requestCode = 0

        /**
         * 请求权限
         * @param context 上下文
         * @param permissions 需要请求的权限数组
         * @param callback 权限请求结果回调
         */
        fun requestService(
            context: Context,
            serviceName: String,
            callback: (
                resultCode: Int,
                data: Intent?
            ) -> Unit
        ) {
            requestCode++
            requestCallbacks.put(requestCode, callback)

            val intent = Intent(context, ServiceRequest::class.java)
                .putExtra(EXTRA_SERVICE, serviceName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }

    }

    private var currentRequestCode = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 设置透明主题

        setTheme(R.style.Theme_OmnibotApp_Permission)

        val serviceName = intent.getStringExtra(EXTRA_SERVICE)
        if (serviceName == null || serviceName.isEmpty()) {
            Handler().postDelayed({
                requestCallbacks.get(currentRequestCode)?.invoke(0, null)
                requestCallbacks.remove(currentRequestCode)
                finish()

            }, 1000)
//            finish()
            return
        }

        currentRequestCode = requestCode

        if (serviceName.equals(MEDIA_PROJECTION_SERVICE)) {
            requestScreenCapturePermission(this)
        } else {
            Handler().postDelayed({
                requestCallbacks.get(currentRequestCode)?.invoke(0, null)
                requestCallbacks.remove(currentRequestCode)
                finish()
            }, 1000)
//            finish()
            return
        }

    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        requestCallbacks.get(currentRequestCode)?.invoke(resultCode, data)
        requestCallbacks.remove(currentRequestCode)
        // 关闭透明Activity
        finish()
    }

    /**
     * 在 Activity 中调用，拉起系统截屏授权弹窗
     */
    fun requestScreenCapturePermission(activity: Activity) {
        val mpm =
            activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        activity.startActivityForResult(
            mpm.createScreenCaptureIntent(),
            PERMISSION_REQUEST_CODE
        )
    }


}