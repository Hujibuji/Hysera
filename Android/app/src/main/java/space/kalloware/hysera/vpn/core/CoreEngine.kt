package space.kalloware.hysera.vpn.core

import space.kalloware.hysera.config.CoreType
import space.kalloware.hysera.config.SavedConfig

data class CoreStartResult(
    val nativeCoreStarted: Boolean,
    val detail: String,
)

interface CoreEngine {
    val type: CoreType

    fun start(config: SavedConfig, tunFileDescriptor: Int): CoreStartResult

    fun stop()
}
