package space.kalloware.hysera.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import space.kalloware.hysera.config.ConfigRepository
import space.kalloware.hysera.config.CoreType
import space.kalloware.hysera.config.SaveConfigResult
import space.kalloware.hysera.logging.EventLogger
import space.kalloware.hysera.subscription.SubscriptionNode
import space.kalloware.hysera.subscription.SubscriptionOperationResult
import space.kalloware.hysera.subscription.SubscriptionParseResult
import space.kalloware.hysera.subscription.SubscriptionRepository
import space.kalloware.hysera.vpn.HyseraVpnService
import space.kalloware.hysera.vpn.VpnStateStore
import space.kalloware.hysera.vpn.VpnStatus

class HyseraViewModel(application: Application) : AndroidViewModel(application) {
    private val app = getApplication<Application>()
    private val repository = ConfigRepository(app)
    private val subscriptionRepository = SubscriptionRepository(app)
    private val settings = app.getSharedPreferences(SETTINGS_PREFERENCES, Context.MODE_PRIVATE)
    private val mutableSelectedConfigId = MutableStateFlow(repository.configs.value.firstOrNull()?.id)
    private val mutableDarkTheme = MutableStateFlow(settings.getBoolean(KEY_DARK_THEME, false))
    private val mutableUiMessage = MutableStateFlow<String?>(null)
    private val mutableSubscriptionPreview = MutableStateFlow<SubscriptionParseResult?>(null)
    private val mutableSubscriptionBusy = MutableStateFlow(false)

    val configs = repository.configs
    val subscriptions = subscriptionRepository.profiles
    val selectedConfigId = mutableSelectedConfigId.asStateFlow()
    val darkTheme = mutableDarkTheme.asStateFlow()
    val uiMessage = mutableUiMessage.asStateFlow()
    val subscriptionPreview = mutableSubscriptionPreview.asStateFlow()
    val subscriptionBusy = mutableSubscriptionBusy.asStateFlow()
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

    fun checkSubscription(url: String, rawText: String) {
        runSubscriptionOperation {
            val subscriptionText = loadSubscriptionText(url, rawText) ?: return@runSubscriptionOperation
            val result = subscriptionRepository.parse(subscriptionText)
            mutableSubscriptionPreview.value = result
            showMessage(
                "Subscription check found ${result.validNodes.size} valid node(s) " +
                    "and ${result.errors.size} warning(s).",
            )
        }
    }

    fun importSubscription(url: String, rawText: String) {
        runSubscriptionOperation {
            val normalizedUrl = url.trim().takeIf(String::isNotBlank)
            val result = if (rawText.isNotBlank()) {
                subscriptionRepository.importText(normalizedUrl, rawText)
            } else {
                if (normalizedUrl == null) {
                    showMessage("Paste a subscription URL or raw subscription text.")
                    return@runSubscriptionOperation
                }
                subscriptionRepository.fetchAndImport(normalizedUrl)
            }

            when (result) {
                is SubscriptionOperationResult.Success -> {
                    mutableSubscriptionPreview.value = result.parseResult
                    showMessage(
                        "Imported '${result.profile.name}' with ${result.profile.nodes.size} node(s).",
                    )
                }

                is SubscriptionOperationResult.Error -> showMessage(result.message)
            }
        }
    }

    fun refreshSubscription(id: String) {
        runSubscriptionOperation {
            when (val result = subscriptionRepository.refresh(id)) {
                is SubscriptionOperationResult.Success -> {
                    mutableSubscriptionPreview.value = result.parseResult
                    showMessage("Updated '${result.profile.name}'.")
                }

                is SubscriptionOperationResult.Error -> showMessage(result.message)
            }
        }
    }

    fun deleteSubscription(id: String) {
        subscriptionRepository.delete(id)
        showMessage("Subscription deleted.")
    }

    fun saveSubscriptionNode(node: SubscriptionNode) {
        saveConfig(node.name, node.rawConfig, CoreType.AUTO)
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

    private fun runSubscriptionOperation(operation: suspend () -> Unit) {
        if (mutableSubscriptionBusy.value) {
            return
        }
        viewModelScope.launch {
            mutableSubscriptionBusy.value = true
            try {
                operation()
            } finally {
                mutableSubscriptionBusy.value = false
            }
        }
    }

    private suspend fun loadSubscriptionText(url: String, rawText: String): String? {
        if (rawText.isNotBlank()) {
            return rawText
        }

        val normalizedUrl = url.trim()
        if (normalizedUrl.isBlank()) {
            showMessage("Paste a subscription URL or raw subscription text.")
            return null
        }

        return subscriptionRepository.fetchText(normalizedUrl).fold(
            onSuccess = { it },
            onFailure = { exception ->
                val message = exception.message ?: "Could not download subscription."
                EventLogger.error("Subscription check failed: $message")
                showMessage(message)
                null
            },
        )
    }

    private companion object {
        const val SETTINGS_PREFERENCES = "hysera_settings"
        const val KEY_DARK_THEME = "dark_theme"
    }
}
