package com.kolhey.p2p.quic;

import com.kolhey.p2p.gui.utils.ConnectionEvent;
import com.kolhey.p2p.gui.utils.P2PServiceManager;
import com.kolhey.p2p.gui.utils.TransferEvent;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import com.kolhey.p2p.io.FileIOService;

import java.util.UUID;

public class FileTransferStreamHandler extends ChannelInboundHandlerAdapter {

    private final FileIOService fileIOService = new FileIOService();

    // State tracking for incoming files - each handler instance has its own state
    private boolean headerReceived = false;
    private String currentFileName;
    private long currentFileSize;
    private long totalBytesRead = 0; // Track progress for state reset
    private String currentTransferId;
    private final P2PServiceManager serviceManager;
    private String remotePeerName = "Unknown";

    public FileTransferStreamHandler(boolean isSender) {
        // isSender parameter reserved for future use (e.g., client-side vs server-side logging)
        this.serviceManager = null;
    }

    public FileTransferStreamHandler(boolean isSender, P2PServiceManager serviceManager) {
        // isSender parameter reserved for future use (e.g., client-side vs server-side logging)
        this.serviceManager = serviceManager;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        String remoteAddress = ctx.channel().remoteAddress().toString();
        remotePeerName = "Peer-" + UUID.randomUUID().toString().substring(0, 4);

        System.out.println("[QUIC Handler] Channel active. Remote: " + remoteAddress);

        // Fire connection event
        if (serviceManager != null) {
            serviceManager.notifyConnectionEvent(
                ConnectionEvent.peerConnected(remotePeerName, remoteAddress, "QUIC", "Trusted")
            );
        }
    }

    public void startFileTransfer(ChannelHandlerContext ctx, java.io.File file) throws java.io.IOException {
        System.out.println("[QUIC Sender] Starting transfer for " + file.getName() +
                         " (" + formatFileSize(file.length()) + ")");

        ctx.writeAndFlush(fileIOService.createHeader(file));
        System.out.println("[QUIC Sender] Header sent");

        long pos = 0;
        int chunkCount = 0;
        while (pos < file.length()) {
            ByteBuf chunk = fileIOService.readChunk(file, pos);
            pos += chunk.readableBytes();
            chunkCount++;

            // Back-pressure check: ensure we don't overwhelm the network buffer
            if (!ctx.channel().isWritable()) {
                try { Thread.sleep(5); } catch (InterruptedException ignored) {}
            }
            ctx.writeAndFlush(chunk);

            // Log progress every 100 chunks
            if (chunkCount % 100 == 0) {
                System.out.println("[QUIC Sender] Sent " + chunkCount + " chunks, " +
                                 formatFileSize(pos) + " / " + formatFileSize(file.length()));
            }
        }
        System.out.println("[QUIC Sender] ✓ Finished sending " + file.getName() +
                         " (" + chunkCount + " chunks)");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf)) {
            ReferenceCountUtil.release(msg);
            return;
        }

        ByteBuf in = (ByteBuf) msg;
        try {
            if (!headerReceived) {
                // STAGE 1: PARSE HEADER
                in.markReaderIndex(); // Save position in case we need to roll back

                if (in.readableBytes() < 4) return;

                int nameLength = in.readInt();
                if (in.readableBytes() < nameLength + 8) {
                    in.resetReaderIndex(); // Restore index to try again when more data arrives
                    return;
                }

                byte[] nameBytes = new byte[nameLength];
                in.readBytes(nameBytes);
                this.currentFileName = new String(nameBytes, CharsetUtil.UTF_8);
                this.currentFileSize = in.readLong();

                this.headerReceived = true;
                this.totalBytesRead = 0;
                this.currentTransferId = UUID.randomUUID().toString();

                System.out.println("[QUIC Receiver] Preparing to receive: " + currentFileName +
                                 " (" + formatFileSize(currentFileSize) + ")");
                notifyTransferEvent(TransferEvent.started(currentTransferId, currentFileName,
                    String.valueOf(ctx.channel().remoteAddress()), currentFileSize));
            } else {
                // STAGE 2: SAVE CHUNKS
                int readable = in.readableBytes();
                fileIOService.saveChunk(currentFileName, in, currentFileSize);
                totalBytesRead += readable;

                double progress = (double) totalBytesRead / currentFileSize;
                System.out.println("[QUIC Receiver] Progress: " + currentFileName + " - " +
                                 String.format("%.1f%%", progress * 100) + " (" +
                                 formatFileSize(totalBytesRead) + " / " + formatFileSize(currentFileSize) + ")");

                notifyTransferEvent(TransferEvent.progress(currentTransferId, currentFileName,
                    String.valueOf(ctx.channel().remoteAddress()), totalBytesRead, currentFileSize));

                // Check if file is finished to reset the handler state
                if (totalBytesRead >= currentFileSize) {
                    System.out.println("[QUIC Receiver] ✓ Transfer complete: " + currentFileName);
                    notifyTransferEvent(TransferEvent.completed(currentTransferId, currentFileName,
                        String.valueOf(ctx.channel().remoteAddress()), currentFileSize));
                    this.headerReceived = false;
                    this.currentFileName = null;
                    this.currentTransferId = null;
                    this.totalBytesRead = 0;
                }
            }
        } catch (Exception e) {
            System.err.println("[QUIC Handler] I/O Error during transfer: " + e.getMessage());
            e.printStackTrace();
            notifyTransferEvent(TransferEvent.failed(currentTransferId, currentFileName,
                String.valueOf(ctx.channel().remoteAddress()), totalBytesRead, currentFileSize, e.getMessage()));
            if (serviceManager != null) {
                serviceManager.notifyConnectionEvent(
                    ConnectionEvent.authenticationFailed(remotePeerName, ctx.channel().remoteAddress().toString(),
                                                        "QUIC", "Error: " + e.getMessage())
                );
            }
            ctx.close();
        } finally {
            in.release();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("[QUIC Handler] Stream error: " + cause.getMessage());
        cause.printStackTrace();
        notifyTransferEvent(TransferEvent.failed(currentTransferId, currentFileName,
            String.valueOf(ctx.channel().remoteAddress()), totalBytesRead, currentFileSize, cause.getMessage()));
        if (serviceManager != null) {
            serviceManager.notifyConnectionEvent(
                ConnectionEvent.authenticationFailed(remotePeerName, ctx.channel().remoteAddress().toString(),
                                                    "QUIC", "Error: " + cause.getMessage())
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