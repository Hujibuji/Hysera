package space.kalloware.hysera.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date
import space.kalloware.hysera.R
import space.kalloware.hysera.config.ConfigDetector
import space.kalloware.hysera.config.CoreType
import space.kalloware.hysera.config.SavedConfig
import space.kalloware.hysera.logging.EventLogger
import space.kalloware.hysera.logging.LogEntry
import space.kalloware.hysera.subscription.SubscriptionMetadata
import space.kalloware.hysera.subscription.SubscriptionNode
import space.kalloware.hysera.subscription.SubscriptionParseResult
import space.kalloware.hysera.subscription.SubscriptionProfile
import space.kalloware.hysera.subscription.SubscriptionUserInfo
import space.kalloware.hysera.ui.theme.HyseraTheme
import space.kalloware.hysera.vpn.VpnConnectionState
import space.kalloware.hysera.vpn.VpnStatus

private enum class HyseraDestination(
    val title: String,
    val icon: ImageVector,
) {
    HOME("Hysera", Icons.Default.Home),
    CONFIGS("Hysera Configs", Icons.Default.List),
    SUBSCRIPTIONS("Hysera Subscriptions", Icons.Default.Subscriptions),
    LOGS("Hysera Logs", Icons.Default.Article),
    SETTINGS("Hysera Settings", Icons.Default.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HyseraApp(
    viewModel: HyseraViewModel,
    requestVpnPermission: (String) -> Unit,
) {
    val configs by viewModel.configs.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val selectedConfigId by viewModel.selectedConfigId.collectAsStateWithLifecycle()
    val vpnState by viewModel.vpnState.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val darkTheme by viewModel.darkTheme.collectAsStateWithLifecycle()
    val uiMessage by viewModel.uiMessage.collectAsStateWithLifecycle()
    val subscriptionPreview by viewModel.subscriptionPreview.collectAsStateWithLifecycle()
    val subscriptionBusy by viewModel.subscriptionBusy.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var destination by rememberSaveable { mutableStateOf(HyseraDestination.HOME) }

    LaunchedEffect(uiMessage) {
        uiMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeUiMessage()
        }
    }

    HyseraTheme(darkTheme = darkTheme) {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text(destination.title) })
            },
            bottomBar = {
                NavigationBar {
                    HyseraDestination.entries.forEach { item ->
                        NavigationBarItem(
                            selected = destination == item,
                            onClick = { destination = item },
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = { Text(item.title.removePrefix("Hysera ").ifBlank { "Home" }) },
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { contentPadding ->
            when (destination) {
                HyseraDestination.HOME -> HomeScreen(
                    vpnState = vpnState,
                    selectedConfig = configs.firstOrNull { it.id == selectedConfigId },
                    connect = { viewModel.connectSelected(requestVpnPermission) },
                    disconnect = viewModel::disconnectVpn,
                    contentPadding = contentPadding,
                )

                HyseraDestination.CONFIGS -> ConfigsScreen(
                    configs = configs,
                    selectedConfigId = selectedConfigId,
                    selectConfig = viewModel::selectConfig,
                    deleteConfig = viewModel::deleteConfig,
                    saveConfig = viewModel::saveConfig,
                    contentPadding = contentPadding,
                )

                HyseraDestination.SUBSCRIPTIONS -> SubscriptionsScreen(
                    profiles = subscriptions,
                    preview = subscriptionPreview,
                    busy = subscriptionBusy,
                    checkSubscription = viewModel::checkSubscription,
                    importSubscription = viewModel::importSubscription,
                    refreshSubscription = viewModel::refreshSubscription,
                    deleteSubscription = viewModel::deleteSubscription,
                    saveNode = viewModel::saveSubscriptionNode,
                    contentPadding = contentPadding,
                )

                HyseraDestination.LOGS -> LogsScreen(
                    logs = logs,
                    contentPadding = contentPadding,
                )

                HyseraDestination.SETTINGS -> SettingsScreen(
                    darkTheme = darkTheme,
                    setDarkTheme = viewModel::setDarkTheme,
                    contentPadding = contentPadding,
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    vpnState: VpnConnectionState,
    selectedConfig: SavedConfig?,
    connect: () -> Unit,
    disconnect: () -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.hysera_logo),
            contentDescription = "Hysera logo",
            modifier = Modifier.size(112.dp),
        )
        Text(
            text = "Hysera",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
        )
        StatusCard(vpnState = vpnState, selectedConfig = selectedConfig)
        val stoppingOrStarting = vpnState.status == VpnStatus.CONNECTING ||
            vpnState.status == VpnStatus.DISCONNECTING
        val shouldDisconnect = vpnState.status == VpnStatus.CONNECTED ||
            vpnState.status == VpnStatus.CONNECTING
        Button(
            onClick = if (shouldDisconnect) disconnect else connect,
            enabled = !stoppingOrStarting,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
        ) {
            Icon(
                imageVector = if (shouldDisconnect) Icons.Default.Stop else Icons.Default.PowerSettingsNew,
                contentDescription = null,
            )
            Spacer(Modifier.size(10.dp))
            Text(
                when {
                    vpnState.status == VpnStatus.CONNECTING -> "Connecting..."
                    vpnState.status == VpnStatus.DISCONNECTING -> "Disconnecting..."
                    shouldDisconnect -> "Disconnect"
                    else -> "Connect"
                },
            )
        }
        NativeCoreNotice()
    }
}

@Composable
private fun StatusCard(
    vpnState: VpnConnectionState,
    selectedConfig: SavedConfig?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Hysera VPN", style = MaterialTheme.typography.titleLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (vpnState.status == VpnStatus.CONNECTED) {
                        Icons.Default.CheckCircle
                    } else {
                        Icons.Default.Info
                    },
                    contentDescription = null,
                )
                Text(vpnState.status.displayName, fontWeight = FontWeight.Medium)
            }
            Text(
                text = vpnState.configName ?: selectedConfig?.name ?: "No config selected",
                style = MaterialTheme.typography.bodyLarge,
            )
            vpnState.coreType?.let { Text("Core adapter: ${it.displayName}") }
            Text(vpnState.detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NativeCoreNotice() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Default.Info, contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Native core integration pending", fontWeight = FontWeight.Medium)
                Text(
                    "Hysera currently creates a safe TUN placeholder without routing device traffic. " +
                        "Pinned sing-box and Xray binaries must be integrated through GitHub Actions.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ConfigsScreen(
    configs: List<SavedConfig>,
    selectedConfigId: String?,
    selectConfig: (String) -> Unit,
    deleteConfig: (String) -> Unit,
    saveConfig: (String, String, CoreType) -> Boolean,
    contentPadding: PaddingValues,
) {
    var showForm by rememberSaveable { mutableStateOf(configs.isEmpty()) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Saved configs",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (configs.isEmpty()) {
            item {
                Text("No configs saved yet. Add raw JSON or a supported URI.")
            }
        }
        items(configs, key = SavedConfig::id) { config ->
            ConfigCard(
                config = config,
                selected = config.id == selectedConfigId,
                select = { selectConfig(config.id) },
                delete = { deleteConfig(config.id) },
            )
        }
        item {
            ExtendedFloatingActionButton(
                onClick = { showForm = !showForm },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(if (showForm) "Close editor" else "Add config") },
            )
        }
        if (showForm) {
            item {
                AddConfigForm(
                    saveConfig = { name, rawConfig, core ->
                        if (saveConfig(name, rawConfig, core)) {
                            showForm = false
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ConfigCard(
    config: SavedConfig,
    selected: Boolean,
    select: () -> Unit,
    delete: () -> Unit,
) {
    val detection = remember(config.rawConfig) { ConfigDetector.detect(config.rawConfig) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = select),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RadioButton(selected = selected, onClick = select)
            Column(modifier = Modifier.weight(1f)) {
                Text(config.name, fontWeight = FontWeight.Medium)
                Text(
                    "${detection.format.displayName} | ${config.preferredCore.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = delete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete ${config.name}")
            }
        }
    }
}

@Composable
private fun AddConfigForm(
    saveConfig: (String, String, CoreType) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var rawConfig by rememberSaveable { mutableStateOf("") }
    var preferredCore by rememberSaveable { mutableStateOf(CoreType.AUTO) }
    val clipboardManager = LocalClipboardManager.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add Hysera config", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Config name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = rawConfig,
                onValueChange = { rawConfig = it },
                label = { Text("Raw JSON or URI") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            AssistChip(
                onClick = {
                    clipboardManager.getText()?.text?.let { clipboardText ->
                        rawConfig = clipboardText
                    }
                },
                label = { Text("Import from clipboard") },
                leadingIcon = { Icon(Icons.Default.ContentPaste, contentDescription = null) },
            )
            Text("Core preference", style = MaterialTheme.typography.labelLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CoreType.entries.forEach { core ->
                    FilterChip(
                        selected = preferredCore == core,
                        onClick = { preferredCore = core },
                        label = { Text(core.displayName) },
                    )
                }
            }
            Button(
                onClick = { saveConfig(name, rawConfig, preferredCore) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save config")
            }
        }
    }
}

@Composable
private fun SubscriptionsScreen(
    profiles: List<SubscriptionProfile>,
    preview: SubscriptionParseResult?,
    busy: Boolean,
    checkSubscription: (String, String) -> Unit,
    importSubscription: (String, String) -> Unit,
    refreshSubscription: (String) -> Unit,
    deleteSubscription: (String) -> Unit,
    saveNode: (SubscriptionNode) -> Unit,
    contentPadding: PaddingValues,
) {
    var showForm by rememberSaveable { mutableStateOf(profiles.isEmpty()) }
    var expandedProfileId by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Hysera subscription profiles",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Import a subscription URL or raw subscription text. Metadata and VPN nodes stay local.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (profiles.isEmpty()) {
            item { Text("No subscriptions saved yet.") }
        }
        items(profiles, key = SubscriptionProfile::id) { profile ->
            SubscriptionProfileCard(
                profile = profile,
                expanded = expandedProfileId == profile.id,
                busy = busy,
                toggleNodes = {
                    expandedProfileId = if (expandedProfileId == profile.id) null else profile.id
                },
                refresh = { refreshSubscription(profile.id) },
                delete = { deleteSubscription(profile.id) },
                saveNode = saveNode,
            )
        }
        item {
            ExtendedFloatingActionButton(
                onClick = { showForm = !showForm },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(if (showForm) "Close subscription editor" else "Add subscription") },
            )
        }
        if (showForm) {
            item {
                SubscriptionImportForm(
                    busy = busy,
                    checkSubscription = checkSubscription,
                    importSubscription = importSubscription,
                )
            }
        }
        preview?.let { result ->
            item { SubscriptionPreviewCard(result) }
        }
    }
}

@Composable
private fun SubscriptionProfileCard(
    profile: SubscriptionProfile,
    expanded: Boolean,
    busy: Boolean,
    toggleNodes: () -> Unit,
    refresh: () -> Unit,
    delete: () -> Unit,
    saveNode: (SubscriptionNode) -> Unit,
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(profile.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${profile.nodes.size} node(s) | Refresh interval: ${profile.updateIntervalHours} hour(s)",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Last updated: ${formatOptionalDate(profile.lastUpdatedAtMillis)}",
                style = MaterialTheme.typography.bodySmall,
            )
            SubscriptionUsage(profile.metadata.userInfo)
            profile.metadata.announce?.takeIf(String::isNotBlank)?.let { announcement ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Text(
                        text = announcement,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            profile.lastUpdateError?.let { error ->
                Text(
                    text = "Last update warning: $error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            profile.sourceUrl?.let { sourceUrl ->
                Text(
                    text = sourceUrl,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = refresh,
                    enabled = !busy && profile.sourceUrl != null,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Update")
                }
                OutlinedButton(onClick = toggleNodes) {
                    Icon(Icons.Default.ExpandMore, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Nodes")
                }
                IconButton(onClick = delete, enabled = !busy) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete ${profile.name}")
                }
            }
            SubscriptionLinks(metadata = profile.metadata, context = context)
            if (expanded) {
                HorizontalDivider()
                profile.nodes.forEach { node ->
                    SubscriptionNodeRow(node = node, saveNode = { saveNode(node) })
                }
            }
        }
    }
}

@Composable
private fun SubscriptionLinks(metadata: SubscriptionMetadata, context: Context) {
    if (metadata.supportUrl == null && metadata.profileWebPageUrl == null) {
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        metadata.supportUrl?.let { url ->
            OutlinedButton(onClick = { openExternalUrl(context, url) }) {
                Icon(Icons.Default.OpenInNew, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Support")
            }
        }
        metadata.profileWebPageUrl?.let { url ->
            OutlinedButton(onClick = { openExternalUrl(context, url) }) {
                Icon(Icons.Default.OpenInNew, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Profile page")
            }
        }
    }
}

@Composable
private fun SubscriptionNodeRow(node: SubscriptionNode, saveNode: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(node.name, fontWeight = FontWeight.Medium)
        Text(
            "${node.format.displayName} | ${node.suggestedCore?.displayName ?: "Unsupported"}",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = saveNode) {
            Text("Save as config")
        }
        HorizontalDivider()
    }
}

@Composable
private fun SubscriptionImportForm(
    busy: Boolean,
    checkSubscription: (String, String) -> Unit,
    importSubscription: (String, String) -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("") }
    var rawText by rememberSaveable { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add Hysera subscription", style = MaterialTheme.typography.titleMedium)
            Text(
                "Use this mode for subscription links. For one VPN node, use Hysera Configs.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Subscription URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = rawText,
                onValueChange = { rawText = it },
                label = { Text("Raw subscription text") },
                minLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            AssistChip(
                onClick = {
                    clipboardManager.getText()?.text?.let { clipboardText ->
                        rawText = clipboardText
                    }
                },
                label = { Text("Paste raw text from clipboard") },
                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { checkSubscription(url, rawText) },
                    enabled = !busy,
                ) {
                    Text("Проверить подписку")
                }
                Button(
                    onClick = { importSubscription(url, rawText) },
                    enabled = !busy,
                ) {
                    Text("Импортировать")
                }
            }
            if (busy) {
                Text("Hysera is loading subscription data...", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SubscriptionPreviewCard(result: SubscriptionParseResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Subscription preview", style = MaterialTheme.typography.titleMedium)
            Text("Title: ${result.metadata.profileTitle ?: "Untitled subscription"}")
            Text("Valid nodes: ${result.validNodes.size}")
            Text("Warnings: ${result.errors.size}")
            result.metadata.announce?.let { Text("Announcement: $it") }
        }
    }
}

@Composable
private fun SubscriptionUsage(userInfo: SubscriptionUserInfo?) {
    if (userInfo == null) {
        Text("Traffic: unavailable", style = MaterialTheme.typography.bodySmall)
        return
    }

    val used = (userInfo.upload ?: 0L) + (userInfo.download ?: 0L)
    Text(
        "Upload: ${formatBytes(userInfo.upload)} | Download: ${formatBytes(userInfo.download)}",
        style = MaterialTheme.typography.bodySmall,
    )
    Text(
        "Used: ${formatBytes(used)} | Limit: ${formatTrafficLimit(userInfo.total)}",
        style = MaterialTheme.typography.bodySmall,
    )
    Text(
        "Expires: ${formatExpiration(userInfo.expire)}",
        style = MaterialTheme.typography.bodySmall,
    )
}

private fun formatBytes(value: Long?): String {
    if (value == null) {
        return "Unknown"
    }
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var amount = value.toDouble()
    var unitIndex = 0
    while (amount >= 1024 && unitIndex < units.lastIndex) {
        amount /= 1024
        unitIndex++
    }
    return if (unitIndex == 0) {
        "$value ${units[unitIndex]}"
    } else {
        "%.1f %s".format(amount, units[unitIndex])
    }
}

private fun formatTrafficLimit(total: Long?): String {
    return when {
        total == null -> "Unknown"
        total == 0L -> "Unlimited"
        else -> formatBytes(total)
    }
}

private fun formatExpiration(expireUnixSeconds: Long?): String {
    return if (expireUnixSeconds == null || expireUnixSeconds == 0L) {
        "No expiration"
    } else {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(expireUnixSeconds * 1000))
    }
}

private fun formatOptionalDate(timestampMillis: Long?): String {
    return timestampMillis?.let { timestamp ->
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
    } ?: "Never"
}

private fun openExternalUrl(context: Context, url: String) {
    runCatching {
        val uri = Uri.parse(url)
        require(uri.scheme == "https" || uri.scheme == "http") {
            "Only HTTPS or HTTP subscription links can be opened."
        }
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }.onFailure { exception ->
        EventLogger.error("Could not open subscription URL: ${exception.message}")
    }
}

@Composable
private fun LogsScreen(
    logs: List<LogEntry>,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (logs.isEmpty()) {
            item { Text("Hysera has not recorded any events yet.") }
        }
        items(logs.asReversed(), key = LogEntry::id) { entry ->
            LogRow(entry)
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val formattedTime = remember(entry.timestampMillis) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
            .format(Date(entry.timestampMillis))
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(entry.level.name, fontWeight = FontWeight.SemiBold)
            Text(formattedTime, style = MaterialTheme.typography.labelSmall)
        }
        Text(
            entry.message,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        HorizontalDivider()
    }
}

@Composable
private fun SettingsScreen(
    darkTheme: Boolean,
    setDarkTheme: (Boolean) -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Dark theme", fontWeight = FontWeight.Medium)
                    Text("Use Hysera's dark Material 3 palette.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = darkTheme, onCheckedChange = setDarkTheme)
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Core routing", fontWeight = FontWeight.Medium)
                Text(
                    "Auto selects sing-box first and uses Xray only for detected fallback transports. " +
                        "You can override the adapter per config.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Build policy", fontWeight = FontWeight.Medium)
                Text(
                    "Hysera APK files are built only by GitHub Actions. Local Android builds are forbidden.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
