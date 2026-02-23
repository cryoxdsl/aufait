package com.aufait.alpha.data

data class UserIdentity(
    val id: String,
    val alias: String,
    val publicKeyBase64: String,
    val fingerprint: String
)

enum class MessageDirection {
    OUTBOUND,
    INBOUND
}

enum class ReceiptKind {
    DELIVERED,
    READ
}

enum class MessageTransportChannel {
    LOCAL,
    WIFI,
    BLUETOOTH,
    RELAY,
    TOR
}

data class ChatMessage(
    val id: String,
    val direction: MessageDirection,
    val author: String,
    val body: String,
    val timestampMs: Long,
    val transportChannel: MessageTransportChannel? = null,
    val deliveredAtMs: Long? = null,
    val deliveredChannel: MessageTransportChannel? = null,
    val readAtMs: Long? = null,
    val readChannel: MessageTransportChannel? = null
)

data class ContactRecord(
    val userId: String,
    val alias: String,
    val publicKeyBase64: String,
    val fingerprint: String,
    val createdAtMs: Long
)

data class StoredMessageEnvelope(
    val id: String,
    val direction: MessageDirection,
    val author: String,
    val timestampMs: Long,
    val ivBase64: String,
    val cipherBase64: String,
    val transportChannel: MessageTransportChannel? = null,
    val deliveredAtMs: Long? = null,
    val deliveredChannel: MessageTransportChannel? = null,
    val readAtMs: Long? = null,
    val readChannel: MessageTransportChannel? = null
)

enum class TransportRoutingMode {
    AUTO,
    LAN_ONLY,
    BLUETOOTH_ONLY
}

data class TransportDiagnostics(
    val routingMode: TransportRoutingMode = TransportRoutingMode.AUTO,
    val lanPeerCount: Int = 0,
    val bluetoothPeerCount: Int = 0,
    val bluetoothEnabled: Boolean = false,
    val bluetoothPermissionGranted: Boolean = false,
    val bluetoothDiscoveryActive: Boolean = false,
    val bluetoothServerListening: Boolean = false,
    val bluetoothLastError: String? = null
)

enum class RelayNetworkMode {
    DIRECT,
    TOR
}

enum class TorFallbackPolicy {
    TOR_PREFERRED,
    TOR_STRICT
}

enum class TorRuntimeState {
    DISABLED,
    STARTING,
    READY,
    UNAVAILABLE,
    ERROR
}

data class TorRuntimeStatus(
    val state: TorRuntimeState = TorRuntimeState.DISABLED,
    val bootstrapPercent: Int = 0,
    val socksHost: String? = null,
    val socksPort: Int? = null,
    val lastError: String? = null
)

data class RelayDiagnostics(
    val enabled: Boolean = false,
    val relayConfigured: Boolean = false,
    val relayNetworkMode: RelayNetworkMode = RelayNetworkMode.DIRECT,
    val torFallbackPolicy: TorFallbackPolicy = TorFallbackPolicy.TOR_PREFERRED,
    val torRuntimeState: TorRuntimeState = TorRuntimeState.DISABLED,
    val torBootstrapPercent: Int = 0,
    val torReady: Boolean = false,
    val torUsingProxy: Boolean = false,
    val lastError: String? = null
)
