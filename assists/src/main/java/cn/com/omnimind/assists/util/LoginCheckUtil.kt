package cn.com.omnimind.assists.util

import android.annotation.SuppressLint
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import cn.com.omnimind.assists.detection.scenarios.loading.AccessibilityNode
import cn.com.omnimind.accessibility.service.AssistsService
import cn.com.omnimind.baselib.util.OmniLog

data class LoginDetectionResult(
    val isLoginPage: Boolean,
    val confidence: Float,
    val reasons: List<String>
)

object LoginCheckUtil {
    private const val TAG = "[LoginCheckUtil]"

    /**
     * 检测当前 AccessibilityNodeInfo 是否是登录页
     * @param rootNodeInfo 根节点信息，如果为 null 则从 AssistsService 获取
     * @return 检测结果，如果 rootNodeInfo 为 null 且无法获取则返回 null
     */
    suspend fun detectLoginPage(rootNodeInfo: AccessibilityNodeInfo? = null): LoginDetectionResult? {
        val nodeInfo = rootNodeInfo ?: AssistsService.instance?.rootInActiveWindow
        if (nodeInfo == null) {
            OmniLog.w(TAG, "无法获取 rootNodeInfo，跳过登录页检测")
            return null
        }

        val rootNode = buildNodeTree(nodeInfo)
        return LoginPageDetector().detectLoginPage(rootNode)
    }

    /**
     * 构建节点树（激进优化版本：更严格的深度和子节点限制）
     * 登录页的关键元素通常在浅层（1-3层），进一步限制深度和子节点数量以提升性能
     * 如果只有一个子节点，不计入深度(不超过maxIgnoreDepth限制)
     */
    private fun buildNodeTree(info: AccessibilityNodeInfo, maxDepth: Int = 4, currentDepth: Int = 0, maxIgnoreDepth: Int = 5): AccessibilityNode {
        val bounds = Rect().also { info.getBoundsInScreen(it) }

        // 如果达到最大深度，不再构建子节点
        if (currentDepth >= maxDepth) {
            return AccessibilityNode(
                className = info.className?.toString() ?: "",
                text = info.text?.toString(),
                contentDescription = info.contentDescription?.toString(),
                isClickable = info.isClickable,
                isVisibleToUser = info.isVisibleToUser,
                isAccessibilityFocused = info.isAccessibilityFocused,
                boundsInScreen = bounds,
                children = emptyList()
            )
        }

        val children = mutableListOf<AccessibilityNode>()
        val childCount = info.childCount

        val useIgnore = childCount == 1 && maxIgnoreDepth > 0

        // 如果忽略，保持当前深度；否则加 1
        val childDepth = if (useIgnore) currentDepth else currentDepth + 1
        // 如果忽略，最大忽略深度减 1；否则保持不变
        val childMaxIgnoreDepth = if (useIgnore) maxIgnoreDepth - 1 else maxIgnoreDepth
        
        // 更严格的子节点数量限制：浅层最多30个，深层最多10个
        val maxChildren = when {
            currentDepth < 2 -> 30  // 前2层可以稍多
            currentDepth < 3 -> 15  // 第3层减少
            else -> 10               // 第4层及以后更严格
        }
        
        for (i in 0 until minOf(childCount, maxChildren)) {
            val childInfo = try {
                info.getChild(i)
            } catch (e: Exception) {
                null
            } ?: continue

            try {
                // 只构建可见的节点，不可见节点通常不需要检测
                if (childInfo.isVisibleToUser) {
                    children.add(buildNodeTree(childInfo, maxDepth, childDepth, childMaxIgnoreDepth))
                }
            } catch (e: Exception) {
                // 静默处理错误，避免日志过多影响性能
            } finally {
                // 避免节点引用泄漏
                childInfo.recycle()
            }
        }

        return AccessibilityNode(
            className = info.className?.toString() ?: "",
            text = info.text?.toString(),
            contentDescription = info.contentDescription?.toString(),
            isClickable = info.isClickable,
            isVisibleToUser = info.isVisibleToUser,
            isAccessibilityFocused = info.isAccessibilityFocused,
            boundsInScreen = bounds,
            children = children
        )
    }
}

class LoginPageDetector {
    private  val TAG = "[LoginPageDetector]"

