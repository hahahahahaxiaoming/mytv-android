package top.yogiczy.mytv.tv.ui.screens.videoplayer

enum class VideoPlayerDecodeMode(
    val label: String,
    val value: Int,
) {
    /** Media3 按系统优先级自动选择解码器 */
    DEFAULT("默认", 0),

    /** Media3 仅选择系统软件视频解码器 */
    MEDIA3_SOFTWARE("Media3 软件解码", 1),

    /** Media3 仅选择硬件视频解码器 */
    MEDIA3_HARDWARE("Media3 硬件解码", 2),

    /** libVLC 禁用硬件视频解码 */
    VLC_SOFTWARE("VLC 软件解码", 3),

    /** libVLC 强制启用硬件视频解码 */
    VLC_HARDWARE("VLC 硬件解码", 4);

    companion object {
        fun fromValue(value: Int): VideoPlayerDecodeMode =
            entries.firstOrNull { it.value == value } ?: DEFAULT
    }
}
