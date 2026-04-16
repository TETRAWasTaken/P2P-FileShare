package com.kolhey.p2p.quic;

import com.kolhey.p2p.TransferTracker;
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

import java.io.File;
import java.net.InetSocketAddress;

public class QuicClientNode {

    private final PeerDatabase peerDatabase;
    private NioEventLoopGroup group;
    private Channel clientChannel;
    private Channel datagramChannel;

    public QuicClientNode(PeerDatabase peerDatabase) {
        this.peerDatabase = peerDatabase;
    }

    /** Connect to a peer without sending a file (connection test / future use). */
    public void connectAndSend(String targetIp, int targetPort) throws Exception {
        connectAndSend(targetIp, targetPort, null, null);
    }

    /**
     * Connect to a peer and send {@code fileToSend}.
     * The file transfer runs on a background thread; this method returns once the
     * QUIC stream is open.
     *
     * @param fileToSend file to transfer; may be {@code null} to just connect
     * @param tracker    transfer tracker for progress/notification; may be {@code null}
     */
    public void connectAndSend(String targetIp, int targetPort,
                               File fileToSend, TransferTracker tracker) throws Exception {
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

            System.out.println("[QUIC] Connecting to " + targetIp + ":" + targetPort + " ...");

            QuicChannel quicChannel = QuicChannel.newBootstrap(channel)
                    .streamHandler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) {
                            // Handles streams initiated BY the server (rare in our workflow)
                        }
                    })
                    .remoteAddress(new InetSocketAddress(targetIp, targetPort))
                    .connect()
                    .get();

            System.out.println("[QUIC] Connected securely to peer at " + targetIp + ":" + targetPort);

            QuicStreamChannel streamChannel = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) {
                            FileTransferStreamHandler fh =
                                    new FileTransferStreamHandler(true, tracker);
                            ctx.pipeline().addLast(fh);

                            if (fileToSend != null) {
                                // Retrieve the context for the newly added handler
                                ChannelHandlerContext fhCtx = ctx.pipeline().context(fh);
                                ctx.pipeline().remove(this);

                                Thread t = new Thread(() -> {
                                    try {
                                        fh.startFileTransfer(fhCtx, fileToSend);
                                    } catch (Exception e) {
                                        System.err.println("[QUIC] File send failed: " + e.getMessage());
                                        fhCtx.close();
                                    }
                                }, "quic-file-sender");
                                t.setDaemon(true);
                                t.start();
                            }
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