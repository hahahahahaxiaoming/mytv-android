package top.yogiczy.mytv.kitkat

import top.yogiczy.mytv.data.repositories.iptv.ChannelListCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yogiczy.mytv.data.entities.Iptv
import top.yogiczy.mytv.data.repositories.iptv.IptvSourceSettingParser
import top.yogiczy.mytv.data.repositories.iptv.WebIptvParser
import top.yogiczy.mytv.data.repositories.iptv.parser.IptvParser
import top.yogiczy.mytv.data.utils.Constants
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

internal class ChannelRepository(directory: File) {
    private val cache = ChannelListCache(File(directory, "web-iptv-last-good.txt"))

    suspend fun sources() = withContext(Dispatchers.IO) {
        fetch(Constants.IPTV_SOURCE_SETTING_URL) { text ->
            IptvSourceSettingParser.parse(text).ifEmpty { throw IOException("源列表为空") }
        }
    }

    suspend fun channels(source: String): List<Iptv> = withContext(Dispatchers.IO) {
        if (source == Constants.WEB_IPTV_LIST_URL) {
            try {
                fetch(source) { text ->
                    val channels = parseWeb(text)
                    cache.save(text)
                    channels
                }
            } catch (e: IOException) {
                try { parseWeb(cache.read()) } catch (_: IOException) { throw e }
            }
        } else {
            fetch(source) { text ->
                val parser = IptvParser.instances.first { it.isSupport(source, text) }
                parser.parse(text).flatMap { it.iptvList }.filter { it.urlList.isNotEmpty() }
                    .ifEmpty { throw IOException("订阅里没有可播放的频道") }
            }
        }
    }

    private fun parseWeb(text: String) = WebIptvParser.parse(text) { address ->
        val uri = runCatching { URI(address) }.getOrNull()
        address.takeIf { uri?.host != null && uri.scheme in listOf("https", "http") }
    }.flatMap { it.iptvList }

    private suspend fun <T> fetch(source: String, parse: suspend (String) -> T): T {
        try {
            return parse(download(source))
        } catch (e: IOException) {
            if (!source.startsWith(Constants.GITEE_IPTV_BASE_URL)) throw e
            val path = source.removePrefix(Constants.GITEE_IPTV_BASE_URL).substringBefore('?').substringBefore('#')
            val mirrorPath = when (path) {
                "iptv.setting" -> "set.setting"
                "iptv.m3u" -> "default.m3u"
                else -> path
            }
            return parse(download(Constants.GITCODE_IPTV_BASE_URL + mirrorPath + "?ref=main"))
        }
    }

    private fun download(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 6000
            connection.readTimeout = 8000
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val text = StringBuilder()
                val buffer = CharArray(8192)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    if (text.length + count > 4 * 1024 * 1024) throw IOException("频道表过大")
                    text.append(buffer, 0, count)
                }
                text.toString().trimStart('\uFEFF', ' ', '\r', '\n', '\t')
            }
        } finally {
            connection.disconnect()
        }
    }
}
