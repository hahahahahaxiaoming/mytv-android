package top.yogiczy.mytv.core.data.repositories.iptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.network.await
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.util.concurrent.TimeUnit

/**
 * 快速探测直播源是否至少有一条可连接线路。
 *
 * 每个直播源优先检测名称为 CCTV-1 的频道；找不到时回退检测第一条频道。
 */
class IptvSourceProbeRepository(
    private val userAgent: String,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun isPlayable(source: IptvSource): Boolean = withTimeoutOrNull(18_000) {
        val channels = IptvRepository(source)
            .getChannelGroupList(cacheTime = 0)
            .asSequence()
            .flatMap { it.channelList.asSequence() }
            .toList()

        val probeChannel = channels.firstOrNull {
            isCctv1(it.name) || isCctv1(it.epgName)
        } ?: channels.firstOrNull()

        val channelUrls = probeChannel?.urlList.orEmpty()
        if (channelUrls.isEmpty()) return@withTimeoutOrNull false

        coroutineScope {
            val results = Channel<Boolean>(Channel.UNLIMITED)
            val jobs = channelUrls.map { url ->
                async { results.send(probeUrl(url)) }
            }

            repeat(jobs.size) {
                if (results.receive()) {
                    jobs.forEach { it.cancel() }
                    results.cancel()
                    return@coroutineScope true
                }
            }

            results.cancel()
            false
        }
    } ?: false

    private fun isCctv1(name: String): Boolean {
        val normalized = name.trim()
        return CCTV1_NAME_REGEX.containsMatchIn(normalized) ||
                normalized.startsWith("央视一套") ||
                normalized.startsWith("中央一套") ||
                normalized.startsWith("中央1")
    }

    private suspend fun probeUrl(url: String): Boolean {
        return when {
            url.startsWith("rtp://", ignoreCase = true) -> probeRtp(url)
            url.startsWith("http://", ignoreCase = true) ||
                    url.startsWith("https://", ignoreCase = true) -> probeHttp(url)
            else -> false
        }
    }

    private suspend fun probeHttp(url: String): Boolean = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .build()
        client.newCall(request).await().use { response ->
            if (!response.isSuccessful) return@use false
            val body = response.body ?: return@use false
            withContext(Dispatchers.IO) {
                body.byteStream().read(ByteArray(HTTP_PROBE_BUFFER_SIZE)) > 0
            }
        }
    }.getOrDefault(false)

    private suspend fun probeRtp(url: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val addressPart = url
                .substringAfter("rtp://")
                .substringBefore("/")
                .removePrefix("@")
            val host = addressPart.substringBeforeLast(":")
            val port = addressPart.substringAfterLast(":").toInt()
            val group = InetAddress.getByName(host)

            MulticastSocket(null).use { socket ->
                socket.reuseAddress = true
                socket.soTimeout = RTP_PACKET_TIMEOUT_MS
                socket.bind(InetSocketAddress(port))
                if (group.isMulticastAddress) socket.joinGroup(group)

                try {
                    val packet = DatagramPacket(
                        ByteArray(RTP_PROBE_BUFFER_SIZE),
                        RTP_PROBE_BUFFER_SIZE,
                    )
                    socket.receive(packet)
                    packet.length > 0
                } finally {
                    if (group.isMulticastAddress) runCatching { socket.leaveGroup(group) }
                }
            }
        }.getOrDefault(false)
    }

    private companion object {
        val CCTV1_NAME_REGEX = Regex(
            pattern = "^CCTV\\s*[-_]?\\s*0*1(?![0-9+])",
            option = RegexOption.IGNORE_CASE,
        )
        const val HTTP_PROBE_BUFFER_SIZE = 4 * 1024
        const val RTP_PROBE_BUFFER_SIZE = 64 * 1024
        const val RTP_PACKET_TIMEOUT_MS = 8_000
    }
}
