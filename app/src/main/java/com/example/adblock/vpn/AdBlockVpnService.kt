package com.example.adblock.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.adblock.MainActivity
import com.example.adblock.filtering.BlocklistManager
import com.example.adblock.filtering.FilterEngine
import com.example.adblock.statistics.StatisticsManager
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AdBlockVpnService : VpnService() {
    private val running = AtomicBoolean(false)
    private var tun: ParcelFileDescriptor? = null
    private lateinit var engine: FilterEngine
    private lateinit var statistics: StatisticsManager

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopProtection(); return Service.START_NOT_STICKY }
        if (!running.compareAndSet(false, true)) return Service.START_STICKY
        engine = BlocklistManager(this).let { FilterEngine(it.blockedDomains(), it.whitelist()) }
        statistics = StatisticsManager(this)
        startForeground(NOTIFICATION_ID, notification())
        try {
            tun = Builder().setSession("AdBlock Android DNS")
                .setMtu(1500).addAddress(VPN_ADDRESS, 32).addRoute(DNS_ADDRESS, 32).addDnsServer(DNS_ADDRESS)
                .establish()
            if (tun == null) throw IllegalStateException("Android no pudo crear la interfaz VPN")
            active.value = true
            startDnsLoop(tun!!)
        } catch (_: Exception) { stopProtection() }
        return Service.START_STICKY
    }

    private fun startDnsLoop(descriptor: ParcelFileDescriptor) = thread(name = "adblock-dns", isDaemon = true) {
        FileInputStream(descriptor.fileDescriptor).use { input -> FileOutputStream(descriptor.fileDescriptor).use { output ->
            val buffer = ByteArray(32767)
            while (running.get()) {
                val size = try { input.read(buffer) } catch (_: Exception) { break }
                if (size <= 0) continue
                val request = DnsPacket.parseIpv4Udp(buffer, size) ?: continue
                val domain = DnsPacket.questionDomain(request.dns) ?: continue
                val blocked = engine.isBlocked(domain) && blockingEnabledFor(domain)
                val answer = if (blocked) { statistics.recordBlocked(domain); DnsPacket.nxdomain(request.dns) } else { statistics.recordAllowed(); resolve(request.dns) }
                if (answer != null) output.write(DnsPacket.ipv4UdpResponse(request.sourceAddress, request.sourcePort, answer))
            }
        }}
    }

    private fun resolve(query: ByteArray): ByteArray? = try {
        DatagramSocket().use { socket ->
            protect(socket); socket.soTimeout = 3_000
            socket.send(DatagramPacket(query, query.size, InetAddress.getByName(UPSTREAM_DNS), 53))
            val reply = ByteArray(4096); val packet = DatagramPacket(reply, reply.size); socket.receive(packet)
            packet.data.copyOf(packet.length)
        }
    } catch (_: Exception) { null }

    private fun blockingEnabledFor(domain: String): Boolean {
        val settings = getSharedPreferences("settings", Context.MODE_PRIVATE)
        return if (domain.contains("track", ignoreCase = true)) settings.getBoolean("trackers", true) else settings.getBoolean("ads", true)
    }

    private fun stopProtection() { running.set(false); tun?.close(); tun = null; active.value = false; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    override fun onDestroy() { stopProtection(); super.onDestroy() }

    private fun notification() = NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(android.R.drawable.ic_lock_lock).setContentTitle("AdBlock Android").setContentText("Protección DNS activa").setOngoing(true).setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)).addAction(0, "Desactivar", PendingIntent.getService(this, 1, Intent(this, AdBlockVpnService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)).build()
    override fun onCreate() { super.onCreate(); if (Build.VERSION.SDK_INT >= 26) (getSystemService(NotificationManager::class.java)).createNotificationChannel(NotificationChannel(CHANNEL_ID, "Protección VPN", NotificationManager.IMPORTANCE_LOW)) }

    companion object { const val ACTION_START = "com.example.adblock.START"; const val ACTION_STOP = "com.example.adblock.STOP"; private const val VPN_ADDRESS = "10.67.0.1"; private const val DNS_ADDRESS = "10.67.0.2"; private const val UPSTREAM_DNS = "1.1.1.1"; private const val CHANNEL_ID = "vpn"; private const val NOTIFICATION_ID = 42; private val active = MutableStateFlow(false); val isActive = active.asStateFlow() }
}
