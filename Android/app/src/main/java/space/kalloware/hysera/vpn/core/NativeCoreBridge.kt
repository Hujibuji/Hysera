package space.kalloware.hysera.vpn.core

import space.kalloware.hysera.config.CoreType
import space.kalloware.hysera.config.SavedConfig
import space.kalloware.hysera.logging.EventLogger

class NativeCoreBridge {
    fun start(coreType: CoreType, config: SavedConfig, tunFileDescriptor: Int): CoreStartResult {
        require(tunFileDescriptor >= 0) { "TUN file descriptor must be valid." }

        // TODO: Start the pinned native sing-box or Xray library and pass it the TUN descriptor.
        val detail = "${coreType.displayName} adapter selected for '${config.name}'. " +
            "Native core is not bundled; no device traffic is routed."
        EventLogger.warning(detail)
        return CoreStartResult(
            nativeCoreStarted = false,
            detail = detail,
        )
    }

    fun stop(coreType: CoreType) {
        // TODO: Stop the native core process or library after it is integrated.
        EventLogger.info("${coreType.displayName} adapter stopped.")
    }
}
