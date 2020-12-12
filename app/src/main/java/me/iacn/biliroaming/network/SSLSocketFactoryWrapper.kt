package me.iacn.biliroaming.network

import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory


class SSLSocketFactoryWrapper(private val wrappedFactory: SSLSocketFactory, private val ip: String? , private val portoverride: String?) : SSLSocketFactory() {
	override fun createSocket(host: String, port: Int): Socket {
		return wrappedFactory.createSocket(host, port) as SSLSocket
	}

	override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket {
		return wrappedFactory.createSocket(host, port, localHost, localPort) as SSLSocket
	}

	override fun createSocket(host: InetAddress, port: Int): Socket {
		return wrappedFactory.createSocket(host, port) as SSLSocket
	}

	override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket {
		return wrappedFactory.createSocket(address, port, localAddress, localPort) as SSLSocket
	}

	override fun createSocket(): Socket {
		return wrappedFactory.createSocket() as SSLSocket
	}

	override fun getDefaultCipherSuites(): Array<String> {
		return wrappedFactory.defaultCipherSuites
	}

	override fun getSupportedCipherSuites(): Array<String> {
		return wrappedFactory.supportedCipherSuites
	}

	override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket {
		val mysocket:Socket = if (ip == null) {
			Socket(host,portoverride?.toIntOrNull()?:port)
		} else {
			Socket(InetAddress.getByAddress(host, InetAddress.getByName(ip).address),portoverride?.toIntOrNull()?:port)
		}
		return wrappedFactory.createSocket(mysocket, host, port, autoClose) as SSLSocket
	}
}