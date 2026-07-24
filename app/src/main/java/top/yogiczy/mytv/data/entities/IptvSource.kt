package top.yogiczy.mytv.data.entities

import androidx.compose.runtime.Immutable

@Immutable
data class IptvSource(
    val name: String,
    val url: String,
    val remote: Boolean = false,
)

