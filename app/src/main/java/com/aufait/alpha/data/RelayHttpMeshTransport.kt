package com.aufait.alpha.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.net.URLEncoder
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class RelayHttpMeshTransport(
    private val enabled: Boolean = false,
    private val relayUrl: String? = null,
    private val torRuntime: TorRuntime
) : MeshTransport, RelayTransportControl {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _inboundMessages = MutableSharedFlow<InboundTransportMessage>(extraBufferCapacity = 8)
    private val _inboundReceipts = MutableSharedFlow<InboundReceipt>(extraBufferCapacity = 8)
    private val _peers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    private val _diagnostics = MutableStateFlow(RelayDiagnostics())

    override val inboundMessages: SharedFlow<InboundTransportMessage> = _inboundMessages
    override val inboundReceipts: SharedFlow<InboundReceipt> = _inboundReceipts
    override val peers: StateFlow<List<DiscoveredPeer>> = _peers
    override val diagnostics: StateFlow<RelayDiagnostics> = _diagnostics.asStateFlow()

    @Volatile private var started = false
    @Volatile private var localAliasValue: String = "android-alpha"
    @Volatile private var localNodeIdValue: String = ""
    @Volatile private var relayNetworkMode: RelayNetworkMode = RelayNetworkMode.DIRECT
    @Volatile private var torFallbackPolicy: TorFallbackPolicy = TorFallbackPolicy.TOR_PREFERRED
    private val seenEventIds = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    override suspend fun start(localAlias: String, localNodeId: String) {
        this.localAliasValue = localAlias
        this.localNodeIdValue = localNodeId
        if (started || !isConfigured()) {
            refreshDiagnostics()
            return
        }
        started = true
        scope.launch {
            torRuntime.status.collect {
                refreshDiagnostics()
            }
        }
        scope.launch { pollLoop() }
        refreshDiagnostics()
    }

    override suspend fun updateLocalAlias(localAlias: String) {
        this.localAliasValue = localAlias.trim().ifBlank { this.localAliasValue }
    }

    override suspend fun setRelayNetworkMode(mode: RelayNetworkMode) {
        relayNetworkMode = mode
        if (mode == RelayNetworkMode.TOR) {
            torRuntime.start()
        } else {
            torRuntime.stop()
        }
        refreshDiagnostics()
    }

    override suspend fun setTorFallbackPolicy(policy: TorFallbackPolicy) {
        torFallbackPolicy = policy
        refreshDiagnostics()
    }

    override suspend fun sendMessage(toPeer: String, messageId: String, body: String) {
        if (!isConfigured()) return
        postEvent(
            JSONObject()
                .put("type", "msg")
                .put("toRef", toPeer)
                .put("messageId", messageId)
                .put("fromNodeId", localNodeIdValue)
                .put("fromAlias", localAliasValue)
                .put("body", body)
        )
    }

    override suspend fun sendReceipt(toPeer: String, messageId: String, kind: ReceiptKind) {
        if (!isConfigured()) return
        postEvent(
            JSONObject()
                .put("type", "receipt")
                .put("toRef", toPeer)
                .put("messageId", messageId)
                .put("fromNodeId", localNodeIdValue)
                .put("fromAlias", localAliasValue)
                .put("receiptKind", kind.name.lowercase())
        )
    }

    private suspend fun pollLoop() {
        while (true) {
            runCatching { pullOnce() }
                .onFailure { setLastError(it) }
            delay(currentPollIntervalMs())
        }
    }

    private fun currentPollIntervalMs(): Long {
        return if (relayNetworkMode == RelayNetworkMode.TOR) TOR_POLL_INTERVAL_MS else DIRECT_POLL_INTERVAL_MS
    }

    private fun pullOnce() {
        val base = relayUrl?.trim()?.trimEnd('/') ?: return
        val encodedId = URLEncoder.encode(localNodeIdValue, "UTF-8")
        val url = URL("$base/v1/pull?nodeId=$encodedId")
        val conn = openHttp(url).apply {
            requestMethod = "GET"
            connectTimeout = currentConnectTimeoutMs()
            readTimeout = currentReadTimeoutMs()
            setRequestProperty("Accept", "application/json")
        }
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream ?: return
        stream.use { input ->
            val text = input.bufferedReader().use { reader -> reader.readText() }
            val root = JSONObject(text)
            val events = root.optJSONArray("events") ?: JSONArray()
            for (i in 0 until events.length()) {
                handleRelayEvent(events.optJSONObject(i) ?: continue)
            }
        }
        conn.disconnect()
    }

    private fun handleRelayEvent(event: JSONObject) {
        val eventId = event.optString("eventId")
        if (eventId.isNotBlank() && !seenEventIds.add(eventId)) return
        val type = event.optString("type")
        val messageId = event.optString("messageId")
        val fromNodeId = event.optString("fromNodeId")
        val fromAlias = event.optString("fromAlias").ifBlank { "relay-${fromNodeId.take(6)}" }
        val timestampMs = event.optLong("timestampMs", System.currentTimeMillis())

        when (type) {
            "msg" -> {
                val body = event.optString("body")
                if (messageId.isBlank() || body.isBlank()) return
                scope.launch {
                    _inboundMessages.emit(
                        InboundTransportMessage(
                            messageId = messageId,
                            fromPeer = fromAlias,
                            fromNodeId = fromNodeId,
                            body = body,
                            receivedAtMs = timestampMs
                        )
                    )
                }
            }
            "receipt" -> {
                val kind = when (event.optString("receiptKind").lowercase()) {
                    "delivered" -> ReceiptKind.DELIVERED
                    "read" -> ReceiptKind.READ
                    else -> null
                } ?: return
                if (messageId.isBlank()) return
                scope.launch {
                    _inboundReceipts.emit(
                        InboundReceipt(
                            messageId = messageId,
                            fromPeer = fromAlias,
                            fromNodeId = fromNodeId,
                            kind = kind,
                            receivedAtMs = timestampMs
                        )
                    )
                }
            }
        }
    }

    private fun postEvent(payload: JSONObject) {
        val base = relayUrl?.trim()?.trimEnd('/') ?: return
        retryDelaysMs().forEachIndexed { index, delayMs ->
            if (index > 0) Thread.sleep(delayMs)
            val sent = runCatching {
                val conn = openHttp(URL("$base/v1/push")).apply {
                    requestMethod = "POST"
                    connectTimeout = currentConnectTimeoutMs()
                    readTimeout = currentReadTimeoutMs()
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                conn.outputStream.use { os ->
                    OutputStreamWriter(os, Charsets.UTF_8).use { it.write(payload.toString()) }
                }
                (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)?.close()
                conn.disconnect()
                true
            }.onFailure { setLastError(it) }.getOrDefault(false)
            if (sent) return
        }
    }

    private fun retryDelaysMs(): LongArray {
        return if (relayNetworkMode == RelayNetworkMode.TOR) longArrayOf(0L, 800L, 2_000L) else longArrayOf(0L, 300L, 900L)
    }

    private fun currentConnectTimeoutMs(): Int {
        return if (relayNetworkMode == RelayNetworkMode.TOR) 10_000 else 3_000
    }

    private fun currentReadTimeoutMs(): Int {
        return if (relayNetworkMode == RelayNetworkMode.TOR) 20_000 else 5_000
    }

    private fun openHttp(url: URL): HttpURLConnection {
        val proxy = resolveProxyOrNull()
        refreshDiagnostics(proxy != null)
        return if (proxy != null) {
            (url.openConnection(proxy) as HttpURLConnection)
        } else {
            (url.openConnection() as HttpURLConnection)
        }
    }

    private fun resolveProxyOrNull(): Proxy? {
        if (relayNetworkMode != RelayNetworkMode.TOR) return null
        val tor = torRuntime.status.value
        val host = tor.socksHost
        val port = tor.socksPort
        val torReady = tor.state == TorRuntimeState.READY && !host.isNullOrBlank() && port != null && port > 0
        if (torReady) {
            return Proxy(Proxy.Type.SOCKS, InetSocketAddress(host, port))
        }
        return when (torFallbackPolicy) {
            TorFallbackPolicy.TOR_PREFERRED -> null
            TorFallbackPolicy.TOR_STRICT -> throw IllegalStateException("Tor non prêt (mode strict)")
        }
    }

    private fun setLastError(error: Throwable) {
        _diagnostics.value = _diagnostics.value.copy(
            lastError = error.message ?: error::class.java.simpleName
        )
    }

    private fun refreshDiagnostics(torUsingProxy: Boolean = _diagnostics.value.torUsingProxy) {
        val tor = torRuntime.status.value
        _diagnostics.value = RelayDiagnostics(
            enabled = enabled,
            relayConfigured = !relayUrl.isNullOrBlank(),
            relayNetworkMode = relayNetworkMode,
            torFallbackPolicy = torFallbackPolicy,
            torRuntimeState = tor.state,
            torBootstrapPercent = tor.bootstrapPercent,
            torReady = tor.state == TorRuntimeState.READY,
            torUsingProxy = torUsingProxy && relayNetworkMode == RelayNetworkMode.TOR,
            lastError = _diagnostics.value.lastError ?: tor.lastError
        )
    }

    private fun isConfigured(): Boolean = enabled && !relayUrl.isNullOrBlank()

    companion object {
        private const val DIRECT_POLL_INTERVAL_MS = 2_000L
        private const val TOR_POLL_INTERVAL_MS = 4_000L
    }
}
