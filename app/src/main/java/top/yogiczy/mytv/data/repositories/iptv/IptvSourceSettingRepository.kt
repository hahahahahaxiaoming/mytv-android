package top.yogiczy.mytv.data.repositories.iptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import top.yogiczy.mytv.data.entities.IptvSource
import top.yogiczy.mytv.data.utils.Constants

/** Downloads and safely parses the remote iptv.setting file. */
class IptvSourceSettingRepository(
    private val client: OkHttpClient = OkHttpClient(),
) {
    suspend fun fetch(): List<IptvSource> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(Constants.IPTV_SOURCE_SETTING_URL).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("${response.code}: ${response.message}")
            parse(response.body?.string().orEmpty()).also {
                if (it.isEmpty()) error("远程直播源配置为空或格式不正确")
            }
        }
    }

    companion object {
        private val sourceBlockRegex = Regex("IptvSource\\s*\\((.*?)\\)", RegexOption.DOT_MATCHES_ALL)
        private val nameRegex = Regex("name\\s*=\\s*\"([^\"]+)\"")
        private val urlRegex = Regex("url\\s*=\\s*\"(https?://[^\"]+)\"")

        /** Reads only name and HTTP(S) URL fields; the setting file is never executed. */
        fun parse(data: String): List<IptvSource> =
            sourceBlockRegex.findAll(data).mapNotNull { match ->
                val block = match.groupValues[1]
                val name = nameRegex.find(block)?.groupValues?.get(1)?.trim()
                val url = urlRegex.find(block)?.groupValues?.get(1)?.trim()
                if (name.isNullOrEmpty() || url.isNullOrEmpty()) null
                else IptvSource(name = name, url = url, remote = true)
            }.distinctBy { it.url }.toList()
    }
}

