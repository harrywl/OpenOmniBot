package cn.com.omnimind.uikit.view.data

/**
 * CatDialogShowInfoView 的视图状态枚举
 */
enum class CatDialogViewState {
    /**
     * 隐藏状态
     */
    EMPTY,
    /**
     * 消息状态
     */
    MESSAGE_INFO,

    /**
     * 任务执行状态
     */
    TASK_DOING,

    /**
     * 用户接管状态
     */
    USER_INFO,

    /**
     * 用户接管后隐藏的状态
     */
    USER_INFO_HINT,
}

