package space.kalloware.hysera.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object VpnStateStore {
    private val mutableState = MutableStateFlow(VpnConnectionState())

    val state = mutableState.asStateFlow()

    fun publish(connectionState: VpnConnectionState) {
        mutableState.value = connectionState
    }
}
