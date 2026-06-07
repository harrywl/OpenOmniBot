package cn.com.omnimind.uikit.util

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.CountDownTimer
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import cn.com.omnimind.uikit.util.receiver.NotificationActionReceiver
import cn.com.omnimind.uikit.R
import kotlin.jvm.javaClass

object NotificationUtil {

    private const val CHANNEL_ID = "task_notification_channel"
    private const val NOTIFICATION_ID = 1

    private var countDownTimer: CountDownTimer? = null

    @SuppressLint("RemoteViewLayout")
    fun showTaskNotification(
        countdownSeconds: Long,
        title: String,
        subtitle: String
    ) {
        val notificationManager = BaseApplication.instance.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><hierarchy xmlns=\"http://schemas.android.com/apk/res/android\"><node id=\"0\" bounds=\"[0,0][1080,2376]\"><node id=\"1\" focusable=\"true\" scrollable=\"true\" bounds=\"[0,0][1080,2182]\"><node id=\"3\" clickable=\"true\" focusable=\"true\" bounds=\"[49,252][193,396]\" /><node id=\"4\" clickable=\"true\" focusable=\"true\" bounds=\"[195,252][456,396]\"><node id=\"5\" text=\"Zzzze\" bounds=\"[237,290][456,357]\" /></node><node id=\"6\" clickable=\"true\" focusable=\"true\" bounds=\"[456,252][612,396]\"><node id=\"7\" text=\"主页\" bounds=\"[474,297][612,351]\" /></node><node id=\"8\" clickable=\"true\" focusable=\"true\" bounds=\"[36,453][1044,513]\"><node id=\"9\" text=\"会员中心\" bounds=\"[825,459][1017,507]\" /></node><node id=\"10\" clickable=\"true\" focusable=\"true\" bounds=\"[72,564][396,648]\"><node id=\"11\" text=\"1.8倍\" bounds=\"[165,564][251,606]\" /><node id=\"12\" text=\"积分加速\" bounds=\"[165,606][309,648]\" /></node><node id=\"13\" clickable=\"true\" focusable=\"true\" bounds=\"[396,564][720,648]\"><node id=\"14\" text=\"延迟退房\" bounds=\"[489,564][633,606]\" /><node id=\"15\" text=\"免费延迟退房\" bounds=\"[489,606][684,648]\" /></node><node id=\"16\" clickable=\"true\" focusable=\"true\" bounds=\"[720,564][1044,648]\"><node id=\"17\" text=\"携程积多分\" bounds=\"[813,564][993,606]\" /><node id=\"18\" text=\"积分翻番\" bounds=\"[813,606][957,648]\" /></node><node id=\"19\" clickable=\"true\" focusable=\"true\" bounds=\"[36,624][1044,816]\"><node id=\"21\" text=\"享5倍积分 领视频会员\" bounds=\"[333,728][686,777]\" /><node id=\"22\" text=\"去开通\" bounds=\"[861,722][1008,784]\" /></node><node id=\"23\" clickable=\"true\" focusable=\"true\" bounds=\"[36,840][288,1042]\"><node id=\"24\" text=\"2\" bounds=\"[147,888][176,946]\" /><node id=\"25\" text=\"收藏\" bounds=\"[126,952][198,994]\" /></node><node id=\"26\" clickable=\"true\" focusable=\"true\" bounds=\"[288,840][540,1042]\"><node id=\"27\" text=\"29\" bounds=\"[385,888][443,946]\" /><node id=\"28\" text=\"浏览历史\" bounds=\"[342,952][486,994]\" /></node><node id=\"29\" clickable=\"true\" focusable=\"true\" bounds=\"[540,840][792,1042]\"><node id=\"30\" text=\"价值￥164\" bounds=\"[627,846][792,887]\" /><node id=\"31\" text=\"16,454\" bounds=\"[590,888][742,946]\" /><node id=\"32\" text=\"积分\" bounds=\"[630,952][702,994]\" /></node><node id=\"33\" clickable=\"true\" focusable=\"true\" bounds=\"[792,840][1044,1042]\"><node id=\"34\" text=\"7\" bounds=\"[904,888][931,946]\" /><node id=\"35\" text=\"优惠券\" bounds=\"[864,952][972,994]\" /></node><node id=\"36\" clickable=\"true\" focusable=\"true\" bounds=\"[45,1102][237,1264]\"><node id=\"37\" text=\"待付款\" bounds=\"[87,1204][195,1246]\" /></node><node id=\"38\" clickable=\"true\" focusable=\"true\" bounds=\"[237,1102][429,1264]\"><node id=\"39\" text=\"待出行\" bounds=\"[279,1204][387,1246]\" /></node><node id=\"40\" clickable=\"true\" focusable=\"true\" bounds=\"[429,1102][621,1264]\"><node id=\"41\" text=\"退款/售后\" bounds=\"[445,1204][604,1246]\" /></node><node id=\"42\" clickable=\"true\" focusable=\"true\" bounds=\"[621,1102][813,1264]\"><node id=\"43\" text=\"待点评\" bounds=\"[663,1204][771,1246]\" /></node><node id=\"44\" clickable=\"true\" focusable=\"true\" bounds=\"[843,1102][1035,1264]\"><node id=\"45\" text=\"全部订单\" bounds=\"[867,1204][1011,1246]\" /></node><node id=\"46\" clickable=\"true\" focusable=\"true\" bounds=\"[36,1318][1044,1579]\"><node id=\"47\" focusable=\"true\" scrollable=\"true\" bounds=\"[36,1318][1044,1498]\"><node id=\"48\" focusable=\"true\" scrollable=\"true\" bounds=\"[36,1336][1044,1498]\"><node id=\"49\" clickable=\"true\" focusable=\"true\" bounds=\"[36,1336][238,1498]\"><node id=\"50\" text=\"常用信息\" bounds=\"[65,1456][209,1498]\" /></node><node id=\"51\" clickable=\"true\" focusable=\"true\" bounds=\"[238,1336][439,1498]\"><node id=\"52\" text=\"报销凭证\" bounds=\"[266,1456][410,1498]\" /></node><node id=\"53\" clickable=\"true\" focusable=\"true\" bounds=\"[439,1336][641,1498]\"><node id=\"54\" text=\"礼品卡\" bounds=\"[486,1456][594,1498]\" /></node><node id=\"55\" clickable=\"true\" focusable=\"true\" bounds=\"[641,1336][842,1498]\"><node id=\"56\" text=\"出行清单\" bounds=\"[669,1456][813,1498]\" /></node><node id=\"57\" clickable=\"true\" focusable=\"true\" bounds=\"[842,1336][1044,1498]\"><node id=\"58\" text=\"特价购物车\" bounds=\"[853,1456][1033,1498]\" /></node></node></node></node><node id=\"59\" text=\"我的钱包\" clickable=\"true\" focusable=\"true\" bounds=\"[93,1639][273,1692]\" /><node id=\"60\" text=\"现金 · 返现\" clickable=\"true\" focusable=\"true\" bounds=\"[780,1644][999,1692]\" /><node id=\"61\" clickable=\"true\" focusable=\"true\" bounds=\"[45,1701][292,1911]\"><node id=\"62\" text=\"50万\" bounds=\"[96,1741][187,1799]\" /><node id=\"63\" text=\"生意人贷\" bounds=\"[96,1808][240,1850]\" /><node id=\"64\" text=\"最高可借\" bounds=\"[96,1862][216,1897]\" /></node><node id=\"65\" clickable=\"true\" focusable=\"true\" bounds=\"[292,1701][539,1911]\"><node id=\"66\" text=\"0.0元\" bounds=\"[361,1741][465,1799]\" /><node id=\"67\" text=\"拿去花\" bounds=\"[361,1808][469,1850]\" /><node id=\"68\" text=\"已关闭\" bounds=\"[361,1862][451,1897]\" /></node><node id=\"69\" clickable=\"true\" focusable=\"true\" bounds=\"[539,1701][787,1911]\"><node id=\"70\" text=\"30万\" bounds=\"[603,1741][694,1799]\" /><node id=\"71\" text=\"信用贷\" bounds=\"[603,1808][711,1850]\" /><node id=\"72\" text=\"最高可借\" bounds=\"[603,1862][723,1897]\" /></node><node id=\"73\" clickable=\"true\" focusable=\"true\" bounds=\"[787,1701][1035,1911]\"><node id=\"74\" text=\"10万\" bounds=\"[851,1741][934,1799]\" /><node id=\"75\" text=\"联名卡\" bounds=\"[851,1808][959,1850]\" /><node id=\"76\" text=\"最高额度\" bounds=\"[851,1862][971,1897]\" /></node><node id=\"77\" clickable=\"true\" focusable=\"true\" bounds=\"[54,1965][1026,2054]\"><node id=\"78\" text=\"互动参与\" bounds=\"[84,1989][264,2042]\" /><node id=\"79\" text=\"去旅行社区发现世界\" bounds=\"[630,1994][1002,2042]\" /></node><node id=\"80\" focusable=\"true\" bounds=\"[54,2054][1026,2182]\"><node id=\"81\" clickable=\"true\" focusable=\"true\" bounds=\"[54,2054][540,2182]\"><node id=\"83\" text=\"旅行足迹\" bounds=\"[102,2096][270,2145]\" /><node id=\"84\" text=\"用脚步丈量世界\" bounds=\"[102,2147][354,2182]\" /></node><node id=\"85\" clickable=\"true\" focusable=\"true\" bounds=\"[540,2054][1026,2182]\"><node id=\"87\" text=\"创作中心\" bounds=\"[588,2096][756,2145]\" /><node id=\"88\" text=\"发笔记 赚现金\" bounds=\"[588,2147][813,2182]\" /></node></node></node><node id=\"89\" clickable=\"true\" focusable=\"true\" bounds=\"[0,0][1080,252]\"><node id=\"90\" content-desc=\"扫一扫\" clickable=\"true\" focusable=\"true\" bounds=\"[630,120][720,233]\"><node id=\"91\" text=\"扫一扫\" bounds=\"[630,192][720,233]\" /></node><node id=\"93\" clickable=\"true\" focusable=\"true\" bounds=\"[768,120][828,233]\"><node id=\"94\" text=\"签到\" bounds=\"[768,192][828,233]\" /></node><node id=\"95\" content-desc=\"客服\" clickable=\"true\" focusable=\"true\" bounds=\"[876,120][936,233]\"><node id=\"96\" text=\"客服\" bounds=\"[876,192][936,233]\" /></node><node id=\"97\" content-desc=\"设置\" clickable=\"true\" focusable=\"true\" bounds=\"[984,120][1044,233]\"><node id=\"98\" text=\"设置\" bounds=\"[984,192][1044,233]\" /></node></node><node id=\"99\" content-desc=\"首页\" clickable=\"true\" focusable=\"true\" bounds=\"[0,2181][216,2328]\"><node id=\"100\" text=\"首页\" bounds=\"[75,2277][141,2321]\" /></node><node id=\"101\" content-desc=\"消息\" clickable=\"true\" focusable=\"true\" bounds=\"[216,2181][432,2328]\"><node id=\"102\" text=\"36\" bounds=\"[336,2187][396,2229]\" /><node id=\"103\" text=\"消息\" bounds=\"[291,2277][357,2321]\" /></node><node id=\"104\" content-desc=\"社区\" clickable=\"true\" focusable=\"true\" bounds=\"[432,2181][648,2328]\" /><node id=\"105\" content-desc=\"行程\" clickable=\"true\" focusable=\"true\" bounds=\"[648,2181][864,2328]\"><node id=\"106\" text=\"行程\" bounds=\"[723,2277][789,2321]\" /></node><node id=\"107\" content-desc=\"我的\" clickable=\"true\" focusable=\"true\" selected=\"true\" bounds=\"[864,2181][1080,2328]\"><node id=\"108\" text=\"我的\" selected=\"true\" bounds=\"[939,2277][1005,2321]\" /></node></node></hierarchy>"
        // Create Notification Channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "预约任务",
                NotificationManager.IMPORTANCE_DEFAULT // Changed from HIGH to DEFAULT to prevent sounds
            ).apply {
                enableVibration(false)
                enableLights(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Create RemoteViews for the custom layout
        val remoteViews = RemoteViews( BaseApplication.instance.packageName, R.layout.task_notification_layout).apply {
            setTextViewText(R.id.task_title, title)
            setTextViewText(R.id.task_subtitle, subtitle)

            // Set PendingIntents for button clicks
            val executeIntent = Intent( BaseApplication.instance, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.Companion.ACTION_EXECUTE
            }
            val executePendingIntent = PendingIntent.getBroadcast(
                BaseApplication.instance, 0, executeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setOnClickPendingIntent(R.id.execute_button, executePendingIntent)

            val cancelIntent = Intent( BaseApplication.instance, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.Companion.ACTION_CANCEL
            }
            val cancelPendingIntent = PendingIntent.getBroadcast(
                BaseApplication.instance, 1, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setOnClickPendingIntent(R.id.cancel_button, cancelPendingIntent)
        }

        // Build the notification
        val notificationBuilder = NotificationCompat.Builder( BaseApplication.instance, CHANNEL_ID)
            .setSmallIcon(R.drawable.baseline_play_arrow_24) // Replace with a real icon
            .setStyle(NotificationCompat.DecoratedCustomViewStyle()) // Add this for better custom view support
            .setCustomContentView(remoteViews)
            .setOngoing(true) // Make it a permanent notification during countdown
            .setOnlyAlertOnce(true) // IMPORTANT: Prevents sound/vibration on every update
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // Changed from HIGH to DEFAULT
            .setCategory(NotificationCompat.CATEGORY_SERVICE) // Declare as a service
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Show on lock screen
            .setSilent(true) // Make sure it's silent
            .setAutoCancel(false) // Don't auto cancel since this is an ongoing notification

        // --- Countdown Logic ---
        // Cancel any previous timer to avoid multiple running timers
        countDownTimer?.cancel()

        // Create and start a new timer
        countDownTimer = object : CountDownTimer(countdownSeconds * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                val minutes = seconds / 60
                val remainingSeconds = seconds % 60
                val timeString = String.format("%02d:%02d 后将执行", minutes, remainingSeconds)

                remoteViews.setTextViewText(R.id.time_text, timeString)
                remoteViews.setTextViewText(R.id.task_title, title)
                remoteViews.setTextViewText(R.id.task_subtitle, subtitle)
                val notification = notificationBuilder.build().applyMiuiCustomViewLegacyFix()
                notificationManager.notify(NOTIFICATION_ID, notification)
            }

            override fun onFinish() {
                remoteViews.setTextViewText(R.id.time_text, "任务已开始")
                // Make it dismissible by user after countdown finishes
                notificationBuilder.setOngoing(false)
                val notification = notificationBuilder.build().applyMiuiCustomViewLegacyFix()
                notificationManager.notify(NOTIFICATION_ID, notification)
                countDownTimer = null
            }
        }.start()
    }

    fun dismiss() {
        // Stop the timer if it's running
        countDownTimer?.cancel()
        countDownTimer = null

        val notificationManager = BaseApplication.instance.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    /**
     * Applies a legacy fix for custom notifications on older MIUI versions (V6-V9).
     * This uses reflection to enable the custom view layout.
     */
    @SuppressLint("PrivateApi")
    private fun Notification.applyMiuiCustomViewLegacyFix(): Notification {
        try {
            val extraNotificationField = this.javaClass.getDeclaredField("extraNotification")
            extraNotificationField.isAccessible = true
            val extraNotification = extraNotificationField.get(this)
            val setCustomizedIconMethod = extraNotification.javaClass.getDeclaredMethod("setCustomizedIcon", Boolean::class.javaPrimitiveType)
            setCustomizedIconMethod.isAccessible = true
            setCustomizedIconMethod.invoke(extraNotification, true)
        } catch (e: Exception) {
            // Fails silently on non-MIUI devices or unsupported versions.
        }
        return this
    }
}