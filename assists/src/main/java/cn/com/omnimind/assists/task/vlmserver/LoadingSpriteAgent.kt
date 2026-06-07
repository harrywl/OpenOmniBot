package cn.com.omnimind.assists.task.vlmserver

import cn.com.omnimind.baselib.llm.ModelSceneRegistry
import cn.com.omnimind.baselib.util.OmniLog
import cn.com.omnimind.assists.controller.http.HttpController
import cn.com.omnimind.omniintelligence.models.AgentRequest.Payload.VLMChatPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loading Sprite Agent - 赛博精灵加载状态生成器
 * 在任务开始时根据任务目标一次性生成多个加载提示词
 * Compactor 工作期间按顺序取用
 */
class LoadingSpriteAgent {
    private val Tag = "LoadingSpriteAgent"
    private val sceneId = "scene.loading.sprite"

    // 当前任务的加载词列表
    private var loadingPhrases: MutableList<String> = mutableListOf()
    private var currentIndex = 0

    // 预设的本地候选词库（当 LLM 请求失败时使用）
    private val fallbackPhrases = listOf(
        "正在给比特流打蝴蝶结",
        "把乱码梳理成麻花辫",
        "炼化逻辑金丹中",
        "正在把 0 和 1 熬成浓汤",
        "翻箱倒柜找灵感",
        "正在听硬盘窃窃私语",
        "脑回路打结中",
        "正在安抚暴躁的晶体管",
        "神游太虚收集碎片",
        "絮絮叨叨整理思绪",
        "正在编织数据蛛网",
        "叽里咕噜念咒语",
        "抓耳挠腮想对策",
        "正在给像素点排队",
        "左顾右盼找出路"
    )

    /**
     * 根据任务目标预生成加载提示词列表
     * 应在任务开始时调用
     * @param taskGoal 用户的任务目标
     */
    suspend fun prepareForTask(taskGoal: String) {
        currentIndex = 0
        loadingPhrases.clear()

        try {
            OmniLog.i(Tag, "为任务预生成加载提示词: $taskGoal")
            
            // 获取 prompt 模板并渲染
            val promptTemplate = ModelSceneRegistry.getPrompt(sceneId)
            if (promptTemplate == null) {
                OmniLog.w(Tag, "未找到 $sceneId 的 prompt 模板，使用本地候选")
                loadingPhrases.addAll(fallbackPhrases.shuffled().take(5))
                return
            }

            val prompt = ModelSceneRegistry.renderPrompt(
                promptTemplate,
                mapOf("task" to taskGoal)
            )

            // 调用 LLM 生成
            val payload = VLMChatPayload(
                model = sceneId,
                text = prompt,
                images = emptyList()
            )

            val response = withContext(Dispatchers.IO) {
                HttpController.postVLMRequest(payload)
            }

            val content = response?.message?.trim()
            if (!content.isNullOrBlank()) {
                // 解析多行输出
                val phrases = content.lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() && it.length in 4..20 }
                    .take(5)
                
                if (phrases.isNotEmpty()) {
                    loadingPhrases.addAll(phrases)
                    OmniLog.i(Tag, "预生成了 ${loadingPhrases.size} 个加载提示词: $loadingPhrases")
                    return
                }
            }

            // 如果解析失败，使用本地候选
            loadingPhrases.addAll(fallbackPhrases.shuffled().take(5))
            OmniLog.w(Tag, "LLM 返回格式异常，使用本地候选")

        } catch (e: Exception) {
            OmniLog.e(Tag, "预生成加载提示词失败: ${e.message}")
            loadingPhrases.addAll(fallbackPhrases.shuffled().take(5))
        }
    }

    /**
     * 获取下一个加载提示词
     * 按顺序返回，用完后循环
     * @return 加载提示词
     */
    fun getNextPhrase(): String {
        if (loadingPhrases.isEmpty()) {
            return fallbackPhrases.random()
        }

        val phrase = loadingPhrases[currentIndex]
        currentIndex = (currentIndex + 1) % loadingPhrases.size
        return phrase
    }

    /**
     * 重置索引（用于新一轮循环）
     */
    fun reset() {
        currentIndex = 0
    }
}
