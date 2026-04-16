package com.kolhey.p2p.io;

public interface TransferCallback {
    void onProgress(String fileName, long bytesTransferred, long totalSize);
    void onComplete(String fileName);
    void onError(String fileName, Throwable cause);

    /** A no-op callback for contexts where transfer tracking is not needed. */
    TransferCallback NOOP = new TransferCallback() {
        @Override public void onProgress(String fileName, long bytesTransferred, long totalSize) {}
        @Override public void onComplete(String fileName) {}
        @Override public void onError(String fileName, Throwable cause) {}
    };
}