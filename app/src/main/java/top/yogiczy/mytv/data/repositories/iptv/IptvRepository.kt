package top.yogiczy.mytv.data.repositories.iptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import top.yogiczy.mytv.data.entities.Iptv
import top.yogiczy.mytv.data.entities.IptvGroup
import top.yogiczy.mytv.data.entities.IptvGroupList
import top.yogiczy.mytv.data.entities.IptvList
import top.yogiczy.mytv.data.repositories.FileCacheRepository
import top.yogiczy.mytv.data.repositories.iptv.parser.IptvParser
import top.yogiczy.mytv.utils.Logger

/**
 * 直播源获取
 */
class IptvRepository : FileCacheRepository("iptv.txt") {
    private val log = Logger.create(javaClass.simpleName)

    /**
     * 获取远程直播源数据
     */
    private suspend fun fetchSource(sourceUrl: String): String = withContext(Dispatchers.IO) {
        log.d("获取远程直播源: $sourceUrl")

        // 1. 处理本地资产文件 (使用 Java ClassLoader，不需要 Context)
        if (sourceUrl.startsWith("file:///android_asset/")) {
            val fileName = sourceUrl.substringAfter("android_asset/")
            return@withContext try {
                // 直接从类加载器读取 assets 下的文件
                this.javaClass.classLoader?.getResourceAsStream("assets/$fileName")?.bufferedReader()?.use {
                    it.readText()
                } ?: throw Exception("找不到资源文件: $fileName")
            } catch (ex: Exception) {
                log.e("读取本地文件失败", ex)
                throw Exception("读取本地文件失败: ${ex.message}")
            }
        }

        // 2. 原有的网络逻辑 (保持原样)
        val client = OkHttpClient()
        val request = Request.Builder().url(sourceUrl).build()
        try {
            val response = client.newCall(request).execute()
            val content = response.body?.string() ?: ""
            response.close()
            content
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
            val sourceData = getOrRefresh(cacheTime) {
                fetchSource(sourceUrl)
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
        } catch (ex: Exception) {
            log.e("获取直播源失败", ex)
            throw Exception(ex)
        }
    }
}