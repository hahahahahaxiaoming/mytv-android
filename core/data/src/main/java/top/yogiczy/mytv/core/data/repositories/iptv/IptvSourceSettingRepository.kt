package top.yogiczy.mytv.core.data.repositories.iptv

import java.io.IOException
import okhttp3.OkHttpClient
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSourceList
import top.yogiczy.mytv.core.data.utils.Constants

/** 获取并解析远程直播源设置。 */
class IptvSourceSettingRepository(private val client: OkHttpClient = mirrorIptvClient) {
    suspend fun fetch(): IptvSourceList =
        client.readIptvFile(Constants.IPTV_SOURCE_SETTING_URL) { data ->
            parse(data).also { sources ->
                if (sources.isEmpty()) throw IOException("远程直播源配置为空或格式不正确")
            }
        }

    companion object {
        private val sourceBlockRegex = Regex("IptvSource\\s*\\((.*?)\\)", RegexOption.DOT_MATCHES_ALL)
        private val nameRegex = Regex("name\\s*=\\s*\"([^\"]+)\"")
        private val urlRegex = Regex("url\\s*=\\s*\"(https?://[^\"]+)\"")

        /** 只读取 name 和 http(s) url，不执行设置文件中的代码。 */
        fun parse(data: String): IptvSourceList = IptvSourceList(
            sourceBlockRegex.findAll(data).mapNotNull { match ->
                val block = match.groupValues[1]
                val name = nameRegex.find(block)?.groupValues?.get(1)?.trim()
                val url = urlRegex.find(block)?.groupValues?.get(1)?.trim()
                if (name.isNullOrEmpty() || url.isNullOrEmpty()) null else IptvSource(name, url)
            }.distinctBy { it.url }.toList()
        )
    }
}
