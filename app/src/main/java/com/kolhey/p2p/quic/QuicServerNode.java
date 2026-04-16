package com.kolhey.p2p.quic;

import com.kolhey.p2p.TransferTracker;
import com.kolhey.p2p.crypto.QuicSecurityManager;
import com.kolhey.p2p.database.PeerDatabase;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.incubator.codec.quic.InsecureQuicTokenHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicServerCodecBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;

import java.net.InetSocketAddress;

public class QuicServerNode {

    private static final boolean ALLOW_INSECURE_DEV_TLS = Boolean.getBoolean("p2p.allowInsecureDevTls");

    private final String bindIp;
    private final int bindPort;
    private final PeerDatabase peerDatabase;
    private final TransferTracker tracker;
    private Channel serverChannel;
    private NioEventLoopGroup group;

    /** Constructor without tracker (for backward-compatibility / tests). */
    public QuicServerNode(String bindIp, int bindPort, PeerDatabase peerDatabase) {
        this(bindIp, bindPort, peerDatabase, null);
    }

    /** Constructor with tracker for transfer notifications. */
    public QuicServerNode(String bindIp, int bindPort, PeerDatabase peerDatabase,
                          TransferTracker tracker) {
        this.bindIp     = bindIp;
        this.bindPort   = bindPort;
        this.peerDatabase = peerDatabase;
        this.tracker    = tracker;
    }

    public void start()
    throws Exception {
        group = new NioEventLoopGroup(1);

        QuicServerCodecBuilder serverCodecBuilder = new QuicServerCodecBuilder()
            .sslContext(QuicSecurityManager.buildServerSslContext(peerDatabase))
            .maxIdleTimeout(5000, java.util.concurrent.TimeUnit.MILLISECONDS)
            .initialMaxData(10000000)
            .initialMaxStreamDataBidirectionalLocal(1000000)
            .initialMaxStreamDataBidirectionalRemote(1000000)
            .initialMaxStreamDataUnidirectional(1000000)
            .initialMaxStreamsBidirectional(100);

        if (ALLOW_INSECURE_DEV_TLS) {
            serverCodecBuilder.tokenHandler(InsecureQuicTokenHandler.INSTANCE);
        }

        io.netty.channel.ChannelHandler codec = serverCodecBuilder
            .handler(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) {
                    QuicChannel channel = (QuicChannel) ctx.channel();
                    System.out.println("[QUIC] New connection from " + channel.remoteAddress());
                }
            })
            .streamHandler(new ChannelInitializer<QuicStreamChannel>() {
                @Override
                protected void initChannel(QuicStreamChannel ch) {
                    ch.pipeline().addLast(new FileTransferStreamHandler(false, tracker));
                }
            })
            .build();

        Bootstrap b = new Bootstrap();
        b.group(group)
            .channel(NioDatagramChannel.class)
            .handler(codec)
            .bind(new InetSocketAddress(bindIp, bindPort))
            .addListener(future -> {
                if (future.isSuccess()) {
                    serverChannel = ((io.netty.channel.ChannelFuture) future).channel();
                    System.out.println("[QUIC] Server started on " + serverChannel.localAddress());
                } else {
                    System.err.println("[QUIC] Failed to start server: " + future.cause());
                }
            });
    }

    public void stop() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (group != null) {
            group.shutdownGracefully();
        }
    }
}
