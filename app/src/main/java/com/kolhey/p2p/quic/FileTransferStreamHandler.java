package com.kolhey.p2p.quic;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import com.kolhey.p2p.io.FileIOService;

public class FileTransferStreamHandler extends ChannelInboundHandlerAdapter {

    private final FileIOService fileIOService = new FileIOService();
    private boolean headerReceived = false;
    private String currentFileName;
    private long currentFileSize;
    private long totalBytesRead = 0; // Track progress for state reset
    private final boolean isSender;

    public FileTransferStreamHandler(boolean isSender) {
        this.isSender = isSender;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        System.out.println("[QUIC] Channel active. Ready for stream.");
    }

    public void startFileTransfer(ChannelHandlerContext ctx, java.io.File file) throws java.io.IOException {
        ctx.writeAndFlush(fileIOService.createHeader(file));
    
        long pos = 0;
        while (pos < file.length()) {
            ByteBuf chunk = fileIOService.readChunk(file, pos);
            pos += chunk.readableBytes();
            
            // Back-pressure check: ensure we don't overwhelm the network buffer
            if (!ctx.channel().isWritable()) {
                try { Thread.sleep(5); } catch (InterruptedException ignored) {}
            }
            ctx.writeAndFlush(chunk);
        }
        System.out.println("[QUIC Sender] Finished sending " + file.getName());
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
                System.out.println("[Receiver] Starting transfer for: " + currentFileName + " (" + currentFileSize + " bytes)");
            } else {
                // STAGE 2: SAVE CHUNKS
                int readable = in.readableBytes();
                fileIOService.saveChunk(currentFileName, in, currentFileSize);
                totalBytesRead += readable;

                // Check if file is finished to reset the handler state
                if (totalBytesRead >= currentFileSize) {
                    System.out.println("[Receiver] Transfer complete: " + currentFileName);
                    this.headerReceived = false;
                    this.currentFileName = null;
                }
            }
        } catch (Exception e) {
            System.err.println("I/O Error during transfer: " + e.getMessage());
            ctx.close();
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