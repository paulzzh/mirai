package net.mamoe.mirai.network

import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.bytes.ByteArrayDecoder
import io.netty.handler.codec.bytes.ByteArrayEncoder
import net.mamoe.mirai.network.packet.client.ClientPacket
import net.mamoe.mirai.network.packet.client.login.ClientPasswordSubmissionPacket
import net.mamoe.mirai.network.packet.client.login.ClientServerRedirectionPacket
import net.mamoe.mirai.network.packet.client.writeHex
import net.mamoe.mirai.network.packet.server.ServerPacket
import net.mamoe.mirai.network.packet.server.login.ServerLoginFailedResponsePacket
import net.mamoe.mirai.network.packet.server.login.ServerLoginResendResponsePacket
import net.mamoe.mirai.network.packet.server.login.ServerLoginSucceedResponsePacket
import net.mamoe.mirai.network.packet.server.login.ServerLoginVerificationCodeResponsePacket
import net.mamoe.mirai.network.packet.server.touch.ServerTouchResponsePacket
import net.mamoe.mirai.utils.MiraiLogger
import java.net.DatagramPacket
import java.net.InetSocketAddress

/**
 * [number] is a QQ number.
 *
 * @author Him188moe @ Mirai Project
 */
class Robot(val number: Int, private val password: String) {
    private var channel: Channel? = null


    @ExperimentalUnsignedTypes
    internal fun onPacketReceived(packet: ServerPacket) {
        packet.decode()
        when (packet) {
            is ServerTouchResponsePacket -> {
                if (packet.serverIP != null) {//redirection
                    connect(packet.serverIP!!)
                    sendPacket(ClientServerRedirectionPacket(
                            serverIP = packet.serverIP!!,
                            qq = number
                    ))
                } else {//password submission
                    sendPacket(ClientPasswordSubmissionPacket(
                            qq = this.number,
                            password = this.password,
                            loginTime = packet.loginTime,
                            loginIP = packet.loginIP,
                            token0825 = packet.token,
                            tgtgtKey = packet.tgtgtKey
                    ))
                }
            }

            is ServerLoginFailedResponsePacket -> {
                channel = null
                println("Login failed: " + packet.state.toString())
            }

            is ServerLoginVerificationCodeResponsePacket -> {

            }

            is ServerLoginSucceedResponsePacket -> {

            }

            is ServerLoginResendResponsePacket -> {

            }

            else -> throw IllegalStateException()
        }

    }

    @ExperimentalUnsignedTypes
    private fun sendPacket(packet: ClientPacket) {
        packet.encode()
        packet.writeHex(Protocol.tail);
        channel!!.writeAndFlush(DatagramPacket(packet.toByteArray()))
    }

    companion object {
        private fun DatagramPacket(toByteArray: ByteArray): DatagramPacket = DatagramPacket(toByteArray, toByteArray.size)
    }

    @ExperimentalUnsignedTypes
    @Throws(InterruptedException::class)
    fun connect(host: String, port: Int = 8000) {
        val group = NioEventLoopGroup()
        try {
            val b = Bootstrap()

            b.group(group)
                    .channel(NioSocketChannel::class.java)
                    .remoteAddress(InetSocketAddress(host, port))
                    .handler(object : ChannelInitializer<SocketChannel>() {
                        @Throws(Exception::class)
                        override fun initChannel(ch: SocketChannel) {
                            println("connected server...")
                            ch.pipeline().addLast(ByteArrayEncoder())
                            ch.pipeline().addLast(ByteArrayDecoder())
                            ch.pipeline().addLast(object : SimpleChannelInboundHandler<ByteArray>() {
                                override fun channelRead0(ctx: ChannelHandlerContext, bytes: ByteArray) {
                                    try {
                                        /*val remaining = Reader.read(bytes);
                                        if (Reader.isPacketAvailable()) {
                                            robot.onPacketReceived(Reader.toServerPacket())
                                            Reader.init()
                                            remaining
                                        }*/
                                        this@Robot.onPacketReceived(ServerPacket.ofByteArray(bytes))
                                    } catch (e: Exception) {
                                        MiraiLogger.catching(e)
                                    }
                                }

                                override fun channelActive(ctx: ChannelHandlerContext) {
                                    println("Successfully connected to server")
                                }

                                override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
                                    MiraiLogger.catching(cause)
                                }
                            })
                        }
                    })

            channel = b.connect().sync().channel();
            channel!!.closeFuture().sync()
        } finally {
            group.shutdownGracefully().sync()
        }
    }

    private object Reader {
        private var length: Int? = null
        private lateinit var bytes: ByteArray

        fun init(bytes: ByteArray) {
            this.length = bytes.size
            this.bytes = bytes
        }

        /**
         * Reads bytes, combining them to create a packet, returning remaining bytes.
         */
        fun read(bytes: ByteArray): ByteArray? {
            checkNotNull(this.length)
            val needSize = length!! - this.bytes.size//How many bytes we need
            if (needSize == bytes.size || needSize > bytes.size) {
                this.bytes += bytes
                return null
            }

            //We got more than we need
            this.bytes += bytes.copyOfRange(0, needSize)
            return bytes.copyOfRange(needSize, bytes.size - needSize)//We got remaining bytes, that is of another packet
        }

        fun isPacketAvailable() = this.length == this.bytes.size

        fun toServerPacket(): ServerPacket {
            return ServerPacket.ofByteArray(this.bytes)
        }
    }
}