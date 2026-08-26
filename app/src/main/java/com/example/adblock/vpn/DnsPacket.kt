package com.example.adblock.vpn

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal data class UdpDnsPacket(val sourceAddress: ByteArray, val sourcePort: Int, val dns: ByteArray)

/** Narrow packet codec for the only traffic routed into this first-version VPN: IPv4 UDP DNS. */
internal object DnsPacket {
    fun parseIpv4Udp(bytes: ByteArray, count: Int): UdpDnsPacket? {
        if (count < 28 || bytes[0].toInt().ushr(4) != 4) return null
        val header = (bytes[0].toInt() and 15) * 4
        if (header < 20 || count < header + 8 || bytes[9].toInt() != 17) return null
        val udpLength = u16(bytes, header + 4)
        if (udpLength < 8 || header + udpLength > count) return null
        return UdpDnsPacket(bytes.copyOfRange(12, 16), u16(bytes, header), bytes.copyOfRange(header + 8, header + udpLength))
    }

    fun questionDomain(dns: ByteArray): String? {
        if (dns.size < 17 || u16(dns, 4) != 1) return null
        var index = 12
        val labels = mutableListOf<String>()
        while (index < dns.size) {
            val length = dns[index].toInt() and 0xff
            if (length == 0) { index++; break }
            if (length > 63 || index + length >= dns.size) return null
            labels += dns.copyOfRange(index + 1, index + 1 + length).toString(Charsets.US_ASCII)
            index += length + 1
        }
        if (index + 4 > dns.size || labels.isEmpty()) return null
        return labels.joinToString(".")
    }

    fun nxdomain(request: ByteArray): ByteArray? {
        if (request.size < 17) return null
        var end = 12
        while (end < request.size && (request[end].toInt() and 0xff) != 0) end += (request[end].toInt() and 0xff) + 1
        if (end + 5 > request.size) return null
        end++ // terminating label
        val response = ByteArray(end + 4)
        request.copyInto(response, 0, 0, end + 4)
        response[2] = (response[2].toInt() or 0x81).toByte() // response + recursion available
        response[3] = 0x83.toByte() // recursion desired + NXDOMAIN
        response[6] = 0; response[7] = 0; response[8] = 0; response[9] = 0; response[10] = 0; response[11] = 0
        return response
    }

    fun ipv4UdpResponse(destination: ByteArray, destinationPort: Int, dns: ByteArray): ByteArray {
        val length = 20 + 8 + dns.size
        val packet = ByteBuffer.allocate(length).order(ByteOrder.BIG_ENDIAN)
        packet.put(0x45); packet.put(0); packet.putShort(length.toShort()); packet.putInt(0); packet.put(64); packet.put(17); packet.putShort(0)
        packet.put(byteArrayOf(10, 67, 0, 2)); packet.put(destination); packet.putShort(53); packet.putShort(destinationPort.toShort()); packet.putShort((8 + dns.size).toShort()); packet.putShort(0); packet.put(dns)
        val result = packet.array()
        val checksum = checksum(result, 0, 20)
        result[10] = (checksum ushr 8).toByte(); result[11] = checksum.toByte()
        return result
    }
    private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 255) shl 8) or (b[i + 1].toInt() and 255)
    private fun checksum(b: ByteArray, offset: Int, length: Int): Int { var sum = 0; var i = offset; while (i < offset + length) { sum += u16(b, i); i += 2 }; while (sum ushr 16 != 0) sum = (sum and 0xffff) + (sum ushr 16); return sum.inv() and 0xffff }
}
