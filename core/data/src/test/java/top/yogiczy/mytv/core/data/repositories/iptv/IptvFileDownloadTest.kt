package top.yogiczy.mytv.core.data.repositories.iptv

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import top.yogiczy.mytv.core.data.utils.Constants

class IptvFileDownloadTest {
    @Test
    fun usesGiteeWhenTheChannelListIsValid() = runBlocking {
        val requests = mutableListOf<String>()
        val client = client { url ->
            requests += url.toString()
            """"CCTV-1" to listOf("https://example.com/live")"""
        }

        val channel = WebFallbackRepository(client).fetchChannelGroupList().single().channelList.single()

        assertEquals("CCTV-1", channel.name)
        assertEquals(listOf(Constants.WEB_FALLBACK_LIST_URL), requests)
    }

    @Test
    fun usesGitCodeWhenGiteeReturnsAnInterceptionPage() = runBlocking {
        val requests = mutableListOf<String>()
        val client = client { url ->
            requests += url.toString()
            if (url.host == "gitee.com") "<html>Blocked</html>"
            else """"CCTV-1" to listOf("https://example.com/live")"""
        }

        val channel = WebFallbackRepository(client).fetchChannelGroupList().single().channelList.single()

        assertEquals("CCTV-1", channel.name)
        assertEquals(
            listOf(Constants.WEB_FALLBACK_LIST_URL, "${Constants.GITCODE_IPTV_BASE_URL}webview.txt?ref=main"),
            requests,
        )
    }

    @Test
    fun mapsRenamedFilesOnGitCode() = runBlocking {
        val paths = mutableListOf<String>()
        val client = client { url ->
            if (url.host == "gitee.com") throw IOException("Network unavailable")
            paths += url.pathSegments.last()
            """IptvSource(name = "备用源", url = "https://example.com/live.m3u")"""
        }

        val sources = IptvSourceSettingRepository(client).fetch()
        client.readIptvFile("${Constants.GITEE_IPTV_BASE_URL}iptv.m3u") { it }

        assertEquals("备用源", sources.single().name)
        assertEquals(listOf("set.setting", "default.m3u"), paths)
    }

    private fun client(body: (HttpUrl) -> String) = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body(request.url).toResponseBody())
                .build()
        }
        .build()
}
