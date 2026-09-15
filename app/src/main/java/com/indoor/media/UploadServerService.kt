package com.indoor.media

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.IBinder
import kotlinx.coroutines.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Enumeration

class UploadServerService : Service() {

    private val binder = LocalBinder()
    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var playlistManager: PlaylistManager
    private lateinit var prefs: SharedPreferences

    companion object {
        const val DEFAULT_PORT = 8080
        const val PREFS_NAME = "upload_server"
        const val KEY_PORT = "port"
        private const val BUFFER_SIZE = 64 * 1024

        const val LISTEN_PORT = "listening_port"
        const val ACTION_START = "com.indoor.media.START"
        const val ACTION_STOP = "com.indoor.media.STOP"
    }

    inner class LocalBinder : Binder() {
        fun getService(): UploadServerService = this@UploadServerService
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        playlistManager = PlaylistManager(this)
        playlistManager.init()
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopServer()
            else -> startServer()
        }
        return START_STICKY
    }

    fun startServer() {
        if (serverSocket != null) return
        val context = this as Context
        val port = prefs.getInt(KEY_PORT, DEFAULT_PORT)
        scope.launch {
            try {
                serverSocket = ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"))
                val effectivePort = serverSocket?.localPort ?: port
                withContext(Dispatchers.Main) {
                    broadcastPort(effectivePort)
                }
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putInt(KEY_PORT, effectivePort).apply()
                while (serverSocket != null && !serverSocket!!.isClosed) {
                    val socket = serverSocket?.accept() ?: break
                    scope.launch { handleClient(socket) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    broadcastPort(-1)
                }
            }
        }
    }

    fun stopServer() {
        scope.launch {
            try {
                serverSocket?.close()
            } catch (e: Exception) {
            }
            serverSocket = null
        }
    }

    private fun broadcastPort(port: Int) {
        sendBroadcast(Intent(ACTION_START).putExtra(LISTEN_PORT, port))
    }

    private suspend fun handleClient(socket: Socket) {
        try {
            val header = mutableMapOf<String, String>()
            val readerThread = scopedReader(socket, header)

            val method = header["_method"] ?: "GET"
            val path = header["_path"] ?: "/"

            when (method) {
                "UNLOCK", "OPTIONS" -> sendText(socket, 200, "OK")
                else -> handleHttp(socket, method, path, header, readerThread)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
            }
        }
    }

    private suspend fun scopedReader(socket: Socket, header: MutableMap<String, String>): Deferred<Unit> {
        return scope.async {
            val input = DataInputStream(socket.getInputStream())
            val firstLine = readLine(input)
            if (firstLine != null) {
                val parts = firstLine.split(" ")
                if (parts.size >= 2) {
                    header["_method"] = parts[0].uppercase()
                    header["_path"] = parts[1]
                }
                var line = readLine(input)
                while (line != null && line.isNotEmpty()) {
                    val idx = line.indexOf(":")
                    if (idx > 0) {
                        header[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
                    }
                    if (line.startsWith("Content-Length", true)) {
                        header["_contentLength"] = line.substring(idx + 1).trim()
                    }
                    line = readLine(input)
                }
            }
        }
    }

    private suspend fun readLine(input: DataInputStream): String? {
        val bytes = mutableListOf<Byte>()
        try {
            var b: Int
            while (bytes.lastOrNull() != '\n'.code.toByte() || bytes.isEmpty()) {
                b = input.read()
                if (b == -1) return if (bytes.isEmpty()) null else String(bytes.toByteArray()).trim('\r', '\n')
                bytes.add(b.toByte())
            }
        } catch (e: java.io.EOFException) {
            return null
        }
        return String(bytes.toByteArray()).trim('\r', '\n')
    }

    private suspend fun handleHttp(
        socket: Socket,
        method: String,
        path: String,
        header: MutableMap<String, String>,
        readerThread: Deferred<Unit>
    ) {
        when {
            path == "/" || path == "/index.html" -> sendHtml(socket, buildIndexHtml())
            path.startsWith("/api/playlist") -> sendJson(socket, playlistJson())
            path == "/api/clear-all" && method == "POST" -> {
                playlistManager.clearAll()
                sendText(socket, 200, "OK")
            }
            path == "/api/delete" && method == "POST" && header["_contentLength"] != null -> {
                val body = readBody(socket, header["_contentLength"]!!.toInt())
                val fileName = body.replace("\"", "").replace("{", "").replace("}", "").substringAfter(":")
                playlistManager.removeItem(fileName)
                sendText(socket, 200, "OK")
            }
            path.startsWith("/upload") && method == "POST" -> {
                val contentLength = header["_contentLength"]?.toInt() ?: 0
                if (contentLength > 0) {
                    val contentType = header["content-type"] ?: "application/octet-stream"
                    val fileName = header["x-file-name"] ?: "upload_${System.currentTimeMillis()}.bin"
                    val safeName = fileName.replace("/", "_").replace("\\", "_")
                    receiveFile(socket, contentLength, safeName)
                    sendText(socket, 200, "OK")
                } else {
                    sendText(socket, 400, "BAD REQUEST")
                }
            }
            else -> sendText(socket, 404, "NOT FOUND")
        }
    }

    private suspend fun readBody(socket: Socket, length: Int): String {
        val input = DataInputStream(socket.getInputStream())
        val bytes = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(bytes, read, length - read)
            if (n == -1) break
            read += n
        }
        return String(bytes, StandardCharsets.UTF_8)
    }

    private suspend fun receiveFile(socket: Socket, contentLength: Int, fileName: String) {
        val input = DataInputStream(socket.getInputStream())
        val file = File(playlistManager.contentDir, fileName)
        FileOutputStream(file).use { out ->
            val buffer = ByteArray(BUFFER_SIZE)
            var remaining = contentLength
            while (remaining > 0) {
                val toRead = minOf(remaining.toLong(), BUFFER_SIZE.toLong()).toInt()
                val n = input.read(buffer, 0, toRead)
                if (n == -1) break
                out.write(buffer, 0, n)
                remaining -= n
            }
        }
        playlistManager.addItem(file)
    }

    private fun playlistJson(): String {
        val items = playlistManager.getPlaylist()
        val sb = StringBuilder()
        sb.append("{\"playlist\":[")
        items.forEachIndexed { idx, item ->
            if (idx > 0) sb.append(",")
            sb.append("{\"name\":\"${item.fileName}\",\"type\":\"${item.type.name}\",\"duration\":${item.durationSeconds}}")
        }
        sb.append("]}")
        return sb.toString()
    }

    private fun sendText(socket: Socket, status: Int, body: String) {
        sendRaw(socket, "text/plain", status, body)
    }

    private fun sendJson(socket: Socket, body: String) {
        sendRaw(socket, "application/json", 200, body)
    }

    private fun sendHtml(socket: Socket, body: String) {
        sendRaw(socket, "text/html", 200, body)
    }

    private fun sendRaw(socket: Socket, contentType: String, status: Int, body: String) {
        try {
            val out = DataOutputStream(socket.getOutputStream())
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            val statusText = when (status) {
                200 -> "OK"
                400 -> "BAD REQUEST"
                404 -> "NOT FOUND"
                else -> "OK"
            }
            out.writeBytes("HTTP/1.1 $status $statusText\r\n")
            out.writeBytes("Content-Type: $contentType; charset=utf-8\r\n")
            out.writeBytes("Content-Length: ${bytes.size}\r\n")
            out.writeBytes("Connection: close\r\n")
            out.writeBytes("Access-Control-Allow-Origin: *\r\n")
            out.writeBytes("\r\n")
            out.write(bytes)
            out.flush()
        } catch (e: Exception) {
        }
    }

    fun getListenPort(): Int = serverSocket?.localPort ?: prefs.getInt(KEY_PORT, DEFAULT_PORT)

    private fun buildIndexHtml(): String {
        val ip = getLocalIpAddress() ?: "0.0.0.0"
        return """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Indoor Media - Upload</title>
              <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body { font-family: Arial, sans-serif; background: #0f172a; color: #e2e8f0; padding: 20px; }
                .container { max-width: 800px; margin: 0 auto; background: #1e293b; border-radius: 12px; padding: 24px; }
                h1 { font-size: 24px; margin-bottom: 20px; color: #38bdf8; }
                .section { margin-bottom: 24px; }
                .section h2 { font-size: 16px; margin-bottom: 12px; color: #94a3b8; }
                input[type="file"] { display: block; width: 100%; padding: 10px; background: #334155; border: 1px solid #475569; border-radius: 8px; color: #e2e8f0; margin-bottom: 12px; }
                button { background: #38bdf8; color: #0f172a; border: none; padding: 10px 24px; border-radius: 8px; font-size: 16px; font-weight: bold; cursor: pointer; }
                button:hover { background: #0ea5e9; }
                button.danger { background: #ef4444; color: white; }
                .dropzone { border: 2px dashed #475569; border-radius: 12px; padding: 40px; text-align: center; color: #94a3b8; margin-bottom: 16px; cursor: pointer; transition: all 0.2s; }
                .dropzone.dragging { border-color: #38bdf8; background: #334155; color: #38bdf8; }
                .file-list { list-style: none; }
                .file-list li { display: flex; align-items: center; justify-content: space-between; padding: 10px 12px; background: #334155; border-radius: 8px; margin-bottom: 8px; }
                .file-list .tag { font-size: 11px; padding: 2px 8px; border-radius: 4px; background: #475569; color: #e2e8f0; margin-left: 8px; }
                .tag.VIDEO { background: #2563eb; }
                .tag.IMAGE { background: #16a34a; }
                .tag.AUDIO { background: #d97706; }
                .status { margin-top: 12px; padding: 10px; border-radius: 8px; background: #334155; display: none; }
                .status.show { display: block; }
                .status.error { background: #7f1d1d; color: #fecaca; }
                .status.success { background: #14532d; color: #bbf7d0; }
                .footer { margin-top: 16px; font-size: 12px; color: #64748b; text-align: center; }
                .ip-display { font-size: 14px; background: #334155; padding: 8px 12px; border-radius: 8px; margin-bottom: 16px; }
                .ip-display b { color: #38bdf8; }
                .progress-wrap { background: #334155; border-radius: 8px; height: 8px; margin-top: 8px; overflow: hidden; display: none; }
                .progress-wrap.show { display: block; }
                .progress-bar { height: 100%; background: #38bdf8; width: 0%; transition: width 0.2s; }
              </style>
            </head>
            <body>
              <div class="container">
                <h1>Indoor Media Player</h1>
                <div class="ip-display">IP: <b>$ip</b> - Envie arquivos pelo navegador de qualquer dispositivo na rede</div>

                <div class="section">
                  <h2>Enviar arquivos</h2>
                  <div class="dropzone" id="dropzone">
                    Arraste arquivos ou clique para selecionar<br>
                    <small>Formatos suportados: MP4, MKV, AVI, JPG, PNG, MP3, WAV</small>
                  </div>
                  <input type="file" id="fileInput" multiple hidden>
                  <button onclick="uploadFiles()">Enviar</button>
                  <div class="progress-wrap" id="progressWrap"><div class="progress-bar" id="progressBar"></div></div>
                  <div class="status" id="status"></div>
                </div>

                <div class="section">
                  <h2>Conteúdo atual</h2>
                  <ul class="file-list" id="fileList"></ul>
                </div>

                <div class="section">
                  <button class="danger" onclick="clearAll()">Limpar todo o conteúdo</button>
                </div>

                <div class="footer">Conecte ao IP acima pelo navegador para gerenciar o conteudo.</div>
              </div>

              <script>
                let selectedFiles = [];

                const dropzone = document.getElementById('dropzone');
                const fileInput = document.getElementById('fileInput');
                const statusEl = document.getElementById('status');

                dropzone.addEventListener('click', () => fileInput.click());
                dropzone.addEventListener('dragover', (e) => {
                  e.preventDefault();
                  dropzone.classList.add('dragging');
                });
                dropzone.addEventListener('dragleave', () => dropzone.classList.remove('dragging'));
                dropzone.addEventListener('drop', (e) => {
                  e.preventDefault();
                  dropzone.classList.remove('dragging');
                  selectedFiles = Array.from(e.dataTransfer.files);
                  showStatus('${'$'}{selectedFiles.length} arquivo(s) selecionado(s)', 'success');
                });
                fileInput.addEventListener('change', () => {
                  selectedFiles = Array.from(fileInput.files);
                  showStatus('${'$'}{selectedFiles.length} arquivo(s) selecionado(s)', 'success');
                });

                async function uploadFiles() {
                  if (selectedFiles.length === 0) {
                    showStatus('Nenhum arquivo selecionado', 'error');
                    return;
                  }
                  const wrap = document.getElementById('progressWrap');
                  const bar = document.getElementById('progressBar');
                  wrap.classList.add('show');
                  let done = 0;

                  for (const file of selectedFiles) {
                    try {
                      const res = await fetch('/upload', {
                        method: 'POST',
                        headers: { 'X-File-Name': file.name },
                        body: file
                      });
                      if (!res.ok) throw new Error('Falha no upload');
                    } catch (e) {
                      showStatus('Erro no arquivo: ${'$'}{file.name}', 'error');
                      bar.style.width = '0%';
                      wrap.classList.remove('show');
                      return;
                    }
                    done++;
                    bar.style.width = Math.round((done / selectedFiles.length) * 100) + '%';
                  }

                  showStatus('Upload concluído: ${'$'}{done} arquivo(s)', 'success');
                  setTimeout(() => { wrap.classList.remove('show'); bar.style.width = '0%'; }, 2000);
                  selectedFiles = [];
                  fileInput.value = '';
                  loadPlaylist();
                }

                function showStatus(msg, type) {
                  statusEl.textContent = msg;
                  statusEl.className = 'status show ' + type;
                  setTimeout(() => statusEl.classList.remove('show'), 4000);
                }

                async function loadPlaylist() {
                  try {
                    const res = await fetch('/api/playlist');
                    const data = await res.json();
                    const list = document.getElementById('fileList');
                    list.innerHTML = '';
                    data.playlist.forEach(item => {
                      const li = document.createElement('li');
                      li.innerHTML = '<span>' + item.name + '<span class="tag">' + item.type + '</span></span>' +
                        '<button class="danger" onclick="deleteFile(\'' + item.name + '\')" style="background:#dc2626;color:white;padding:6px 12px;font-size:12px">Excluir</button>';
                      list.appendChild(li);
                    });
                    if (data.playlist.length === 0) {
                      list.innerHTML = '<li style="justify-content:center;color:#64748b">Nenhum conteúdo</li>';
                    }
                  } catch (e) {}
                }

                async function deleteFile(name) {
                  const res = await fetch('/api/delete', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ name: name })
                  });
                  if (res.ok) { loadPlaylist(); showStatus('Arquivo excluído', 'success'); }
                }

                async function clearAll() {
                  if (!confirm('Tem certeza que deseja limpar todo o conteúdo?')) return;
                  await fetch('/api/clear-all', { method: 'POST' });
                  loadPlaylist();
                  showStatus('Conteúdo limpo', 'success');
                }

                loadPlaylist();
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addresses: Enumeration<InetAddress> = intf.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr.address.size == 4) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
        }
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
        scope.cancel()
    }
}