package com.kolhey.p2p.ws;

import com.kolhey.p2p.TransferTracker;
import com.kolhey.p2p.crypto.WsSecurityManager;
import com.kolhey.p2p.database.PeerDatabase;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.ssl.SslContext;

public class WsServerNode {

    private final String bindIp;
    private final int bindPort;
    private final PeerDatabase peerDatabase;
    private final TransferTracker tracker;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    /** Constructor without tracker (for backward-compatibility / tests). */
    public WsServerNode(String bindIp, int bindPort, PeerDatabase peerDatabase) {
        this(bindIp, bindPort, peerDatabase, null);
    }

    /** Constructor with tracker for transfer notifications. */
    public WsServerNode(String bindIp, int bindPort, PeerDatabase peerDatabase,
                        TransferTracker tracker) {
        this.bindIp     = bindIp;
        this.bindPort   = bindPort;
        this.peerDatabase = peerDatabase;
        this.tracker    = tracker;
    }

    public void start()
    throws Exception {
        bossGroup  = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();

        final SslContext sslContext = WsSecurityManager.buildServerSslContext(peerDatabase);

        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) throws Exception {
                    ch.pipeline().addLast(sslContext.newHandler(ch.alloc()));
                    ch.pipeline().addLast(new HttpServerCodec());
                    ch.pipeline().addLast(new HttpObjectAggregator(65536));
                    ch.pipeline().addLast(new WebSocketServerProtocolHandler("/p2p"));
                    ch.pipeline().addLast(new WsFileTransferHandler(false, null, tracker));
                }
            });

        serverChannel = b.bind(bindIp, bindPort).sync().channel();
        System.out.println("[WS] Server started at wss://" + bindIp + ":" + bindPort + "/p2p");
    }

    public void stop() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        System.out.println("[WS] Server stopped.");
    }
}
