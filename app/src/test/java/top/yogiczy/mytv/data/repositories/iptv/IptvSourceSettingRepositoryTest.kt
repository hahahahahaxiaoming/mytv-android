package top.yogiczy.mytv.data.repositories.iptv

import org.junit.Assert.assertEquals
import org.junit.Test

class IptvSourceSettingRepositoryTest {
    @Test
    fun parseKotlinStyleSetting() {
        val data = """
            IptvSource(name = "远程源一", url = "https://example.com/one.m3u")
            IptvSource(name = "远程源二", url = "http://example.com/two.m3u")
        """.trimIndent()
        val result = IptvSourceSettingRepository.parse(data)
        assertEquals(2, result.size)
        assertEquals("远程源一", result[0].name)
    }

    @Test
    fun ignoresInvalidAndDuplicateEntries() {
        val data = """
            IptvSource(name = "有效", url = "https://example.com/live.m3u")
            IptvSource(name = "重复", url = "https://example.com/live.m3u")
            IptvSource(name = "本地", url = "file:///live.m3u")
        """.trimIndent()
        val result = IptvSourceSettingRepository.parse(data)
        assertEquals(1, result.size)
    }
}