    // 预编译正则表达式，避免重复创建
    private val loginKeywordRegexes = listOf(
        // 基础登录关键词
        Regex("\\b登录\\b", RegexOption.IGNORE_CASE),
        Regex("\\b登陆\\b", RegexOption.IGNORE_CASE),
        Regex("\\b登入\\b", RegexOption.IGNORE_CASE),
        Regex("\\bsign in\\b", RegexOption.IGNORE_CASE),
        Regex("\\blogin\\b", RegexOption.IGNORE_CASE),
        Regex("\\b密码\\b", RegexOption.IGNORE_CASE),
        Regex("\\bpassword\\b", RegexOption.IGNORE_CASE),
        Regex("\\bpwd\\b", RegexOption.IGNORE_CASE),
        Regex("\\b用户名\\b", RegexOption.IGNORE_CASE),
        Regex("\\buser name\\b", RegexOption.IGNORE_CASE),
        Regex("\\busername\\b", RegexOption.IGNORE_CASE),
        Regex("\\b账号\\b", RegexOption.IGNORE_CASE),
        Regex("\\b账户\\b", RegexOption.IGNORE_CASE),
        Regex("\\baccount\\b", RegexOption.IGNORE_CASE),
        Regex("\\b手机号\\b", RegexOption.IGNORE_CASE),
        Regex("\\b手机号码\\b", RegexOption.IGNORE_CASE),
        Regex("\\bphone number\\b", RegexOption.IGNORE_CASE),
        Regex("\\bmobile number\\b", RegexOption.IGNORE_CASE),
        Regex("\\btelephone number\\b", RegexOption.IGNORE_CASE),
        Regex("\\b验证码\\b", RegexOption.IGNORE_CASE),
        Regex("\\bverification code\\b", RegexOption.IGNORE_CASE),
        Regex("\\bverify code\\b", RegexOption.IGNORE_CASE),
        Regex("\\bcaptcha\\b", RegexOption.IGNORE_CASE),
        // 其他重要关键词
        Regex("\\b忘记密码\\b", RegexOption.IGNORE_CASE),
        Regex("\\bforgot password\\b", RegexOption.IGNORE_CASE),
        Regex("\\b找回密码\\b", RegexOption.IGNORE_CASE),
        Regex("\\b注册\\b", RegexOption.IGNORE_CASE),
        Regex("\\bregister\\b", RegexOption.IGNORE_CASE),
        Regex("\\bsign up\\b", RegexOption.IGNORE_CASE),
        Regex("\\bsignup\\b", RegexOption.IGNORE_CASE),
        Regex("\\b立即登录\\b", RegexOption.IGNORE_CASE),
        Regex("\\b马上登录\\b", RegexOption.IGNORE_CASE),
        // 短信验证码登录
        Regex("\\b短信登录\\b", RegexOption.IGNORE_CASE),
        Regex("\\b验证码登录\\b", RegexOption.IGNORE_CASE),
        Regex("\\b短信验证码\\b", RegexOption.IGNORE_CASE),
        Regex("\\bsms login\\b", RegexOption.IGNORE_CASE),
        Regex("\\bsms code\\b", RegexOption.IGNORE_CASE),
        // 一键登录
        Regex("\\b一键登录\\b", RegexOption.IGNORE_CASE),
        Regex("\\b本机号码一键登录\\b", RegexOption.IGNORE_CASE),
        Regex("\\b免密登录\\b", RegexOption.IGNORE_CASE),
        Regex("\\bone click login\\b", RegexOption.IGNORE_CASE),
        Regex("\\bquick login\\b", RegexOption.IGNORE_CASE),
        // 异地登录/安全验证
        Regex("\\b异地登录\\b", RegexOption.IGNORE_CASE),
        Regex("\\b在其他地方登录\\b", RegexOption.IGNORE_CASE),
        Regex("\\b在其他设备登录\\b", RegexOption.IGNORE_CASE),
        Regex("\\bnew device login\\b", RegexOption.IGNORE_CASE),
        Regex("\\b安全验证\\b", RegexOption.IGNORE_CASE),
        Regex("\\b身份验证\\b", RegexOption.IGNORE_CASE),
        Regex("\\bsecurity verification\\b", RegexOption.IGNORE_CASE),
        Regex("\\bidentity verification\\b", RegexOption.IGNORE_CASE),
        Regex("\\b为了您的账户安全\\b", RegexOption.IGNORE_CASE),
        Regex("\\baccount security\\b", RegexOption.IGNORE_CASE),
        Regex("\\b检测到异常登录\\b", RegexOption.IGNORE_CASE),
        Regex("\\bunusual login detected\\b", RegexOption.IGNORE_CASE),
        Regex("\\b登录异常\\b", RegexOption.IGNORE_CASE),
        Regex("\\blogin anomaly\\b", RegexOption.IGNORE_CASE)
    )

    /**
     * 登录页相关的关键词（更严格的匹配）- 用于简单匹配
     */
    private val loginKeywords = listOf(
        // 基础登录关键词（必须是完整词汇）
        "登录", "登陆", "登入", "sign in", "login",
        "密码", "password", "pwd",
        "用户名", "user name", "username",
        "账号", "账户", "account",
        "手机号", "手机号码", "phone number", "mobile number", "telephone number",
        "验证码", "verification code", "verify code", "captcha",
        "忘记密码", "forgot password", "找回密码",
        "注册", "register", "sign up", "signup",
        "记住密码", "remember password", "remember me",
        "登录按钮", "login button", "立即登录", "马上登录",
        // 短信验证码登录
        "短信登录", "验证码登录", "短信验证码", "sms login", "sms code",
        "获取验证码", "发送验证码", "重新获取", "get verification code", "send code",
        "验证码已发送", "code sent", "请输入验证码", "enter verification code",
        // 一键登录
        "一键登录", "本机号码一键登录", "免密登录", "one click login", "quick login",
        "本机号码", "当前手机号", "使用本机号码", "use current number",
        // 异地登录/安全验证
        "在其他地方登录", "异地登录", "在其他设备登录", "new device login", "login from new device",
        "安全验证", "身份验证", "安全检测", "security verification", "identity verification",
        "为了您的账户安全", "account security", "安全提醒", "security alert",
        "检测到异常登录", "unusual login detected", "登录异常", "login anomaly",
        // 其他登录相关
        "登录方式", "登录选项", "login method", "login option",
        "微信登录", "qq登录", "支付宝登录", "wechat login", "qq login", "alipay login",
        "第三方登录", "第三方账号", "third party login"
    )

