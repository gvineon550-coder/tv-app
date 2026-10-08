package com.tv.player.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tv.player.data.Channel
import com.tv.player.data.ChannelInfo
import com.tv.player.data.Prefs
import com.tv.player.data.RutubeRepository
import com.tv.player.util.LicenseManager
import com.tv.player.util.LicenseResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.inject.Inject

enum class PinDialogMode {
    NONE,
    OPEN_SETTINGS,
    SET_PIN,
    REMOVE_PIN
}

enum class LicenseState {
    Checking,
    Allowed,
    Denied,
    Unknown
}

data class HomeState(
    val loading: Boolean = false,
    val channels: List<Channel> = emptyList(),
    val blockedIds: Set<String> = emptySet(),
    val status: String = "",
    val currentId: String? = null,
    val currentTitle: String = "",
    val streamUrl: String? = null,
    val search: String = "",
    val showSettings: Boolean = false,
    val maxHeight: Int = 0,
    val playerFullscreen: Boolean = false,
    val resolution: String = "",
    val fullscreenControlsVisible: Boolean = false,
    val favorites: Set<String> = emptySet(),
    val sleepDeadline: Long = 0L,
    val lastActivity: Long = System.currentTimeMillis(),
    val isPlaying: Boolean = false,
    val showChannelOverlay: Boolean = false,
    val showDiagnostics: Boolean = false,
    val jsonSourceUrl: String = "",
    val hasPin: Boolean = false,
    val showPinDialog: Boolean = false,
    val pinDialogMode: PinDialogMode = PinDialogMode.NONE,
    val pinError: String = "",
    val licenseState: LicenseState = LicenseState.Checking,
    val deviceId: String = "",
    val licenseReason: String = ""
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: RutubeRepository,
    private val prefs: Prefs,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private val infoLoaded = mutableSetOf<String>()
    private var refreshAttempts = 0
    private var currentPinHash: String = ""

    init {
        viewModelScope.launch {
            checkLicense()
            if (_state.value.licenseState != LicenseState.Allowed) {
                return@launch
            }
            initInternal()
        }
    }

    private suspend fun initInternal() {
        val snap = prefs.snapshot()
        currentPinHash = snap.pinHash
        _state.value = _state.value.copy(
            maxHeight = snap.maxHeight,
            favorites = parseFavorites(snap.favorites),
            jsonSourceUrl = snap.jsonSourceUrl,
            hasPin = snap.pinHash.isNotBlank()
        )
        loadCache(snap.cache)

        val jsonOk = runCatching {
            repo.loadFromJson(snap.jsonSourceUrl)
        }.getOrDefault(false)

        val now = System.currentTimeMillis()
        val weekMs = 7L * 24 * 60 * 60 * 1000
        val cacheEmpty = snap.cache.isBlank() || snap.cache == "{}"
        val cacheOld = (now - snap.lastParse) > weekMs

        if (jsonOk) {
            loadChannels()
        } else if (cacheEmpty || cacheOld) {
            loadChannels()
        } else {
            _state.value = _state.value.copy(
                status = "Каналов: ${_state.value.channels.size} (кеш)"
            )
        }
    }

    private suspend fun checkLicense() {
        _state.value = _state.value.copy(
            licenseState = LicenseState.Checking,
            deviceId = LicenseManager.getDeviceId()
        )
        val result = runCatching { LicenseManager.check(appContext) }.getOrNull()
        when (result) {
            is LicenseResult.Allowed -> _state.value = _state.value.copy(
                licenseState = LicenseState.Allowed,
                licenseReason = ""
            )
            is LicenseResult.Denied -> _state.value = _state.value.copy(
                licenseState = LicenseState.Denied,
                licenseReason = "Устройство не в списке разрешённых"
            )
            is LicenseResult.Unknown -> _state.value = _state.value.copy(
                licenseState = LicenseState.Unknown,
                licenseReason = result.reason
            )
            null -> _state.value = _state.value.copy(
                licenseState = LicenseState.Unknown,
                licenseReason = "Ошибка проверки"
            )
        }
    }

    fun recheckLicense() {
        viewModelScope.launch {
            LicenseManager.clearCache(appContext)
            checkLicense()
            if (_state.value.licenseState == LicenseState.Allowed) {
                initInternal()
            }
        }
    }

    private fun sha256(s: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun onSettingsClick() {
        registerActivity()
        if (_state.value.hasPin) {
            _state.value = _state.value.copy(
                showPinDialog = true,
                pinDialogMode = PinDialogMode.OPEN_SETTINGS,
                pinError = ""
            )
        } else {
            openSettings()
        }
    }

    fun requestSetPin() {
        _state.value = _state.value.copy(
            showPinDialog = true,
            pinDialogMode = PinDialogMode.SET_PIN,
            pinError = ""
        )
    }

    fun requestRemovePin() {
        _state.value = _state.value.copy(
            showPinDialog = true,
            pinDialogMode = PinDialogMode.REMOVE_PIN,
            pinError = ""
        )
    }

    fun submitPin(pin: String) {
        val mode = _state.value.pinDialogMode
        when (mode) {
            PinDialogMode.OPEN_SETTINGS -> {
                if (sha256(pin) == currentPinHash) {
                    _state.value = _state.value.copy(
                        showPinDialog = false,
                        pinDialogMode = PinDialogMode.NONE,
                        pinError = ""
                    )
                    openSettings()
                } else {
                    _state.value = _state.value.copy(pinError = "Неверный PIN")
                }
            }
            PinDialogMode.SET_PIN -> {
                if (pin.length < 4) {
                    _state.value = _state.value.copy(pinError = "Минимум 4 цифры")
                    return
                }
                val hash = sha256(pin)
                viewModelScope.launch {
                    prefs.setPinHash(hash)
                    currentPinHash = hash
                    _state.value = _state.value.copy(
                        hasPin = true,
                        showPinDialog = false,
                        pinDialogMode = PinDialogMode.NONE,
                        pinError = "",
                        status = "PIN установлен"
                    )
                }
            }
            PinDialogMode.REMOVE_PIN -> {
                if (sha256(pin) == currentPinHash) {
                    viewModelScope.launch {
                        prefs.setPinHash("")
                        currentPinHash = ""
                        _state.value = _state.value.copy(
                            hasPin = false,
                            showPinDialog = false,
                            pinDialogMode = PinDialogMode.NONE,
                            pinError = "",
                            status = "PIN снят"
                        )
                    }
                } else {
                    _state.value = _state.value.copy(pinError = "Неверный PIN")
                }
            }
            PinDialogMode.NONE -> {}
        }
    }

    fun cancelPinDialog() {
        _state.value = _state.value.copy(
            showPinDialog = false,
            pinDialogMode = PinDialogMode.NONE,
            pinError = ""
        )
    }

    fun registerActivity() {
        _state.value = _state.value.copy(lastActivity = System.currentTimeMillis())
    }

    fun setPlaying(value: Boolean) {
        if (value) refreshAttempts = 0
        _state.value = _state.value.copy(isPlaying = value)
    }

    fun toggleDiagnostics() {
        registerActivity()
        _state.value = _state.value.copy(
            showDiagnostics = !_state.value.showDiagnostics
        )
    }

    fun setSleepTimer(minutes: Int) {
        val deadline = if (minutes <= 0) 0L
            else System.currentTimeMillis() + minutes * 60_000L
        _state.value = _state.value.copy(sleepDeadline = deadline)
        registerActivity()
    }

    fun openChannelOverlay() {
        registerActivity()
        _state.value = _state.value.copy(showChannelOverlay = true)
    }

    fun closeChannelOverlay() {
        registerActivity()
        _state.value = _state.value.copy(showChannelOverlay = false)
    }

    private fun parseFavorites(s: String): Set<String> {
        if (s.isBlank()) return emptySet()
        return s.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
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
                val avaEl = if (arr.size() > 3) arr.get(3) else null
                val title = if (titleEl != null && !titleEl.isJsonNull)
                    titleEl.asString else null
                if (title.isNullOrBlank()) continue
                val group = if (groupEl != null && !groupEl.isJsonNull)
                    groupEl.asString else null
                val desc = if (descEl != null && !descEl.isJsonNull)
                    descEl.asString else ""
                val ava = if (avaEl != null && !avaEl.isJsonNull)
                    avaEl.asString else ""
                list += Channel(id, title, desc, group, ava)
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
                arr.add(ch.avatar)
                obj.add(ch.id, arr)
            }
            prefs.setCache(obj.toString())
        }
    }

    fun loadChannels() {
        registerActivity()
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, status = "Загрузка каналов...")
            val ch = runCatching { repo.fetchChannels() }.getOrDefault(emptyList())
            val filtered = ch.filterNot { it.id in _state.value.blockedIds }

            // Мерджим свежий JSON с кэшем:
            // если в JSON аватар/описание пустые, а в кэше заполнены — берём из кэша.
            // Благодаря этому запросы к API идут только когда данных реально нет.
            val currentById = _state.value.channels.associateBy { it.id }
            val merged = filtered.map { fresh ->
                val cached = currentById[fresh.id]
                if (cached == null) fresh
                else fresh.copy(
                    avatar = if (fresh.avatar.isBlank()) cached.avatar else fresh.avatar,
                    description = if (fresh.description.isBlank()) cached.description
                                  else fresh.description,
                    category = fresh.category ?: cached.category
                )
            }

            _state.value = _state.value.copy(
                loading = false,
                channels = merged,
                status = if (merged.isEmpty()) "Каналы не найдены" else "Каналов: ${merged.size}"
            )

            if (merged.isNotEmpty()) {
                saveCache()
                prefs.setLastParse(System.currentTimeMillis())

                // Префетч только для тех, где ВСЁ ЕЩЁ нет аватара/описания.
                val needPrefetch = merged.filter {
                    it.avatar.isBlank() || it.description.isBlank()
                }
                if (needPrefetch.isNotEmpty()) {
                    prefetchInfo(needPrefetch)
                }
            }
        }
    }

    private fun prefetchInfo(channels: List<Channel>) {
        viewModelScope.launch {
            channels.chunked(4).forEach { batch ->
                val results = batch.map { ch ->
                    async(Dispatchers.IO) {
                        if (ch.id in infoLoaded) return@async ch.id to null
                        val info = runCatching { repo.fetchChannelInfo(ch.id) }.getOrNull()
                        ch.id to info
                    }
                }.awaitAll()

                for ((id, info) in results) {
                    if (info == null) continue
                    if (info.blocked) {
                        hideBlocked(id, info.reason)
                        continue
                    }
                    infoLoaded += id
                    if (info.description.isNotBlank() || info.category.isNotBlank()
                        || info.avatar.isNotBlank()) {
                        _state.value = _state.value.copy(
                            channels = _state.value.channels.map {
                                if (it.id == id) it.copy(
                                    description = info.description.ifBlank { it.description },
                                    category = info.category.ifBlank { it.category ?: "" }
                                        .ifBlank { null },
                                    avatar = info.avatar.ifBlank { it.avatar }
                                ) else it
                            }
                        )
                    }
                }
            }
            saveCache()
        }
    }

    fun setSearch(text: String) {
        registerActivity()
        _state.value = _state.value.copy(search = text)
    }

    private fun openSettings() {
        registerActivity()
        _state.value = _state.value.copy(showSettings = true)
    }

    fun closeSettings() {
        registerActivity()
        _state.value = _state.value.copy(showSettings = false)
    }

    fun togglePlayerFullscreen() {
        registerActivity()
        _state.value = _state.value.copy(
            playerFullscreen = !_state.value.playerFullscreen,
            fullscreenControlsVisible = false,
            showChannelOverlay = false
        )
    }

    fun exitPlayerFullscreen() {
        registerActivity()
        _state.value = _state.value.copy(
            playerFullscreen = false,
            fullscreenControlsVisible = false,
            showChannelOverlay = false
        )
    }

    fun setResolution(value: String) {
        _state.value = _state.value.copy(resolution = value)
    }

    fun toggleFullscreenControls() {
        registerActivity()
        _state.value = _state.value.copy(
            fullscreenControlsVisible = !_state.value.fullscreenControlsVisible
        )
    }

    fun hideFullscreenControls() {
        _state.value = _state.value.copy(fullscreenControlsVisible = false)
    }

    fun toggleFavorite(id: String) {
        registerActivity()
        val cur = _state.value.favorites
        val next = if (id in cur) cur - id else cur + id
        _state.value = _state.value.copy(favorites = next)
        viewModelScope.launch {
            prefs.setFavorites(next.joinToString(","))
        }
    }

    fun nextChannel() {
        registerActivity()
        val list = _state.value.channels
        if (list.isEmpty()) return
        val cur = _state.value.currentId
        val idx = list.indexOfFirst { it.id == cur }
        val nextIdx = if (idx < 0) 0 else (idx + 1) % list.size
        play(list[nextIdx])
    }

    fun prevChannel() {
        registerActivity()
        val list = _state.value.channels
        if (list.isEmpty()) return
        val cur = _state.value.currentId
        val idx = list.indexOfFirst { it.id == cur }
        val prevIdx = if (idx < 0) 0 else (idx - 1 + list.size) % list.size
        play(list[prevIdx])
    }

    fun saveSettings(apiProxy: String, apiProxyEnabled: Boolean,
                     streamProxy: String, streamProxyEnabled: Boolean,
                     maxHeight: Int, jsonSourceUrl: String) {
        viewModelScope.launch {
            prefs.setApiProxy(apiProxy)
            prefs.setApiProxyEnabled(apiProxyEnabled)
            prefs.setStreamProxy(streamProxy)
            prefs.setStreamProxyEnabled(streamProxyEnabled)
            prefs.setMaxHeight(maxHeight)
            prefs.setJsonSourceUrl(jsonSourceUrl)
            _state.value = _state.value.copy(
                maxHeight = maxHeight,
                showSettings = false,
                jsonSourceUrl = jsonSourceUrl
            )
            repo.clearJsonCache()
            loadChannels()
        }
    }

    fun play(channel: Channel) {
        refreshAttempts = 0
        playInternal(channel)
    }

    fun refreshCurrentStream() {
        if (refreshAttempts >= MAX_REFRESH_ATTEMPTS) {
            _state.value = _state.value.copy(
                status = "Канал недоступен. Переключите канал."
            )
            return
        }
        refreshAttempts++
        val cur = _state.value.currentId ?: return
        val ch = _state.value.channels.firstOrNull { it.id == cur } ?: return
        playInternal(ch)
    }

    private fun playInternal(channel: Channel) {
        registerActivity()
        viewModelScope.launch {
            _state.value = _state.value.copy(
                currentId = channel.id,
                currentTitle = channel.title,
                streamUrl = null,
                resolution = "",
                fullscreenControlsVisible = false,
                isPlaying = false,
                showChannelOverlay = false,
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

    companion object {
        private const val MAX_REFRESH_ATTEMPTS = 2
    }
}
