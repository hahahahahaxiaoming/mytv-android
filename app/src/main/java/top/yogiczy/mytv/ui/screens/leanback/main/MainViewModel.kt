package top.yogiczy.mytv.ui.screens.leanback.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import top.yogiczy.mytv.data.entities.EpgList
import top.yogiczy.mytv.data.entities.IptvGroupList
import top.yogiczy.mytv.data.entities.IptvGroupList.Companion.iptvList
import top.yogiczy.mytv.data.repositories.epg.EpgRepository
import top.yogiczy.mytv.data.repositories.iptv.IptvRepository
import top.yogiczy.mytv.data.repositories.iptv.WebIptvRepository
import top.yogiczy.mytv.data.utils.Constants
import top.yogiczy.mytv.ui.utils.SP

class LeanbackMainViewModel : ViewModel() {
    private val iptvRepository = IptvRepository()
    private val webIptvRepository = WebIptvRepository()
    private val epgRepository = EpgRepository()

    private val _uiState = MutableStateFlow<LeanbackMainUiState>(LeanbackMainUiState.Loading())
    val uiState: StateFlow<LeanbackMainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            SP.iptvSource.collectLatest { sourceUrl ->
                _uiState.value = LeanbackMainUiState.Loading()
                refreshIptv(sourceUrl)
                refreshEpg()
            }
        }
    }

    private suspend fun refreshIptv(sourceUrl: String) {
        if (sourceUrl == Constants.WEB_IPTV_LIST_URL) {
            _uiState.value = LeanbackMainUiState.Loading("正在加载全国直播…")
            _uiState.value = try {
                LeanbackMainUiState.Ready(iptvGroupList = webIptvRepository.load())
            } catch (e: java.io.IOException) {
                LeanbackMainUiState.Error("全国直播加载失败，请检查网络后重启应用")
            }
            return
        }

        // 自定义源共享一个缓存文件，每次切源先清除前一份列表。
        iptvRepository.clearCache()
        flow {
            emit(
                iptvRepository.getIptvGroupList(
                    sourceUrl = sourceUrl,
                    cacheTime = SP.iptvSourceCacheTime,
                    simplify = SP.iptvSourceSimplify,
                )
            )
        }
            .retryWhen { _, attempt ->
                if (attempt >= Constants.HTTP_RETRY_COUNT) return@retryWhen false

                _uiState.value =
                    LeanbackMainUiState.Loading("获取远程直播源(${attempt + 1}/${Constants.HTTP_RETRY_COUNT})...")
                delay(Constants.HTTP_RETRY_INTERVAL)
                true
            }
            .catch {
                _uiState.value = LeanbackMainUiState.Error(it.message)
                SP.iptvSourceUrlHistoryList -= sourceUrl
            }
            .map {
                _uiState.value = LeanbackMainUiState.Ready(iptvGroupList = it)
                SP.iptvSourceUrlHistoryList += sourceUrl
                it
            }
            .collect()
    }

    private suspend fun refreshEpg() {
        if (_uiState.value is LeanbackMainUiState.Ready) {
            val iptvGroupList = (_uiState.value as LeanbackMainUiState.Ready).iptvGroupList

            flow {
                emit(
                    epgRepository.getEpgList(
                        xmlUrl = SP.epgXmlUrl,
                        filteredChannels = iptvGroupList.iptvList.map { it.channelName },
                        refreshTimeThreshold = SP.epgRefreshTimeThreshold,
                    )
                )
            }
                .retry(Constants.HTTP_RETRY_COUNT) { delay(Constants.HTTP_RETRY_INTERVAL); true }
                .catch {
                    emit(EpgList())
                    SP.epgXmlUrlHistoryList -= SP.epgXmlUrl
                }
                .map { epgList ->
                    _uiState.value =
                        (_uiState.value as LeanbackMainUiState.Ready).copy(epgList = epgList)
                    SP.epgXmlUrlHistoryList += SP.epgXmlUrl
                }
                .collect()
        }
    }
}

sealed interface LeanbackMainUiState {
    data class Loading(val message: String? = null) : LeanbackMainUiState
    data class Error(val message: String? = null) : LeanbackMainUiState
    data class Ready(
        val iptvGroupList: IptvGroupList = IptvGroupList(),
        val epgList: EpgList = EpgList(),
    ) : LeanbackMainUiState
}
