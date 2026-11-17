package top.yogiczy.mytv.data.repositories.iptv

import java.io.IOException
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import top.yogiczy.mytv.data.utils.Constants

class WebIptvRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun parsesCloudChannelsAndPreservesLineOrder() {
        val groups = WebIptvRepository.parse(
            """
            "CCTV-1综合" to listOf(
                "https://example.com/watch?id=(1)",
                "https://example.com/live/",
                "https://example.com/live/",
            ),
            "无效频道" to listOf("file:///local"),
            """.trimIndent()
        )

        val group = groups.single()
        assertEquals("全国直播", group.name)
        val channel = group.iptvList.single()
        assertEquals("CCTV-1综合", channel.name)
        assertEquals(
            listOf(
                "hybrid-webview://https://example.com/watch?id=(1)",
                "hybrid-webview://https://example.com/live/",
            ),
            channel.urlList,
        )
    }

    @Test(expected = IOException::class)
    fun rejectsInvalidCloudList() {
        WebIptvRepository.parse("<html>Service unavailable</html>")
    }

    @Test
    fun reopensWithMostRecentValidListWhenWifiFails() = runBlocking {
        val savedList = File(folder.root, "channels.txt")
        repository(savedList) { channelList("旧频道") }.load()
        val latest = repository(savedList) { channelList("新频道") }.load()

        assertEquals(latest, repository(savedList) { throw IOException("Network unavailable") }.load())
        assertEquals("新频道", latest.single().iptvList.single().name)
    }

    @Test
    fun invalidDownloadDoesNotReplaceSavedList() = runBlocking {
        val savedList = File(folder.root, "channels.txt")
        val latest = repository(savedList) { channelList("CCTV-1") }.load()

        assertEquals(latest, repository(savedList) { "<html>Wi-Fi sign-in</html>" }.load())
        assertEquals(latest, repository(savedList) { throw IOException("Offline") }.load())
    }

    @Test(expected = IOException::class)
    fun reportsFailureWhenNoListHasBeenSaved() {
        runBlocking {
            repository(File(folder.root, "channels.txt")) { throw IOException("Offline") }.load()
        }
    }

    @Test
    fun blockedGiteePageUsesGitCodeAndSavesItsChannelList() = runBlocking {
        val savedList = File(folder.root, "channels.txt")
        val requests = mutableListOf<String>()
        val remoteList = repository(savedList) { url ->
            requests += url.toString()
            if (url.host == "gitee.com") "<html>Blocked</html>" else channelList("GitCode 频道")
        }.load()

        assertEquals(
            listOf(Constants.WEB_IPTV_LIST_URL, "${Constants.GITCODE_IPTV_BASE_URL}webview.txt?ref=main"),
            requests,
        )
        assertEquals("GitCode 频道", remoteList.single().iptvList.single().name)
        assertEquals(remoteList, repository(savedList) { throw IOException("Offline") }.load())
    }

    @Test
    fun usesRenamedGitCodeFilesWhenGiteeIsUnavailable() = runBlocking {
        val requests = mutableListOf<String>()
        val client = mockClient { url ->
            requests += url.toString()
            if (url.host == "gitee.com") throw IOException("Gitee unavailable")
            "mirror"
        }

        assertEquals("mirror", client.readIptvFile(Constants.IPTV_SOURCE_SETTING_URL) { it })
        assertEquals("mirror", client.readIptvFile("${Constants.GITEE_IPTV_BASE_URL}iptv.m3u") { it })
        assertEquals(
            listOf(
                Constants.IPTV_SOURCE_SETTING_URL,
                "${Constants.GITCODE_IPTV_BASE_URL}set.setting?ref=main",
                "${Constants.GITEE_IPTV_BASE_URL}iptv.m3u",
                "${Constants.GITCODE_IPTV_BASE_URL}default.m3u?ref=main",
            ),
            requests,
        )
    }

    private fun channelList(name: String) = """"$name" to listOf("https://example.com/live/")"""

    private fun repository(savedList: File, responseText: (HttpUrl) -> String) =
        WebIptvRepository(savedList, mockClient(responseText))

    private fun mockClient(responseText: (HttpUrl) -> String) =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(responseText(chain.request().url).toResponseBody())
                    .build()
            }
            .build()
}
