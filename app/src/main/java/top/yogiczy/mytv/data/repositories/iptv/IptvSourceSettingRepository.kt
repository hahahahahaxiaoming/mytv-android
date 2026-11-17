package top.yogiczy.mytv.data.repositories.iptv

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import top.yogiczy.mytv.data.entities.IptvSource
import top.yogiczy.mytv.data.utils.Constants

/** Downloads and safely parses the remote iptv.setting file. */
class IptvSourceSettingRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(5, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun fetch(): List<IptvSource> = withContext(Dispatchers.IO) {
        client.readIptvFile(Constants.IPTV_SOURCE_SETTING_URL) { data ->
            parse(data).also {
                if (it.isEmpty()) throw IOException("远程直播源配置为空或格式不正确")
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

