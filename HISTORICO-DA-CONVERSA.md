# Histórico da Sessão — Indoor Media Player SaaS

> Data: 15/09/2026 · Conversa salva junto com o projeto para continuidade futura.

---

## O que é este projeto

Sistema de **mídia indoor (digital signage)** para TVs comerciais (TV Box / Android TV), criado como **SaaS** para vender ou alugar. O dono do negócio controla os conteúdos pela web e as TVs exibem vídeos/imagens/áudio remotamente, com prova de exibição (proof of play).

**Arquitetura em 3 partes:**
1. **Backend** (`backend/`) — Node.js + Express + Socket.io + MongoDB (fallback em memória)
2. **App Android TV** (`app/`) — Kotlin, Kotlin: player de mídia + pareamento via PIN + WebSocket
3. **Dashboard web** (`dashboard/index.html`) — painel de controle do dono

---

## Linha do tempo do que foi construído

### 1. Definição do projeto
- Usuário viu um sistema similar rodando em TV Box com APP e quis construir o próprio.
- **Escolhido:** Kotlin nativo para Android, mídia (vídeo + imagem + áudio), app com upload de conteúdo.

### 2. Primeira versão (offline)
- Player local: `MainActivity`, `PlaylistManager`, `UploadServerService`, `ContentManagerActivity`.
- Upload direto via rede local (socket TCP) do PC para a TV.

### 3. Controle na nuvem
- Usuário perguntou: "como controlar de redes diferentes (casa x academia)?" → **controle via nuvem**.
- Primeiro foi tentado **Firebase** (`FirebaseManager.kt`, `FirebaseConfig.kt`), depois evoluído.

### 4. Decisão de vender (SaaS)
- Usuário: *"coloque tudo que for necessário para ser melhor, pois minha intenção é vender ou alugar o app."*
- **Refeito como SaaS completo**, trocando Firebase por backend próprio:

#### Backend Node.js (`backend/`)
- `src/index.js` — entry point (conecta DB + semeador automático em modo memória)
- `src/db.js` — fallback automático: MongoDB real → **mongodb-memory-server** (sem precisar instalar o Mongo)
- `src/models/` — `User`, `Device`, `Content`, `Playlist`, `Schedule`, `ProofOfPlay`
- `src/routes/` — `auth` (login JWT), `devices`, `contents`, `playlists`, `schedules`
- `src/services/realtime.js` — WebSocket Socket.io: `device:hello`, pareamento, `stream:manifest`, comandos
- `src/services/demoseed.js` — popula dados demo quando o banco está vazio
- `src/utils/jwt.js` — autenticação JWT

#### App Android (`app/`)
- `AppPreferences.kt` — deviceId UUID, estado de pareamento, código PIN, `SERVER_URL`
- `CloudSocketManager.kt` — cliente Socket.IO com reconexão
- `ManifestPlayer.kt` — download/cache + reprodução (ExoPlayer/ImageView) + proof of play
- `PairingActivity.kt` — tela de pareamento por PIN de 6 dígitos
- `MainActivity.kt` — player principal
- Dependências: ExoPlayer 2.19.1, socket.io-client 2.1.0, OkHttp 4.12.0

#### Dashboard web (`dashboard/index.html`)
- Painel SaaS completo (~80KB): login, devices, conteúdos, playlists, horários (schedules).

### 5. Estratégia de venda (conselho dado)
- **Público-alvo:** academias, bares, clínicas, clínicas dentárias, lojas, clinics, igrejas.
- **Planos sugeridos:** Free (1 tela) · Starter R$29/mês (5 telas) · Pro R$99/mês (20 telas) · Enterprise R$249/mês.
- **Canais:** boca a boca, Instagram, parcerias com instaladores de TV.
- Observação: o usuário revelou que construiu por curiosidade no início (motivação evoluiu).

### 6. Rodando o sistema
- **Node.js:** já instalado (v24.19.0). *Atenção:* `npm` bloqueado no PowerShell por execution policy → usar `& "C:\Program Files\nodejs\npm.cmd"`.
- **MongoDB:** instalação winget falhou (instalador travou → processo morto). **Solução:** `mongodb-memory-server` como fallback.
- **Servidor iniciado:** `node src/index.js` na **porta 3000** → API testada e funcionando.
- **Login demo:** `admin@test.com` / `123456`
- **Device demo:** `TEST-DEVICE-001`
- **Conteúdos demo:** `video-promo.mp4`, `banner-academia.jpg`, `video-sneakers.mp4` · Playlist "Playlist Principal" · Horário "Comercial" (Seg–Sex 08h–18h).

