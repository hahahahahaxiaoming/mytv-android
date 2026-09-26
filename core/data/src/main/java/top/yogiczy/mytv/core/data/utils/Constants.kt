package top.yogiczy.mytv.core.data.utils

import top.yogiczy.mytv.core.data.entities.epgsource.EpgSource
import top.yogiczy.mytv.core.data.entities.epgsource.EpgSourceList
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSourceList

/**
 * 常量
 */
object Constants {
    /** 远程IPTV直播源配置 */
    const val IPTV_SOURCE_SETTING_URL =
        "https://gitee.com/giteesnail/iptv/raw/master/iptv.setting"

    /** 远程全国直播列表（iptv 仓库 webview.txt，网页兜底内容源） */
    const val WEB_FALLBACK_LIST_URL =
        "https://gitee.com/giteesnail/iptv/raw/master/webview.txt"

    /**
     * 应用 标题
     */
    const val APP_TITLE = "TV"

    /**
     * 应用 代码仓库
     */
    const val APP_REPO = "https://github.com/hahahahahaxiaoming/mytv-android"

    /**
     * IPTV直播源
     */
    val IPTV_SOURCE_LIST = IptvSourceList(
        listOf(
//            IptvSource(
//                name = "默认直播源",
//                url = "NULL",
//            ),
        )
    )

    /**
     * 全国直播兜底源：本地列表为空/注销 NULL 源时作为当前源，
     * url=NULL 触发网页模式（从云端 webview.txt 拉取频道）。
     * 恢复本地 NULL 源后，该逻辑同样生效。
     */
    val WEB_FALLBACK_SOURCE = IptvSource(name = "全国直播", url = "NULL")

    /**
     * IPTV源缓存时间（毫秒）
     */
    const val IPTV_SOURCE_CACHE_TIME = 1000 * 60 * 60 * 24L // 24小时

    /**
     * 节目单来源
     */
    val EPG_SOURCE_LIST = EpgSourceList(
        listOf(
            EpgSource(
                name = "默认节目单 老张的EPG",
                url = "http://epg.51zmt.top:8000/e.xml.gz",
            ),
            EpgSource(
                name = "默认节目单 回看七天",
                url = "https://e.erw.cc/all.xml.gz",
            ),
        )
    )

    /**
     * 节目单刷新时间阈值（小时）
     */
    const val EPG_REFRESH_TIME_THRESHOLD = 2 // 不到2点不刷新

    /**
     * Git最新版本信息
     */
    val GIT_RELEASE_LATEST_URL = mapOf(
        "stable" to "https://api.github.com/repos/hahahahahaxiaoming/mytv-android/releases/latest",
        "beta" to "https://api.github.com/repos/hahahahahaxiaoming/mytv-android/releases/latest",
    )

    /**
     * GitHub加速代理地址
     */
    const val GITHUB_PROXY = "https://ghp.ci/"

    /**
     * HTTP请求重试次数
     */
    const val HTTP_RETRY_COUNT = 10L

    /**
     * HTTP请求重试间隔时间（毫秒）
     */
    const val HTTP_RETRY_INTERVAL = 3000L

    /**
     * 播放器 userAgent
     */
    const val VIDEO_PLAYER_USER_AGENT = "ExoPlayer"

    /**
     * 播放器加载超时
     */
    const val VIDEO_PLAYER_LOAD_TIMEOUT = 1000L * 15 // 15秒

    /**
     * 日志历史最大保留条数
     */
    const val LOG_HISTORY_MAX_SIZE = 50

    /**
     * 界面 临时频道界面显示时间
     */
    const val UI_TEMP_CHANNEL_SCREEN_SHOW_DURATION = 1500L // 1.5秒

    /**
     * 界面 超时未操作自动关闭界面
     */
    const val UI_SCREEN_AUTO_CLOSE_DELAY = 1000L * 15 // 15秒

    /**
     * 界面 时间显示前后范围
     */
    const val UI_TIME_SCREEN_SHOW_DURATION = 1000L * 30 // 前后30秒
}
