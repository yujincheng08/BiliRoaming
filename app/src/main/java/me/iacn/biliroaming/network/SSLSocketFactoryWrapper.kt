package me.iacn.biliroaming.network

import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.net.UnknownHostException
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory


class SSLSocketFactoryWrapper(
	private val wrappedFactory: SSLSocketFactory,
	private val sslParameters: SSLParameters,
	private val ip: String
	) :
	SSLSocketFactory() {
	@Throws(IOException::class, UnknownHostException::class)
	override fun createSocket(host: String, port: Int): Socket {
		return wrappedFactory.createSocket(host, port) as SSLSocket
	}

	@Throws(IOException::class, UnknownHostException::class)
	override fun createSocket(
		host: String,
		port: Int,
		localHost: InetAddress,
		localPort: Int
	): Socket {
		return wrappedFactory.createSocket(host, port, localHost, localPort) as SSLSocket
	}

	@Throws(IOException::class)
	override fun createSocket(host: InetAddress, port: Int): Socket {
		return wrappedFactory.createSocket(host, port) as SSLSocket
	}

	@Throws(IOException::class)
	override fun createSocket(
		address: InetAddress,
		port: Int,
		localAddress: InetAddress,
		localPort: Int
	): Socket {
		return wrappedFactory.createSocket(
			address,
			port,
			localAddress,
			localPort
		) as SSLSocket
	}

	@Throws(IOException::class)
	override fun createSocket(): Socket {
		return wrappedFactory.createSocket() as SSLSocket
	}

	override fun getDefaultCipherSuites(): Array<String> {
		return wrappedFactory.defaultCipherSuites
	}

	override fun getSupportedCipherSuites(): Array<String> {
		return wrappedFactory.supportedCipherSuites
	}

	@Throws(IOException::class)
	override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket {
		val mysocket = Socket(
			InetAddress.getByAddress(host, InetAddress.getByName(ip).address),
			port
		)
		return wrappedFactory.createSocket(mysocket, host, port, autoClose) as SSLSocket
	}
}