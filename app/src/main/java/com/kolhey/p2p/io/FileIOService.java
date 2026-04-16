package com.kolhey.p2p.io;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

public class FileIOService {

    private static final int CHUNK_SIZE = 64 * 1024; // 64KB chunks
    private FileOutputStream currentDownloadStream;
    private String currentDownloadFileName;
    private long bytesReceived = 0;
    private TransferCallback callback = TransferCallback.NOOP;

    public void setCallback(TransferCallback callback) {
        this.callback = callback != null ? callback : TransferCallback.NOOP;
    }

    /**
     * Prepares a header ByteBuf to be sent before the file data.
     */
    public ByteBuf createHeader(File file) {
        byte[] fileNameBytes = file.getName().getBytes(StandardCharsets.UTF_8);
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(fileNameBytes.length);
        buffer.writeBytes(fileNameBytes);
        buffer.writeLong(file.length());
        return buffer;
    }

    /**
     * Reads a specific chunk from a file.
     */
    public ByteBuf readChunk(File file, long position) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             FileChannel channel = raf.getChannel()) {
            
            long remaining = file.length() - position;
            int toRead = (int) Math.min(CHUNK_SIZE, remaining);
            
            ByteBuf buffer = Unpooled.directBuffer(toRead);
            buffer.writeBytes(channel, position, toRead);
            return buffer;
        }
    }

    /**
     * Writes incoming bytes to a file in the downloads folder.
     * Fires {@link TransferCallback} progress and completion events.
     */
    public void saveChunk(String fileName, ByteBuf data, long totalSize) throws IOException {
        if (currentDownloadStream == null) {
            File downloadDir = new File("downloads");
            if (!downloadDir.exists()) downloadDir.mkdirs();
            currentDownloadStream = new FileOutputStream(new File(downloadDir, fileName));
            currentDownloadFileName = fileName;
            bytesReceived = 0;
        }

        int readableBytes = data.readableBytes();
        data.readBytes(currentDownloadStream.getChannel(), bytesReceived, readableBytes);
        bytesReceived += readableBytes;

        callback.onProgress(currentDownloadFileName, bytesReceived, totalSize);

        if (bytesReceived >= totalSize) {
            currentDownloadStream.close();
            currentDownloadStream = null;
            String done = currentDownloadFileName;
            currentDownloadFileName = null;
            callback.onComplete(done);
        }
    }
}