package com.kolhey.p2p;

import com.kolhey.p2p.io.TransferCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks active and completed file transfers for both sender and receiver sides.
 * Provides formatted status output for the 'transfers' CLI command and fires
 * prominent console notifications when an incoming transfer begins or ends.
 */
public class TransferTracker {

    public enum Direction { SEND, RECEIVE }

    public static final class TransferEntry {
        final long id;
        public final String fileName;
        public final long totalSize;
        public final Direction direction;
        final long startTime = System.currentTimeMillis();
        volatile long bytesTransferred;
        volatile boolean complete;
        volatile boolean failed;

        TransferEntry(long id, String fileName, long totalSize, Direction direction) {
            this.id = id;
            this.fileName = fileName;
            this.totalSize = totalSize;
            this.direction = direction;
        }

        int percent() {
            return totalSize == 0 ? 100 : (int) (bytesTransferred * 100L / totalSize);
        }

        String statusLine() {
            String dir  = direction == Direction.SEND ? "⬆ SEND   " : "⬇ RECEIVE";
            String sz   = formatBytes(bytesTransferred) + " / " + formatBytes(totalSize);
            String pct  = failed ? "✗ FAILED" : (complete ? "✓ DONE  " : percent() + "%     ");
            return String.format("  [%s]  %-40s  %20s  %s", dir, fileName, sz, pct);
        }
    }

    private static final AtomicLong SEQ = new AtomicLong();
    private final ConcurrentHashMap<Long, TransferEntry> active    = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<TransferEntry>   completed = new CopyOnWriteArrayList<>();

    /**
     * Register an incoming (receiver-side) transfer and print a notification banner.
     * Returns a {@link TransferCallback} that the handler must call during the transfer.
     */
    public TransferCallback onIncomingTransfer(String fileName, long totalSize) {
        long id = SEQ.incrementAndGet();
        TransferEntry e = new TransferEntry(id, fileName, totalSize, Direction.RECEIVE);
        active.put(id, e);

        System.out.println();
        System.out.println("┌─────────────────────────────────────────────┐");
        System.out.println("│  📥  INCOMING FILE TRANSFER                 │");
        System.out.printf ("│  File : %-37s│%n", truncate(fileName, 37));
        System.out.printf ("│  Size : %-37s│%n", formatBytes(totalSize));
        System.out.println("└─────────────────────────────────────────────┘");
        System.out.println("   (type 'transfers' to monitor progress)");
        System.out.print("\np2p> ");

        return makeCallback(id, e);
    }

    /**
     * Register an outgoing (sender-side) transfer.
     * Returns a {@link TransferCallback} that the handler must call during the transfer.
     */
    public TransferCallback onOutgoingTransfer(String fileName, long totalSize) {
        long id = SEQ.incrementAndGet();
        TransferEntry e = new TransferEntry(id, fileName, totalSize, Direction.SEND);
        active.put(id, e);
        return makeCallback(id, e);
    }

    private TransferCallback makeCallback(long id, TransferEntry e) {
        return new TransferCallback() {
            @Override
            public void onProgress(String name, long bytes, long total) {
                e.bytesTransferred = bytes;
            }

            @Override
            public void onComplete(String name) {
                e.bytesTransferred = e.totalSize;
                e.complete = true;
                active.remove(id);
                completed.add(e);
                String extra = e.direction == Direction.RECEIVE
                        ? " (saved to downloads/" + name + ")"
                        : " (sent successfully)";
                System.out.println("\n✅ " + (e.direction == Direction.RECEIVE ? "Received" : "Sent") + ": " + name + extra);
                System.out.print("p2p> ");
            }

            @Override
            public void onError(String name, Throwable cause) {
                e.failed = true;
                active.remove(id);
                System.err.println("\n❌ Transfer error [" + name + "]: " + cause.getMessage());
                System.out.print("p2p> ");
            }
        };
    }

    /** Print a snapshot of all active and recently completed transfers. */
    public void printStatus() {
        List<TransferEntry> act  = new ArrayList<>(active.values());
        List<TransferEntry> done = new ArrayList<>(completed);

        if (act.isEmpty() && done.isEmpty()) {
            System.out.println("No file transfers recorded in this session.");
            return;
        }

        if (!act.isEmpty()) {
            System.out.println("Active transfers:");
            act.forEach(en -> System.out.println(en.statusLine()));
        } else {
            System.out.println("No active transfers.");
        }

        if (!done.isEmpty()) {
            System.out.println("\nCompleted transfers (this session):");
            int start = Math.max(0, done.size() - 10);
            for (int i = start; i < done.size(); i++) {
                System.out.println(done.get(i).statusLine());
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    public static String formatBytes(long b) {
        if (b < 1_024L)          return b + " B";
        if (b < 1_048_576L)      return String.format("%.1f KB", b / 1_024.0);
        if (b < 1_073_741_824L)  return String.format("%.1f MB", b / 1_048_576.0);
        return String.format("%.2f GB", b / 1_073_741_824.0);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
