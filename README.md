# Aufait (alpha Android)

Prototype alpha d'une messagerie Android orientee decentralisee (P2P a venir).

Cette version contient:
- identite locale (paire de cles Ed25519 generee sur l'appareil)
- chat 1:1 alpha avec UI Compose
- stockage local chiffre des messages (AES/GCM via Android Keystore)
- transport "mesh" simule (loopback) pour valider les flux avant la vraie couche P2P

## Limites de cette alpha
- pas encore de vrai reseau P2P (`libp2p`/LAN a faire)
- pas de protocole Signal (X3DH/Double Ratchet)
- pas de QR code contact
- pas de multi-appareils

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
- remplacer `LoopbackMeshTransport` par une couche LAN/P2P
- ajouter echange de contacts (QR)
- migrer vers un vrai protocole E2E (X3DH + Double Ratchet)
