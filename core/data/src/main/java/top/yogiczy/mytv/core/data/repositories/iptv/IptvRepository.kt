package top.yogiczy.mytv.core.data.repositories.iptv

import kotlinx.coroutines.CancellationException
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.repositories.FileCacheRepository
import top.yogiczy.mytv.core.data.repositories.iptv.parser.IptvParser
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.core.data.utils.Logger

/**
 * 直播源数据获取
 */
class IptvRepository(
    private val source: IptvSource,
) : FileCacheRepository(
    if (source.isLocal) source.url
    else "iptv-${source.url.hashCode().toUInt().toString(16)}.txt",
    source.isLocal,
) {
    private val log = Logger.create(javaClass.simpleName)

    /**
     * 获取直播源数据
     */
    private suspend fun fetchSource(sourceUrl: String): String {
        log.d("获取远程直播源: $source")

        val client = if (sourceUrl.startsWith(Constants.GITEE_IPTV_BASE_URL) ||
            sourceUrl.startsWith(Constants.GITCODE_IPTV_BASE_URL)
        ) mirrorIptvClient else iptvClient

        try {
            return client.readIptvFile(sourceUrl) { it }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            log.e("获取直播源失败", ex)
            throw Exception("获取直播源失败，请检查网络连接", ex)
        }
    }

    /**
     * 获取直播源分组列表
     */
    suspend fun getChannelGroupList(cacheTime: Long): ChannelGroupList {
        try {
            val sourceData = getOrRefresh(if (source.isLocal) Long.MAX_VALUE else cacheTime) {
                fetchSource(source.url)
            }

            val parser = IptvParser.instances.first { it.isSupport(source.url, sourceData) }
            val startTime = System.currentTimeMillis()
            val groupList = parser.parse(sourceData)
            log.i(
                listOf(
                    "解析直播源（${source.name}）完成：${groupList.size}个分组",
                    "${groupList.sumOf { it.channelList.size }}个频道",
                    "${groupList.sumOf { it.channelList.sumOf { channel -> channel.urlList.size } }}条线路",
                    "耗时：${System.currentTimeMillis() - startTime}ms",
                ).joinToString()
            )

            return groupList
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            log.e("获取直播源失败", ex)
            throw Exception(ex)
        }
    }

    override suspend fun clearCache() {
        if (source.isLocal) return
        super.clearCache()
    }
}
