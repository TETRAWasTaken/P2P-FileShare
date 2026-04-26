package com.kolhey.p2p.quic;

import com.kolhey.p2p.crypto.QuicSecurityManager;
import com.kolhey.p2p.database.PeerDatabase;
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

    private final PeerDatabase peerDatabase;
    private NioEventLoopGroup group;
    private Channel clientChannel;
    private Channel datagramChannel;

    public QuicClientNode(PeerDatabase peerDatabase) {
        this.peerDatabase = peerDatabase;
    }

    public void connectAndSend(String targetIp, int targetPort, java.io.File file) throws Exception {
        stop();

        group = new NioEventLoopGroup(1);
        boolean connected = false;

        try {
            ChannelHandler codec = new QuicClientCodecBuilder()
                    .sslContext(QuicSecurityManager.buildClientSslContext(peerDatabase))
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

            System.out.println("[QUIC Client] Attempting connection to " + targetIp + ":" + targetPort + "...");

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

            System.out.println("[QUIC Client] Connected securely to peer!");

            QuicStreamChannel streamChannel = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) throws java.io.IOException {
                            System.out.println("[QUIC Client] Stream opened. Sending file: " + file.getName());
                            FileTransferStreamHandler handler = new FileTransferStreamHandler(true);
                            ctx.pipeline().addLast(handler);
                            handler.startFileTransfer(ctx, file);
                        }
                    }).sync().get();

            this.clientChannel = streamChannel;
            connected = true;

            // Wait for transfer to complete before closing
            System.out.println("[QUIC Client] Waiting for file transfer to complete...");
            Thread.sleep(2000);
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