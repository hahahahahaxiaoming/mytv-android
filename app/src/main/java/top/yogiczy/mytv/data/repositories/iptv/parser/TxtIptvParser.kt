package top.yogiczy.mytv.data.repositories.iptv.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yogiczy.mytv.data.entities.Iptv
import top.yogiczy.mytv.data.entities.IptvGroup
import top.yogiczy.mytv.data.entities.IptvGroupList
import top.yogiczy.mytv.data.entities.IptvList

/** Parses name,url TXT playlists, with or without #genre# group markers. */
class TxtIptvParser : IptvParser {
    override fun isSupport(url: String, data: String): Boolean {
        if (data.contains("#genre#")) return true
        return data.lineSequence().any { line ->
            val parts = line.split(",", "，", limit = 2)
            parts.size == 2 && parts[1].trim().substringBefore("#").let {
                it.startsWith("http://") || it.startsWith("https://") || it.startsWith("rtp://")
            }
        }
    }

    override suspend fun parse(data: String): IptvGroupList = withContext(Dispatchers.Default) {
        var groupName = "其他"
        val rows = mutableListOf<Row>()
        data.lineSequence().forEach { line ->
            if (line.isBlank() || line.startsWith("#") || line.startsWith("//")) return@forEach
            if (line.contains("#genre#")) {
                groupName = line.split(",", "，").firstOrNull()?.trim().orEmpty().ifBlank { "其他" }
            } else {
                val parts = line.split(",", "，", limit = 2)
                if (parts.size == 2) {
                    parts[1].split("#").map { it.trim() }.filter { it.isNotEmpty() }.forEach { url ->
                        rows += Row(parts[0].trim(), groupName, url)
                    }
                }
            }
        }
        IptvGroupList(rows.groupBy { it.group }.map { group ->
            IptvGroup(group.key, IptvList(group.value.groupBy { it.name }.map { channel ->
                Iptv(channel.key, channel.key, channel.value.map { it.url }.distinct())
            }))
        })
    }

    private data class Row(val name: String, val group: String, val url: String)
}
