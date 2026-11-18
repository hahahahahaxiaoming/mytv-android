package top.yogiczy.mytv.kitkat

import android.os.Build
import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** API 19 supports TLS 1.2 but leaves it disabled on newly created sockets. */
internal class KitkatTls(private val delegate: SSLSocketFactory) : SSLSocketFactory() {
    private fun configure(socket: Socket): Socket {
        if (socket is SSLSocket && "TLSv1.2" in socket.supportedProtocols) {
            socket.enabledProtocols = (socket.enabledProtocols.toList() + "TLSv1.2").distinct().toTypedArray()
        }
        return socket
    }

    override fun getDefaultCipherSuites() = delegate.defaultCipherSuites
    override fun getSupportedCipherSuites() = delegate.supportedCipherSuites
    override fun createSocket() = configure(delegate.createSocket())
    override fun createSocket(s: Socket, host: String, port: Int, close: Boolean) =
        configure(delegate.createSocket(s, host, port, close))
    override fun createSocket(host: String, port: Int) = configure(delegate.createSocket(host, port))
    override fun createSocket(host: InetAddress, port: Int) = configure(delegate.createSocket(host, port))
    override fun createSocket(host: String, port: Int, local: InetAddress, localPort: Int) =
        configure(delegate.createSocket(host, port, local, localPort))
    override fun createSocket(host: InetAddress, port: Int, local: InetAddress, localPort: Int) =
        configure(delegate.createSocket(host, port, local, localPort))

    companion object {
        fun install() {
            val factory = HttpsURLConnection.getDefaultSSLSocketFactory()
            if (Build.VERSION.SDK_INT == 19 && factory !is KitkatTls) {
                HttpsURLConnection.setDefaultSSLSocketFactory(KitkatTls(factory))
            }
        }
    }
}
