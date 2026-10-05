package com.gvineon550coder.tvapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gvineon550coder.tvapp.data.Channel
import com.gvineon550coder.tvapp.data.ChannelInfo
import com.gvineon550coder.tvapp.data.Prefs
import com.gvineon550coder.tvapp.data.Program
import com.gvineon550coder.tvapp.data.RutubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HomeState(
    val loading: Boolean = false,
    val channels: List<Channel> = emptyList(),
    val blockedIds: Set<String> = emptySet(),
    val status: String = "",
    val currentId: String? = null,
    val currentTitle: String = "",
    val streamUrl: String? = null,
    val program: List<Program> = emptyList(),
    val programDate: LocalDate = LocalDate.now(),
    val programLoading: Boolean = false,
    val search: String = "",
    val showSettings: Boolean = false,
    val maxHeight: Int = 0
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: RutubeRepository,
    private val prefs: Prefs
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private val programCache = mutableMapOf<Pair<String, LocalDate>, List<Program>>()
    private val infoLoaded = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            val snap = prefs.snapshot()
            _state.value = _state.value.copy(maxHeight = snap.maxHeight)
            loadCache(snap.cache)
        }
        loadChannels()
    }

    private fun loadCache(json: String) {
        if (json.isBlank() || json == "{}") return
        runCatching {
            val obj = com.google.gson.JsonParser.parseString(json).asJsonObject
            val list = mutableListOf<Channel>()
            for ((id, v) in obj.entrySet()) {
                val arr = v.asJsonArray
                val titleEl = if (arr.size() > 0) arr.get(0) else null
                val groupEl = if (arr.size() > 1) arr.get(1) else null
                val descEl = if (arr.size() > 2) arr.get(2) else null
                val title = if (titleEl != null && !titleEl.isJsonNull)
                    titleEl.asString else null
                if (title.isNullOrBlank()) continue
                val group = if (groupEl != null && !groupEl.isJsonNull)
                    groupEl.asString else null
                val desc = if (descEl != null && !descEl.isJsonNull)
                    descEl.asString else ""
                list += Channel(id, title, desc, group)
            }
            if (list.isNotEmpty()) {
                _state.value = _state.value.copy(
                    channels = list,
                    status = "Каналов: ${list.size} (кеш)"
                )
            }
        }
    }

    private fun saveCache() {
        viewModelScope.launch {
            val obj = com.google.gson.JsonObject()
            _state.value.channels.forEach { ch ->
                val arr = com.google.gson.JsonArray()
                arr.add(ch.title)
                arr.add(ch.category ?: "")
                arr.add(ch.description)
                obj.add(ch.id, arr)
            }
            prefs.setCache(obj.toString())
        }
    }

    fun loadChannels() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, status = "Загрузка каналов...")
            val ch = runCatching { repo.fetchChannels() }.getOrDefault(emptyList())
            val filtered = ch.filterNot { it.id in _state.value.blockedIds }
            _state.value = _state.value.copy(
                loading = false,
                channels = filtered,
                status = if (filtered.isEmpty()) "Каналы не найдены" else "Каналов: ${filtered.size}"
            )
            if (filtered.isNotEmpty()) {
                saveCache()
                prefetchInfo(filtered)
            }
        }
    }

    private fun prefetchInfo(channels: List<Channel>) {
        viewModelScope.launch {
            for (ch in channels) {
                if (ch.id in infoLoaded) continue
                val info = runCatching { repo.fetchChannelInfo(ch.id) }.getOrNull() ?: continue
                if (info.blocked) {
                    hideBlocked(ch.id, info.reason)
                    continue
                }
                infoLoaded += ch.id
                if (info.description.isNotBlank() || info.category.isNotBlank()) {
                    _state.value = _state.value.copy(
                        channels = _state.value.channels.map {
                            if (it.id == ch.id) it.copy(
                                description = info.description.ifBlank { it.description },
                                category = info.category.ifBlank { it.category ?: "" }
                                    .ifBlank { null }
                            ) else it
                        }
                    )
                }
            }
            saveCache()
        }
    }

    fun setSearch(text: String) {
        _state.value = _state.value.copy(search = text)
    }

    fun openSettings() { _state.value = _state.value.copy(showSettings = true) }
    fun closeSettings() { _state.value = _state.value.copy(showSettings = false) }

    fun saveSettings(apiProxy: String, apiProxyEnabled: Boolean,
                     streamProxy: String, streamProxyEnabled: Boolean,
                     maxHeight: Int) {
        viewModelScope.launch {
            prefs.setApiProxy(apiProxy)
            prefs.setApiProxyEnabled(apiProxyEnabled)
            prefs.setStreamProxy(streamProxy)
            prefs.setStreamProxyEnabled(streamProxyEnabled)
            prefs.setMaxHeight(maxHeight)
            _state.value = _state.value.copy(maxHeight = maxHeight, showSettings = false)
        }
    }

    fun play(channel: Channel) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                currentId = channel.id,
                currentTitle = channel.title,
                streamUrl = null,
                status = "Получение потока: ${channel.title}..."
            )
            val snap = prefs.snapshot()
            val info = runCatching { repo.fetchStream(channel.id, snap.maxHeight) }
                .getOrDefault(ChannelInfo(ok = false))

            if (info.blocked) {
                hideBlocked(channel.id, info.reason)
                return@launch
            }
            if (info.streamUrl == null) {
                _state.value = _state.value.copy(status = "Поток не найден")
                return@launch
            }
            _state.value = _state.value.copy(
                streamUrl = info.streamUrl,
                status = "Играет: ${channel.title}"
            )
            loadProgram(channel.id, LocalDate.now())
        }
    }

    private fun hideBlocked(id: String, reason: String) {
        _state.value = _state.value.copy(
            blockedIds = _state.value.blockedIds + id,
            channels = _state.value.channels.filterNot { it.id == id },
            status = "Канал недоступен: $reason"
        )
        saveCache()
    }

    fun loadProgram(id: String, date: LocalDate) {
        val key = id to date
        programCache[key]?.let {
            _state.value = _state.value.copy(program = it, programDate = date)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(programLoading = true, programDate = date)
            val list = runCatching { repo.fetchProgram(id, date) }.getOrDefault(emptyList())
            programCache[key] = list
            _state.value = _state.value.copy(program = list, programLoading = false)
        }
    }
}
