package com.yuanman.app.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Base64
import com.yuanman.app.data.local.entity.RecordEntity
import com.yuanman.app.data.repository.CategoryRepository
import com.yuanman.app.utils.JsonBackupUtils
import com.yuanman.app.widget.WidgetUpdateManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class SyncEvent(val peerName: String, val recordCount: Int, val categoryCount: Int)

data class PeerDevice(
    val name: String,
    val host: InetAddress,
    val port: Int,
    val connected: Boolean
)

data class PendingSyncRequest(
    val id: String,
    val deviceName: String,
    val hostAddress: String
)

private enum class SyncMode {
    APPROVAL,
    PAIRING_CODE
}

class FamilySyncManager(
    context: Context,
    private val categoryRepository: CategoryRepository,
    private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val resolveExecutor = Executors.newSingleThreadExecutor()

    private var serverSocket: ServerSocket? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var ownServiceName: String = ""

    @Volatile
    private var running = false

    private val _devices = MutableStateFlow<List<PeerDevice>>(emptyList())
    val devices: StateFlow<List<PeerDevice>> = _devices.asStateFlow()

    private val _status = MutableStateFlow("未开启同步")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _pairingCode = MutableStateFlow(loadOrCreatePairingCode())
    val pairingCode: StateFlow<String> = _pairingCode.asStateFlow()

    private val _pendingRequests = MutableStateFlow<List<PendingSyncRequest>>(emptyList())
    val pendingRequests: StateFlow<List<PendingSyncRequest>> = _pendingRequests.asStateFlow()

    private val _pendingOutboundDevices = MutableStateFlow<Set<String>>(emptySet())
    val pendingOutboundDevices: StateFlow<Set<String>> = _pendingOutboundDevices.asStateFlow()

    private val _events = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<SyncEvent> = _events.asSharedFlow()

    private val _lastEvent = MutableStateFlow<SyncEvent?>(null)
    val lastEvent: StateFlow<SyncEvent?> = _lastEvent.asStateFlow()

    private val authFailures = mutableMapOf<String, Long>()
    private val approvalDecisions = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val outboundSyncs = ConcurrentHashMap.newKeySet<String>()
    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()

    val deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null)
            ?: UUID.randomUUID().toString().also {
                prefs.edit().putString(KEY_DEVICE_ID, it).apply()
            }

    fun regeneratePairingCode() {
        val code = randomCode()
        _pairingCode.value = code
        prefs.edit().putString(KEY_PAIRING_CODE, code).apply()
    }

    fun setPairingCode(code: String): Boolean {
        if (!code.matches(Regex("\\d{6}"))) return false
        _pairingCode.value = code
        prefs.edit().putString(KEY_PAIRING_CODE, code).apply()
        return true
    }

    @Synchronized
    fun start() {
        if (running) return
        running = true
        _status.value = "正在启动服务..."
        scope.launch(Dispatchers.IO) {
            try {
                acquireMulticastLock()
                if (!running) return@launch
                val server = ServerSocket(0)
                serverSocket = server
                launch { acceptLoop(server) }
                if (!running) return@launch
                registerService(server.localPort)
                if (!running) return@launch
                startDiscovery()
                _status.value = "正在搜索同一局域网下的设备..."
            } catch (e: Exception) {
                stop()
                _status.value = "启动失败: ${e.message}"
            }
        }
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        discoveryListener?.let { runCatching { nsdManager.stopServiceDiscovery(it) } }
        registrationListener?.let { runCatching { nsdManager.unregisterService(it) } }
        discoveryListener = null
        registrationListener = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        activeSockets.forEach { socket -> runCatching { socket.close() } }
        activeSockets.clear()
        multicastLock?.let { lock -> runCatching { if (lock.isHeld) lock.release() } }
        multicastLock = null
        outboundSyncs.clear()
        _pendingOutboundDevices.value = emptySet()
        approvalDecisions.values.forEach { it.complete(false) }
        approvalDecisions.clear()
        _pendingRequests.value = emptyList()
        _devices.value = emptyList()
        _status.value = "已停止同步"
    }

    fun close() {
        stop()
        resolveExecutor.shutdownNow()
    }

    fun syncNow() {
        if (_syncing.value) return
        val peers = _devices.value
        if (peers.isEmpty()) {
            _status.value = "未发现同一 WiFi 下的其他设备"
            return
        }
        _status.value = "正在同步数据..."
        _syncing.value = true
        val jobs = peers.map { connectAndSync(it, SyncMode.APPROVAL) }
        scope.launch {
            jobs.forEach { it.join() }
            _syncing.value = false
        }
    }

    /** 发起一次需要对方确认的同步请求。 */
    fun requestSync(device: PeerDevice) {
        if (!running) {
            _status.value = "请先开启设备同步"
            return
        }
        connectAndSync(device, SyncMode.APPROVAL)
    }

    /** 配对码备用入口，默认 UI 不展示，保留给无法点击设备时使用。 */
    fun syncNowWithPairingCode() {
        if (_syncing.value) return
        val peers = _devices.value
        if (peers.isEmpty()) {
            _status.value = "未发现同一 WiFi 下的其他设备"
            return
        }
        _status.value = "正在使用配对码同步..."
        _syncing.value = true
        val jobs = peers.map { connectAndSync(it, SyncMode.PAIRING_CODE) }
        scope.launch {
            jobs.forEach { it.join() }
            _syncing.value = false
        }
    }

    fun respondToSyncRequest(requestId: String, accepted: Boolean) {
        approvalDecisions[requestId]?.complete(accepted)
    }

    private fun acquireMulticastLock() {
        runCatching {
            val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("yuanman_nsd")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        }
    }

    private fun registerService(port: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME_PREFIX + deviceId.take(6)
            serviceType = SERVICE_TYPE
            this.port = port
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                ownServiceName = serviceInfo.serviceName ?: ""
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                _status.value = "注册局域网服务失败 (错误码 $errorCode)"
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        registrationListener = listener
        nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun startDiscovery() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val name = serviceInfo.serviceName ?: return
                if (!name.startsWith(SERVICE_NAME_PREFIX)) return
                if (ownServiceName.isNotEmpty() && name == ownServiceName) return
                if (name == SERVICE_NAME_PREFIX + deviceId.take(6)) return
                resolve(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                removeDevice(serviceInfo.serviceName ?: return)
            }

            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                _status.value = "搜索设备失败 (错误码 $errorCode)"
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        discoveryListener = listener
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    @Suppress("DEPRECATION")
    private fun resolve(serviceInfo: NsdServiceInfo) {
        resolveExecutor.execute {
            if (!running) return@execute
            val latch = CountDownLatch(1)
            runCatching {
                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) {
                        latch.countDown()
                    }

                    override fun onServiceResolved(si: NsdServiceInfo) {
                        onPeerResolved(si)
                        latch.countDown()
                    }
                })
            }.onFailure { latch.countDown() }
            runCatching { latch.await(5, TimeUnit.SECONDS) }
        }
    }

    @Suppress("DEPRECATION")
    private fun onPeerResolved(info: NsdServiceInfo) {
        if (!running) return
        val name = info.serviceName ?: return
        val host = info.host ?: return
        if (name == ownServiceName) return
        val device = PeerDevice(name = name, host = host, port = info.port, connected = false)
        _devices.value = _devices.value.filterNot { it.name == name } + device
        _status.value = "发现 ${_devices.value.size} 台设备在线"
    }

    private fun connectAndSync(device: PeerDevice, mode: SyncMode): Job {
        if (!running) return scope.launch { }
        return scope.launch(Dispatchers.IO) {
            if (!outboundSyncs.add(device.name)) return@launch
            _pendingOutboundDevices.value = _pendingOutboundDevices.value + device.name
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(device.host, device.port), CONNECT_TIMEOUT_MS)
                val (peerName, recordCount, categoryCount) = exchange(socket, incoming = false, mode = mode)
                markConnected(device.name, true)
                _status.value = "已成功同步设备 $peerName"
                val event = SyncEvent(peerName, recordCount, categoryCount)
                _lastEvent.value = event
                _events.emit(event)
            } catch (e: Exception) {
                _status.value = "同步 ${device.name} 失败：${e.message ?: "连接异常"}"
            } finally {
                runCatching { socket.close() }
                outboundSyncs.remove(device.name)
                _pendingOutboundDevices.value = _pendingOutboundDevices.value - device.name
            }
        }
    }

    private fun acceptLoop(server: ServerSocket) {
        while (running) {
            try {
                val socket = server.accept()
                if (!running) {
                    socket.close()
                    continue
                }
                val remoteHost = socket.inetAddress.hostAddress ?: "unknown"
                val blockedUntil = synchronized(authFailures) { authFailures[remoteHost] ?: 0L }
                if (blockedUntil > System.currentTimeMillis()) {
                    socket.close()
                    continue
                }
                scope.launch(Dispatchers.IO) {
                    try {
                        val (peerName, recordCount, categoryCount) = exchange(socket, incoming = true, mode = SyncMode.APPROVAL)
                        synchronized(authFailures) { authFailures.remove(remoteHost) }
                        val event = SyncEvent(peerName, recordCount, categoryCount)
                        _lastEvent.value = event
                        _events.emit(event)
                    } catch (e: Exception) {
                        synchronized(authFailures) {
                            authFailures[remoteHost] = System.currentTimeMillis() + AUTH_FAILURE_BACKOFF_MS
                        }
                        _status.value = "接收 $remoteHost 的同步失败：${e.message ?: "认证或数据异常"}"
                    }
                }
            } catch (e: Exception) {
                if (!running) break
            }
        }
    }

    private suspend fun exchange(
        socket: Socket,
        incoming: Boolean,
        mode: SyncMode
    ): Triple<String, Int, Int> {
        activeSockets.add(socket)
        return try {
            socket.use { s ->
            s.soTimeout = REQUEST_TIMEOUT_MS
            val writer = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))

            // 1. 握手交换
            val localNonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
            val localName = ownServiceName.ifBlank { SERVICE_NAME_PREFIX + deviceId.take(6) }
            val hello = JSONObject()
                .put("protocol", PROTOCOL_VERSION)
                .put("syncFormat", SYNC_FORMAT_VERSION)
                .put("deviceId", deviceId)
                .put("deviceName", localName)
                .put("nonce", Base64.encodeToString(localNonce, Base64.NO_WRAP))
            writer.write(hello.toString())
            writer.newLine()
            writer.flush()

            val peerLine = readLimitedLine(reader, MAX_CONTROL_LINE_CHARS) ?: throw IOException("对端未响应")
            val peerHello = JSONObject(peerLine)
            val peerId = peerHello.optString("deviceId", "unknown")
            if (peerHello.optInt("protocol", 0) != PROTOCOL_VERSION ||
                peerHello.optInt("syncFormat", 0) != SYNC_FORMAT_VERSION ||
                peerId == deviceId
            ) {
                throw IOException("同步协议不匹配，请将两台设备都升级到 v0.0.3")
            }
            val peerNonce = Base64.decode(peerHello.getString("nonce"), Base64.NO_WRAP)
            val peerName = peerHello.optString("deviceName", peerId.take(6))

            // 2. 新流程先请求对方确认；旧的配对码流程仍可从折叠入口使用。
            var firstPeerAuth: String? = null
            val authMode: SyncMode
            if (incoming) {
                val firstLine = readLimitedLine(reader, MAX_CONTROL_LINE_CHARS) ?: throw IOException("对端未响应")
                val firstJson = runCatching { JSONObject(firstLine) }.getOrNull()
                if (firstJson?.optString("type") == "sync_request") {
                    val requestId = firstJson.optString("requestId")
                    if (requestId.isBlank()) throw IOException("同步请求无效")
                    val request = PendingSyncRequest(
                        id = requestId,
                        deviceName = firstJson.optString("deviceName", peerName),
                        hostAddress = s.inetAddress.hostAddress ?: "unknown"
                    )
                    val accepted = awaitIncomingApproval(request)
                    writer.write(
                        JSONObject()
                            .put("type", "sync_decision")
                            .put("requestId", requestId)
                            .put("accepted", accepted)
                            .toString()
                    )
                    writer.newLine()
                    writer.flush()
                    if (!accepted) throw IOException("对方拒绝同步请求")
                    authMode = SyncMode.APPROVAL
                } else {
                    // 同一版本仍支持手动配对码；这里的首行就是对端的加密认证消息。
                    authMode = SyncMode.PAIRING_CODE
                    firstPeerAuth = firstLine
                }
            } else {
                authMode = mode
                if (mode == SyncMode.APPROVAL) {
                    val requestId = UUID.randomUUID().toString()
                    writer.write(
                        JSONObject()
                            .put("type", "sync_request")
                            .put("requestId", requestId)
                            .put("deviceName", localName)
                            .toString()
                    )
                    writer.newLine()
                    writer.flush()
                    val decision = JSONObject(
                        readLimitedLine(reader, MAX_CONTROL_LINE_CHARS) ?: throw IOException("等待对方确认超时")
                    )
                    if (decision.optString("type") != "sync_decision" ||
                        decision.optString("requestId") != requestId
                    ) {
                        throw IOException("对方返回的同步确认无效")
                    }
                    if (!decision.optBoolean("accepted", false)) {
                        throw IOException("对方拒绝同步请求")
                    }
                }
            }

            s.soTimeout = IO_TIMEOUT_MS
            val sessionKey = deriveSessionKey(localNonce, peerNonce, peerId, authMode)

            // 3. 双向认证。点击在线设备同意后使用临时会话密钥，不再要求输入配对码。
            val auth = JSONObject()
                .put("type", "auth")
                .put("from", deviceId)
                .put("to", peerId)
                .put("mode", authMode.name)
                .toString()
            writer.write(encryptEnvelope(auth, sessionKey, AUTH_AAD))
            writer.newLine()
            writer.flush()

            val peerAuthRaw = firstPeerAuth ?: readLimitedLine(reader, MAX_CONTROL_LINE_CHARS) ?: throw IOException("认证超时")
            val peerAuth = JSONObject(decryptEnvelope(peerAuthRaw, sessionKey, AUTH_AAD))
            if (peerAuth.optString("from") != peerId || peerAuth.optString("to") != deviceId) {
                throw IOException("配对码错误或认证失败")
            }
            if (peerAuth.optString("mode").isNotBlank() && peerAuth.optString("mode") != authMode.name) {
                throw IOException("双方同步方式不一致，请重新发起同步")
            }

            // 4. 分类用于映射，账单仅发送该设备尚未确认过的变化。
            val snapshot = categoryRepository.getSyncSnapshot()
            val previouslySent = loadSentRecordVersions(peerId)
            val currentVersions = snapshot.records.associate { it.syncId to recordVersion(it) }
            val changedRecords = snapshot.records.filter { record ->
                previouslySent[record.syncId] != currentVersions[record.syncId]
            }
            val payload = JsonBackupUtils.exportEntitiesToJsonString(snapshot.categories, changedRecords)
            if (payload.toByteArray(Charsets.UTF_8).size > MAX_PLAIN_PAYLOAD_BYTES) {
                throw IOException("同步数据超过 ${MAX_PLAIN_PAYLOAD_BYTES / 1024 / 1024}MB 限制，请先导出备份并清理历史数据")
            }
            writer.write(encryptEnvelope(payload, sessionKey, DATA_AAD))
            writer.newLine()
            writer.flush()

            // 5. 解密并合并对端数据
            val dataRaw = readLimitedLine(reader, MAX_ENVELOPE_LINE_CHARS) ?: throw IOException("接收数据超时")
            val dataJson = decryptEnvelope(dataRaw, sessionKey, DATA_AAD)
            val backupData = JsonBackupUtils.parseFromJsonString(dataJson, legacySourceId = peerId)

            // 分类与账单主键只在各自设备内有效。先匹配本地分类，再将远端账单
            // 映射到本地分类主键后一起写入，避免重复分类和错误关联。
            val mergeResult = categoryRepository.mergeSyncedData(
                backupData.categories,
                backupData.records
            )

            // 6. 双方都完成数据库事务后再推进增量游标，失败会在下次重传。
            val ack = JSONObject()
                .put("type", "ack")
                .put("to", peerId)
                .put("skippedRecords", mergeResult.skippedRecordCount)
                .toString()
            writer.write(encryptEnvelope(ack, sessionKey, ACK_AAD))
            writer.newLine()
            writer.flush()
            val peerAckRaw = readLimitedLine(reader, MAX_CONTROL_LINE_CHARS)
                ?: throw IOException("对端未确认写入结果")
            val peerAck = JSONObject(decryptEnvelope(peerAckRaw, sessionKey, ACK_AAD))
            if (peerAck.optString("type") != "ack" || peerAck.optString("to") != deviceId) {
                throw IOException("对端写入确认无效")
            }
            if (peerAck.optInt("skippedRecords", 0) > 0) {
                throw IOException("对端有 ${peerAck.optInt("skippedRecords")} 笔账单未能匹配分类，将在下次同步重试")
            }
            saveSentRecordVersions(peerId, currentVersions)
            if (mergeResult.changedRecordCount > 0 || mergeResult.changedCategoryCount > 0) {
                WidgetUpdateManager.requestUpdate(appContext)
            }

            if (mergeResult.skippedRecordCount > 0) {
                _status.value = "同步完成，但有 ${mergeResult.skippedRecordCount} 笔账单因分类缺失被跳过"
            }

            Triple(
                peerId.take(6),
                mergeResult.changedRecordCount,
                mergeResult.changedCategoryCount
            )
            }
        } finally {
            activeSockets.remove(socket)
        }
    }

    private suspend fun awaitIncomingApproval(request: PendingSyncRequest): Boolean {
        val decision = CompletableDeferred<Boolean>()
        approvalDecisions[request.id] = decision
        _pendingRequests.value = _pendingRequests.value + request
        return try {
            withTimeoutOrNull(REQUEST_TIMEOUT_MS.toLong()) { decision.await() } ?: false
        } finally {
            approvalDecisions.remove(request.id)
            _pendingRequests.value = _pendingRequests.value.filterNot { it.id == request.id }
        }
    }

    private fun markConnected(name: String, connected: Boolean) {
        _devices.value = _devices.value.map {
            if (it.name == name) it.copy(connected = connected) else it
        }
    }

    private fun removeDevice(name: String) {
        _devices.value = _devices.value.filterNot { it.name == name }
        _status.value = if (_devices.value.isEmpty()) {
            "正在搜索同一局域网下的设备..."
        } else {
            "发现 ${_devices.value.size} 台设备在线"
        }
    }

    private fun deriveSessionKey(
        localNonce: ByteArray,
        peerNonce: ByteArray,
        peerId: String,
        mode: SyncMode
    ): SecretKey {
        val first = Base64.encodeToString(localNonce, Base64.NO_WRAP)
        val second = Base64.encodeToString(peerNonce, Base64.NO_WRAP)
        val ordered = if (first <= second) "$first|$second" else "$second|$first"
        val orderedIds = if (deviceId <= peerId) "$deviceId|$peerId" else "$peerId|$deviceId"
        val salt = MessageDigest.getInstance("SHA-256")
            .digest("yuanman-sync-v3|$ordered|$orderedIds".toByteArray(Charsets.UTF_8))
        val secret = when (mode) {
            SyncMode.APPROVAL -> "approved-session"
            SyncMode.PAIRING_CODE -> _pairingCode.value
        }
        val spec = PBEKeySpec(
            secret.toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            KEY_BITS
        )
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
            SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun encryptEnvelope(plainText: String, key: SecretKey, aad: String): String {
        val iv = ByteArray(GCM_IV_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .toString()
    }

    private fun decryptEnvelope(envelope: String, key: SecretKey, aad: String): String {
        val json = JSONObject(envelope)
        val iv = Base64.decode(json.getString("iv"), Base64.NO_WRAP)
        val data = Base64.decode(json.getString("data"), Base64.NO_WRAP)
        if (iv.size != GCM_IV_BYTES) throw IOException("随机数无效")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }

    private fun readLimitedLine(reader: Reader, maxChars: Int): String? {
        val result = StringBuilder(minOf(maxChars, 8 * 1024))
        while (true) {
            val value = reader.read()
            if (value == -1) return result.takeIf { it.isNotEmpty() }?.toString()
            if (value == '\n'.code) return result.toString()
            if (value != '\r'.code) {
                if (result.length >= maxChars) throw IOException("同步消息超过安全大小限制")
                result.append(value.toChar())
            }
        }
    }

    private fun recordVersion(record: RecordEntity): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(record.toString().toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    private fun loadSentRecordVersions(peerId: String): Map<String, String> = runCatching {
        val raw = prefs.getString(KEY_SENT_VERSIONS_PREFIX + peerId, null) ?: return emptyMap()
        val json = JSONObject(raw)
        buildMap {
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, json.optString(key))
            }
        }
    }.getOrDefault(emptyMap())

    private fun saveSentRecordVersions(peerId: String, versions: Map<String, String>) {
        val json = JSONObject()
        versions.forEach { (syncId, version) -> json.put(syncId, version) }
        prefs.edit().putString(KEY_SENT_VERSIONS_PREFIX + peerId, json.toString()).apply()
    }

    private fun loadOrCreatePairingCode(): String {
        val saved = prefs.getString(KEY_PAIRING_CODE, null)
        if (saved != null && saved.matches(Regex("\\d{6}"))) return saved
        val code = randomCode()
        prefs.edit().putString(KEY_PAIRING_CODE, code).apply()
        return code
    }

    private fun randomCode(): String {
        val num = SecureRandom().nextInt(1_000_000)
        return String.format(Locale.US, "%06d", num)
    }

    companion object {
        private const val PREFS_NAME = "yuanman_sync"
        private const val KEY_PAIRING_CODE = "pairing_code"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_SENT_VERSIONS_PREFIX = "sent_versions_"
        private const val SERVICE_TYPE = "_yuanman_sync._tcp."
        private const val SERVICE_NAME_PREFIX = "YM-"
        private const val PROTOCOL_VERSION = 3
        private const val SYNC_FORMAT_VERSION = 2
        private const val NONCE_BYTES = 16
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val PBKDF2_ITERATIONS = 120_000
        private const val KEY_BITS = 256
        private const val AUTH_AAD = "yuanman-auth-v2"
        private const val DATA_AAD = "yuanman-data-v2"
        private const val ACK_AAD = "yuanman-ack-v2"
        private const val CONNECT_TIMEOUT_MS = 6000
        private const val IO_TIMEOUT_MS = 12000
        private const val AUTH_FAILURE_BACKOFF_MS = 5_000L
        private const val REQUEST_TIMEOUT_MS = 120_000
        private const val MAX_CONTROL_LINE_CHARS = 16 * 1024
        private const val MAX_PLAIN_PAYLOAD_BYTES = 16 * 1024 * 1024
        private const val MAX_ENVELOPE_LINE_CHARS = 24 * 1024 * 1024
    }
}
