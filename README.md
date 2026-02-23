# EnFait (alpha Android)

English version: `README_EN.md`

## Sommaire

- [Etat actuel (alpha)](#etat-actuel-alpha)
- [Ce que cette alpha ne fait pas encore](#ce-que-cette-alpha-ne-fait-pas-encore)
- [Architecture (resume)](#architecture-resume)
- [Build APK (debug)](#build-apk-debug)
- [Build en ligne (GitHub Actions)](#build-en-ligne-github-actions)
- [Installation (test)](#installation-test)
- [Test rapide (2 telephones Android)](#test-rapide-2-telephones-android)
- [Relai HTTP (optionnel)](#relai-http-optionnel)
- [Permissions Android (alpha)](#permissions-android-alpha)
- [Canaux des messages et statuts](#canaux-des-messages-et-statuts)
- [Comportement Bluetooth et BLE](#comportement-bluetooth-et-ble)
- [Erreurs connues / messages frequents](#erreurs-connues--messages-frequents)
- [Securite (etat alpha)](#securite-etat-alpha)
- [Roadmap conseillee (prochaines etapes)](#roadmap-conseillee-prochaines-etapes)
- [Fichiers utiles](#fichiers-utiles)

Prototype alpha d'une messagerie Android orientee decentralisee, sans serveur central de confiance.

Le projet vise une experience "Signal-like" sur Android, avec transport pair-a-pair local, relai optionnel, et une architecture preparee pour un vrai chiffrement E2E (X3DH / Double Ratchet).

## Etat actuel (alpha)

Cette alpha est utilisable pour des tests sur 2 telephones Android.

Fonctionnalites disponibles:
- UI chat Compose (style messagerie moderne)
- identite locale (cle publique/privee generee sur l'appareil)
- alias local modifiable et persistant
- QR identite (generation) + scan QR contact (import local)
- liste de contacts importes + selection de cible
- messages texte + piece jointe "metadata only" (nom/type/taille)
- stockage local chiffre des messages (AES/GCM via Android Keystore)
- accusés de reception (`recu`) / lecture (`lu`)
- notifications Android (foreground/background)
- transport hybride:
  - LAN Wi-Fi (UDP) pour echange local
  - Bluetooth classique (RFCOMM) pour echange local
  - relai HTTP optionnel (store-and-forward alpha)
- decouverte Bluetooth amelioree:
  - BLE discovery filtree sur UUID EnFait (pour limiter TV/casques/etc.)
  - filtrage des appareils non mobiles
- indication du canal utilise sur les messages / receipts (`Wi-Fi`, `BT`, `relay`, `Tor`, `local`)

## Ce que cette alpha ne fait pas encore

- pas de vrai chiffrement E2E Signal-like (X3DH / Double Ratchet)
  - la couche est preparee mais le moteur actuel est placeholder
- pas de groupes
- pas de synchronisation multi-appareils
- pas de transfert binaire reel de fichiers (seulement metadata de piece jointe)
- pas de transport Internet P2P natif (relai HTTP uniquement, optionnel)
- pas de publication Play Store / signing release integre

## Architecture (resume)

### Identite
- cle locale + fingerprint
- QR code pour echanger l'identite/contact

### Crypto
- stockage local chiffre (Keystore + AES/GCM)
- couche E2E abstraite (`E2ECipherEngine`) prete a etre remplacee

### Transport
- `LanUdpMeshTransport` (Wi-Fi local)
- `BluetoothMeshTransport` (RFCOMM) + BLE pour discovery/filtrage
- `RelayHttpMeshTransport` (optionnel)
- `HybridMeshTransport` pour agregation

### Persistance
- messages et contacts stockes localement
- protections alpha ajoutees:
  - validation des champs
  - caps de taille
  - rotation/retention
  - backup JSON local de secours

## Build APK (debug)

Le repo ne contient pas encore `gradlew`, donc utiliser Android Studio ou un `gradle` installe localement.

```bash
gradle assembleDebug
```

APK attendu:
- `app/build/outputs/apk/debug/app-debug.apk`

## Build en ligne (GitHub Actions)

Workflow present:
- `.github/workflows/android-debug-apk.yml`

Declenchement:
- push sur `main` ou `alpha`
- pull request

Resultat:
- artifact telechargeable `app-debug-apk` dans GitHub Actions

## Installation (test)

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Test rapide (2 telephones Android)

### Chat LAN sur Wi-Fi
1. Installer l'app sur 2 telephones
2. Connecter les 2 telephones au meme Wi-Fi
3. Ouvrir l'app sur les 2
4. Verifier la detection des pairs LAN
5. Selectionner un pair et envoyer un message

### Chat Bluetooth
1. Donner les permissions Bluetooth (scan/connect/advertise)
2. Passer en mode `Bluetooth` dans `Configuration > Transport offline`
3. Verifier que les pairs visibles sont des appareils mobiles EnFait (filtrage BLE + filtres device)
4. Envoyer un message

Notes:
- la discovery BLE est utilisee pour filtrer la liste (UUID EnFait)
- l'echange des messages reste en Bluetooth classique (RFCOMM) dans cette alpha

## Relai HTTP (optionnel)

Mini-relai de test fourni:
- `relay/minirelay.py`

### Lancer le relai (sans auth)
```bash
python3 relay/minirelay.py
```

### Lancer le relai (avec auth HMAC)
```bash
AUFAIT_RELAY_SHARED_SECRET=monsecret python3 relay/minirelay.py
```

### Cote app (secret relai)
Dans l'app:
- `Configuration > Relay / Tor`
- champ `Secret relay (HMAC)`
- `Sauver`

Si le secret est vide:
- auth HMAC desactivee cote client

Si le secret est rempli:
- les requetes `push/pull` du relai sont signees (HMAC-SHA256)

### Activer le relai dans le code (alpha)
Dans `app/src/main/java/com/aufait/alpha/data/AlphaChatContainer.kt`:
- mettre `enabled = true`
- configurer `relayUrl`
  - `10.0.2.2` pour emulateur Android
  - IP LAN du PC pour vrais telephones

## Permissions Android (alpha)

Permissions utilisees:
- `INTERNET`
- `POST_NOTIFICATIONS`
- `CAMERA`
- `BLUETOOTH_CONNECT`
- `BLUETOOTH_SCAN`
- `BLUETOOTH_ADVERTISE`
- `ACCESS_FINE_LOCATION` (Android <= 30, compat)

Comportement UX:
- la permission Bluetooth est demandee a la demande (quand l'utilisateur choisit un mode utilisant Bluetooth)
- la permission camera est demandee uniquement pour scanner un QR

## Canaux des messages et statuts

L'UI affiche des infos de canal de maniere discrete:
- message entrant: canal de reception (`Wi-Fi`, `BT`, `relay`, `Tor`, `local`)
- message sortant: canal du retour d'accuse (`recu` / `lu`) quand disponible

Ameliorations alpha incluses:
- dedup des messages/receipts sur multi-canaux
- fusion des metadata de receipt (heure + canal)
- statuts plus monotones (moins de regressions visuelles)

## Comportement Bluetooth et BLE

### Pourquoi la discovery BLE
Pour ne plus voir dans la liste:
- TV
- casques / enceintes
- autres peripheriques non pertinents

### Ce qui est filtre
- scan BLE sur UUID EnFait
- filtrage des classes Bluetooth non mobiles
- prefixe `EnFait-` utilise en complement sur les devices classiques

### Appareils deja appaires au smartphone
Ils restent appaires au systeme Android, mais:
- ils ne sont plus affiches dans EnFait s'ils n'annoncent pas le service EnFait (BLE) / ne matchent pas les filtres

## Erreurs connues / messages frequents

### BLE advertise error 1
Cause probable:
- payload d'advertising BLE trop grand (`ADVERTISE_FAILED_DATA_TOO_LARGE`)

Correctif deja applique:
- advertising BLE reduit a l'UUID EnFait (sans `serviceData`)

### BT erreur read failed read ret minus 1
Ce message correspond souvent a une fermeture normale de socket Bluetooth.

Correctif deja applique:
- les fermetures de socket benignes (EOF / reset / broken pipe) sont filtrees et ne doivent plus remonter en erreur rouge persistante.

## Securite (etat alpha)

Ce qui est deja fait:
- stockage local chiffre
- validation / caps de taille sur QR, contacts, metadata pieces jointes
- relai HTTP:
  - signature HMAC optionnelle
  - anti-rejeu (timestamp + nonce)
  - rate limiting basique
  - caps de taille / queue

Dette securite principale:
- chiffrement E2E de messagerie non implemente (X3DH / Double Ratchet)

## Roadmap conseillee (prochaines etapes)

1. Vrai chiffrement E2E (X3DH puis Double Ratchet)
2. URL du relai configurable depuis l'UI (pas en dur dans `AlphaChatContainer`)
3. Transfert binaire reel des pieces jointes (images puis docs)
4. BLE discovery + handshakes plus stricts (signalement EnFait robuste)
5. Transport Internet plus robuste (relai store-and-forward persistant)

## Fichiers utiles

- `app/src/main/java/com/aufait/alpha/data/AlphaChatContainer.kt`
- `app/src/main/java/com/aufait/alpha/data/ChatService.kt`
- `app/src/main/java/com/aufait/alpha/data/LanUdpMeshTransport.kt`
- `app/src/main/java/com/aufait/alpha/data/BluetoothMeshTransport.kt`
- `app/src/main/java/com/aufait/alpha/data/RelayHttpMeshTransport.kt`
- `relay/minirelay.py`
- `.github/workflows/android-debug-apk.yml`
