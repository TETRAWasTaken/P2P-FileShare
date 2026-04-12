package com.kolhey.p2p;

import com.kolhey.p2p.discovery.PeerDiscoveryManager;

import java.io.IOException;
import java.util.UUID;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("--- Starting P2P Node ---");

        // Generate a random node name for testing (e.g., Node-1a2b)
        String nodeName = "Node-" + UUID.randomUUID().toString().substring(0, 4);
        
        PeerDiscoveryManager discovery = new PeerDiscoveryManager();
        
        try {
            discovery.start(nodeName, 9000, 8080);
        } catch (IOException exception) {
            System.err.println("Failed to start peer discovery: " + exception.getMessage());
            return;
        }

        // Crucial: Ensure JmDNS unregisters the service if you stop the program
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nExecuting shutdown hook...");
            discovery.stop();
        }));

        // Keep the main thread alive so the listener can run in the background
        System.out.println("Node is running. Press Ctrl+C to gracefully exit.");
        Thread.currentThread().join();
    }
}