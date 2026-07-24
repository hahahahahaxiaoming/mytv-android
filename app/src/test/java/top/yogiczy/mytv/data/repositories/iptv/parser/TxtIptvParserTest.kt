package top.yogiczy.mytv.data.repositories.iptv.parser

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtIptvParserTest {
    @Test
    fun parsesNamedRtpAndHttpChannels() = runBlocking {
        val data = """
            CCTV1,rtp://239.255.30.101:8231
            CCTV2,http://example.com/cctv2/index.m3u8
        """.trimIndent()

        val parser = TxtIptvParser()
        val channels = parser.parse(data).flatMap { it.iptvList }

        assertTrue(parser.isSupport("source.txt", data))
        assertEquals("rtp://239.255.30.101:8231", channels.first().urlList.first())
    }

    @Test
    fun treatsFirstBareStreamAsCctv1() = runBlocking {
        val data = """
            rtp://225.1.4.73:1102
            http://example.com/second/index.m3u8
        """.trimIndent()

        val parser = TxtIptvParser()
        val channels = parser.parse(data).flatMap { it.iptvList }

        assertTrue(parser.isSupport("source.txt", data))
        assertEquals("CCTV-1", channels.first().name)
        assertEquals("rtp://225.1.4.73:1102", channels.first().urlList.first())
    }

    @Test
    fun unwrapsMarkdownHttpUrl() = runBlocking {
        val data =
            "CCTV1,[http://39.135.133.170/live/index.m3u8](http://39.135.133.170/live/index.m3u8)"

        val parser = TxtIptvParser()
        val channel = parser.parse(data).flatMap { it.iptvList }.single()

        assertEquals("CCTV1", channel.name)
        assertEquals("http://39.135.133.170/live/index.m3u8", channel.urlList.single())
    }
}
