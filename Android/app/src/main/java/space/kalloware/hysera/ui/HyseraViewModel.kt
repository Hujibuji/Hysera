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
import space.kalloware.hysera.config.ImportSourceDetector
import space.kalloware.hysera.config.ImportSourceType
import space.kalloware.hysera.config.SaveConfigResult
import space.kalloware.hysera.config.SavedConfig
import space.kalloware.hysera.logging.EventLogger
import space.kalloware.hysera.subscription.SubscriptionNode
import space.kalloware.hysera.subscription.SubscriptionOperationResult
import space.kalloware.hysera.subscription.SubscriptionProfile
import space.kalloware.hysera.subscription.SubscriptionRepository
import space.kalloware.hysera.vpn.HyseraVpnService
import space.kalloware.hysera.vpn.VpnStateStore
import space.kalloware.hysera.vpn.VpnStatus

class HyseraViewModel(application: Application) : AndroidViewModel(application) {
    private val app = getApplication<Application>()
    private val repository = ConfigRepository(app)
    private val subscriptionRepository = SubscriptionRepository(app)
    private val settings = app.getSharedPreferences(SETTINGS_PREFERENCES, Context.MODE_PRIVATE)
    private val mutableSelectedTarget = MutableStateFlow(repository.configs.value.firstOrNull())
    private val mutableDarkTheme = MutableStateFlow(settings.getBoolean(KEY_DARK_THEME, false))
    private val mutableUiMessage = MutableStateFlow<String?>(null)
    private val mutableSubscriptionBusy = MutableStateFlow(false)

    val configs = repository.configs
    val subscriptions = subscriptionRepository.profiles
    val selectedTarget = mutableSelectedTarget.asStateFlow()
    val darkTheme = mutableDarkTheme.asStateFlow()
    val uiMessage = mutableUiMessage.asStateFlow()
    val subscriptionBusy = mutableSubscriptionBusy.asStateFlow()
    val vpnState = VpnStateStore.state
    val logs = EventLogger.entries

    init {
        EventLogger.info("Hysera UI started.")
    }

    fun selectConfig(id: String) {
        repository.findById(id)?.let { config ->
            mutableSelectedTarget.value = config
            showMessage("Selected '${config.name}'.")
        }
    }

    fun selectSubscriptionNode(profileId: String, node: SubscriptionNode) {
        if (!node.isValid) {
            showMessage(node.error ?: "This subscription node is not supported.")
            return
        }

        mutableSelectedTarget.value = node.toSavedConfig(profileId)
        showMessage("Selected '${node.name}'.")
    }

    fun saveConfig(name: String, rawConfig: String, preferredCore: CoreType): Boolean {
        return when (val result = repository.save(name, rawConfig, preferredCore)) {
            is SaveConfigResult.Success -> {
                mutableSelectedTarget.value = result.config
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
        if (mutableSelectedTarget.value?.id == id) {
            mutableSelectedTarget.value = fallbackSelection()
        }
        showMessage("Config deleted.")
    }

    fun importEntry(name: String, rawInput: String, preferredCore: CoreType) {
        val input = rawInput.trim()
        when (ImportSourceDetector.detect(input)) {
            ImportSourceType.EMPTY -> showMessage("Paste a config, subscription URL, or raw subscription text.")
            ImportSourceType.SINGLE_CONFIG -> saveConfig(name, input, preferredCore)
            ImportSourceType.SUBSCRIPTION_URL -> runSubscriptionOperation {
                handleSubscriptionResult(subscriptionRepository.fetchAndImport(input))
            }

            ImportSourceType.RAW_SUBSCRIPTION -> runSubscriptionOperation {
                handleSubscriptionResult(subscriptionRepository.importText(null, input))
            }

            ImportSourceType.UNKNOWN -> showUnsupportedInput()
        }
    }

    fun refreshSubscription(id: String) {
        runSubscriptionOperation {
            when (val result = subscriptionRepository.refresh(id)) {
                is SubscriptionOperationResult.Success -> {
                    refreshSelectedNode(result.profile)
                    showMessage("Updated '${result.profile.name}'.")
                }

                is SubscriptionOperationResult.Error -> showMessage(result.message)
            }
        }
    }

    fun deleteSubscription(id: String) {
        subscriptionRepository.delete(id)
        if (mutableSelectedTarget.value?.id?.startsWith(subscriptionNodePrefix(id)) == true) {
            mutableSelectedTarget.value = fallbackSelection()
        }
        showMessage("Subscription deleted.")
    }

    fun connectSelected(requestVpnPermission: (SavedConfig) -> Unit) {
        val selectedTarget = mutableSelectedTarget.value
        if (selectedTarget == null) {
            EventLogger.error("Connection blocked: no config or subscription node selected.")
            showMessage("Add and select a config or subscription node before connecting.")
            return
        }
        requestVpnPermission(selectedTarget)
    }

    fun startVpn(config: SavedConfig) {
        app.startForegroundService(HyseraVpnService.connectIntent(app, config))
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

    private fun handleSubscriptionResult(result: SubscriptionOperationResult) {
        when (result) {
            is SubscriptionOperationResult.Success -> {
                showMessage(
                    "Imported '${result.profile.name}' with ${result.profile.nodes.size} node(s).",
                )
            }

            is SubscriptionOperationResult.Error -> showMessage(result.message)
        }
    }

    private fun showUnsupportedInput() {
        val message = "Unknown input. Paste JSON, a supported protocol:// URI, an HTTPS subscription URL, " +
            "or raw subscription text."
        EventLogger.error(message)
        showMessage(message)
    }

    private fun refreshSelectedNode(profile: SubscriptionProfile) {
        val prefix = subscriptionNodePrefix(profile.id)
        val selectedId = mutableSelectedTarget.value?.id ?: return
        if (!selectedId.startsWith(prefix)) {
            return
        }

        val nodeId = selectedId.removePrefix(prefix)
        mutableSelectedTarget.value = profile.nodes
            .firstOrNull { node -> node.id == nodeId && node.isValid }
            ?.toSavedConfig(profile.id)
            ?: fallbackSelection()
    }

    private fun fallbackSelection(): SavedConfig? = repository.configs.value.firstOrNull()

    private fun SubscriptionNode.toSavedConfig(profileId: String) = SavedConfig(
        id = subscriptionNodePrefix(profileId) + id,
        name = name,
        rawConfig = rawConfig,
        preferredCore = CoreType.AUTO,
        createdAtMillis = 0L,
    )

    private fun subscriptionNodePrefix(profileId: String) = "subscription:$profileId:"

    private companion object {
        const val SETTINGS_PREFERENCES = "hysera_settings"
        const val KEY_DARK_THEME = "dark_theme"
    }
}
