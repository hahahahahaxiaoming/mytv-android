package top.yogiczy.mytv.tv.ui.screens.videoplayer.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import top.yogiczy.mytv.tv.ui.utils.Configs
import java.io.IOException
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket

@OptIn(UnstableApi::class)
class RtpDataSource : BaseDataSource(true) {
    class Factory : DataSource.Factory {
        override fun createDataSource(): DataSource = RtpDataSource()
    }

    private val datagramBuffer = ByteArray(64 * 1024)
    private val payloadBuffer = ByteArray(64 * 1024)
    private var payloadOffset = 0
    private var payloadLength = 0
    private var socket: MulticastSocket? = null
    private var uri: Uri? = null
    private var groupAddress: InetAddress? = null

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)

        uri = dataSpec.uri
        val host = dataSpec.uri.host ?: throw IOException("RTP url missing host: ${dataSpec.uri}")
        val port = dataSpec.uri.port.takeIf { it > 0 }
            ?: throw IOException("RTP url missing port: ${dataSpec.uri}")

        val group = InetAddress.getByName(host.removePrefix("@"))
        groupAddress = group
        socket = MulticastSocket(null).apply {
            reuseAddress = true
            soTimeout = Configs.videoPlayerLoadTimeout.toInt()
            bind(InetSocketAddress(port))
            if (group.isMulticastAddress) joinGroup(group)
        }

        transferStarted(dataSpec)
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0

        while (payloadOffset >= payloadLength) {
            readNextPayload()
        }

        val bytesToRead = minOf(length, payloadLength - payloadOffset)
        System.arraycopy(payloadBuffer, payloadOffset, buffer, offset, bytesToRead)
        payloadOffset += bytesToRead
        bytesTransferred(bytesToRead)
        return bytesToRead
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        val socketToClose = socket
        val group = groupAddress
        if (socketToClose != null && group != null && group.isMulticastAddress) {
            runCatching { socketToClose.leaveGroup(group) }
        }
        socketToClose?.close()
        socket = null
        groupAddress = null
        uri = null
        payloadOffset = 0
        payloadLength = 0
        transferEnded()
    }

    private fun readNextPayload() {
        val packet = DatagramPacket(datagramBuffer, datagramBuffer.size)
        val socketToRead = socket ?: throw IOException("RTP socket is not open")
        socketToRead.receive(packet)

        val packetOffset = packet.offset
        val packetLength = packet.length
        val payloadStart = rtpPayloadOffset(datagramBuffer, packetOffset, packetLength)
        val payloadEnd = rtpPayloadEnd(datagramBuffer, packetOffset, packetLength)

        if (payloadStart >= payloadEnd) {
            payloadOffset = 0
            payloadLength = 0
            return
        }

        payloadLength = payloadEnd - payloadStart
        System.arraycopy(datagramBuffer, payloadStart, payloadBuffer, 0, payloadLength)
        payloadOffset = 0
    }

    private fun rtpPayloadOffset(data: ByteArray, offset: Int, length: Int): Int {
        if (length < RTP_FIXED_HEADER_SIZE) return offset
        if ((data[offset].toInt() and 0xFF) == TS_SYNC_BYTE) return offset
        if ((data[offset].toInt() and RTP_VERSION_MASK) != RTP_VERSION_2) return offset

        val csrcCount = data[offset].toInt() and RTP_CSRC_COUNT_MASK
        var headerLength = RTP_FIXED_HEADER_SIZE + csrcCount * RTP_CSRC_SIZE
        if (headerLength > length) return offset

        val hasExtension = (data[offset].toInt() and RTP_EXTENSION_MASK) != 0
        if (hasExtension) {
            if (headerLength + RTP_EXTENSION_HEADER_SIZE > length) return offset

            val extensionLengthWords =
                ((data[offset + headerLength + 2].toInt() and 0xFF) shl 8) or
                        (data[offset + headerLength + 3].toInt() and 0xFF)
            headerLength += RTP_EXTENSION_HEADER_SIZE + extensionLengthWords * RTP_EXTENSION_WORD_SIZE
        }

        return (offset + headerLength).takeIf { it <= offset + length } ?: offset
    }

    private fun rtpPayloadEnd(data: ByteArray, offset: Int, length: Int): Int {
        var end = offset + length
        val hasPadding = length >= RTP_FIXED_HEADER_SIZE &&
                (data[offset].toInt() and RTP_PADDING_MASK) != 0
        if (hasPadding) {
            val paddingLength = data[end - 1].toInt() and 0xFF
            if (paddingLength in 1..length) end -= paddingLength
        }
        return end
    }

    private companion object {
        const val RTP_FIXED_HEADER_SIZE = 12
        const val RTP_CSRC_SIZE = 4
        const val RTP_EXTENSION_HEADER_SIZE = 4
        const val RTP_EXTENSION_WORD_SIZE = 4
        const val RTP_VERSION_MASK = 0xC0
        const val RTP_VERSION_2 = 0x80
        const val RTP_PADDING_MASK = 0x20
        const val RTP_EXTENSION_MASK = 0x10
        const val RTP_CSRC_COUNT_MASK = 0x0F
        const val TS_SYNC_BYTE = 0x47
    }
}
