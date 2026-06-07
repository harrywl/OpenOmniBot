package com.google.android.accessibility.selecttospeak

import cn.com.omnimind.accessibility.service.AssistsService

/**
 * 伪装成 Google 官方 SelectToSpeak 无障碍服务。
 *
 * 微信等应用会检测当前运行的无障碍服务列表，如果发现第三方无障碍服务，
 * 就会混淆/隐藏无障碍节点信息，导致无法正常操作。
 *
 * 通过使用 Google 官方包名 + 类名，让目标应用将本服务识别为系统级辅助功能，
 * 从而绕过反无障碍检测机制。
 *
 * 技术来源：
 * https://github.com/ven-coder/Assists
 * https://github.com/ven-coder/Assists/issues/12#issuecomment-2684469065
 */
class SelectToSpeakService : AssistsService()
