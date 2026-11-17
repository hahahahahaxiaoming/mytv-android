package top.yogiczy.mytv.data.repositories.iptv

import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import top.yogiczy.mytv.data.utils.Constants

internal suspend fun <T> OkHttpClient.readIptvFile(
    sourceUrl: String,
    parse: (String) -> T,
): T {
    val fallbackUrl = if (sourceUrl.startsWith(Constants.GITEE_IPTV_BASE_URL)) {
        val path = sourceUrl.removePrefix(Constants.GITEE_IPTV_BASE_URL)
            .substringBefore('?').substringBefore('#')
        val fallbackPath = when (path) {
            "iptv.setting" -> "set.setting"
            "iptv.m3u" -> "default.m3u"
            else -> path
        }
        (Constants.GITCODE_IPTV_BASE_URL + fallbackPath).toHttpUrl().newBuilder()
            .addQueryParameter("ref", "main")
            .build().toString()
    } else null

    return try {
        parse(downloadIptvFile(sourceUrl))
    } catch (primaryError: IOException) {
        if (fallbackUrl == null) throw primaryError
        currentCoroutineContext().ensureActive()
        parse(downloadIptvFile(fallbackUrl))
    }
}

private fun OkHttpClient.downloadIptvFile(url: String): String {
    val request = Request.Builder().url(url).build()
    return newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
        val text = response.body?.string().orEmpty()
        val start = text.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        if (text.isBlank() || start.startsWith("<!doctype html", ignoreCase = true) ||
            start.startsWith("<html", ignoreCase = true)
        ) {
            throw IOException("直播源返回的是网页或空文件")
        }
        text
    }
}
