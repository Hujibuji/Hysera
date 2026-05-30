package space.kalloware.hysera.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TextButton
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
    LOGS("Hysera Logs", Icons.Default.Article),
    SETTINGS("Hysera Settings", Icons.Default.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HyseraApp(
    viewModel: HyseraViewModel,
    requestVpnPermission: (SavedConfig) -> Unit,
) {
    val configs by viewModel.configs.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val selectedTarget by viewModel.selectedTarget.collectAsStateWithLifecycle()
    val vpnState by viewModel.vpnState.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val darkTheme by viewModel.darkTheme.collectAsStateWithLifecycle()
    val uiMessage by viewModel.uiMessage.collectAsStateWithLifecycle()
    val subscriptionBusy by viewModel.subscriptionBusy.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    var destination by rememberSaveable { mutableStateOf(HyseraDestination.HOME) }
    var showImportMenu by remember { mutableStateOf(false) }
    var showManualImport by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiMessage) {
        uiMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeUiMessage()
        }
    }

    HyseraTheme(darkTheme = darkTheme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(destination.title) },
                    navigationIcon = {
                        if (
                            destination == HyseraDestination.HOME ||
                            destination == HyseraDestination.CONFIGS
                        ) {
                            Box {
                                IconButton(onClick = { showImportMenu = true }) {
                                    Icon(Icons.Default.Add, contentDescription = "Add config or subscription")
                                }
                                DropdownMenu(
                                    expanded = showImportMenu,
                                    onDismissRequest = { showImportMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Import from clipboard") },
                                        leadingIcon = {
                                            Icon(Icons.Default.ContentPaste, contentDescription = null)
                                        },
                                        onClick = {
                                            showImportMenu = false
                                            viewModel.importEntry(
                                                name = "",
                                                rawInput = clipboardManager.getText()?.text.orEmpty(),
                                                preferredCore = CoreType.AUTO,
                                            )
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Enter manually") },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                        onClick = {
                                            showImportMenu = false
                                            showManualImport = true
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
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
                    selectedConfig = selectedTarget,
                    connect = { viewModel.connectSelected(requestVpnPermission) },
                    disconnect = viewModel::disconnectVpn,
                    contentPadding = contentPadding,
                )

                HyseraDestination.CONFIGS -> ConfigsScreen(
                    configs = configs,
                    profiles = subscriptions,
                    busy = subscriptionBusy,
                    selectedTargetId = selectedTarget?.id,
                    selectConfig = viewModel::selectConfig,
                    deleteConfig = viewModel::deleteConfig,
                    refreshSubscription = viewModel::refreshSubscription,
                    deleteSubscription = viewModel::deleteSubscription,
                    selectNode = viewModel::selectSubscriptionNode,
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
        if (showManualImport) {
            ManualImportDialog(
                busy = subscriptionBusy,
                dismiss = { showManualImport = false },
                importEntry = { name, rawInput, preferredCore ->
                    showManualImport = false
                    viewModel.importEntry(name, rawInput, preferredCore)
                },
            )
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
    profiles: List<SubscriptionProfile>,
    busy: Boolean,
    selectedTargetId: String?,
    selectConfig: (String) -> Unit,
    deleteConfig: (String) -> Unit,
    refreshSubscription: (String) -> Unit,
    deleteSubscription: (String) -> Unit,
    selectNode: (String, SubscriptionNode) -> Unit,
    contentPadding: PaddingValues,
) {
    var expandedProfileIds by rememberSaveable { mutableStateOf(emptyList<String>()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Tap + above to import from the clipboard or enter a config manually.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            Text("Saved VPN configs", style = MaterialTheme.typography.titleMedium)
        }
        if (configs.isEmpty()) {
            item {
                Text("No standalone configs saved yet.")
            }
        }
        items(configs, key = SavedConfig::id) { config ->
            ConfigCard(
                config = config,
                selected = config.id == selectedTargetId,
                select = { selectConfig(config.id) },
                delete = { deleteConfig(config.id) },
            )
        }
        item {
            Text("Saved subscription links", style = MaterialTheme.typography.titleMedium)
        }
        if (profiles.isEmpty()) {
            item { Text("No subscriptions saved yet.") }
        }
        items(profiles, key = SubscriptionProfile::id) { profile ->
            SubscriptionProfileCard(
                profile = profile,
                expanded = profile.id in expandedProfileIds,
                busy = busy,
                selectedTargetId = selectedTargetId,
                toggleNodes = {
                    expandedProfileIds = if (profile.id in expandedProfileIds) {
                        expandedProfileIds - profile.id
                    } else {
                        expandedProfileIds + profile.id
                    }
                },
                refresh = { refreshSubscription(profile.id) },
                delete = { deleteSubscription(profile.id) },
                selectNode = { node -> selectNode(profile.id, node) },
            )
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
                Icon(Icons.Default.Delete, contentDescription = "Delete config")
            }
        }
    }
}

@Composable
private fun ManualImportDialog(
    busy: Boolean,
    dismiss: () -> Unit,
    importEntry: (String, String, CoreType) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var rawInput by rememberSaveable { mutableStateOf("") }
    var preferredCore by rememberSaveable { mutableStateOf(CoreType.AUTO) }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Add to Hysera") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Enter JSON, a protocol:// config, an HTTPS subscription URL, or raw subscription text.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Config name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = rawInput,
                    onValueChange = { rawInput = it },
                    label = { Text("Config, URL, or raw text") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Standalone config core", style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CoreType.entries.forEach { core ->
                        FilterChip(
                            selected = preferredCore == core,
                            onClick = { preferredCore = core },
                            label = { Text(core.displayName) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (busy) {
                    Text(
                        "Hysera is loading subscription data...",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { importEntry(name, rawInput, preferredCore) },
                enabled = !busy && rawInput.isNotBlank(),
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = dismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun SubscriptionProfileCard(
    profile: SubscriptionProfile,
    expanded: Boolean,
    busy: Boolean,
    selectedTargetId: String?,
    toggleNodes: () -> Unit,
    refresh: () -> Unit,
    delete: () -> Unit,
    selectNode: (SubscriptionNode) -> Unit,
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = toggleNodes),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse subscription" else "Expand subscription",
                )
            }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = refresh,
                    enabled = !busy && profile.sourceUrl != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Update")
                }
                OutlinedButton(
                    onClick = delete,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Delete")
                }
            }
            SubscriptionLinks(metadata = profile.metadata, context = context)
            if (expanded) {
                HorizontalDivider()
                profile.nodes.forEach { node ->
                    SubscriptionNodeRow(
                        node = node,
                        selected = selectedTargetId == subscriptionNodeId(profile.id, node.id),
                        select = { selectNode(node) },
                    )
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        metadata.supportUrl?.let { url ->
            OutlinedButton(
                onClick = { openExternalUrl(context, url) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Support")
            }
        }
        metadata.profileWebPageUrl?.let { url ->
            OutlinedButton(
                onClick = { openExternalUrl(context, url) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Profile page")
            }
        }
    }
}

@Composable
private fun SubscriptionNodeRow(
    node: SubscriptionNode,
    selected: Boolean,
    select: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = node.isValid, onClick = select)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = select.takeIf { node.isValid },
            enabled = node.isValid,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(node.name, fontWeight = FontWeight.Medium)
            Text(
                "${node.format.displayName} | ${node.suggestedCore?.displayName ?: "Unsupported"}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    HorizontalDivider()
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

private fun subscriptionNodeId(profileId: String, nodeId: String) = "subscription:$profileId:$nodeId"

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
