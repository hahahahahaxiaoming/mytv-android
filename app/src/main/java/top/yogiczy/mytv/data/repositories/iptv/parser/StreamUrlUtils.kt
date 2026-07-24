package top.yogiczy.mytv.data.repositories.iptv.parser

private val markdownUrlRegex = Regex("^\\[[^]]+\\]\\(([^)]+)\\)$")

/** Accepts plain stream URLs and Markdown-wrapped URLs copied from rendered lists. */
internal fun normalizeIptvStreamUrl(value: String): String {
    val trimmed = value.trim().trim('"', '\'')
    return markdownUrlRegex.matchEntire(trimmed)
        ?.groupValues
        ?.get(1)
        ?.trim()
        ?: trimmed
}

internal fun isIptvStreamUrl(value: String): Boolean =
    normalizeIptvStreamUrl(value).let {
        it.startsWith("http://", ignoreCase = true) ||
            it.startsWith("https://", ignoreCase = true) ||
            it.startsWith("rtp://", ignoreCase = true)
    }

