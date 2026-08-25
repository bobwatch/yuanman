package com.yuanman.app.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Base64
import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.local.entity.RecordEntity
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.data.repository.CategoryRepository
import com.yuanman.app.data.repository.RecordRepository
import com.yuanman.app.utils.JsonBackupUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
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

class FamilySyncManager(
    context: Context,
    private val recordRepository: RecordRepository,
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

    private val _events = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<SyncEvent> = _events.asSharedFlow()

    private val _lastEvent = MutableStateFlow<SyncEvent?>(null)
    val lastEvent: StateFlow<SyncEvent?> = _lastEvent.asStateFlow()

    private val lastSyncAt = mutableMapOf<String, Long>()
    private val authFailures = mutableMapOf<String, Long>()

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
        multicastLock?.let { lock -> runCatching { if (lock.isHeld) lock.release() } }
        multicastLock = null
        _devices.value = emptyList()
        _status.value = "已停止同步"
    }

    fun close() {
        stop()
        resolveExecutor.shutdownNow()
    }

    fun syncNow() {
        val peers = _devices.value
        if (peers.isEmpty()) {
            _status.value = "未发现同一 WiFi 下的其他设备"
            return
        }
        _status.value = "正在同步数据..."
        _syncing.value = true
        val jobs = peers.map { connectAndSync(it) }
        scope.launch {
            jobs.forEach { it.join() }
            _syncing.value = false
        }
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
        val name = info.serviceName ?: return
        val host = info.host ?: return
        if (name == ownServiceName) return
        val device = PeerDevice(name = name, host = host, port = info.port, connected = false)
        _devices.value = _devices.value.filterNot { it.name == name } + device
        _status.value = "发现 ${_devices.value.size} 台设备在线"
        if (shouldAutoSync(name)) connectAndSync(device)
    }

    private fun connectAndSync(device: PeerDevice): Job {
        if (!running) return scope.launch { }
        return scope.launch(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(device.host, device.port), CONNECT_TIMEOUT_MS)
                val (peerName, recordCount, categoryCount) = exchange(socket)
                markConnected(device.name, true)
                _status.value = "已成功同步设备 $peerName"
                val event = SyncEvent(peerName, recordCount, categoryCount)
                _lastEvent.value = event
                _events.emit(event)
            } catch (e: Exception) {
                // 配对码不一致或异常
            }
        }
    }

    private fun acceptLoop(server: ServerSocket) {
        while (running) {
            try {
                val socket = server.accept()
                scope.launch(Dispatchers.IO) {
                    try {
                        val (peerName, recordCount, categoryCount) = exchange(socket)
                        val event = SyncEvent(peerName, recordCount, categoryCount)
                        _lastEvent.value = event
                        _events.emit(event)
                    } catch (e: Exception) {
                        // 忽略
                    }
                }
            } catch (e: Exception) {
                if (!running) break
            }
        }
    }

    private suspend fun exchange(socket: Socket): Triple<String, Int, Int> {
        return socket.use { s ->
            s.soTimeout = IO_TIMEOUT_MS
            val writer = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))

            // 1. 握手交换
            val localNonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
            val hello = JSONObject()
                .put("protocol", PROTOCOL_VERSION)
                .put("deviceId", deviceId)
                .put("nonce", Base64.encodeToString(localNonce, Base64.NO_WRAP))
            writer.write(hello.toString())
            writer.newLine()
            writer.flush()

            val peerLine = reader.readLine() ?: throw IOException("对端未响应")
            val peerHello = JSONObject(peerLine)
            val peerId = peerHello.optString("deviceId", "unknown")
            if (peerHello.optInt("protocol", 0) != PROTOCOL_VERSION || peerId == deviceId) {
                throw IOException("协议不匹配")
            }
            val peerNonce = Base64.decode(peerHello.getString("nonce"), Base64.NO_WRAP)
            val sessionKey = deriveSessionKey(localNonce, peerNonce)

            // 2. 双向认证
            val auth = JSONObject().put("type", "auth").put("from", deviceId).put("to", peerId).toString()
            writer.write(encryptEnvelope(auth, sessionKey, AUTH_AAD))
            writer.newLine()
            writer.flush()

            val peerAuthRaw = reader.readLine() ?: throw IOException("认证超时")
            val peerAuth = JSONObject(decryptEnvelope(peerAuthRaw, sessionKey, AUTH_AAD))
            if (peerAuth.optString("from") != peerId || peerAuth.optString("to") != deviceId) {
                throw IOException("配对码错误或认证失败")
            }

            // 3. 加密发送本机全部数据
            val allCats = categoryRepository.getAllCategories().first()
            val allRecs = recordRepository.getAllRecords().first()
            val payload = JsonBackupUtils.exportToJsonString(allCats, allRecs)
            writer.write(encryptEnvelope(payload, sessionKey, DATA_AAD))
            writer.newLine()
            writer.flush()

            // 4. 解密并合并对端数据
            val dataRaw = reader.readLine() ?: throw IOException("接收数据超时")
            val dataJson = decryptEnvelope(dataRaw, sessionKey, DATA_AAD)
            val backupData = JsonBackupUtils.parseFromJsonString(dataJson)

            // 合并分类与账单
            if (backupData.categories.isNotEmpty()) {
                categoryRepository.insertCategories(backupData.categories)
            }
            if (backupData.records.isNotEmpty()) {
                recordRepository.insertRecords(backupData.records)
            }

            Triple(peerId.take(6), backupData.records.size, backupData.categories.size)
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

    private fun shouldAutoSync(name: String): Boolean = synchronized(lastSyncAt) {
        val now = System.currentTimeMillis()
        val last = lastSyncAt[name] ?: 0L
        if (now - last < AUTO_SYNC_INTERVAL_MS) {
            false
        } else {
            lastSyncAt[name] = now
            true
        }
    }

    private fun deriveSessionKey(localNonce: ByteArray, peerNonce: ByteArray): SecretKey {
        val first = Base64.encodeToString(localNonce, Base64.NO_WRAP)
        val second = Base64.encodeToString(peerNonce, Base64.NO_WRAP)
        val ordered = if (first <= second) "$first|$second" else "$second|$first"
        val salt = MessageDigest.getInstance("SHA-256")
            .digest("yuanman-sync-v3|$ordered".toByteArray(Charsets.UTF_8))
        val spec = PBEKeySpec(
            _pairingCode.value.toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            KEY_BITS
        )
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
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
        private const val SERVICE_TYPE = "_yuanman_sync._tcp."
        private const val SERVICE_NAME_PREFIX = "YM-"
        private const val PROTOCOL_VERSION = 3
        private const val NONCE_BYTES = 16
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val PBKDF2_ITERATIONS = 4096
        private const val KEY_BITS = 256
        private const val AUTH_AAD = "yuanman-auth-v3"
        private const val DATA_AAD = "yuanman-data-v3"
        private const val CONNECT_TIMEOUT_MS = 6000
        private const val IO_TIMEOUT_MS = 12000
        private const val AUTO_SYNC_INTERVAL_MS = 30_000L
    }
}
