package top.yogiczy.mytv.data.repositories.iptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import top.yogiczy.mytv.data.entities.Iptv
import top.yogiczy.mytv.data.entities.IptvSource
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.util.concurrent.TimeUnit

class IptvSourceProbeRepository(private val userAgent: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun isPlayable(source: IptvSource): Boolean = withTimeoutOrNull(18_000) {
        val channels = IptvRepository()
            .getIptvGroupList(source.url, cacheTime = 0)
            .flatMap { it.iptvList }
        val channel = channels.firstOrNull(::isCctv1) ?: channels.firstOrNull()
        probeAny(channel?.urlList.orEmpty())
    } ?: false

    private fun isCctv1(channel: Iptv): Boolean {
        val names = listOf(channel.name, channel.channelName).map(String::trim)
        return names.any {
            CCTV1_NAME_REGEX.containsMatchIn(it) ||
                it.startsWith("央视一套") || it.startsWith("中央一套") || it.startsWith("中央1")
        }
    }

    private suspend fun probeAny(urls: List<String>): Boolean = coroutineScope {
        if (urls.isEmpty()) return@coroutineScope false
        val results = Channel<Boolean>(Channel.UNLIMITED)
        val jobs = urls.map { url -> async { results.send(probeUrl(url)) } }
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

    private suspend fun probeUrl(url: String): Boolean = when {
        url.startsWith("rtp://", true) -> probeRtp(url)
        url.startsWith("http://", true) || url.startsWith("https://", true) -> probeHttp(url)
        else -> false
    }

    private suspend fun probeHttp(url: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).header("User-Agent", userAgent).build())
                .execute().use { response ->
                    response.isSuccessful &&
                        response.body?.byteStream()?.read(ByteArray(HTTP_PROBE_BUFFER_SIZE))?.let { it > 0 } == true
                }
        }.getOrDefault(false)
    }

    private suspend fun probeRtp(url: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val address = url.substringAfter("rtp://").substringBefore("/").removePrefix("@")
            val host = address.substringBeforeLast(":")
            val port = address.substringAfterLast(":").toInt()
            val group = InetAddress.getByName(host)
            MulticastSocket(null).use { socket ->
                socket.reuseAddress = true
                socket.soTimeout = RTP_PACKET_TIMEOUT_MS
                socket.bind(InetSocketAddress(port))
                if (group.isMulticastAddress) socket.joinGroup(group)
                try {
                    val packet = DatagramPacket(ByteArray(RTP_PROBE_BUFFER_SIZE), RTP_PROBE_BUFFER_SIZE)
                    socket.receive(packet)
                    packet.length > 0
                } finally {
                    if (group.isMulticastAddress) runCatching { socket.leaveGroup(group) }
                }
            }
        }.getOrDefault(false)
    }

    private companion object {
        val CCTV1_NAME_REGEX = Regex("^CCTV\\s*[-_]?\\s*0*1(?![0-9+])", RegexOption.IGNORE_CASE)
        const val HTTP_PROBE_BUFFER_SIZE = 4 * 1024
        const val RTP_PROBE_BUFFER_SIZE = 64 * 1024
        const val RTP_PACKET_TIMEOUT_MS = 8_000
    }
}

