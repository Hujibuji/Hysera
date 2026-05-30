package space.kalloware.hysera.vpn.core

import space.kalloware.hysera.config.CoreType
import space.kalloware.hysera.config.SavedConfig

class SingBoxEngine(
    private val nativeCoreBridge: NativeCoreBridge = NativeCoreBridge(),
) : CoreEngine {
    override val type = CoreType.SING_BOX

    override fun start(config: SavedConfig, tunFileDescriptor: Int): CoreStartResult =
        nativeCoreBridge.start(type, config, tunFileDescriptor)

    override fun stop() = nativeCoreBridge.stop(type)
}
