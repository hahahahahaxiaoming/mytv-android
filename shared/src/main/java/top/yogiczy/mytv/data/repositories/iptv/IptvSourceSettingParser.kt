package top.yogiczy.mytv.data.repositories.iptv

import top.yogiczy.mytv.data.entities.IptvSource

internal object IptvSourceSettingParser {
    private val sourceBlockRegex = Regex("IptvSource\\s*\\((.*?)\\)", RegexOption.DOT_MATCHES_ALL)
    private val nameRegex = Regex("name\\s*=\\s*\"([^\"]+)\"")
    private val urlRegex = Regex("url\\s*=\\s*\"(https?://[^\"]+)\"")

    /** Reads only name and HTTP(S) URL fields; the setting file is never executed. */
    fun parse(data: String): List<IptvSource> =
        sourceBlockRegex.findAll(data).mapNotNull { match ->
            val block = match.groupValues[1]
            val name = nameRegex.find(block)?.groupValues?.get(1)?.trim()
            val url = urlRegex.find(block)?.groupValues?.get(1)?.trim()
            if (name.isNullOrEmpty() || url.isNullOrEmpty()) null
            else IptvSource(name = name, url = url, remote = true)
        }.distinctBy { it.url }.toList()
}
