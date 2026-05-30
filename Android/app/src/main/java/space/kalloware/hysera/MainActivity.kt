package space.kalloware.hysera

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import space.kalloware.hysera.config.SavedConfig
import space.kalloware.hysera.ui.HyseraApp
import space.kalloware.hysera.ui.HyseraViewModel

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<HyseraViewModel>()
    private var pendingVpnConfig: SavedConfig? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val config = pendingVpnConfig
        pendingVpnConfig = null
        if (result.resultCode == Activity.RESULT_OK && config != null) {
            viewModel.startVpn(config)
        } else {
            viewModel.onVpnPermissionDenied()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // A denied notification permission does not block the foreground VPN service.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionWhenNeeded()
        setContent {
            HyseraApp(
                viewModel = viewModel,
                requestVpnPermission = ::requestVpnPermission,
            )
        }
    }

    private fun requestVpnPermission(config: SavedConfig) {
        val permissionIntent = VpnService.prepare(this)
        if (permissionIntent == null) {
            viewModel.startVpn(config)
        } else {
            pendingVpnConfig = config
            vpnPermissionLauncher.launch(permissionIntent)
        }
    }

    private fun requestNotificationPermissionWhenNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
