package top.yogiczy.mytv.core.data.repositories.iptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroup
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelList
import top.yogiczy.mytv.core.data.network.await
import top.yogiczy.mytv.core.data.utils.ChannelUtil
import top.yogiczy.mytv.core.data.utils.Constants

/**
 * 云端全国直播列表（iptv 仓库 webview.txt）获取与解析。
 * 实时拉取、不做本地缓存：获取失败即抛错，由调用方决定兜底（禁止本地内置列表播放）。
 */
class WebFallbackRepository {
    suspend fun fetchChannelGroupList(): ChannelGroupList {
        val request = Request.Builder().url(Constants.WEB_FALLBACK_LIST_URL).build()
        val response = OkHttpClient().newCall(request).await()

        response.use {
            if (!it.isSuccessful) throw Exception("${it.code}: ${it.message}")
            val data = withContext(Dispatchers.IO) { it.body?.string().orEmpty() }
            return parse(data)
        }
    }

    companion object {
        private val channelBlockRegex =
            Regex("\"([^\"]+)\"\\s*to\\s*listOf\\s*\\((.*?)\\)", RegexOption.DOT_MATCHES_ALL)
        private val urlRegex = Regex("\"(https?://[^\"]+)\"")

        /**
         * 解析 webview.txt（Kotlin map 风格）：
         * "频道名" to listOf("线路1", "线路2", ...),
         * 解析结果统一加 hybrid-webview:// 前缀，供 WebViewScreen 播放。
         */
        fun parse(data: String): ChannelGroupList {
            val channels = channelBlockRegex.findAll(data)
                .mapNotNull { match ->
                    val name = match.groupValues[1].trim()
                    if (name.isEmpty()) return@mapNotNull null
                    val urls = urlRegex.findAll(match.groupValues[2])
                        .map { it.groupValues[1] }
                        .toList()
                    if (urls.isEmpty()) null
                    else Channel(
                        name = name,
                        epgName = name,
                        urlList = urls.map { "${ChannelUtil.HYBRID_WEB_VIEW_URL_PREFIX}$it" },
                    )
                }
                .distinctBy { it.name }
                .toList()

            if (channels.isEmpty()) throw Exception("全国直播列表为空或格式不正确")

            return ChannelGroupList(
                listOf(
                    ChannelGroup(
                        name = "全国直播",
                        channelList = ChannelList(channels),
                    )
                )
            )
        }
    }
}
