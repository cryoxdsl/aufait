package com.aufait.alpha.ui

import com.aufait.alpha.AttachmentDraft
import com.aufait.alpha.ChatUiState
import com.aufait.alpha.data.ChatMessage
import com.aufait.alpha.data.ContactRecord
import com.aufait.alpha.data.DiscoveredPeer
import com.aufait.alpha.data.MessageDirection

internal object AlphaPreviewData {
    private const val now = 1_710_000_000_000L

    fun messages(): List<ChatMessage> = listOf(
        ChatMessage(
            id = "m1",
            direction = MessageDirection.INBOUND,
            author = "Alice Martin",
            body = "Salut, tu es dispo pour tester la nouvelle UI ?",
            timestampMs = now - 120_000
        ),
        ChatMessage(
            id = "m2",
            direction = MessageDirection.OUTBOUND,
            author = "Moi",
            body = "Oui, je regarde la version avec la bottom sheet.",
            timestampMs = now - 60_000,
            deliveredAtMs = now - 50_000,
            readAtMs = now - 30_000
        )
    )

    fun contacts(): List<ContactRecord> = listOf(
        ContactRecord(
            userId = "user-alice-1234567890",
            alias = "Alice Martin",
            publicKeyBase64 = "pk_alice",
            fingerprint = "AB:CD:EF:01",
            createdAtMs = now - 86_400_000
        ),
        ContactRecord(
            userId = "user-bob-1234567890",
            alias = "Bob Team",
            publicKeyBase64 = "pk_bob",
            fingerprint = "12:34:56:78",
            createdAtMs = now - 40_000_000
        )
    )

    fun peers(): List<DiscoveredPeer> = listOf(
        DiscoveredPeer(
            alias = "Pixel-Local",
            nodeId = "node-1",
            endpoint = "192.168.1.12:4545",
            lastSeenMs = now - 3_000
        ),
        DiscoveredPeer(
            alias = "Tablet-Test",
            nodeId = "node-2",
            endpoint = "192.168.1.34:4545",
            lastSeenMs = now - 8_000
        )
    )

    fun attachmentDraft(): AttachmentDraft = AttachmentDraft(
        uriString = "content://preview/file",
        displayName = "maquette-ui.pdf",
        mimeType = "application/pdf",
        sizeBytes = 152_320
    )

    fun chatState(
        withMessages: Boolean = true,
        withAttachment: Boolean = true
    ): ChatUiState = ChatUiState(
        myIdShort = "local-123456",
        myAlias = "Paul",
        aliasDraft = "Paul",
        fingerprint = "AA:BB:CC:DD",
        peerAlias = "Alice Martin",
        selectedPeerNodeId = "node-1",
        selectedContactUserId = "user-alice-1234567890",
        input = if (withAttachment) "Je t'envoie le mock mis a jour." else "",
        messages = if (withMessages) messages() else emptyList(),
        transportStatus = "LAN mesh alpha (2 pair(s))",
        cryptoStatus = "x3dh actif • prekeys:32",
        peers = peers(),
        contacts = contacts(),
        conversationActive = true,
        contactImportStatus = "Contact importe: Alice Martin",
        attachmentDraft = if (withAttachment) attachmentDraft() else null
    )
}
