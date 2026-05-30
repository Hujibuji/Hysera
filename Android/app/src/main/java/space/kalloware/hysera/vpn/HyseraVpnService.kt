package space.kalloware.hysera.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import space.kalloware.hysera.MainActivity
import space.kalloware.hysera.R
import space.kalloware.hysera.config.ConfigDetector
import space.kalloware.hysera.config.CoreType
import space.kalloware.hysera.config.SavedConfig
import space.kalloware.hysera.logging.EventLogger
import space.kalloware.hysera.vpn.core.CoreEngine
import space.kalloware.hysera.vpn.core.CoreEngineFactory

class HyseraVpnService : VpnService() {
    private var tunInterface: ParcelFileDescriptor? = null
    private var coreEngine: CoreEngine? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> connect(intent?.toSavedConfig())
            ACTION_DISCONNECT -> disconnect()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseResources()
        if (VpnStateStore.state.value.status != VpnStatus.ERROR) {
            VpnStateStore.publish(VpnConnectionState())
        }
        super.onDestroy()
    }

    private fun connect(config: SavedConfig?) {
        releaseResources()
        VpnStateStore.publish(
            VpnConnectionState(
                status = VpnStatus.CONNECTING,
                detail = "Preparing the Hysera TUN placeholder.",
            ),
        )
        EventLogger.info("Hysera VPN connection requested.")

        createNotificationChannel()
        startHyseraForeground()

        try {
            val selectedConfig = requireNotNull(config) {
                "Select a config or subscription node before connecting."
            }
            val detection = ConfigDetector.detect(selectedConfig.rawConfig)
            require(detection.isSupported) { detection.explanation }

            val selectedCore = ConfigDetector.resolveCore(selectedConfig)
            val establishedTun = Builder()
                .setSession("Hysera")
                .setMtu(1500)
                .addAddress("10.7.0.2", 32)
                .establish()
                ?: error("Android did not create a TUN interface.")

            tunInterface = establishedTun
            coreEngine = CoreEngineFactory.create(selectedCore)
            val launch = coreEngine!!.start(selectedConfig, establishedTun.fd)

            VpnStateStore.publish(
                VpnConnectionState(
                    status = VpnStatus.CONNECTED,
                    configName = selectedConfig.name,
                    coreType = selectedCore,
                    detail = launch.detail,
                ),
            )
            EventLogger.info("Hysera TUN placeholder is active for '${selectedConfig.name}'.")
        } catch (exception: Exception) {
            fail(exception.message ?: "Could not start Hysera VPN.")
        }
    }

    private fun disconnect() {
        VpnStateStore.publish(
            VpnStateStore.state.value.copy(
                status = VpnStatus.DISCONNECTING,
                detail = "Stopping Hysera VPN.",
            ),
        )
        EventLogger.info("Hysera VPN disconnect requested.")
        releaseResources()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        VpnStateStore.publish(VpnConnectionState())
    }

    private fun fail(message: String) {
        EventLogger.error("Hysera VPN error: $message")
        releaseResources()
        stopForeground(STOP_FOREGROUND_REMOVE)
        VpnStateStore.publish(
            VpnConnectionState(
                status = VpnStatus.ERROR,
                detail = message,
            ),
        )
        stopSelf()
    }

    private fun releaseResources() {
        coreEngine?.stop()
        coreEngine = null
        tunInterface?.close()
        tunInterface = null
    }

    private fun startHyseraForeground() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val ACTION_CONNECT = "space.kalloware.hysera.action.CONNECT"
        private const val ACTION_DISCONNECT = "space.kalloware.hysera.action.DISCONNECT"
        private const val EXTRA_CONFIG_ID = "config_id"
        private const val EXTRA_CONFIG_NAME = "config_name"
        private const val EXTRA_RAW_CONFIG = "raw_config"
        private const val EXTRA_PREFERRED_CORE = "preferred_core"
        private const val NOTIFICATION_CHANNEL_ID = "hysera_vpn"
        private const val NOTIFICATION_ID = 1001

        fun connectIntent(context: Context, config: SavedConfig) =
            Intent(context, HyseraVpnService::class.java)
                .setAction(ACTION_CONNECT)
                .putExtra(EXTRA_CONFIG_ID, config.id)
                .putExtra(EXTRA_CONFIG_NAME, config.name)
                .putExtra(EXTRA_RAW_CONFIG, config.rawConfig)
                .putExtra(EXTRA_PREFERRED_CORE, config.preferredCore.name)

        fun disconnectIntent(context: Context) =
            Intent(context, HyseraVpnService::class.java)
                .setAction(ACTION_DISCONNECT)
    }

    private fun Intent.toSavedConfig(): SavedConfig? {
        val id = getStringExtra(EXTRA_CONFIG_ID) ?: return null
        val name = getStringExtra(EXTRA_CONFIG_NAME) ?: return null
        val rawConfig = getStringExtra(EXTRA_RAW_CONFIG) ?: return null
        val preferredCore = getStringExtra(EXTRA_PREFERRED_CORE)
            ?.let { value -> runCatching { CoreType.valueOf(value) }.getOrNull() }
            ?: CoreType.AUTO
        return SavedConfig(
            id = id,
            name = name,
            rawConfig = rawConfig,
            preferredCore = preferredCore,
            createdAtMillis = 0L,
        )
    }
}
