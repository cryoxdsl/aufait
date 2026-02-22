# Aufait (alpha Android)

Prototype alpha d'une messagerie Android orientee decentralisee (P2P a venir).

Cette version contient:
- identite locale (paire de cles Ed25519 generee sur l'appareil)
- chat 1:1 alpha avec UI Compose
- stockage local chiffre des messages (AES/GCM via Android Keystore)
- transport LAN P2P (UDP broadcast) + fallback loopback
- QR identite (generation) + scan QR contact (import local)
- accusés de reception / lecture
- notifications Android (foreground/background)
- architecture hybride LAN + relai HTTP (client relai minimal)

## Limites de cette alpha
- chiffrement E2E "Signal-like" non implemente (couche preparee uniquement)
- relai HTTP non active par defaut (mini-relai fourni pour tests)
- pas de groupes
- pas de multi-appareils synchronises

## Build APK (debug)

1. Installer Android Studio (ou Android SDK + Gradle)
2. Ouvrir le projet
3. Lancer (ce repo n'inclut pas encore `gradlew`, donc utiliser `gradle` installe localement ou Android Studio):

```bash
gradle assembleDebug
```

APK attendu:
- `app/build/outputs/apk/debug/app-debug.apk`

## Prochaine etape recommandees
- brancher un vrai protocole E2E (X3DH + Double Ratchet) sur `E2ECipherEngine`
- ajouter scanner QR robuste + selection de contact pour envoi via relai
- activer/configurer le relai HTTP et ajouter auth anti-spam

## Test du mini-relai HTTP (optionnel)

1. Lancer le relai sur la machine:

```bash
python3 relay/minirelay.py
```

2. Dans `app/src/main/java/com/aufait/alpha/data/AlphaChatContainer.kt`, passer:
- `enabled = true`
- `relayUrl` vers l'IP LAN du PC (sur vrais telephones) ou `10.0.2.2` (emulateur)

3. Important:
- le relai route par `nodeId` (pas par alias)
- pour une vraie UX relai, il faut selectionner/envoyer vers un contact connu (prochaine etape UI)
