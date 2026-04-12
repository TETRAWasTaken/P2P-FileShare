package com.kolhey.p2p.ws;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

public class WsFileTransferHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final int MAX_TEXT_MESSAGE_LENGTH = 1024;
    private static final int MAX_BINARY_FRAME_BYTES = 1024 * 1024;
    private static final String HANDSHAKE_INIT = "{\"action\": \"P2P_HANDSHAKE_INIT\"}";
    private static final String HANDSHAKE_ACK = "{\"status\": \"ACK_READY\"}";

    private final boolean isClient;

    public WsFileTransferHandler(boolean isClient) {
        this.isClient = isClient;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        if (isClient) {
            System.out.println("[Client] Sending WSS initialization frame...");
            ctx.writeAndFlush(new TextWebSocketFrame(HANDSHAKE_INIT));
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof TextWebSocketFrame) {
            String text = ((TextWebSocketFrame) frame).text();
            if (text.length() > MAX_TEXT_MESSAGE_LENGTH) {
                System.err.println("WebSocket stream error: oversized text frame");
                ctx.close();
                return;
            }
            System.out.println((isClient ? "[Client]" : "[Server]") + " Received Text: " + text);

            if (!isClient) {
                if (!HANDSHAKE_INIT.equals(text)) {
                    System.err.println("WebSocket stream error: invalid client handshake payload");
                    ctx.close();
                    return;
                }
                ctx.writeAndFlush(new TextWebSocketFrame(HANDSHAKE_ACK));
            } else if (!HANDSHAKE_ACK.equals(text)) {
                System.err.println("WebSocket stream error: invalid server acknowledgement payload");
                ctx.close();
            }
        } 
        else if (frame instanceof BinaryWebSocketFrame) {
            io.netty.buffer.ByteBuf buffer = frame.content();
            if (buffer.readableBytes() > MAX_BINARY_FRAME_BYTES) {
                System.err.println("WebSocket stream error: oversized binary frame");
                ctx.close();
                return;
            }
            System.out.println((isClient ? "[Client]" : "[Server]") + " Received Binary Chunk of size: " + buffer.readableBytes());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("WebSocket stream error: " + cause.getMessage());
        ctx.close();
    }
}
