package com.aufait.alpha.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class RelayHttpMeshTransport(
    private val enabled: Boolean = false,
    private val relayUrl: String? = null
) : MeshTransport {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _inboundMessages = MutableSharedFlow<InboundTransportMessage>(extraBufferCapacity = 8)
    private val _inboundReceipts = MutableSharedFlow<InboundReceipt>(extraBufferCapacity = 8)
    private val _peers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())

    override val inboundMessages: SharedFlow<InboundTransportMessage> = _inboundMessages
    override val inboundReceipts: SharedFlow<InboundReceipt> = _inboundReceipts
    override val peers: StateFlow<List<DiscoveredPeer>> = _peers

    @Volatile private var started = false
    @Volatile private var localAliasValue: String = "android-alpha"
    @Volatile private var localNodeIdValue: String = ""
    private val seenEventIds = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    override suspend fun start(localAlias: String, localNodeId: String) {
        this.localAliasValue = localAlias
        this.localNodeIdValue = localNodeId
        if (started || !isConfigured()) return
        started = true
        scope.launch { pollLoop() }
    }

    override suspend fun updateLocalAlias(localAlias: String) {
        this.localAliasValue = localAlias.trim().ifBlank { this.localAliasValue }
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
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun pullOnce() {
        val base = relayUrl?.trim()?.trimEnd('/') ?: return
        val encodedId = URLEncoder.encode(localNodeIdValue, "UTF-8")
        val url = URL("$base/v1/pull?nodeId=$encodedId")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3_000
            readTimeout = 5_000
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
        val conn = (URL("$base/v1/push").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 3_000
            readTimeout = 5_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        runCatching {
            conn.outputStream.use { os ->
                OutputStreamWriter(os, Charsets.UTF_8).use { it.write(payload.toString()) }
            }
            (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)?.close()
        }
        conn.disconnect()
    }

    private fun isConfigured(): Boolean = enabled && !relayUrl.isNullOrBlank()

    companion object {
        private const val POLL_INTERVAL_MS = 2_000L
    }
}
