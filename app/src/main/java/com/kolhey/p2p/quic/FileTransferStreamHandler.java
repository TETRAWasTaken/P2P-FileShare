package com.kolhey.p2p.quic;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import com.kolhey.p2p.TransferTracker;
import com.kolhey.p2p.io.FileIOService;
import com.kolhey.p2p.io.TransferCallback;

public class FileTransferStreamHandler extends ChannelInboundHandlerAdapter {

    private final FileIOService fileIOService = new FileIOService();
    private boolean headerReceived = false;
    private String currentFileName;
    private long currentFileSize;
    private long totalBytesRead = 0;
    private final boolean isSender;
    private final TransferTracker tracker;

    /** Receiver-side constructor (no tracker). */
    public FileTransferStreamHandler(boolean isSender) {
        this(isSender, null);
    }

    /** Constructor with optional tracker for both sender and receiver sides. */
    public FileTransferStreamHandler(boolean isSender, TransferTracker tracker) {
        this.isSender = isSender;
        this.tracker  = tracker;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        System.out.println("[QUIC] Channel active. Ready for stream.");
    }

    /**
     * Sends {@code file} over the given context.
     * Must be called from a non-I/O thread to avoid blocking Netty's event loop.
     */
    public void startFileTransfer(ChannelHandlerContext ctx, java.io.File file) throws java.io.IOException {
        TransferCallback cb = (tracker != null)
                ? tracker.onOutgoingTransfer(file.getName(), file.length())
                : TransferCallback.NOOP;

        System.out.println("[QUIC] Sending: " + file.getName()
                + " (" + TransferTracker.formatBytes(file.length()) + ")");

        ctx.writeAndFlush(fileIOService.createHeader(file));

        long pos = 0;
        long fileLength = file.length();
        while (pos < fileLength) {
            ByteBuf chunk = fileIOService.readChunk(file, pos);
            int chunkSize = chunk.readableBytes();
            pos += chunkSize;

            // Back-pressure: wait until the channel write buffer drains.
            // This loop runs on a dedicated sender thread, not the I/O thread.
            while (!ctx.channel().isWritable() && ctx.channel().isActive()) {
                try { Thread.sleep(5); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            ctx.writeAndFlush(chunk);
            cb.onProgress(file.getName(), pos, fileLength);
        }

        cb.onComplete(file.getName());
        System.out.println("[QUIC] Finished sending: " + file.getName());
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
                in.markReaderIndex();

                if (in.readableBytes() < 4) return;

                int nameLength = in.readInt();
                if (in.readableBytes() < nameLength + 8) {
                    in.resetReaderIndex();
                    return;
                }

                byte[] nameBytes = new byte[nameLength];
                in.readBytes(nameBytes);
                this.currentFileName = new String(nameBytes, CharsetUtil.UTF_8);
                this.currentFileSize = in.readLong();

                this.headerReceived = true;
                this.totalBytesRead = 0;

                // Register with tracker – this prints the notification banner
                if (tracker != null) {
                    fileIOService.setCallback(tracker.onIncomingTransfer(currentFileName, currentFileSize));
                }
            } else {
                // STAGE 2: SAVE CHUNKS
                int readable = in.readableBytes();
                fileIOService.saveChunk(currentFileName, in, currentFileSize);
                totalBytesRead += readable;

                // Reset state once the file is fully received
                if (totalBytesRead >= currentFileSize) {
                    this.headerReceived  = false;
                    this.currentFileName = null;
                }
            }
        } catch (Exception e) {
            System.err.println("[QUIC] I/O error during transfer: " + e.getMessage());
            ctx.close();
        } finally {
            in.release();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("[QUIC] Stream error: " + cause.getMessage());
        ctx.close();
    }
}