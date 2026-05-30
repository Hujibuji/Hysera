package space.kalloware.hysera.vpn

import space.kalloware.hysera.config.CoreType

enum class VpnStatus(val displayName: String) {
    DISCONNECTED("Disconnected"),
    CONNECTING("Connecting"),
    CONNECTED("Connected"),
    DISCONNECTING("Disconnecting"),
    ERROR("Error"),
}

data class VpnConnectionState(
    val status: VpnStatus = VpnStatus.DISCONNECTED,
    val configName: String? = null,
    val coreType: CoreType? = null,
    val detail: String = "Choose a config to start the Hysera TUN placeholder.",
)
