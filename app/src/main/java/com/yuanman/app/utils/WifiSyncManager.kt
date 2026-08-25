package com.yuanman.app.utils

import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.local.entity.RecordWithCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.*
import java.util.Enumeration

object WifiSyncManager {

    const val DEFAULT_PORT = 8999
    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    /**
     * 获取本机在当前 WiFi / 局域网下的 IPv4 地址
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue

                val addresses: Enumeration<InetAddress> = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress ?: continue
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * 启动局域网同步服务端（等待接收端拉取数据）
     */
    suspend fun startServer(
        categories: List<CategoryEntity>,
        records: List<RecordWithCategory>,
        port: Int = DEFAULT_PORT,
        onClientConnected: (String) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            stopServer()
            serverSocket = ServerSocket(port).apply {
                reuseAddress = true
            }
            isRunning = true

            val jsonPayload = JsonBackupUtils.exportToJsonString(categories, records)
            val jsonBytes = jsonPayload.toByteArray(Charsets.UTF_8)

            Thread {
                while (isRunning && serverSocket != null && !serverSocket!!.isClosed) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        val clientIp = client.inetAddress.hostAddress ?: "未知设备"
                        onClientConnected(clientIp)

                        val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                        val line = reader.readLine()

                        // 发送标准 HTTP 200 响应
                        val out: OutputStream = client.getOutputStream()
                        val httpResponse = (
                                "HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: application/json; charset=utf-8\r\n" +
                                        "Content-Length: ${jsonBytes.size}\r\n" +
                                        "Access-Control-Allow-Origin: *\r\n" +
                                        "Connection: close\r\n\r\n"
                                ).toByteArray(Charsets.UTF_8)

                        out.write(httpResponse)
                        out.write(jsonBytes)
                        out.flush()
                        client.close()
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            }.start()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 关闭局域网同步服务端
     */
    fun stopServer() {
        isRunning = false
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 客户端：连接到目标设备并拉取同步数据
     */
    suspend fun pullDataFromPeer(
        targetIp: String,
        port: Int = DEFAULT_PORT
    ): Result<JsonBackupUtils.BackupData> = withContext(Dispatchers.IO) {
        try {
            val cleanIp = targetIp.trim().removePrefix("http://").removePrefix("https://").substringBefore(":")
            val url = URL("http://$cleanIp:$port/")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 15000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                val backupData = JsonBackupUtils.parseFromJsonString(jsonString)
                Result.success(backupData)
            } else {
                Result.failure(Exception("连接失败，服务器返回码：$responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("同步失败：无法连接至目标设备，请确保两台手机处于同一 WiFi 下且 IP 地址输入正确"))
        }
    }
}
