package com.kolhey.p2p.quic;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class QuicIntegrationTest {

    private static QuicServerNode server;
    private static QuicClientNode client;
    
    // Use localhost and a specific port for the loopback test
    private static final String LOCALHOST = "127.0.0.1";
    private static final int TEST_PORT = 9090; 

    @BeforeAll
    static void setupServer() throws Exception {
        System.out.println("--- Starting Test Setup ---");
        
        // 1. Initialize and start the server
        server = new QuicServerNode(LOCALHOST, TEST_PORT);
        server.start();
        
        // 2. Give the Netty boss group a moment to successfully bind to the UDP port
        Thread.sleep(500);
        
        // 3. Initialize the client
        client = new QuicClientNode();
    }

    @Test
    void testQuicConnectionAndStream() {
        System.out.println("--- Executing QUIC Connection Test ---");
        
        // 4. Assert that the client can complete the TLS 1.3 handshake 
        // and open a bidirectional stream without throwing any exceptions
        assertDoesNotThrow(() -> {
            client.connectAndSend(LOCALHOST, TEST_PORT);
            
            // 5. Pause the main test thread. 
            // Because Netty reads/writes on separate I/O threads, we must keep the 
            // JVM alive long enough for the FileTransferStreamHandler to exchange 
            // the "P2P_HANDSHAKE_INIT" and "ACK" messages.
            Thread.sleep(1500);
            
        }, "The QUIC connection or stream initialization failed.");
    }

    @AfterAll
    static void teardown() {
        System.out.println("--- Tearing Down Network Layer ---");
        // 6. Gracefully shut down the Netty EventLoopGroups to free the ports
        if (client != null) {
            client.stop();
        }
        if (server != null) {
            server.stop();
        }
    }
}