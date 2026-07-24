package top.yogiczy.mytv.core.data.repositories.iptv

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.network.await
import java.util.concurrent.TimeUnit

/**
 * 快速探测直播源是否至少有一条可连接线路。
 *
 * 每个直播源只检测解析结果中的前两个频道地址。
 */
class IptvSourceProbeRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun isPlayable(source: IptvSource): Boolean = withTimeoutOrNull(8_000) {
        val channelUrls = IptvRepository(source)
            .getChannelGroupList(cacheTime = 0)
            .asSequence()
            .flatMap { it.channelList.asSequence() }
            .mapNotNull { it.urlList.firstOrNull() }
            .take(PROBE_CHANNEL_COUNT)
            .toList()

        coroutineScope {
            channelUrls.map { url ->
                async { probeUrl(url) }
            }.awaitAll().any { it }
        }
    } ?: false

    private suspend fun probeUrl(url: String): Boolean = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-1023")
            .build()
        client.newCall(request).await().use { it.isSuccessful }
    }.getOrDefault(false)

    private companion object {
        const val PROBE_CHANNEL_COUNT = 2
    }
}