    /**
     * 输入框相关的类名
     */
    private val inputFieldClassNames = listOf(
        "EditText", "android.widget.EditText",
        "TextField", "TextInputEditText"
    )

    /**
     * 检查必定命中规则（如果满足这些规则，直接判定为登录页）
     */
    private fun checkForceMatchRules(rootNode: AccessibilityNode): LoginDetectionResult? {
        // 收集所有可见文本
        val allTexts = collectAllTexts(rootNode)
        val allTextsLower = allTexts.map { it.lowercase() }
        val combinedText = allTextsLower.joinToString(" ")

        // 规则1: 同时出现"一键登录"、"阅读并同意"、"隐私"、"协议"
        val rule1Keywords = listOf("一键登录", "阅读并同意", "隐私", "协议")
        val rule1Matches = rule1Keywords.count { keyword ->
            allTextsLower.any { text -> text.contains(keyword.lowercase()) }
        }
        if (rule1Matches >= 4) {
            return LoginDetectionResult(
                isLoginPage = true,
                confidence = 1.0f,
                reasons = listOf("必定命中规则1: 同时出现\"一键登录\"、\"阅读并同意\"、\"隐私\"、\"协议\"")
            )
        }

        // 规则2: 绝对相等"请验证指纹"（精确匹配，不区分大小写）
        if (allTexts.any { it.trim().equals("请验证指纹", ignoreCase = true) }) {
            return LoginDetectionResult(
                isLoginPage = true,
                confidence = 1.0f,
                reasons = listOf("必定命中规则2: 出现绝对相等\"请验证指纹\"")
            )
        }

        // 规则3: 同时出现"登录"、"密码"、"手机号"、"忘记密码"
        val rule3Keywords = listOf("登录", "密码", "手机号", "忘记密码")
        val rule3Matches = rule3Keywords.count { keyword ->
            allTextsLower.any { it.contains(keyword) }
        }
        if (rule3Matches >= 4) {
            return LoginDetectionResult(
                isLoginPage = true,
                confidence = 1.0f,
                reasons = listOf("必定命中规则3: 同时出现\"登录\"、\"密码\"、\"手机号\"、\"忘记密码\"")
            )
        }

        // 规则4: 同时出现"微信登录"/"微信登陆"、"其他登录方式"/"其他登陆方式"
        val rule4Keywords = listOf("微信登录", "微信登陆", "其他登录方式", "其他登陆方式")
        val hasWechatLogin = allTextsLower.any { it.contains("微信登录") || it.contains("微信登陆") }
        val hasOtherLogin = allTextsLower.any { it.contains("其他登录方式") || it.contains("其他登陆方式") }
        if (hasWechatLogin && hasOtherLogin) {
            return LoginDetectionResult(
                isLoginPage = true,
                confidence = 1.0f,
                reasons = listOf("必定命中规则4: 同时出现\"微信登录/微信登陆\"、\"其他登录方式/其他登陆方式\"")
            )
        }

        // 规则4.1: 第三方登录页通用规则（小红书等）
        // 同时出现"微信登录"/"微信登陆"、"其他登录方式"/"其他登陆方式"、以及协议相关文本（"用户协议"、"隐私政策"、"阅读并同意"等）或找回账号相关文本
        val hasWechatLogin41 = allTextsLower.any { it.contains("微信登录") || it.contains("微信登陆") }
        val hasOtherLogin41 = allTextsLower.any { it.contains("其他登录方式") || it.contains("其他登陆方式") }
        val rule41AgreementKeywords = listOf("用户协议", "隐私政策", "阅读并同意", "我已阅读", "同意", "协议", "隐私")
        val rule41AccountKeywords = listOf("找回账号", "账号丢失", "找回账户", "账户丢失")
        val hasAgreement41 = rule41AgreementKeywords.any { keyword -> allTextsLower.any { it.contains(keyword) } }
        val hasAccountRecovery41 = rule41AccountKeywords.any { keyword -> allTextsLower.any { it.contains(keyword) } }
        if (hasWechatLogin41 && hasOtherLogin41 && (hasAgreement41 || hasAccountRecovery41)) {
            return LoginDetectionResult(
                isLoginPage = true,
                confidence = 1.0f,
                reasons = listOf("必定命中规则4.1: 第三方登录页（同时出现微信登录、其他登录方式、协议/找回账号）")
            )
        }

        // 规则4.2: 账号选择登录页规则
        // 同时出现"选择账号登录"、"登陆"、"添加账号"
        val hasSelectAccountLogin = allTextsLower.any { it.contains("选择账号登录") }
        val hasLogin = allTextsLower.any { it.contains("登陆") || it.contains("登录") }
        val hasAddAccount = allTextsLower.any { it.contains("添加账号") }
        if (hasSelectAccountLogin && hasLogin && hasAddAccount) {
            return LoginDetectionResult(
                isLoginPage = true,
                confidence = 1.0f,
                reasons = listOf("必定命中规则4.2: 账号选择登录页（同时出现\"选择账号登录\"、\"登陆\"、\"添加账号\"）")
            )
        }

        // 规则4.3: 支付宝风格登录页通用规则
        // 同时出现"刷脸登录"/"Face Login"或"短信登录"/"sms login"、"其他验证方式"/"Other verification methods"、"注册账号"/"Register account"
        // 可选特征："换号"/"Change number"、"遇到问题"/"Encountered problems"
        val hasFaceLogin = allTextsLower.any { 
            it.contains("刷脸登录") || it.contains("face login") || it.contains("刷脸") 
        }
        val hasSmsLogin = allTextsLower.any { 
            it.contains("短信登录") || it.contains("sms login") ||
            it.contains("短信") || it.contains("sms code") ||
            it.contains("验证码")
        }
        val hasOtherVerification = allTextsLower.any { 
            it.contains("其他验证方式") || it.contains("other verification methods") || 
            it.contains("其他验证") || it.contains("other verification")
        }
        val hasRegisterAccount = allTextsLower.any { 
            it.contains("注册账号") || it.contains("register account") || 
            it.contains("注册账户") || it.contains("register")
        }
        // 可选特征（至少出现一个）
        val hasChangeNumber = allTextsLower.any { 
            it.contains("换号") || it.contains("change number") || 
            it.contains("切换账号") || it.contains("switch account")
        }
        val hasProblemHelp = allTextsLower.any { 
            it.contains("遇到问题") || it.contains("encountered problems") || 
            it.contains("遇到") || it.contains("problems") || it.contains("帮助")
        }
        // 核心特征必须全部满足，可选特征至少满足一个
        if ( (hasFaceLogin || hasSmsLogin) && hasOtherVerification && hasRegisterAccount && (hasChangeNumber || hasProblemHelp)) {
            return LoginDetectionResult(
                isLoginPage = true,
                confidence = 1.0f,
                reasons = listOf("必定命中规则4.3: 支付宝风格登录页（同时出现\"刷脸登录\"或\"短信登录\"、\"其他验证方式\"、\"注册账号\"及可选特征）")
            )
        }

        // 规则5: 同时出现"登录"、"密码"、"验证码"、"获取验证码"按钮
        val rule5Keywords = listOf("登录", "密码", "验证码", "获取验证码")
        val rule5Matches = rule5Keywords.count { keyword ->
            allTextsLower.any { it.contains(keyword) }
        }
        if (rule5Matches >= 4) {
            return LoginDetectionResult(
                isLoginPage = true,
                confidence = 1.0f,
                reasons = listOf("必定命中规则5: 同时出现\"登录\"、\"密码\"、\"验证码\"、\"获取验证码\"")
            )
        }

        // 规则6: 同时出现"账号"、"密码"、"登录"按钮、"记住密码"
        val rule6Keywords = listOf("账号", "密码", "登录", "记住密码")
        val rule6Matches = rule6Keywords.count { keyword ->
            allTextsLower.any { it.contains(keyword) }
        }
        if (rule6Matches >= 4) {
            return LoginDetectionResult(
                isLoginPage = true,
                confidence = 1.0f,
                reasons = listOf("必定命中规则6: 同时出现\"账号\"、\"密码\"、\"登录\"、\"记住密码\"")
            )
        }

        // 规则7: 同时出现"手机号"、"验证码"、"登录"、"本机号码一键登录"
        val rule7Keywords = listOf("手机号", "验证码", "登录", "本机号码一键登录")
        val rule7Matches = rule7Keywords.count { keyword ->
            allTextsLower.any { it.contains(keyword) }
        }
        if (rule7Matches >= 4) {
            return LoginDetectionResult(
                isLoginPage = true,
                confidence = 1.0f,
                reasons = listOf("必定命中规则7: 同时出现\"手机号\"、\"验证码\"、\"登录\"、\"本机号码一键登录\"")
            )
        }

        // 规则8: 同时出现"用户名"、"密码"、"登录"、"注册"
        val rule8Keywords = listOf("用户名", "密码", "登录", "注册")
        val rule8Matches = rule8Keywords.count { keyword ->
            allTextsLower.any { it.contains(keyword) }
        }
        if (rule8Matches >= 4) {
            return LoginDetectionResult(
                isLoginPage = true,
                confidence = 1.0f,
                reasons = listOf("必定命中规则8: 同时出现\"用户名\"、\"密码\"、\"登录\"、\"注册\"")
            )
        }

        // 规则9: 同时出现"账户"、"密码"、"登录"、"忘记密码"
        val rule9Keywords = listOf("账户", "密码", "登录", "忘记密码")
        val rule9Matches = rule9Keywords.count { keyword ->
            allTextsLower.any { it.contains(keyword) }
        }
        if (rule9Matches >= 4) {
            return LoginDetectionResult(
                isLoginPage = true,
                confidence = 1.0f,
                reasons = listOf("必定命中规则9: 同时出现\"账户\"、\"密码\"、\"登录\"、\"忘记密码\"")
            )
        }

        // 规则10: 同时出现"登录"、"密码输入框"、"登录按钮"（通过输入框类型和按钮检测）
        val hasPasswordInput = findInputFields(rootNode).any { field ->
            val text = (field.text ?: field.contentDescription ?: "").lowercase()
            listOf("密码", "password", "pwd").any { text.contains(it) }
        }
        val hasLoginButton = findLoginButtons(rootNode).isNotEmpty()
        val hasLoginText = allTextsLower.any { it.contains("登录") || it.contains("login") }
        if (hasPasswordInput && hasLoginButton && hasLoginText) {
            return LoginDetectionResult(
                isLoginPage = true,
                confidence = 1.0f,
                reasons = listOf("必定命中规则10: 同时出现密码输入框、登录按钮和登录文本")
            )
        }

        return null  // 不满足任何必定命中规则
    }

