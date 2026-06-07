package cn.com.omnimind.assists.detection.core

import android.graphics.Rect
import android.util.Xml
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import cn.com.omnimind.accessibility.action.AccessibilityNode
import cn.com.omnimind.accessibility.action.OmniScreenshotAction
import cn.com.omnimind.accessibility.action.XmlTreeNode
import cn.com.omnimind.accessibility.service.AssistsService
import cn.com.omnimind.baselib.util.OmniLog
import java.io.StringWriter

/**
 * Detection 模块核心 XML 构建工具
 *
 * 提供严格的节点过滤逻辑（适用于各种检测场景）：
 * - 只包含 isEnabled == true 的节点
 * - 只包含 isClickable == true 或有 CLICK action 的节点
 * - 必须 isVisibleToUser == true
 *
 * 包含的属性完整，适合用于精确检测：
 * - enabled, class-name, resource-id 等关键属性
 */
object DetectionXmlBuilder {
    private const val TAG = "DetectionXmlBuilder"

    /**
     * 构建严格过滤的 XML 树
     */
    fun buildXmlTree(root: AccessibilityNodeInfo?): XmlTreeNode? =
        buildRecursive(root, 0, visitedNodes = mutableSetOf(), depth = 0).first

    /**
     * 递归构建 XML 树（严格过滤逻辑）
     */
    private fun buildRecursive(
        node: AccessibilityNodeInfo?,
        currentId: Int,
        visitedNodes: MutableSet<AccessibilityNodeInfo> = mutableSetOf(),
        depth: Int = 0
    ): Pair<XmlTreeNode?, Int> {
        // 添加最大深度限制，防止过深的递归调用
        val maxDepth = 50
        if (depth > maxDepth) {
            OmniLog.w(TAG, "Maximum recursion depth reached: $maxDepth")
            return null to currentId
        }

        // 检查节点是否已经访问过，防止循环引用导致的无限递归
        if (node != null && visitedNodes.contains(node)) {
            OmniLog.w(TAG, "Circular reference detected in accessibility tree")
            return null to currentId
        }

        // 基础过滤：可见性检查
        if (node == null || (!node.isVisibleToUser && currentId != 0)) {
            return null to currentId
        }

        // 将当前节点添加到已访问节点集合中
        if (node != null) {
            visitedNodes.add(node)
        }

        val nodeId = currentId.toString()
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        // 严格过滤逻辑（适用于各种检测场景）
        // 检查 hasClickAction
        fun hasClickAction(n: AccessibilityNodeInfo): Boolean {
            val has = n.actionList?.any { it.id == AccessibilityNodeInfo.ACTION_CLICK } == true
            return n.isClickable || has
        }

        // 对于非根节点，应用严格的过滤条件
        val shouldInclude = if (currentId == 0) {
            // 根节点始终包含
            true
        } else {
            // 非根节点必须满足：isEnabled && hasClickAction
            node.isEnabled && hasClickAction(node)
        }

        val hasText = !node.text.isNullOrEmpty()
        val interactive = node.isClickable || node.isLongClickable || node.isFocusable ||
                          node.isFocused || node.isScrollable || node.isPassword ||
                          node.isSelected || node.isEditable

        // show 逻辑：只显示满足严格条件的节点或根节点
        // 不包含纯文本节点
        val show = if (currentId == 0) {
            true  // 根节点始终显示
        } else {
            shouldInclude  // 非根节点必须满足严格条件
        }

        var nextId = currentId + 1
        val children = mutableListOf<XmlTreeNode>()
        for (i in 0 until node.childCount) {
            // 获取子节点时添加异常处理
            val child = try {
                node.getChild(i)
            } catch (e: Exception) {
                OmniLog.w(TAG, "Failed to get child at index $i: ${e.message}")
                continue
            }

            // 递归调用时传递visitedNodes和增加depth
            val (childTree, newId) = buildRecursive(child, nextId, visitedNodes, depth + 1)
            if (childTree != null) {
                children.add(childTree)
                nextId = newId
            }
        }

        // 从已访问节点集合中移除当前节点（确保在其他路径中可以再次访问）
        if (node != null) {
            visitedNodes.remove(node)
        }

        return XmlTreeNode(
            id = nodeId,
            node = AccessibilityNode(
                info = node,
                bounds = bounds,
                show = show,
                interactive = interactive,
            ),
            children = children,
        ) to nextId
    }

