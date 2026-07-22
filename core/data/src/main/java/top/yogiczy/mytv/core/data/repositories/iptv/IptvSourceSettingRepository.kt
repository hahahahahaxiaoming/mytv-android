package top.yogiczy.mytv.core.data.repositories.iptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSourceList
import top.yogiczy.mytv.core.data.network.await
import top.yogiczy.mytv.core.data.utils.Constants

/** 获取并解析远程直播源设置。 */
class IptvSourceSettingRepository {
    suspend fun fetch(): IptvSourceList {
        val request = Request.Builder().url(Constants.IPTV_SOURCE_SETTING_URL).build()
        val response = OkHttpClient().newCall(request).await()

        response.use {
            if (!it.isSuccessful) throw Exception("${it.code}: ${it.message}")
            val data = withContext(Dispatchers.IO) { it.body?.string().orEmpty() }
            return parse(data).also { sources ->
                if (sources.isEmpty()) throw Exception("远程直播源配置为空或格式不正确")
            }
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
