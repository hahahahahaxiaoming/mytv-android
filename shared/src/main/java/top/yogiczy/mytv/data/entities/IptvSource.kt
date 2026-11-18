package top.yogiczy.mytv.data.entities

data class IptvSource(
    val name: String,
    val url: String,
    val remote: Boolean = false,
)

