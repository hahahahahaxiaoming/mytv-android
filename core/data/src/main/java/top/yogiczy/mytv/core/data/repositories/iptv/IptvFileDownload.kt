package top.yogiczy.mytv.core.data.repositories.iptv

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import top.yogiczy.mytv.core.data.network.await
import top.yogiczy.mytv.core.data.utils.Constants

internal val iptvClient = OkHttpClient()
internal val mirrorIptvClient = iptvClient.newBuilder()
    .callTimeout(5, TimeUnit.SECONDS)
    .build()

internal suspend fun <T> OkHttpClient.readIptvFile(sourceUrl: String, parse: (String) -> T): T {
    return try {
        parse(downloadIptvFile(sourceUrl))
    } catch (primaryError: IOException) {
        if (!sourceUrl.startsWith(Constants.GITEE_IPTV_BASE_URL)) throw primaryError
        currentCoroutineContext().ensureActive()
        val path = sourceUrl.removePrefix(Constants.GITEE_IPTV_BASE_URL)
            .substringBefore('?').substringBefore('#')
        val fallbackPath = when (path) {
            "iptv.setting" -> "set.setting"
            "iptv.m3u" -> "default.m3u"
            else -> path
        }
        val fallbackUrl = (Constants.GITCODE_IPTV_BASE_URL + fallbackPath).toHttpUrl().newBuilder()
            .addQueryParameter("ref", "main")
            .build().toString()
        parse(downloadIptvFile(fallbackUrl))
    }
}

private suspend fun OkHttpClient.downloadIptvFile(url: String): String {
    val request = Request.Builder().url(url).build()
    return newCall(request).await().use { response ->
        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
        val text = withContext(Dispatchers.IO) { response.body?.string().orEmpty() }
        val start = text.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        if (text.isBlank() || start.startsWith("<!doctype html", ignoreCase = true) ||
            start.startsWith("<html", ignoreCase = true)
        ) {
            throw IOException("直播源返回的是网页或空文件")
        }
        text
    }
}
