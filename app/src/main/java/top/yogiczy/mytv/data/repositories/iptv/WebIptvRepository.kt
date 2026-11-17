package top.yogiczy.mytv.data.repositories.iptv

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import top.yogiczy.mytv.AppGlobal
import top.yogiczy.mytv.data.entities.Iptv
import top.yogiczy.mytv.data.entities.IptvGroup
import top.yogiczy.mytv.data.entities.IptvGroupList
import top.yogiczy.mytv.data.entities.IptvList
import top.yogiczy.mytv.data.utils.Constants

class WebIptvRepository(
    private val savedList: File = File(AppGlobal.filesDir, "web-iptv-last-good.txt"),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(5, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun load(): IptvGroupList = withContext(Dispatchers.IO) {
        try {
            client.readIptvFile(Constants.WEB_IPTV_LIST_URL) { data ->
                val groups = parse(data)
                saveList(data)
                groups
            }
        } catch (downloadError: IOException) {
            return@withContext try {
                parse(savedList.readText())
            } catch (_: IOException) {
                throw downloadError
            }
        }
    }

    private fun saveList(data: String) {
        try {
            // 先写同目录临时文件，写入完成后再替换有效的频道表。
            val tempList = File.createTempFile("web-iptv-", ".tmp", savedList.parentFile)
            try {
                tempList.writeText(data)
                if (!tempList.renameTo(savedList)) throw IOException("替换全国直播频道表失败")
            } finally {
                tempList.delete()
            }
        } catch (_: IOException) {
            // 保存失败时仍使用本次已成功下载并验证的频道表。
        }
    }

    companion object {
        private val channelBlock = Regex(
            """"([^"]+)"\s*to\s*listOf\s*\(((?:"[^"]*"|[^")])*+)\)"""
        )
        private val quotedValue = Regex(""""([^"]+)"""")

        internal fun parse(data: String): IptvGroupList {
            val channels = channelBlock.findAll(data).mapNotNull { match ->
                val name = match.groupValues[1].trim()
                val urls = quotedValue.findAll(match.groupValues[2])
                    .mapNotNull { it.groupValues[1].trim().toHttpUrlOrNull() }
                    .map { "${Constants.WEB_IPTV_URL_PREFIX}$it" }
                    .distinct()
                    .toList()
                if (name.isEmpty() || urls.isEmpty()) null else Iptv(name, name, urls)
            }.distinctBy { it.name }.toList()

            if (channels.isEmpty()) throw IOException("全国直播列表为空或格式不正确")
            return IptvGroupList(listOf(IptvGroup(Constants.WEB_IPTV_NAME, IptvList(channels))))
        }
    }
}