### 7. Android Studio + geração do APK
- Usuário é **usuário não-administrador** do Windows — instaladores com UAC falham.
- **Android Studio portátil** baixado (ZIP 1,39 GB) e extraído em `...\Documents\Default Project\AndroidStudio\android-studio` (inclui o JDK próprio `jbr`).
- SDK em `C:\Users\Evanilton S.Ribeiro\AppData\Local\Android\Sdk` (só build-tools 36.0.0 / platform android-37).
- Usuário abriu o projeto no Android Studio mas não sabe operar a GUI → **build via linha de comando**:

#### Erros corrigidos durante o build CLI
1. **Java incompatível:** o `jbr` do Android Studio é Java 25, mas Gradle 8.7 só suporta até Java 22 → baixado **JDK 17 (Temurin)** e usado como `JAVA_HOME`. *(Arquivos em `Temp\opencode\jdk17` — temporários.)*
2. **`android.useAndroidX=true` faltando** → criado `gradle.properties`.
3. **Layout inválido:** `resize_mode="refresh"` → corrigido para `resize_mode="fill"` em `activity_main.xml`.
4. **Erros Kotlin:**
   - `import io.socket.Ack` → o correto na versão 2.1.0 é **`io.socket.client.Ack`**.
   - `continue@launch` inválido em `FirebaseManager.kt` → trocado por `continue`.
   - `playlistManager.getContentDir()` → a propriedade é publicada como **`contentDir`**.

### 8. Resultado
- ✅ **APK gerado:** `app\build\outputs\apk\debug\app-debug.apk` (9,9 MB)
- Instalação na TV: via `adb` (modo desenvolvedor + IP da TV) ou pendrive.
- ⚠️ **Importante:** o app usa `SERVER_URL = http://10.0.2.2:3000` (emulador). Para uma TV real, trocar em `AppPreferences.kt` para `http://IP_DO_PC:3000` (mesma rede WiFi).

### 9. Salvamento do projeto (Git)
- Repositório Git inicializado aqui + commit `79f9123` (53 arquivos, ~9 mil linhas).
- `.gitignore` exclui: `build/`, `node_modules/`, `local.properties`, `.env`, APKs.
- **Push para o GitHub:** https://github.com/evanilton17-cloud/indoor-media-player
- 🔒 **Segurança:** chaves/tokens NÃO foram commitados.

---

## Como retomar o trabalho depois

```powershell
cd "C:\Users\Evanilton S.Ribeiro\Documents\Default Project\IndoorMediaPlayer"

# Iniciar o backend (porta 3000, dados demo em memória):
& "C:\Program Files\nodejs\npm.cmd" start   # dentro de backend/

# Build do APK:
& "C:\Users\EVANIL~1.RIB\AppData\Local\Temp\opencode\gradle\gradle-8.7\bin\gradle.bat" assembleDebug
# (com JAVA_HOME apontando para o JDK 17 em Temp\opencode\jdk17\jdk-17.0.20.1+1)

# Abrir dashboard:
# dashboard\index.html  (API_URL = http://localhost:3000)
```

**Comandos Git úteis:**
```powershell
git status          # estado atual
git add -A && git commit -m "mensagem" && git push   # salvar e subir
git pull            # buscar mudanças do GitHub
```

---

## Próximos passos sugeridos (sessão futura)

1. **Testar APK numa TV Box real** — ajustar `SERVER_URL` para o IP da rede local.
2. **Gerar APK release assinado** para instalar fora do modo debug.
3. **Melhorar dashboard** — telas de cadastro (signup), gerenciamento de planos/licenças.
4. **Prova de exibição (proof of play)** — relatórios no dashboard.
5. **Deploy em produção** — servidor VPS (ex.: Railway/Render/Hetzner) + MongoDB Atlas, domínio próprio, HTTPS.
6. **Upload de mídia pelo dashboard** (hoje: API + seed).

---

## Detalhes técnicos importantes

| Item | Valor |
|---|---|
| Backend | `backend/`, ES modules (`"type":"module"`), porta **3000** |
| DB | MongoDB real → fallback **mongodb-memory-server** (dados efêmeros) |
| Auth | JWT (`src/utils/jwt.js`) |
| API demo | `/api/auth/login` → `admin@test.com` / `123456` |
| App Android | `app/src/main/java/com/indoor/media/` (Kotlin) |
| compileSdk | 34 (AGP 8.5.2) — acessível pelo SDK auto-download |
| Build | Gradle 8.7 + JDK 17 (Temurin) |
| APK | `app/build/outputs/apk/debug/app-debug.apk` (9,9 MB) |
| Dashboard | `dashboard/index.html`, `API_URL=http://localhost:3000` |
| Git | Repositório local + GitHub `evanilton17-cloud/indoor-media-player` |

> ⚠️ Ferramentas de build (Gradle, JDK 17) estão em `Temp\opencode\` — são temporários do sistema. Para rebuild duradouro, considerar mover para `AndroidStudio\` do projeto.