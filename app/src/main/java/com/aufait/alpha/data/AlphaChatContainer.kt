package com.aufait.alpha.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AlphaChatContainer(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val identityRepository = IdentityRepository(appContext)
    private val localCipher = LocalCipher()
    val messageRepository = EncryptedMessageRepository(appContext, localCipher)
    private val transport = LoopbackMeshTransport(scope)
    val chatService = ChatService(
        identityRepository = identityRepository,
        messageRepository = messageRepository,
        transport = transport
    )
}
