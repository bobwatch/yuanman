package com.yuanman.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.yuanman.R
import com.yuanman.Transaction
import com.yuanman.TransactionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** 一次同步完成的事件。 */
data class SyncEvent(val peerName: String, val mergedCount: Int)

/** 局域网内发现的家庭设备。 */
data class PeerDevice(
    val name: String,
    val host: InetAddress,
    val port: Int,
    val connected: Boolean
)

/**
 * 家庭同步：同一 Wi-Fi 下通过 NSD 自动发现彼此，TCP 按行交换 JSON，
 * 双向全量合并账本。不经过任何服务器、不连外网。
 *
 * 协议（对称，双方行为一致）：
 * 1. 连接建立后各自立即发送 hello 行：{"deviceId":..., "codeHash":...}
 *    （codeHash = SHA-256(配对码) 前 8 位十六进制，不明文传输配对码）
 * 2. 收到对方 hello 且 codeHash 一致才继续，否则断开
 * 3. 各自发送全量数据行：{"version":2,"transactions":[...]}（含墓碑）
 * 4. 各自合并对方数据并落盘
 *
 * 生命周期：App 前台 start()，退后台 stop()（不做后台常驻）。
 */
class FamilySyncManager(
    context: Context,
    private val store: TransactionStore,
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

    private val _status =
        MutableStateFlow(appContext.getString(R.string.sync_status_idle))
    val status: StateFlow<String> = _status.asStateFlow()

    /** 手动同步进行中（UI 据此禁用「立即同步」按钮并转圈）。 */
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _pairingCode = MutableStateFlow(loadOrCreatePairingCode())
    val pairingCode: StateFlow<String> = _pairingCode.asStateFlow()

    private val _events = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<SyncEvent> = _events.asSharedFlow()

    /** 每台设备的自动同步节流时间戳。 */
    private val lastSyncAt = mutableMapOf<String, Long>()

    /** 本机设备 ID（首次启动生成 UUID，持久化）。 */
    val deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null)
            ?: UUID.randomUUID().toString().also {
                prefs.edit().putString(KEY_DEVICE_ID, it).apply()
            }

    /** 重新生成配对码（旧家庭组立即失效）。 */
    fun regeneratePairingCode() {
        val code = randomCode()
        _pairingCode.value = code
        prefs.edit().putString(KEY_PAIRING_CODE, code).apply()
    }

    /** 输入配对码加入家庭组，必须是 6 位数字。 */
    fun setPairingCode(code: String): Boolean {
        if (!code.matches(Regex("\\d{6}"))) return false
        _pairingCode.value = code
        prefs.edit().putString(KEY_PAIRING_CODE, code).apply()
        return true
    }

    /** 进入前台时调用：监听端口 + NSD 注册/发现。 */
    @Synchronized
    fun start() {
        if (running) return
        running = true
        _status.value = appContext.getString(R.string.sync_status_starting)
        scope.launch(Dispatchers.IO) {
            try {
                acquireMulticastLock()
                val server = ServerSocket(0)
                serverSocket = server
                launch { acceptLoop(server) }
                registerService(server.localPort)
                startDiscovery()
                _status.value = appContext.getString(R.string.sync_status_discovering)
            } catch (e: Exception) {
                _status.value =
                    appContext.getString(R.string.sync_status_failed, e.message)
            }
        }
    }

    /** 退后台时调用：停止一切网络活动。 */
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
        _status.value = appContext.getString(R.string.sync_status_stopped)
    }

    /** 手动触发：对所有已发现设备发起一次同步（全部结束前 syncing 保持 true）。 */
    fun syncNow() {
        val peers = _devices.value
        if (peers.isEmpty()) {
            _status.value = appContext.getString(R.string.sync_status_no_peers)
            return
        }
        _status.value = appContext.getString(R.string.sync_status_syncing)
        _syncing.value = true
        val jobs = peers.map { connectAndSync(it) }
        scope.launch {
            jobs.forEach { it.join() }
            _syncing.value = false
        }
    }

    private fun acquireMulticastLock() {
        val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("yuanman_nsd").apply {
            setReferenceCounted(true)
            acquire()
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
                // 名字冲突时系统会改名，以回调里的为准
                ownServiceName = serviceInfo.serviceName ?: ""
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                _status.value =
                    appContext.getString(R.string.sync_status_register_failed, errorCode)
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
                // 排除自己
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
                _status.value =
                    appContext.getString(R.string.sync_status_discovery_failed, errorCode)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        discoveryListener = listener
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    /** NsdManager 不允许并发 resolve，用单线程串行处理。 */
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

    private fun onPeerResolved(info: NsdServiceInfo) {
        val name = info.serviceName ?: return
        val host = info.host ?: return
        if (name == ownServiceName) return
        val device = PeerDevice(name = name, host = host, port = info.port, connected = false)
        _devices.value = _devices.value.filterNot { it.name == name } + device
        _status.value = appContext.getString(
            R.string.sync_status_devices_found, _devices.value.size
        )
        if (shouldAutoSync(name)) connectAndSync(device)
    }

    private fun connectAndSync(device: PeerDevice): Job {
        if (!running) return scope.launch { }
        return scope.launch(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(device.host, device.port), CONNECT_TIMEOUT_MS)
                val (peerName, merged) = exchange(socket)
                markConnected(device.name, true)
                _status.value = appContext.getString(
                    R.string.sync_status_devices_found, _devices.value.size
                )
                _events.emit(SyncEvent(peerName, merged))
            } catch (e: Exception) {
                // 配对码不一致或对方暂时不可达，静默忽略，下轮发现再试
            }
        }
    }

    private fun acceptLoop(server: ServerSocket) {
        while (running) {
            try {
                val socket = server.accept()
                scope.launch(Dispatchers.IO) {
                    try {
                        val (peerName, merged) = exchange(socket)
                        _events.emit(SyncEvent(peerName, merged))
                    } catch (e: Exception) {
                        // 忽略异常连接
                    }
                }
            } catch (e: Exception) {
                if (!running) break
            }
        }
    }

    /**
     * 对称同步协议：互发 hello 校验配对码，再互发全量数据并各自合并。
     *
     * @return 对端标识 to 合并进来的条数
     */
    private fun exchange(socket: Socket): Pair<String, Int> {
        socket.use { s ->
            s.soTimeout = IO_TIMEOUT_MS
            val writer = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))

            // 1. 发送本机 hello
            val hello = JSONObject()
                .put("deviceId", deviceId)
                .put("codeHash", codeHash())
            writer.write(hello.toString())
            writer.newLine()
            writer.flush()

            // 2. 读取并校验对端 hello
            val peerLine = reader.readLine() ?: throw IOException("对端无响应")
            val peerHello = JSONObject(peerLine)
            val peerId = peerHello.optString("deviceId", "unknown")
            if (peerHello.optString("codeHash") != codeHash()) {
                throw IOException("配对码不一致")
            }

            // 3. 发送本机全量数据（含墓碑）
            val arr = JSONArray()
            store.allIncludingTombstones().forEach { arr.put(it.toJson()) }
            val payload = JSONObject()
                .put("version", TransactionStore.VERSION)
                .put("transactions", arr)
            writer.write(payload.toString())
            writer.newLine()
            writer.flush()

            // 4. 读取对端数据并合并落盘
            val dataLine = reader.readLine() ?: throw IOException("对端未发送数据")
            val root = JSONObject(dataLine)
            val remoteArr = root.optJSONArray("transactions") ?: JSONArray()
            val remote = mutableListOf<Transaction>()
            for (i in 0 until remoteArr.length()) {
                runCatching { remote.add(Transaction.fromJson(remoteArr.getJSONObject(i))) }
            }
            val merged = store.merge(remote)
            return peerId.take(6) to merged
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
            appContext.getString(R.string.sync_status_discovering)
        } else {
            appContext.getString(
                R.string.sync_status_devices_found, _devices.value.size
            )
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

    /** 配对码不明文传输：握手只比对 SHA-256 前 8 位十六进制。 */
    private fun codeHash(): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(_pairingCode.value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(8)
    }

    private fun loadOrCreatePairingCode(): String =
        prefs.getString(KEY_PAIRING_CODE, null) ?: randomCode().also {
            prefs.edit().putString(KEY_PAIRING_CODE, it).apply()
        }

    private fun randomCode(): String =
        String.format(Locale.US, "%06d", SecureRandom().nextInt(1_000_000))

    companion object {
        private const val PREFS_NAME = "family_sync"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_PAIRING_CODE = "pairing_code"
        private const val SERVICE_TYPE = "_yuanman._tcp."
        private const val SERVICE_NAME_PREFIX = "MH-"
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val IO_TIMEOUT_MS = 15_000
        private const val AUTO_SYNC_INTERVAL_MS = 60_000L
    }
}
