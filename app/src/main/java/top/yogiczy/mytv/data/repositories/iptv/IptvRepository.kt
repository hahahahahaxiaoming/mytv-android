package top.yogiczy.mytv.data.repositories.iptv

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import top.yogiczy.mytv.data.entities.Iptv
import top.yogiczy.mytv.data.entities.IptvGroup
import top.yogiczy.mytv.data.entities.IptvGroupList
import top.yogiczy.mytv.data.entities.IptvList
import top.yogiczy.mytv.data.repositories.FileCacheRepository
import top.yogiczy.mytv.data.repositories.iptv.parser.IptvParser
import top.yogiczy.mytv.data.utils.Constants
import top.yogiczy.mytv.utils.Logger

/**
 * 直播源获取
 */
class IptvRepository : FileCacheRepository("iptv.txt") {
    private val log = Logger.create(javaClass.simpleName)
    private val client = OkHttpClient()
    private val mirrorClient = client.newBuilder().callTimeout(5, TimeUnit.SECONDS).build()

    /**
     * 获取远程直播源数据
     */
    private suspend fun fetchSource(sourceUrl: String) = withContext(Dispatchers.IO) {
        log.d("获取远程直播源: $sourceUrl")

        val downloadClient = if (sourceUrl.startsWith(Constants.GITEE_IPTV_BASE_URL) ||
            sourceUrl.startsWith(Constants.GITCODE_IPTV_BASE_URL)
        ) mirrorClient else client

        try {
            downloadClient.readIptvFile(sourceUrl) { it }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            log.e("获取远程直播源失败", ex)
            throw Exception("获取远程直播源失败，请检查网络连接", ex)
        }
    }

    /**
     * 简化规则
     */
    private fun simplifyTest(group: IptvGroup, iptv: Iptv): Boolean {
        return iptv.name.lowercase().startsWith("cctv") || iptv.name.endsWith("卫视")
    }

    /**
     * 获取直播源分组列表
     */
    suspend fun getIptvGroupList(
        sourceUrl: String,
        cacheTime: Long,
        simplify: Boolean = false,
    ): IptvGroupList {
        try {
            val sourceData = if (cacheTime <= 0) {
                fetchSource(sourceUrl)
            } else {
                getOrRefresh(cacheTime) { fetchSource(sourceUrl) }
            }

            val parser = IptvParser.instances.first { it.isSupport(sourceUrl, sourceData) }
            val groupList = parser.parse(sourceData)
            log.i("解析直播源完成：${groupList.size}个分组，${groupList.flatMap { it.iptvList }.size}个频道")

            if (simplify) {
                return IptvGroupList(groupList.map { group ->
                    IptvGroup(
                        name = group.name, iptvList = IptvList(group.iptvList.filter { iptv ->
                            simplifyTest(group, iptv)
                        })
                    )
                }.filter { it.iptvList.isNotEmpty() })
            }

            return groupList
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            log.e("获取直播源失败", ex)
            throw Exception(ex)
        }
    }
}
