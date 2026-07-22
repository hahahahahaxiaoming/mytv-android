package top.yogiczy.mytv.core.data.repositories.iptv

import org.junit.Assert.assertEquals
import org.junit.Test

class IptvSourceSettingRepositoryTest {
    @Test
    fun parseKotlinStyleSetting() {
        val data = """
            listOf(
                IptvSource(
                    name = "远程源一",
                    url = "https://example.com/one.m3u",
                ),
                IptvSource(name = "远程源二", url = "http://example.com/two.m3u"),
            )
        """.trimIndent()

        val result = IptvSourceSettingRepository.parse(data)

        assertEquals(2, result.size)
        assertEquals("远程源一", result[0].name)
        assertEquals("https://example.com/one.m3u", result[0].url)
        assertEquals("远程源二", result[1].name)
    }

    @Test
    fun ignoresInvalidAndDuplicateEntries() {
        val data = """
            IptvSource(name = "有效", url = "https://example.com/live.m3u")
            IptvSource(name = "重复", url = "https://example.com/live.m3u")
            IptvSource(name = "非网络地址", url = "file:///live.m3u")
        """.trimIndent()

        val result = IptvSourceSettingRepository.parse(data)

        assertEquals(1, result.size)
        assertEquals("有效", result.single().name)
    }
}
