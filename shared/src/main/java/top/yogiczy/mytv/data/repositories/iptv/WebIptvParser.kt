package top.yogiczy.mytv.data.repositories.iptv
import java.io.IOException
import top.yogiczy.mytv.data.entities.Iptv
import top.yogiczy.mytv.data.entities.IptvGroup
import top.yogiczy.mytv.data.entities.IptvGroupList
import top.yogiczy.mytv.data.entities.IptvList
import top.yogiczy.mytv.data.utils.Constants

internal object WebIptvParser {
    private val channelBlock = Regex(
        """"([^"]+)"\s*to\s*listOf\s*\(((?:"[^"]*"|[^")])*+)\)"""
    )
    private val quotedValue = Regex(""""([^"]+)"""")

    internal fun parse(data: String, normalize: (String) -> String?): IptvGroupList {
        val channels = channelBlock.findAll(data).mapNotNull { match ->
            val name = match.groupValues[1].trim()
            val urls = quotedValue.findAll(match.groupValues[2])
                .mapNotNull { normalize(it.groupValues[1].trim()) }
                .map { "${Constants.WEB_IPTV_URL_PREFIX}$it" }
                .distinct()
                .toList()
            if (name.isEmpty() || urls.isEmpty()) null else Iptv(name, name, urls)
        }.distinctBy { it.name }.toList()

        if (channels.isEmpty()) throw IOException("全国直播列表为空或格式不正确")
        return IptvGroupList(listOf(IptvGroup(Constants.WEB_IPTV_NAME, IptvList(channels))))
    }
}
