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
        fun parse(data: String) = IptvSourceSettingParser.parse(data)
    }
}
