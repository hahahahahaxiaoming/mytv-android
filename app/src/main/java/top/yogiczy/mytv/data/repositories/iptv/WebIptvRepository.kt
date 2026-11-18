package top.yogiczy.mytv.data.repositories.iptv

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import top.yogiczy.mytv.AppGlobal
import top.yogiczy.mytv.data.entities.IptvGroupList
import top.yogiczy.mytv.data.utils.Constants

class WebIptvRepository(
    private val savedList: File = File(AppGlobal.filesDir, "web-iptv-last-good.txt"),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(5, TimeUnit.SECONDS)
        .build(),
) {
    private val cache = ChannelListCache(savedList)
    suspend fun load(): IptvGroupList = withContext(Dispatchers.IO) {
        try {
            client.readIptvFile(Constants.WEB_IPTV_LIST_URL) { data ->
                val groups = parse(data)
                cache.save(data)
                groups
            }
        } catch (downloadError: IOException) {
            return@withContext try {
                parse(cache.read())
            } catch (_: IOException) {
                throw downloadError
            }
        }
    }

    companion object {
        internal fun parse(data: String) = WebIptvParser.parse(data) { it.toHttpUrlOrNull()?.toString() }
    }
}
