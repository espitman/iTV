package app.itv.prototype.admin

import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket

object LanAddresses {
    const val PREFERRED_PORT = 8196

    fun lanIpv4(): String? {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        val found = interfaces.toList().flatMap { nif ->
            if (!nif.isUp || nif.isLoopback) emptyList()
            else nif.inetAddresses.toList()
        }.filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }
            .mapNotNull { it.hostAddress }
        return found.firstOrNull { it.startsWith("192.168.") }
            ?: found.firstOrNull { it.startsWith("10.") }
            ?: found.firstOrNull()
    }

    fun pickPort(): Int {
        if (isFree(PREFERRED_PORT)) return PREFERRED_PORT
        ServerSocket(0).use { return it.localPort }
    }

    private fun isFree(port: Int): Boolean = runCatching {
        ServerSocket(port).use { true }
    }.getOrDefault(false)
}
