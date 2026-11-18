package top.yogiczy.mytv.kitkat

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.*
import top.yogiczy.mytv.data.entities.Iptv
import top.yogiczy.mytv.data.entities.IptvSource
import top.yogiczy.mytv.data.utils.Constants
import java.net.URI

class TvActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val repository by lazy { ChannelRepository(filesDir) }
    private lateinit var playback: TvPlayback
    private lateinit var status: TextView
    private var source = Constants.WEB_IPTV_LIST_URL
    private var catalog: List<IptvSource> = emptyList()
    private var channels: List<Iptv> = emptyList()
    private var channel = 0
    private var line = 0
    private var load: Job? = null
    private var active = false
    private var menu: AlertDialog? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        KitkatTls.install()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = FrameLayout(this)
        val screen = FrameLayout(this)
        playback = TvPlayback(screen)
        root.addView(screen)
        status = TextView(this).apply {
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(20, 12, 20, 12)
            setBackgroundColor(0xCC101010.toInt())
        }
        root.addView(status, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        root.setOnClickListener { channelMenu() }
        setContentView(root)
        loadSource(source)
        scope.launch {
            try {
                catalog = repository.sources()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Manual entry remains available. */ }
        }
    }

    private fun loadSource(address: String) {
        load?.cancel()
        playback.stop()
        channels = emptyList()
        source = address
        channel = 0
        line = 0
        message(if (source == Constants.WEB_IPTV_LIST_URL) "正在加载全国直播…" else "正在加载 IPTV…")
        load = scope.launch {
            try {
                channels = repository.channels(address)
                if (active) play()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                message("频道表加载失败：${e.localizedMessage.orEmpty()}\n按 OK 重试或切换直播源")
            }
        }
    }

    private fun play() {
        val station = channels.getOrNull(channel) ?: return
        message("${station.name} · 线路 ${line + 1}")
        playback.play(station.urlList[line], ready = { status.visibility = View.GONE }) {
            if (line < station.urlList.lastIndex) {
                line++
                play()
            } else {
                playback.stop()
                message("${station.name} 播放失败\n按 OK 选台或切换直播源")
            }
        }
    }

    private fun select(index: Int) {
        if (channels.isEmpty()) return
        channel = (index + channels.size) % channels.size
        line = 0
        play()
    }

    private fun message(text: String) {
        status.text = text
        status.visibility = View.VISIBLE
    }

    private fun channelMenu() {
        val entries = listOf("切换直播源", "重新加载当前源") + channels.map { it.name }
        menu?.dismiss()
        menu = AlertDialog.Builder(this).setTitle("选台")
            .setSingleChoiceItems(entries.toTypedArray(), if (channels.isEmpty()) 0 else channel + 2) { dialog, i ->
                dialog.dismiss()
                when (i) {
                    0 -> sourceMenu()
                    1 -> loadSource(source)
                    else -> select(i - 2)
                }
            }.show()
    }

    private fun sourceMenu() {
        val prefs = getSharedPreferences("sources", MODE_PRIVATE)
        val saved = prefs.getString("lastCustom", null)
        val choices = (listOf(IptvSource(Constants.WEB_IPTV_NAME, Constants.WEB_IPTV_LIST_URL)) +
            catalog.filterNot { it.url == Constants.DISABLED_IPTV_SOURCE_URL } +
            listOfNotNull(saved?.let { IptvSource(it, it) })).distinctBy { it.url }
        val labels = choices.map { it.name } + "输入 IPTV 订阅地址"
        menu?.dismiss()
        menu = AlertDialog.Builder(this).setTitle("直播源")
            .setItems(labels.toTypedArray()) { _, i ->
                if (i < choices.size) loadSource(choices[i].url)
                else {
                    val field = EditText(this).apply {
                        setSingleLine()
                        hint = "https://…"
                        setText(saved.orEmpty())
                        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
                    }
                    menu = AlertDialog.Builder(this).setTitle("IPTV 订阅地址").setView(field)
                        .setNegativeButton("取消", null).setPositiveButton("播放") { _, _ ->
                            val url = field.text.toString().trim()
                            val uri = runCatching { URI(url) }.getOrNull()
                            if (uri?.host != null && uri.scheme in listOf("http", "https")) {
                                prefs.edit().putString("lastCustom", url).apply()
                                loadSource(url)
                            } else Toast.makeText(this, "请输入完整的 HTTP 或 HTTPS 地址", Toast.LENGTH_LONG).show()
                        }.show()
                }
            }.show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> channelMenu()
            KeyEvent.KEYCODE_MENU -> sourceMenu()
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> select(channel + 1)
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> select(channel - 1)
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val count = channels.getOrNull(channel)?.urlList?.size ?: return true
                line = (line + count + if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1) % count
                play()
            }
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    override fun onStart() {
        super.onStart()
        active = true
        if (channels.isNotEmpty()) play()
    }

    override fun onStop() {
        active = false
        playback.stop()
        super.onStop()
    }

    override fun onDestroy() {
        scope.cancel()
        menu?.dismiss()
        super.onDestroy()
    }
}
