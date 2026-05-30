package space.kalloware.hysera.ui

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
import androidx.compose.foundation.layout.weight
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
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
import space.kalloware.hysera.logging.LogEntry
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
    requestVpnPermission: (String) -> Unit,
) {
    val configs by viewModel.configs.collectAsStateWithLifecycle()
    val selectedConfigId by viewModel.selectedConfigId.collectAsStateWithLifecycle()
    val vpnState by viewModel.vpnState.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val darkTheme by viewModel.darkTheme.collectAsStateWithLifecycle()
    val uiMessage by viewModel.uiMessage.collectAsStateWithLifecycle()
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
