package com.kolhey.p2p.ws;

import com.kolhey.p2p.TransferTracker;
import com.kolhey.p2p.io.FileIOService;
import com.kolhey.p2p.io.TransferCallback;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class WsFileTransferHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final int MAX_TEXT_MESSAGE_LENGTH = 1024;
    private final FileIOService fileIOService = new FileIOService();

    // State tracking for incoming files
    private boolean headerReceived = false;
    private String currentFileName;
    private long currentFileSize;
    private long totalBytesRead = 0;

    private final boolean isClient;
    private final File fileToSend;
    private final TransferTracker tracker;

    /** Receiver-side constructor (no file to send, no tracker). */
    public WsFileTransferHandler(boolean isClient) {
        this(isClient, null, null);
    }

    /** Constructor with optional file and tracker. */
    public WsFileTransferHandler(boolean isClient, File fileToSend, TransferTracker tracker) {
        this.isClient   = isClient;
        this.fileToSend = fileToSend;
        this.tracker    = tracker;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        System.out.println("[WS] " + (isClient ? "Client" : "Server") + " connection established.");
        // If we're the sender and have a file ready, start the transfer on a background thread
        // so we never block Netty's I/O event loop.
        if (isClient && fileToSend != null) {
            Thread t = new Thread(() -> {
                try {
                    startFileTransfer(ctx, fileToSend);
                } catch (IOException e) {
                    System.err.println("[WS] Send failed: " + e.getMessage());
                    if (tracker != null) {
                        // Notify error via a temporary NOOP callback since the transfer
                        // may not have been registered yet
                    }
                    ctx.close();
                }
            }, "ws-file-sender");
            t.setDaemon(true);
            t.start();
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof TextWebSocketFrame) {
            String text = ((TextWebSocketFrame) frame).text();
            if (text.length() > MAX_TEXT_MESSAGE_LENGTH) {
                ctx.close();
                return;
            }
            System.out.println("[WS] Control message: " + text);
        } else if (frame instanceof BinaryWebSocketFrame) {
            handleBinaryStream(ctx, frame.content());
        }
    }

    private void handleBinaryStream(ChannelHandlerContext ctx, ByteBuf buffer) {
        try {
            if (!headerReceived) {
                // STAGE 1: PARSE HEADER
                if (buffer.readableBytes() < 4) return;
                int nameLength = buffer.readInt();

                if (buffer.readableBytes() < nameLength + 8) {
                    buffer.resetReaderIndex();
                    return;
                }

                byte[] nameBytes = new byte[nameLength];
                buffer.readBytes(nameBytes);
                this.currentFileName = new String(nameBytes, StandardCharsets.UTF_8);
                this.currentFileSize = buffer.readLong();

                this.headerReceived = true;
                this.totalBytesRead = 0;

                // Register with tracker – prints the notification banner
                if (tracker != null) {
                    fileIOService.setCallback(tracker.onIncomingTransfer(currentFileName, currentFileSize));
                }
            } else {
                // STAGE 2: SAVE CHUNKS
                int readable = buffer.readableBytes();
                fileIOService.saveChunk(currentFileName, buffer, currentFileSize);
                totalBytesRead += readable;

                if (totalBytesRead >= currentFileSize) {
                    // Reset state for the next potential file on this connection
                    headerReceived  = false;
                    currentFileName = null;
                }
            }
        } catch (IOException e) {
            System.err.println("[WS] File I/O error: " + e.getMessage());
            ctx.close();
        }
    }

    /**
     * Sends {@code file} over the given context.
     * Must be called from a non-I/O thread to avoid blocking Netty's event loop.
     */
    public void startFileTransfer(ChannelHandlerContext ctx, File file) throws IOException {
        TransferCallback cb = (tracker != null)
                ? tracker.onOutgoingTransfer(file.getName(), file.length())
                : TransferCallback.NOOP;

        System.out.println("[WS] Sending: " + file.getName()
                + " (" + TransferTracker.formatBytes(file.length()) + ")");

        ByteBuf header = fileIOService.createHeader(file);
        ctx.writeAndFlush(new BinaryWebSocketFrame(header));

        long pos = 0;
        long fileLength = file.length();
        while (pos < fileLength) {
            ByteBuf chunk = fileIOService.readChunk(file, pos);
            int chunkSize = chunk.readableBytes();
            pos += chunkSize;

            ctx.writeAndFlush(new BinaryWebSocketFrame(chunk));

            // Back-pressure: wait until the channel write buffer drains.
            // Running on a dedicated sender thread, not the I/O thread.
            while (!ctx.channel().isWritable() && ctx.channel().isActive()) {
                try { Thread.sleep(5); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            cb.onProgress(file.getName(), pos, fileLength);
        }

        cb.onComplete(file.getName());
        System.out.println("[WS] Finished sending: " + file.getName());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("[WS] Stream error: " + cause.getMessage());
        ctx.close();
    }
}

