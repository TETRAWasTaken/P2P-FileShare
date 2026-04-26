package com.kolhey.p2p.ws;

import com.kolhey.p2p.gui.utils.ConnectionEvent;
import com.kolhey.p2p.gui.utils.P2PServiceManager;
import com.kolhey.p2p.gui.utils.TransferEvent;
import com.kolhey.p2p.io.FileIOService;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class WsFileTransferHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final int MAX_TEXT_MESSAGE_LENGTH = 1024;
    private final FileIOService fileIOService = new FileIOService();

    // State tracking for incoming files - each handler instance has its own state
    private boolean headerReceived = false;
    private String currentFileName;
    private long currentFileSize;
    private long totalBytesRead = 0;
    private String currentTransferId;

    private final boolean isClient;
    private final File pendingOutgoingFile;
    private final P2PServiceManager serviceManager;
    private String remotePeerName = "Unknown";

    public WsFileTransferHandler(boolean isClient) {
        this(isClient, null, null);
    }

    public WsFileTransferHandler(boolean isClient, File pendingOutgoingFile) {
        this(isClient, pendingOutgoingFile, null);
    }

    public WsFileTransferHandler(boolean isClient, File pendingOutgoingFile, P2PServiceManager serviceManager) {
        this.isClient = isClient;
        this.pendingOutgoingFile = pendingOutgoingFile;
        this.serviceManager = serviceManager;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        String remoteAddress = ctx.channel().remoteAddress().toString();
        remotePeerName = "Peer-" + UUID.randomUUID().toString().substring(0, 4);

        System.out.println((isClient ? "[WS Client]" : "[WS Server]") + " WebSocket connection established: " + remoteAddress);

        // Fire connection event
        if (serviceManager != null) {
            serviceManager.notifyConnectionEvent(
                ConnectionEvent.peerConnected(remotePeerName, remoteAddress, "WS", "Trusted")
            );
        }

        if (isClient && pendingOutgoingFile != null) {
            // Schedule the file transfer to happen on the next event loop cycle
            // to ensure the WebSocket handshake is fully complete
            ctx.executor().execute(() -> {
                try {
                    System.out.println("[WS Client] Starting file transfer for: " + pendingOutgoingFile.getName());
                    startFileTransfer(ctx, pendingOutgoingFile);
                } catch (IOException e) {
                    System.err.println("[WS Sender] Failed to send file: " + e.getMessage());
                    if (serviceManager != null) {
                        serviceManager.notifyConnectionEvent(
                            ConnectionEvent.authenticationFailed(remotePeerName, remoteAddress, "WS",
                                                               "Transfer failed: " + e.getMessage())
                        );
                    }
                } finally {
                    ctx.close();
                }
            });
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
            // Handle control messages (JSON handshakes, cancel signals, etc.)
            System.out.println("[WS Handler] Received Control Message: " + text);
        }
        else if (frame instanceof BinaryWebSocketFrame) {
            // Route binary data to our file processor
            handleBinaryStream(ctx, frame.content());
        }
    }

    /**
     * Logic to distinguish between the first "Metadata" frame and subsequent "Chunk" frames.
     */
    private void handleBinaryStream(ChannelHandlerContext ctx, ByteBuf buffer) {
        try {
            if (!headerReceived) {
                // STAGE 1: PARSE HEADER
                buffer.markReaderIndex();
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
                this.currentTransferId = UUID.randomUUID().toString();

                System.out.println("[WS Receiver] Preparing to receive: " + currentFileName +
                                 " (" + formatFileSize(currentFileSize) + ")");
                notifyTransferEvent(TransferEvent.started(currentTransferId, currentFileName,
                    String.valueOf(ctx.channel().remoteAddress()), currentFileSize));
            } else {
                // STAGE 2: SAVE CHUNKS
                int readable = buffer.readableBytes();
                fileIOService.saveChunk(currentFileName, buffer, currentFileSize);
                totalBytesRead += readable;

                double progress = (double) totalBytesRead / currentFileSize;
                System.out.println("[WS Receiver] Progress: " + currentFileName + " - " +
                                 String.format("%.1f%%", progress * 100) + " (" +
                                 formatFileSize(totalBytesRead) + " / " + formatFileSize(currentFileSize) + ")");

                notifyTransferEvent(TransferEvent.progress(currentTransferId, currentFileName,
                    String.valueOf(ctx.channel().remoteAddress()), totalBytesRead, currentFileSize));

                if (totalBytesRead >= currentFileSize) {
                    System.out.println("[WS Receiver] ✓ Transfer complete for " + currentFileName);
                    notifyTransferEvent(TransferEvent.completed(currentTransferId, currentFileName,
                        String.valueOf(ctx.channel().remoteAddress()), currentFileSize));
                    // Reset state for the next potential file on this connection
                    headerReceived = false;
                    currentFileName = null;
                    currentTransferId = null;
                    totalBytesRead = 0;
                }
            }
        } catch (IOException e) {
            System.err.println("[WS Handler] File I/O Error: " + e.getMessage());
            notifyTransferEvent(TransferEvent.failed(currentTransferId, currentFileName,
                String.valueOf(ctx.channel().remoteAddress()), totalBytesRead, currentFileSize, e.getMessage()));
            ctx.close();
        }
    }

    /**
     * Call this method from your Client or Server logic to push a file across the wire.
     */
    public void startFileTransfer(ChannelHandlerContext ctx, File file) throws IOException {
        System.out.println("[WS Sender] Starting transfer for " + file.getName() + " (" + formatFileSize(file.length()) + ")");

        // 1. Send Header as Binary Frame
        ByteBuf header = fileIOService.createHeader(file);
        ctx.writeAndFlush(new BinaryWebSocketFrame(header));
        System.out.println("[WS Sender] Header sent");

        // 2. Stream Chunks as Binary Frames
        long pos = 0;
        long fileLength = file.length();
        int chunkCount = 0;
        while (pos < fileLength) {
            ByteBuf chunk = fileIOService.readChunk(file, pos);
            pos += chunk.readableBytes();
            chunkCount++;

            // Wrap the Netty ByteBuf in a WebSocket binary frame
            ctx.writeAndFlush(new BinaryWebSocketFrame(chunk));

            // Log progress every 100 chunks
            if (chunkCount % 100 == 0) {
                System.out.println("[WS Sender] Sent " + chunkCount + " chunks, " + formatFileSize(pos) + " / " + formatFileSize(fileLength));
            }

            // Basic back-pressure: pause if the network buffer is full
            if (!ctx.channel().isWritable()) {
                try { Thread.sleep(5); } catch (InterruptedException ignored) {}
            }
        }
        System.out.println("[WS Sender] ✓ Finished sending " + file.getName() + " (" + chunkCount + " chunks)");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("[WS Handler] Stream error: " + cause.getMessage());
        cause.printStackTrace();
        notifyTransferEvent(TransferEvent.failed(currentTransferId, currentFileName,
            String.valueOf(ctx.channel().remoteAddress()), totalBytesRead, currentFileSize, cause.getMessage()));
        if (serviceManager != null) {
            serviceManager.notifyConnectionEvent(
                ConnectionEvent.authenticationFailed(remotePeerName, ctx.channel().remoteAddress().toString(),
                                                    "WS", "Error: " + cause.getMessage())
            );
        }
        ctx.close();
    }

    private void notifyTransferEvent(TransferEvent event) {
        if (serviceManager != null) {
            serviceManager.notifyTransferEvent(event);
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}
