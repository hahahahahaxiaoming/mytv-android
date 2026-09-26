package top.yogiczy.mytv.tv.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelList
import top.yogiczy.mytv.core.data.entities.epg.EpgList
import top.yogiczy.mytv.core.data.repositories.epg.EpgRepository
import top.yogiczy.mytv.core.data.repositories.iptv.IptvRepository
import top.yogiczy.mytv.core.data.repositories.iptv.WebFallbackRepository
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.material.SnackbarType
import top.yogiczy.mytv.tv.ui.utils.Configs

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        init()
    }

    fun init() {
        viewModelScope.launch {
            _uiState.value = MainUiState.Loading()
            refreshChannel()
            refreshEpg()
        }
    }

    private suspend fun refreshChannel() {
        // 网页模式：选中"本地(NULL)"或地址为空 → 从云端拉取全国直播列表（iptv 仓库 webview.txt）
        if (Configs.iptvSourceCurrent.url.isBlank()
            || Configs.iptvSourceCurrent.url.equals("NULL", ignoreCase = true)
        ) {
            val fallback = runCatching { WebFallbackRepository().fetchChannelGroupList() }.getOrNull()
            if (fallback != null) {
                _uiState.value = MainUiState.Ready(channelGroupList = fallback)
            } else {
                // 远端获取失败 → 禁止本地内置列表播放，明确报错
                _uiState.value = MainUiState.Error("全国直播列表获取失败，请检查网络")
                Snackbar.show("全国直播列表获取失败，已禁止本地播放", type = SnackbarType.ERROR)
            }
            return
        }

        flow {
            emit(
                IptvRepository(Configs.iptvSourceCurrent).getChannelGroupList(cacheTime = Configs.iptvSourceCacheTime)
            )
        }
            .retryWhen { _, attempt ->
                if (attempt >= Constants.HTTP_RETRY_COUNT) return@retryWhen false

                _uiState.value =
                    MainUiState.Loading("获取远程直播源(${attempt + 1}/${Constants.HTTP_RETRY_COUNT})...")
                delay(Constants.HTTP_RETRY_INTERVAL)
                true
            }
            .catch {
                // 云端直播源不可用 → 兜底启用全国直播（云端列表）；列表也失败则报错，禁止本地播放
                val fallback = runCatching { WebFallbackRepository().fetchChannelGroupList() }.getOrNull()
                if (fallback != null) {
                    _uiState.value = MainUiState.Ready(channelGroupList = fallback)
                    Snackbar.show("云端直播源不可用，已启用全国直播")
                } else {
                    _uiState.value = MainUiState.Error("云端直播源与全国直播列表均获取失败")
                    Snackbar.show("云端直播源与全国直播列表均获取失败，已禁止本地播放", type = SnackbarType.ERROR)
                }
            }
            .map {
                _uiState.value = MainUiState.Ready(channelGroupList = it)
                it
            }
            .collect()
    }

    private suspend fun refreshEpg() {
        if (!Configs.epgEnable) return

        if (_uiState.value is MainUiState.Ready) {
            EpgList.clearCache()
            val channelGroupList = (_uiState.value as MainUiState.Ready).channelGroupList

            flow {
                emit(
                    EpgRepository(Configs.epgSourceCurrent).getEpgList(
                        filteredChannels = channelGroupList.channelList.map { it.epgName },
                        refreshTimeThreshold = Configs.epgRefreshTimeThreshold,
                    )
                )
            }
                .retry(Constants.HTTP_RETRY_COUNT) { delay(Constants.HTTP_RETRY_INTERVAL); true }
                .catch {
                    emit(EpgList())
                    Snackbar.show("节目单获取失败，请检查网络连接", type = SnackbarType.ERROR)
                }
                .map { epgList ->
                    _uiState.value = (_uiState.value as MainUiState.Ready).copy(epgList = epgList)
                }
                .collect()
        }
    }
}

sealed interface MainUiState {
    data class Loading(val message: String? = null) : MainUiState
    data class Error(val message: String? = null) : MainUiState
    data class Ready(
        val channelGroupList: ChannelGroupList = ChannelGroupList(),
        val epgList: EpgList = EpgList(),
    ) : MainUiState
}