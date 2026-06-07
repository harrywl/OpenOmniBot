package cn.com.omnimind.uikit.api.callback

interface CatApi {
    fun onCatLongPressInDoingTask()//任务中长按小猫
    fun onCatLongPress()//长按小猫
    fun onCatDoubleClick()//双击小猫
    fun onCatClick(x:Int,y:Int)//单击小猫
    fun onCloseChatBotDialog()//关闭聊天机器人对话框
}