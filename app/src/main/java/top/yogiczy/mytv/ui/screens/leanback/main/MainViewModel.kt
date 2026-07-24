package top.yogiczy.mytv.ui.screens.leanback.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
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
import top.yogiczy.mytv.data.entities.EpgList
import top.yogiczy.mytv.data.entities.IptvGroupList
import top.yogiczy.mytv.data.entities.IptvSource
import top.yogiczy.mytv.data.entities.IptvGroupList.Companion.iptvList
import top.yogiczy.mytv.data.repositories.epg.EpgRepository
import top.yogiczy.mytv.data.repositories.iptv.IptvRepository
import top.yogiczy.mytv.data.repositories.iptv.IptvSourceProbeRepository
import top.yogiczy.mytv.data.repositories.iptv.IptvSourceSettingRepository
import top.yogiczy.mytv.data.utils.Constants
import top.yogiczy.mytv.ui.utils.SP

class LeanbackMainViewModel : ViewModel() {
    private val iptvRepository = IptvRepository()
    private val epgRepository = EpgRepository()

    private val _uiState = MutableStateFlow<LeanbackMainUiState>(LeanbackMainUiState.Loading())
    val uiState: StateFlow<LeanbackMainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            selectInitialSource()
            refreshIptv()
            refreshEpg()
        }
    }

    private suspend fun selectInitialSource() {
        val remoteSources = runCatching { IptvSourceSettingRepository().fetch() }
            .getOrDefault(emptyList())
        val current = IptvSource("当前直播源", SP.iptvSourceUrl)
        val candidates = (listOf(current) + remoteSources).distinctBy { it.url }
        if (candidates.isEmpty()) return

        val probeRepository = IptvSourceProbeRepository(SP.videoPlayerUserAgent)
        var checked = 0
        _uiState.value = LeanbackMainUiState.SourceChecking(checked, candidates.size)
        val batches = buildList {
            add(listOf(candidates.first()))
            addAll(candidates.drop(1).chunked(SOURCE_PROBE_CONCURRENCY))
        }
        for (batch in batches) {
            val playable = coroutineScope {
                val results = Channel<Pair<IptvSource, Boolean>>(Channel.UNLIMITED)
                val jobs = batch.map { source ->
                    async {
                        val result = runCatching { probeRepository.isPlayable(source) }
                            .getOrDefault(false)
                        results.send(source to result)
                    }
                }
                repeat(jobs.size) {
                    val result = results.receive()
                    checked += 1
                    _uiState.value = LeanbackMainUiState.SourceChecking(checked, candidates.size)
                    if (result.second) {
                        jobs.forEach { it.cancel() }
                        results.cancel()
                        return@coroutineScope result.first
                    }
                }
                results.cancel()
                null
            }
            if (playable != null) {
                SP.iptvSourceUrl = playable.url
                SP.iptvSourceUrlHistoryList += playable.url
                return
            }
        }
    }

    private suspend fun refreshIptv() {
        flow {
            emit(
                iptvRepository.getIptvGroupList(
                    sourceUrl = SP.iptvSourceUrl,
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
                SP.iptvSourceUrlHistoryList -= SP.iptvSourceUrl
            }
            .map {
                _uiState.value = LeanbackMainUiState.Ready(iptvGroupList = it)
                SP.iptvSourceUrlHistoryList += SP.iptvSourceUrl
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

    private companion object {
        const val SOURCE_PROBE_CONCURRENCY = 8
    }
}

sealed interface LeanbackMainUiState {
    data class SourceChecking(val checked: Int, val total: Int) : LeanbackMainUiState
    data class Loading(val message: String? = null) : LeanbackMainUiState
    data class Error(val message: String? = null) : LeanbackMainUiState
    data class Ready(
        val iptvGroupList: IptvGroupList = IptvGroupList(),
        val epgList: EpgList = EpgList(),
    ) : LeanbackMainUiState
}
