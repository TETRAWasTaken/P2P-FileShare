package com.kolhey.p2p.ws;

import com.kolhey.p2p.TransferTracker;
import com.kolhey.p2p.crypto.WsSecurityManager;
import com.kolhey.p2p.database.PeerDatabase;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;

import java.io.File;
import java.net.URI;

public class WsClientNode {

    private final PeerDatabase peerDatabase;
    private EventLoopGroup group;
    private Channel clientChannel;

    public WsClientNode(PeerDatabase peerDatabase) {
        this.peerDatabase = peerDatabase;
    }

    /** Connect to a peer without sending a file (connection test / future use). */
    public void connectAndSend(String targetIp, int targetPort) throws Exception {
        connectAndSend(targetIp, targetPort, null, null);
    }

    /**
     * Connect to a peer and send {@code fileToSend}.
     * The file transfer starts automatically once the WebSocket handshake completes.
     *
     * @param fileToSend file to transfer; may be {@code null} to just connect
     * @param tracker    transfer tracker for progress/notification; may be {@code null}
     */
    public void connectAndSend(String targetIp, int targetPort,
                               File fileToSend, TransferTracker tracker) throws Exception {
        group = new NioEventLoopGroup();
        final SslContext sslCtx = WsSecurityManager.buildClientSslContext(peerDatabase);

        URI uri = new URI("wss://" + targetIp + ":" + targetPort + "/p2p");

        WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders());

        Bootstrap b = new Bootstrap();
        b.group(group)
         .channel(NioSocketChannel.class)
         .handler(new ChannelInitializer<SocketChannel>() {
             @Override
             protected void initChannel(SocketChannel ch) {
                 ChannelPipeline p = ch.pipeline();
                 p.addLast(sslCtx.newHandler(ch.alloc(), targetIp, targetPort));
                 p.addLast(new HttpClientCodec());
                 p.addLast(new HttpObjectAggregator(8192));
                 p.addLast(new WebSocketClientProtocolHandler(handshaker));

                 p.addLast(new SimpleChannelInboundHandler<Object>() {
                     @Override
                     public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                         if (evt == WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE) {
                             System.out.println("[WS] Handshake complete. Secure connection established.");
                             // Add the file transfer handler; channelActive is fired below
                             ctx.pipeline().addLast(new WsFileTransferHandler(true, fileToSend, tracker));
                             ctx.pipeline().remove(this);
                             ctx.pipeline().fireChannelActive();
                         }
                     }
                     @Override
                     protected void channelRead0(ChannelHandlerContext ctx, Object msg) {}
                 });
             }
         });

        System.out.println("[WS] Connecting to " + uri + " ...");
        clientChannel = b.connect(targetIp, targetPort).sync().channel();
    }

    public void stop() {
        if (clientChannel != null) clientChannel.close();
        if (group != null) group.shutdownGracefully();
    }
}
