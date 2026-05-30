package space.kalloware.hysera.vpn.core

import space.kalloware.hysera.config.CoreType

object CoreEngineFactory {
    fun create(coreType: CoreType): CoreEngine = when (coreType) {
        CoreType.SING_BOX -> SingBoxEngine()
        CoreType.XRAY -> XrayEngine()
        CoreType.AUTO -> error("AUTO must be resolved before creating a core engine.")
    }
}
