package com.aufait.alpha.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AlphaChatContainer(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val identityRepository = IdentityRepository(appContext)
    val contactRepository = ContactRepository(appContext)
    val x3dhPreKeyRepository = X3dhPreKeyRepository(appContext)
    private val localCipher = LocalCipher()
    val messageRepository = EncryptedMessageRepository(appContext, localCipher)
    private val bluetoothTransport = BluetoothMeshTransport(appContext, scope)
    private val lanTransport = LanUdpMeshTransport(scope, fallback = bluetoothTransport)
    private val relayTransport = RelayHttpMeshTransport(
        enabled = false, // passer a true pour tester le mini-relai HTTP
        relayUrl = "http://10.0.2.2:8787" // emu Android -> machine hote; sur telephone utiliser IP LAN du PC
    )
    private val transport = HybridMeshTransport(scope, lan = lanTransport, relay = relayTransport)
    val transportControl: TransportControl = lanTransport
    private val e2eCipherEngine = PlaintextEnvelopeCipher()
    private val incomingMessageSoundPlayer = IncomingMessageSoundPlayer(appContext)
    private val backgroundMessageNotifier = BackgroundMessageNotifier(appContext)
    val chatService = ChatService(
        identityRepository = identityRepository,
        messageRepository = messageRepository,
        transport = transport,
        e2eCipherEngine = e2eCipherEngine,
        incomingMessageSoundPlayer = incomingMessageSoundPlayer,
        backgroundMessageNotifier = backgroundMessageNotifier
    )
}