    /**
     * 序列化 XML 树（包含完整属性）
     */
    fun serializeXml(tree: XmlTreeNode): String {
        val writer = StringWriter()
        val namespace = "http://schemas.android.com/apk/res/android"
        val serializer = Xml.newSerializer().apply {
            setOutput(writer)
            startDocument("UTF-8", true)
            setPrefix("", namespace)
            startTag(namespace, "hierarchy")
        }

        fun addAttr(
            name: String,
            value: String?,
        ) {
            if (!value.isNullOrEmpty() && value != "false") {
                serializer.attribute(null, name, value)
            }
        }

        fun sanitizeXmlString(text: String?): String? {
            if (text == null) return null
            // This regex matches any character that is NOT a valid XML 1.0 character.
            val illegalXmlCharRegex = "[^\\u0009\\u000A\\u000D\\u0020-\\uD7FF\\uE000-\\uFFFD]"
            return text.replace(Regex(illegalXmlCharRegex), "")
        }

        fun serializeNode(node: XmlTreeNode) {
            if (node.node.show) {
                val n = node.node.info
                val bounds = node.node.bounds
                serializer.startTag(null, "node")
                serializer.attribute(null, "id", node.id)
                // Sanitize text-based attributes before adding them
                addAttr("text", sanitizeXmlString(n.text?.toString()))
                addAttr("content-desc", sanitizeXmlString(n.contentDescription?.toString()))
                addAttr("clickable", n.isClickable.toString())
                addAttr("long-clickable", n.isLongClickable.toString())
                addAttr("focusable", n.isFocusable.toString())
                addAttr("focused", n.isFocused.toString())
                addAttr("scrollable", n.isScrollable.toString())
                addAttr("password", n.isPassword.toString())
                addAttr("selected", n.isSelected.toString())
                addAttr("editable", n.isEditable.toString())
                // Detection 核心属性（用于精确检测）
                addAttr("enabled", n.isEnabled.toString())
                addAttr("class-name", sanitizeXmlString(n.className?.toString()))
                addAttr("resource-id", sanitizeXmlString(n.viewIdResourceName))
                serializer.attribute(
                    null,
                    "bounds",
                    "[${bounds.left},${bounds.top}][${bounds.right},${bounds.bottom}]",
                )
                node.children.forEach { serializeNode(it) }
                serializer.endTag(null, "node")
            } else {
                node.children.forEach { serializeNode(it) }
            }
        }

        serializeNode(tree)

        serializer.endTag(namespace, "hierarchy")
        serializer.endDocument()
        return writer.toString()
    }

    /**
     * 获取实际的根节点（优先获取 TYPE_APPLICATION 类型的窗口）
     */
    private fun getActualRootNode(service: AssistsService): AccessibilityNodeInfo? {
        val windows = service.windows
        windows?.forEach { window ->
            // 排除悬浮窗类型的窗口
            if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                val root = window.root
                if (root != null) {
                    return root
                }
            }
        }
        return null
    }
}

/**
 * OmniScreenshot 的扩展函数，用于生成 Detection XML（与 .xmlCatcher 的当前窗口XML一致）
 * 直接使用 rootInActiveWindow，序列化所有节点（无过滤）
 */
fun OmniScreenshotAction.captureDetectionXml(): String? {
    // 使用反射获取 service 字段
    val serviceField = this.javaClass.getDeclaredField("service").apply {
        isAccessible = true
    }
    val service = serviceField.get(this) as AssistsService

    // 直接使用 rootInActiveWindow（与 .xmlCatcher 一致）
    val rootNode = service.rootInActiveWindow ?: return null

    // 构建 XML（序列化所有节点）
    val writer = StringWriter()
    val serializer = Xml.newSerializer().apply {
        setOutput(writer)
        startDocument("UTF-8", true)
        startTag(null, "hierarchy")
        attribute(null, "title", "Detection XML")
    }

    serializeAllNodes(serializer, rootNode)

    serializer.endTag(null, "hierarchy")
    serializer.endDocument()
    return writer.toString()
}

/**
 * 序列化所有节点（无过滤，与 .xmlCatcher 一致）
 */
private fun serializeAllNodes(
    serializer: org.xmlpull.v1.XmlSerializer,
    node: AccessibilityNodeInfo
) {
    serializer.startTag(null, "node")

    // 添加节点属性
    node.className?.let { serializer.attribute(null, "class", it.toString()) }
    node.viewIdResourceName?.let { serializer.attribute(null, "resource-id", it) }
    node.text?.let { serializer.attribute(null, "text", sanitizeXml(it.toString())) }
    node.contentDescription?.let {
        serializer.attribute(null, "content-desc", sanitizeXml(it.toString()))
    }

    val bounds = Rect()
    node.getBoundsInScreen(bounds)
    serializer.attribute(null, "bounds",
        "[${bounds.left},${bounds.top}][${bounds.right},${bounds.bottom}]")

    if (node.isClickable) serializer.attribute(null, "clickable", "true")
    if (node.isLongClickable) serializer.attribute(null, "long-clickable", "true")
    if (node.isScrollable) serializer.attribute(null, "scrollable", "true")
    if (node.isEditable) serializer.attribute(null, "editable", "true")
    if (node.isFocusable) serializer.attribute(null, "focusable", "true")
    if (node.isFocused) serializer.attribute(null, "focused", "true")
    if (node.isSelected) serializer.attribute(null, "selected", "true")
    if (node.isEnabled) serializer.attribute(null, "enabled", "true")
    if (node.isPassword) serializer.attribute(null, "password", "true")
    if (node.isCheckable) serializer.attribute(null, "checkable", "true")
    if (node.isChecked) serializer.attribute(null, "checked", "true")
    if (node.isVisibleToUser) serializer.attribute(null, "visible-to-user", "true")

    // 递归处理子节点
    for (i in 0 until node.childCount) {
        try {
            node.getChild(i)?.let { child ->
                serializeAllNodes(serializer, child)
                child.recycle()
            }
        } catch (e: Exception) {
            OmniLog.w("DetectionXmlBuilder", "Failed to serialize child at index $i: ${e.message}")
        }
    }

    serializer.endTag(null, "node")
}

/**
 * 清理 XML 非法字符
 */
private fun sanitizeXml(text: String): String {
    val illegalXmlCharRegex = "[^\\u0009\\u000A\\u000D\\u0020-\\uD7FF\\uE000-\\uFFFD]"
    return text.replace(Regex(illegalXmlCharRegex), "")
}
