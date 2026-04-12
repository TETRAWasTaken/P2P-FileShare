package com.kolhey.p2p.quic;

import com.kolhey.p2p.crypto.QuicSecurityManager;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicClientCodecBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamType;

import java.net.InetSocketAddress;

public class QuicClientNode {

    private NioEventLoopGroup group;
    private Channel clientChannel;
    private Channel datagramChannel;

    public void connectAndSend(String targetIp, int targetPort) throws Exception {
        stop();

        group = new NioEventLoopGroup(1);
        boolean connected = false;

        try {
            ChannelHandler codec = new QuicClientCodecBuilder()
                    .sslContext(QuicSecurityManager.buildClientSslContext())
                    .maxIdleTimeout(5000, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .initialMaxData(10000000)
                    .initialMaxStreamDataBidirectionalLocal(1000000)
                    .build();

            Bootstrap b = new Bootstrap();
            Channel channel = b.group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(codec)
                    .bind(0)
                    .sync()
                    .channel();
            this.datagramChannel = channel;

            System.out.println("Attempting QUIC connection to " + targetIp + ":" + targetPort + "...");

            QuicChannel quicChannel = QuicChannel.newBootstrap(channel)
                    .streamHandler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) {
                            // This handles streams initiated BY the server (rare in our workflow)
                        }
                    })
                    .remoteAddress(new InetSocketAddress(targetIp, targetPort))
                    .connect()
                    .get();

            System.out.println("Connected securely to peer!");

            QuicStreamChannel streamChannel = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) {
                            System.out.println("Stream opened. Ready to send data.");
                            // Inject our custom file handler here
                            ctx.pipeline().addLast(new FileTransferStreamHandler(true));
                        }
                    }).sync().get();

            this.clientChannel = streamChannel;
            connected = true;
        } finally {
            if (!connected) {
                stop();
            }
        }
    }

    public void stop() {
        if (clientChannel != null) {
            clientChannel.close();
            clientChannel = null;
        }
        if (datagramChannel != null) {
            datagramChannel.close();
            datagramChannel = null;
        }
        if (group != null) {
            group.shutdownGracefully();
            group = null;
        }
    }
}