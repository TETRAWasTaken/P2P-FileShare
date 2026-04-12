package com.kolhey.p2p.quic;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;

public class FileTransferStreamHandler extends ChannelInboundHandlerAdapter {

    private static final int MAX_CONTROL_MESSAGE_BYTES = 1024;
    private static final String HANDSHAKE_INIT = "P2P_HANDSHAKE_INIT";
    private static final String HANDSHAKE_ACK = "ACK: Ready for file chunks";

    private final boolean isSender;

    public FileTransferStreamHandler(boolean isSender) {
        this.isSender = isSender;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        if (isSender) {
            System.out.println("[Client] Sending initiation packet...");
            ByteBuf message = Unpooled.copiedBuffer("P2P_HANDSHAKE_INIT", CharsetUtil.UTF_8);
            ctx.writeAndFlush(message);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf)) {
            ReferenceCountUtil.release(msg);
            ctx.close();
            return;
        }

        ByteBuf in = (ByteBuf) msg;
        try {
            if (in.readableBytes() > MAX_CONTROL_MESSAGE_BYTES) {
                System.err.println("Stream error: oversized control payload");
                ctx.close();
                return;
            }

            String receivedText = in.toString(CharsetUtil.UTF_8);
            
            if (!isSender) {
                if (!HANDSHAKE_INIT.equals(receivedText)) {
                    System.err.println("Stream error: invalid client handshake message");
                    ctx.close();
                    return;
                }
                System.out.println("[Server] Received from peer: " + receivedText);
                ByteBuf ack = Unpooled.copiedBuffer(HANDSHAKE_ACK, CharsetUtil.UTF_8);
                ctx.writeAndFlush(ack);
            } else {
                if (!HANDSHAKE_ACK.equals(receivedText)) {
                    System.err.println("Stream error: invalid server acknowledgement");
                    ctx.close();
                    return;
                }
                System.out.println("[Client] Peer responded with: " + receivedText);
            }
        } finally {
            in.release(); 
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("Stream error: " + cause.getMessage());
        ctx.close();
    }
}