    /**
     * 收集所有可见节点的文本
     */
    private fun collectAllTexts(node: AccessibilityNode, maxCount: Int = 100, currentCount: Int = 0): List<String> {
        if (!node.isVisibleToUser || currentCount >= maxCount) return emptyList()

        val texts = mutableListOf<String>()
        
        node.text?.let { texts.add(it) }
        node.contentDescription?.let { texts.add(it) }

        node.children.filter { it.isVisibleToUser }.forEach { child ->
            if (currentCount + texts.size < maxCount) {
                texts.addAll(collectAllTexts(child, maxCount, currentCount + texts.size))
            }
        }

        return texts.filter { it.isNotBlank() }
    }

    /**
     * 检测登录页（更严格的判断）
     */
    @SuppressLint("SuspiciousIndentation")
    fun detectLoginPage(rootNode: AccessibilityNode): LoginDetectionResult {
        OmniLog.d(TAG, "开始检测登录页...")
        val reasons = mutableListOf<String>()
        var score = 0f
        var isLoginPage = false

        // 0. 优先检测必定命中规则（如果满足，直接返回）
        val forceMatchResult = checkForceMatchRules(rootNode)
        if (forceMatchResult != null) {
            OmniLog.w(TAG, "⚠️ 必定命中登录页！原因: ${forceMatchResult.reasons.joinToString("; ")}")
            return forceMatchResult
        }

        // 1. 检测登录相关文本（降低权重，避免单独文本就判定为登录页）
        val loginTextNodes = findLoginTexts(rootNode)
        if (loginTextNodes.isNotEmpty()) {
            val loginTextCount = loginTextNodes.size
            // 降低文本权重，需要更多文本才给高分
            val textScore = minOf(loginTextCount * 0.15f, 0.4f)
            score += textScore
            val sampleTexts = loginTextNodes.map { it.text ?: it.contentDescription }.filterNotNull().take(3)
            reasons.add("检测到 $loginTextCount 个登录相关文本: $sampleTexts")
            OmniLog.d(TAG, "检测到登录相关文本: ${loginTextNodes.size} 个")
        }

        // 2. 检测特殊登录场景（异地登录、一键登录、短信验证码登录）
        val specialLoginScenarios = detectSpecialLoginScenarios(rootNode)
        if (specialLoginScenarios.isNotEmpty()) {
            score += 0.5f  // 特殊场景权重较高
            reasons.addAll(specialLoginScenarios)
            OmniLog.d(TAG, "检测到特殊登录场景: ${specialLoginScenarios.joinToString("; ")}")
        }

        // 3. 检测输入框（密码、用户名、验证码等）- 更严格的判断
        val inputFields = findInputFields(rootNode)
        val passwordInputFields = inputFields.filter { field ->
            val text = (field.text ?: field.contentDescription ?: "").lowercase()
            listOf("密码", "password", "pwd", "pass").any { keyword ->
                Regex("\\b$keyword\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
            }
        }
        
        if (inputFields.isNotEmpty()) {
            // 如果有密码输入框，权重更高（密码是登录页最典型的特征）
            if (passwordInputFields.isNotEmpty()) {
                score += 0.4f
                reasons.add("检测到 ${passwordInputFields.size} 个密码输入框（关键特征）")
                OmniLog.d(TAG, "检测到密码输入框: ${passwordInputFields.size} 个")
            } else {
                // 没有密码输入框，只给较低权重
                val inputScore = minOf(inputFields.size * 0.15f, 0.3f)
            score += inputScore
                reasons.add("检测到 ${inputFields.size} 个登录相关输入框（无密码输入框）")
                OmniLog.d(TAG, "检测到输入框: ${inputFields.size} 个（无密码输入框）")
            }
        }

        // 4. 检测登录按钮（必须与输入框或登录文本在同一上下文中）
        val loginButtons = findLoginButtons(rootNode)
        if (loginButtons.isNotEmpty()) {
            // 检查登录按钮是否与输入框或登录文本在合理范围内
            val hasContextualRelation = checkContextualRelation(loginButtons, inputFields, loginTextNodes)
            if (hasContextualRelation) {
            score += 0.3f
            val buttonTexts = loginButtons.map { it.text ?: it.contentDescription }.filterNotNull().take(2)
                reasons.add("检测到登录按钮（有上下文关联）: $buttonTexts")
                OmniLog.d(TAG, "检测到登录按钮: ${loginButtons.size} 个（有上下文关联）")
            } else {
                // 没有上下文关联，只给较低权重
                score += 0.1f
                reasons.add("检测到登录按钮（无上下文关联）: ${loginButtons.size} 个")
                OmniLog.d(TAG, "检测到登录按钮: ${loginButtons.size} 个（无上下文关联）")
            }
        }

        // 5. 检测验证码相关按钮（发送验证码、获取验证码等）
        val verificationCodeButtons = findVerificationCodeButtons(rootNode)
        if (verificationCodeButtons.isNotEmpty()) {
            // 验证码按钮必须与验证码输入框在同一上下文中
            val hasVerificationCodeInput = inputFields.any { field ->
                val text = (field.text ?: field.contentDescription ?: "").lowercase()
                listOf("验证码", "verification code", "verify code", "captcha", "sms code").any { keyword ->
                    Regex("\\b$keyword\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
                }
            }
            if (hasVerificationCodeInput) {
            score += 0.25f
                reasons.add("检测到验证码相关按钮（有验证码输入框）: ${verificationCodeButtons.size} 个")
                OmniLog.d(TAG, "检测到验证码相关按钮: ${verificationCodeButtons.size} 个（有验证码输入框）")
            } else {
                // 没有验证码输入框，只给较低权重
                score += 0.1f
                reasons.add("检测到验证码相关按钮（无验证码输入框）: ${verificationCodeButtons.size} 个")
                OmniLog.d(TAG, "检测到验证码相关按钮: ${verificationCodeButtons.size} 个（无验证码输入框）")
            }
        }

        // 6. 综合判断：提高阈值，要求更多证据
        // 如果有密码输入框，阈值可以稍低；如果没有密码输入框，需要更高的分数
        val threshold = if (passwordInputFields.isNotEmpty()) {
            0.75f  // 有密码输入框时，阈值稍低
        } else {
            0.85f  // 没有密码输入框时，需要更高的分数和更多证据
        }
        
        // 额外要求：如果没有密码输入框，必须同时有登录按钮和登录文本
        if (passwordInputFields.isEmpty() && (loginButtons.isEmpty() || loginTextNodes.isEmpty())) {
            score = 0f  // 重置分数，不认为是登录页
            reasons.add("缺少关键特征：无密码输入框且缺少登录按钮或登录文本")
            OmniLog.d(TAG, "缺少关键特征，不判定为登录页")
        }
        
        isLoginPage = score >= threshold

        if (isLoginPage) {
            OmniLog.w(TAG, "⚠️ 检测到登录页！可信度: $score, 原因: ${reasons.joinToString("; ")}")
        } else {
            OmniLog.d(TAG, "未检测到登录页，可信度: $score, 阈值: $threshold")
        }

        return LoginDetectionResult(
            isLoginPage = isLoginPage,
            confidence = minOf(score, 1.0f),
            reasons = reasons
        )
    }

    /**
     * 检查登录按钮是否与输入框或登录文本在合理的上下文中
     */
    private fun checkContextualRelation(
        loginButtons: List<AccessibilityNode>,
        inputFields: List<AccessibilityNode>,
        loginTextNodes: List<AccessibilityNode>
    ): Boolean {
        if (loginButtons.isEmpty()) return false
        
        // 如果同时有输入框和登录文本，认为有上下文关联
        if (inputFields.isNotEmpty() && loginTextNodes.isNotEmpty()) {
            return true
        }
        
        // 检查登录按钮是否与输入框在屏幕上的距离较近（同一区域）
        if (inputFields.isNotEmpty()) {
            val buttonBounds = loginButtons.first().boundsInScreen
            val inputBounds = inputFields.first().boundsInScreen
            
            // 计算垂直距离，如果按钮在输入框下方500像素内，认为有关联
            val verticalDistance = kotlin.math.abs(buttonBounds.top - inputBounds.bottom)
            if (verticalDistance < 500) {
                return true
            }
        }
        
        return false
    }

    /**
     * 查找登录相关文本（使用预编译正则表达式，避免重复创建）
     * 限制最大查找数量，避免遍历过多节点
     */
    private fun findLoginTexts(node: AccessibilityNode, maxCount: Int = 10, currentCount: Int = 0): List<AccessibilityNode> {
        if (!node.isVisibleToUser || currentCount >= maxCount) return emptyList()

        val loginNodes = mutableListOf<AccessibilityNode>()

        val text = node.text ?: ""
        val contentDesc = node.contentDescription ?: ""
        val combinedText = "$text $contentDesc".lowercase()

        // 使用预编译的正则表达式，避免重复创建
        if (loginKeywordRegexes.any { it.containsMatchIn(combinedText) }) {
            loginNodes.add(node)
            if (loginNodes.size >= maxCount) {
                return loginNodes  // 已找到足够的文本，提前终止
            }
        }

        // 只遍历可见的子节点
        node.children.filter { it.isVisibleToUser }.forEach { child ->
            if (currentCount + loginNodes.size < maxCount) {
                loginNodes.addAll(findLoginTexts(child, maxCount, currentCount + loginNodes.size))
            }
        }

        return loginNodes
    }

    // 预编译输入框相关的正则表达式
    private val passwordRegexes = listOf(
        Regex("\\b密码\\b", RegexOption.IGNORE_CASE),
        Regex("\\bpassword\\b", RegexOption.IGNORE_CASE),
        Regex("\\bpwd\\b", RegexOption.IGNORE_CASE),
        Regex("\\bpass\\b", RegexOption.IGNORE_CASE)
    )
    private val usernameRegexes = listOf(
        Regex("\\b用户名\\b", RegexOption.IGNORE_CASE),
        Regex("\\buser name\\b", RegexOption.IGNORE_CASE),
        Regex("\\busername\\b", RegexOption.IGNORE_CASE),
        Regex("\\b账号\\b", RegexOption.IGNORE_CASE),
        Regex("\\b账户\\b", RegexOption.IGNORE_CASE),
        Regex("\\baccount\\b", RegexOption.IGNORE_CASE)
    )
    private val phoneRegexes = listOf(
        Regex("\\b手机号\\b", RegexOption.IGNORE_CASE),
        Regex("\\b手机号码\\b", RegexOption.IGNORE_CASE),
        Regex("\\bphone number\\b", RegexOption.IGNORE_CASE),
        Regex("\\bmobile number\\b", RegexOption.IGNORE_CASE),
        Regex("\\btelephone number\\b", RegexOption.IGNORE_CASE),
        Regex("\\btel\\b", RegexOption.IGNORE_CASE)
    )
    private val verificationCodeRegexes = listOf(
        Regex("\\b验证码\\b", RegexOption.IGNORE_CASE),
        Regex("\\bverification code\\b", RegexOption.IGNORE_CASE),
        Regex("\\bverify code\\b", RegexOption.IGNORE_CASE),
        Regex("\\bcaptcha\\b", RegexOption.IGNORE_CASE),
        Regex("\\b短信验证码\\b", RegexOption.IGNORE_CASE),
        Regex("\\bsms code\\b", RegexOption.IGNORE_CASE),
        Regex("\\b验证码输入\\b", RegexOption.IGNORE_CASE)
    )

    /**
     * 查找输入框（更严格的判断，使用预编译正则表达式）
     * 限制最大查找数量，避免遍历过多节点
     */
    private fun findInputFields(node: AccessibilityNode, maxCount: Int = 5, currentCount: Int = 0): List<AccessibilityNode> {
        if (!node.isVisibleToUser || currentCount >= maxCount) return emptyList()

        val inputNodes = mutableListOf<AccessibilityNode>()

        // 检查是否是输入框类型（先做简单的contains检查，避免不必要的正则）
        val className = node.className.lowercase()
        val isInputField = inputFieldClassNames.any { className.contains(it.lowercase()) }

        if (!isInputField) {
            // 如果不是输入框类型，继续检查子节点（只检查可见节点）
            node.children.filter { it.isVisibleToUser }.forEach { child ->
                if (currentCount + inputNodes.size < maxCount) {
                    inputNodes.addAll(findInputFields(child, maxCount, currentCount + inputNodes.size))
                }
            }
            return inputNodes
        }

        // 对于输入框，需要更严格的判断
        val text = node.text ?: ""
        val contentDesc = node.contentDescription ?: ""
        val combinedText = "$text $contentDesc".lowercase()

        // 使用预编译的正则表达式
        val hasPasswordHint = passwordRegexes.any { it.containsMatchIn(combinedText) }
        val hasUsernameHint = usernameRegexes.any { it.containsMatchIn(combinedText) }
        val hasPhoneHint = phoneRegexes.any { it.containsMatchIn(combinedText) }
        val hasVerificationCodeHint = verificationCodeRegexes.any { it.containsMatchIn(combinedText) }

        // 只有明确匹配到登录相关的输入框类型才认为是登录输入框
        if (hasPasswordHint || hasUsernameHint || hasPhoneHint || hasVerificationCodeHint) {
            inputNodes.add(node)
            // 如果找到密码输入框，可以提前终止（密码是最关键的特征）
            if (hasPasswordHint) {
                return inputNodes
            }
        }

        // 只遍历可见的子节点
        node.children.filter { it.isVisibleToUser }.forEach { child ->
            if (currentCount + inputNodes.size < maxCount) {
                inputNodes.addAll(findInputFields(child, maxCount, currentCount + inputNodes.size))
            }
        }

        return inputNodes
    }

    // 预编译登录按钮关键词（用于简单匹配）
    private val loginButtonKeywords = listOf(
        "登录", "登陆", "登入", "sign in", "login",
        "立即登录", "马上登录", "开始登录", "确认登录",
        "一键登录", "本机号码一键登录", "免密登录"
    )

    /**
     * 查找登录按钮（优化：减少查找数量，使用简单字符串匹配）
     * 限制最大查找数量，避免遍历过多节点
     */
    private fun findLoginButtons(node: AccessibilityNode, maxCount: Int = 3, currentCount: Int = 0): List<AccessibilityNode> {
        if (!node.isVisibleToUser || !node.isClickable || currentCount >= maxCount) return emptyList()

        val buttonNodes = mutableListOf<AccessibilityNode>()

        val text = node.text?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.lowercase() ?: ""
        val combinedText = "$text $contentDesc"

        // 使用简单的contains检查，避免正则表达式
        if (loginButtonKeywords.any { combinedText.contains(it.lowercase()) }) {
            buttonNodes.add(node)
            // 登录按钮通常只有一个，找到后立即终止
            return buttonNodes
        }

        // 只遍历可见且可点击的子节点
        node.children.filter { it.isVisibleToUser && it.isClickable }.forEach { child ->
            if (currentCount + buttonNodes.size < maxCount) {
                buttonNodes.addAll(findLoginButtons(child, maxCount, currentCount + buttonNodes.size))
            }
        }

        return buttonNodes
    }

    /**
     * 检测特殊登录场景（异地登录、一键登录、短信验证码登录）
     * 限制最大检测数量，避免遍历过多节点
     */
    private fun detectSpecialLoginScenarios(node: AccessibilityNode, maxScenarios: Int = 2, currentCount: Int = 0): List<String> {
        if (!node.isVisibleToUser || currentCount >= maxScenarios) return emptyList()

        val scenarios = mutableListOf<String>()
        val text = node.text?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.lowercase() ?: ""
        val combinedText = "$text $contentDesc"

        // 检测异地登录场景（使用简单的contains检查）
        val remoteLoginKeywords = listOf(
            "在其他地方登录", "在其他设备登录", "异地登录", "new device login",
            "检测到异常登录", "unusual login", "登录异常", "login anomaly",
            "为了您的账户安全", "account security", "安全提醒", "security alert"
        )
        if (remoteLoginKeywords.any { combinedText.contains(it.lowercase()) }) {
            scenarios.add("检测到异地登录/安全验证场景")
            if (scenarios.size >= maxScenarios) {
                return scenarios
            }
        }

        // 检测一键登录场景
        val oneClickLoginKeywords = listOf(
            "一键登录", "本机号码一键登录", "免密登录", "one click login", "quick login",
            "本机号码", "当前手机号", "使用本机号码"
        )
        if (oneClickLoginKeywords.any { combinedText.contains(it.lowercase()) }) {
            scenarios.add("检测到一键登录场景")
            if (scenarios.size >= maxScenarios) {
                return scenarios
            }
        }

        // 检测短信验证码登录场景
        val smsLoginKeywords = listOf(
            "短信登录", "验证码登录", "短信验证码", "sms login", "sms code",
            "验证码已发送", "code sent", "请输入验证码", "enter verification code"
        )
        if (smsLoginKeywords.any { combinedText.contains(it.lowercase()) }) {
            scenarios.add("检测到短信验证码登录场景")
            if (scenarios.size >= maxScenarios) {
                return scenarios
            }
        }

        // 只遍历可见的子节点，限制遍历深度
        if (scenarios.size < maxScenarios) {
            node.children.filter { it.isVisibleToUser }.take(20).forEach { child ->
                if (scenarios.size < maxScenarios) {
                    scenarios.addAll(detectSpecialLoginScenarios(child, maxScenarios, scenarios.size))
                }
            }
        }

        return scenarios
    }

    /**
     * 查找验证码相关按钮（发送验证码、获取验证码等）
     * 限制最大查找数量，避免遍历过多节点
     */
    private fun findVerificationCodeButtons(node: AccessibilityNode, maxCount: Int = 3, currentCount: Int = 0): List<AccessibilityNode> {
        if (!node.isVisibleToUser || !node.isClickable || currentCount >= maxCount) return emptyList()

        val buttonNodes = mutableListOf<AccessibilityNode>()

        val text = node.text?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.lowercase() ?: ""

        val verificationCodeButtonKeywords = listOf(
            "获取验证码", "发送验证码", "重新获取", "get verification code", "send code",
            "获取短信验证码", "发送短信", "resend code", "重新发送"
        )

        if (verificationCodeButtonKeywords.any { keyword ->
                text.contains(keyword.lowercase()) || contentDesc.contains(keyword.lowercase())
            }) {
            buttonNodes.add(node)
            // 验证码按钮通常只有一个，找到后可以提前终止
            if (buttonNodes.size >= maxCount) {
                return buttonNodes
            }
        }

        // 只遍历可见且可点击的子节点
        node.children.filter { it.isVisibleToUser && it.isClickable }.forEach { child ->
            if (currentCount + buttonNodes.size < maxCount) {
                buttonNodes.addAll(findVerificationCodeButtons(child, maxCount, currentCount + buttonNodes.size))
            }
        }

        return buttonNodes
    }
}

