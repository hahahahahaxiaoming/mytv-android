package top.yogiczy.mytv.data.repositories.iptv.parser

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uIptvParserTest {
    @Test
    fun parsesCctv1RtpEntry() = runBlocking {
        val data = """
            #EXTM3U x-tvg-url="https://epg.v1.mk/fy.xml"
            #EXTINF:-1,tvg-id="CCTV1" tvg-name="CCTV-1高清" group-title="IPTV",CCTV-1高清
            rtp://225.1.4.73:1102
            #EXTINF:-1,tvg-id="CCTV2" tvg-name="CCTV-2高清" group-title="IPTV",CCTV-2高清
            rtp://225.1.4.74:1103
        """.trimIndent()

        val parser = M3uIptvParser()
        val channels = parser.parse(data).flatMap { it.iptvList }

        assertTrue(parser.isSupport("source.m3u", data))
        assertEquals("CCTV-1高清", channels.first().name)
        assertEquals("rtp://225.1.4.73:1102", channels.first().urlList.single())
    }
}
