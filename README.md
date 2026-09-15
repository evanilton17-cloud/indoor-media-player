# Indoor Media Player

Reprodução de conteúdo (vídeo, imagem, áudio) em TV/LTV/Android TV em **loop contínuo**, controlado por **servidor WebSocket** ou **Firebase**.

```
Painel Web → Servidor Node.js (WebSocket) → TV baixa e toca sozinha
```

## Como funciona

| Componente | Função |
|------------|--------|
| App Android | Roda na TV, baixa conteúdo via WebSocket/HTTP e toca em loop |
| Servidor Node.js | WebSocket API — envia manifest, recebe status/proof de play |
| Dashboard Web | Painel web para gerenciar dispositivos, playlists e agendamentos |

A TV se conecta via WebSocket, recebe o manifesto de conteúdo, baixa e toca em loop.
Emit `device:proof` e `device:status` a cada item tocado/atualizado.

## Fluxo de pareamento

1. Ao abrir pela primeira vez, o app exibe um **código PIN de 6 dígitos** na tela.
2. No dashboard web, o usuário escolhe a TV e digita o código para vincular.
3. Após vincular, o app recebe `needPairing=false` no ack de `device:hello` e passa a tocar o conteúdo.
4. Para desligar pareamento, o dashboard envia comando UNPAIR; o app volta à tela de PIN.

## Configuração do servidor

A URL do servidor WebSocket está definida em `AppPreferences.kt`:

```kotlin
const val SERVER_URL = "http://10.0.2.2:3000"  // Emulador Android → localhost
```

**Em produção**, substitua pelo IP/hostname real do servidor (ex.: `http://192.168.1.100:3000` ou `https://media.seudominio.com`).

## Estrutura do Manifesto (stream:manifest)

```json
{
  "schedule": { "id": "...", "name": "..." },
  "playlistId": "...",
  "items": [
    {
      "id": "content-id",
      "name": "video.mp4",
      "type": "VIDEO",
      "storageUrl": "https://...",
      "durationSeconds": 30,
      "version": 1
    }
  ]
}
```

## Eventos WebSocket

| Evento | Direção | Dados |
|--------|---------|-------|
| `device:hello` | App → Server | `{uniqueId, appVersion, model, androidVersion}` ack: `{deviceId, needPairing, pairingCode?}` |
| `device:status` | App → Server | `{currentContent, itemCount, volume}` |
| `device:proof` | App → Server | `{contentId, contentName, type, startedAt, endedAt, durationSeconds}` |
| `stream:manifest` | Server → App | `{schedule, playlistId, items: [...]}` |
| `device:command` | Server → App | `{action, value}` action: REBOOT/REFRESH/PLAY/PAUSE/STOP/VOLUME/SCREEN_ON/SCREEN_OFF/UNPAIR |

## Firebase (fallback offline)

O suporte Firebase continua disponível como fallback local caso o servidor WebSocket não esteja disponível.

Configure em `FirebaseConfig.kt` com as credenciais do projeto Firebase (opcional).

## Suporte de formatos

- **Vídeo:** MP4, MKV, WebM, AVI, MOV
- **Imagem:** JPG, PNG, GIF, WebP, BMP
- **Áudio:** MP3, WAV, AAC, OGG, M4A, FLAC

## Estrutura

```
IndoorMediaPlayer/
├── app/                         # App Android (Kotlin + ExoPlayer)
├── dashboard/index.html         # Painel web de controle
├── security-realtime-db.json    # Regras do Realtime Database
└── security-storage.rules       # Regras do Storage
```