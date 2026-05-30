package space.kalloware.hysera.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import space.kalloware.hysera.config.ConfigRepository
import space.kalloware.hysera.config.CoreType
import space.kalloware.hysera.config.SaveConfigResult
import space.kalloware.hysera.logging.EventLogger
import space.kalloware.hysera.vpn.HyseraVpnService
import space.kalloware.hysera.vpn.VpnStateStore
import space.kalloware.hysera.vpn.VpnStatus

class HyseraViewModel(application: Application) : AndroidViewModel(application) {
    private val app = getApplication<Application>()
    private val repository = ConfigRepository(app)
    private val settings = app.getSharedPreferences(SETTINGS_PREFERENCES, Context.MODE_PRIVATE)
    private val mutableSelectedConfigId = MutableStateFlow(repository.configs.value.firstOrNull()?.id)
    private val mutableDarkTheme = MutableStateFlow(settings.getBoolean(KEY_DARK_THEME, false))
    private val mutableUiMessage = MutableStateFlow<String?>(null)

    val configs = repository.configs
    val selectedConfigId = mutableSelectedConfigId.asStateFlow()
    val darkTheme = mutableDarkTheme.asStateFlow()
    val uiMessage = mutableUiMessage.asStateFlow()
    val vpnState = VpnStateStore.state
    val logs = EventLogger.entries

    init {
        EventLogger.info("Hysera UI started.")
    }

    fun selectConfig(id: String) {
        mutableSelectedConfigId.value = id
    }

    fun saveConfig(name: String, rawConfig: String, preferredCore: CoreType): Boolean {
        return when (val result = repository.save(name, rawConfig, preferredCore)) {
            is SaveConfigResult.Success -> {
                mutableSelectedConfigId.value = result.config.id
                showMessage("Saved '${result.config.name}'.")
                true
            }

            is SaveConfigResult.Error -> {
                showMessage(result.message)
                false
            }
        }
    }

    fun deleteConfig(id: String) {
        repository.delete(id)
        if (mutableSelectedConfigId.value == id) {
            mutableSelectedConfigId.value = repository.configs.value.firstOrNull()?.id
        }
    }

    fun connectSelected(requestVpnPermission: (String) -> Unit) {
        val configId = mutableSelectedConfigId.value
        if (configId == null) {
            EventLogger.error("Connection blocked: no config selected.")
            showMessage("Add and select a config before connecting.")
            return
        }
        requestVpnPermission(configId)
    }

    fun startVpn(configId: String) {
        app.startForegroundService(HyseraVpnService.connectIntent(app, configId))
    }

    fun disconnectVpn() {
        if (vpnState.value.status == VpnStatus.DISCONNECTED) {
            return
        }
        app.startService(HyseraVpnService.disconnectIntent(app))
    }

    fun onVpnPermissionDenied() {
        EventLogger.error("Android VPN permission was denied.")
        showMessage("Android VPN permission is required to start Hysera.")
    }

    fun setDarkTheme(enabled: Boolean) {
        mutableDarkTheme.value = enabled
        settings.edit().putBoolean(KEY_DARK_THEME, enabled).apply()
        EventLogger.info("Hysera theme changed to ${if (enabled) "dark" else "light"}.")
    }

    fun consumeUiMessage() {
        mutableUiMessage.value = null
    }

    private fun showMessage(message: String) {
        mutableUiMessage.value = message
    }

    private companion object {
        const val SETTINGS_PREFERENCES = "hysera_settings"
        const val KEY_DARK_THEME = "dark_theme"
    }
}
