package com.kolhey.p2p;

import com.kolhey.p2p.database.PeerDatabase;
import com.kolhey.p2p.database.SqlitePeerDatabase;
import com.kolhey.p2p.discovery.NetworkUtil;
import com.kolhey.p2p.discovery.PeerDiscoveryManager;
import com.kolhey.p2p.quic.QuicClientNode;
import com.kolhey.p2p.quic.QuicServerNode;
import com.kolhey.p2p.ws.WsClientNode;
import com.kolhey.p2p.ws.WsServerNode;
import javax.jmdns.ServiceInfo;

import java.io.File;
import java.net.InetAddress;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("--- Starting Secure P2P Node ---\n");

        // Verify security configuration
        boolean devMode = Boolean.getBoolean("p2p.allowInsecureDevTls");
        System.out.println("=== SECURITY CONFIGURATION ===");
        System.out.println("Dev TLS Mode: " + devMode);
        System.out.println("Peer Identity Verification: " + (devMode ? "DISABLED (Dev Mode)" : "ENABLED ✅"));
        if (devMode) {
            System.out.println("⚠️  WARNING: Running in INSECURE dev mode with self-signed certs");
            System.out.println("   PeerIdentityTrustManager is BYPASSED");
        } else {
            System.out.println("✓ Production mode - PeerIdentityTrustManager ACTIVE");
        }
        System.out.println("==============================\n");

        String nodeName = "Node-" + UUID.randomUUID().toString().substring(0, 4);
        InetAddress localIp = NetworkUtil.getLocalIPv4Address();

        // Get configurable ports from environment variables (default to 8080 and 9000)
        int wsPort   = getPortFromEnv("P2P_WS_PORT",   8080);
        int quicPort = getPortFromEnv("P2P_QUIC_PORT", 9000);

        System.out.println("Configuration:");
        System.out.println("  Node Name  : " + nodeName);
        System.out.println("  IP Address : " + localIp.getHostAddress());
        System.out.println("  WS Port    : " + wsPort);
        System.out.println("  QUIC Port  : " + quicPort);
        System.out.println();

        // 1. Shared transfer tracker (notifies receiver, tracks active transfers)
        TransferTracker tracker = new TransferTracker();

        // 2. Initialize the SQLite Identity Database
        PeerDatabase peerDb = new SqlitePeerDatabase(System.getProperty("user.dir") + "/.p2p-data");

        // 3. Start the WSS Server (passes tracker so the server handler can notify on incoming files)
        WsServerNode wsServer = new WsServerNode(localIp.getHostAddress(), wsPort, peerDb, tracker);
        wsServer.start();
        System.out.println("✓ WebSocket Secure (WSS) server started on wss://"
                + localIp.getHostAddress() + ":" + wsPort + "/p2p");

        // 4. Start the QUIC Server
        QuicServerNode quicServer = new QuicServerNode(localIp.getHostAddress(), quicPort, peerDb, tracker);
        quicServer.start();
        System.out.println("✓ QUIC server started on " + localIp.getHostAddress() + ":" + quicPort);

        // 5. Start Discovery
        PeerDiscoveryManager discovery = new PeerDiscoveryManager();
        try {
            discovery.start(nodeName, quicPort, wsPort);
            System.out.println("✓ Peer discovery started. Node name: " + nodeName);
        } catch (Exception e) {
            System.err.println("Failed to start peer discovery: " + e.getMessage());
            wsServer.stop();
            quicServer.stop();
            return;
        }

        // 6. Initialize outbound clients
        QuicClientNode quicClient = new QuicClientNode(peerDb);
        WsClientNode   wsClient   = new WsClientNode(peerDb);

        // 7. Register shutdown hook for graceful exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n--- Shutting down P2P Node ---");

            try { discovery.stop();    System.out.println("✓ Peer discovery stopped"); }
            catch (Exception e) { System.err.println("Error stopping discovery: " + e.getMessage()); }

            try { wsServer.stop();     System.out.println("✓ WebSocket server stopped"); }
            catch (Exception e) { System.err.println("Error stopping WS server: " + e.getMessage()); }

            try { quicServer.stop();   System.out.println("✓ QUIC server stopped"); }
            catch (Exception e) { System.err.println("Error stopping QUIC server: " + e.getMessage()); }

            try { quicClient.stop();   System.out.println("✓ QUIC client stopped"); }
            catch (Exception e) { System.err.println("Error stopping QUIC client: " + e.getMessage()); }

            try { wsClient.stop();     System.out.println("✓ WebSocket client stopped"); }
            catch (Exception e) { System.err.println("Error stopping WS client: " + e.getMessage()); }

            System.out.println("--- P2P Node shutdown complete ---");
        }));

        // 8. Terminal command loop
        System.out.println("\nNode is running. Type 'help' for commands, or 'exit' to quit.\n");

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("p2p> ");
                if (!scanner.hasNextLine()) break;

                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split("\\s+");
                String cmd = parts[0].toLowerCase();

                switch (cmd) {
                    case "help":
                        printHelp();
                        break;

                    case "peers":
                        printPeers(discovery.getActivePeers(), nodeName);
                        break;

                    case "transfers":
                        tracker.printStatus();
                        break;

                    case "exit":
                    case "quit":
                        System.out.println("Exiting on user command...");
                        return;

                    case "connect-quic":
                        if (parts.length == 2) {
                            connectToDiscoveredPeer(parts[1], "quic", null,
                                    discovery.getActivePeers(), quicClient, wsClient, tracker);
                        } else {
                            System.out.println("Usage: connect-quic <peerName>");
                        }
                        break;

                    case "connect-ws":
                        if (parts.length == 2) {
                            connectToDiscoveredPeer(parts[1], "ws", null,
                                    discovery.getActivePeers(), quicClient, wsClient, tracker);
                        } else {
                            System.out.println("Usage: connect-ws <peerName>");
                        }
                        break;

                    case "connect-quic-host":
                        if (parts.length == 3) {
                            connectToHost(parts[1], parts[2], "quic", null, quicClient, wsClient, tracker);
                        } else {
                            System.out.println("Usage: connect-quic-host <ip> <port>");
                        }
                        break;

                    case "connect-ws-host":
                        if (parts.length == 3) {
                            connectToHost(parts[1], parts[2], "ws", null, quicClient, wsClient, tracker);
                        } else {
                            System.out.println("Usage: connect-ws-host <ip> <port>");
                        }
                        break;

                    case "send-quic":
                        if (parts.length == 3) {
                            connectToDiscoveredPeer(parts[1], "quic", parts[2],
                                    discovery.getActivePeers(), quicClient, wsClient, tracker);
                        } else {
                            System.out.println("Usage: send-quic <peerName> <filePath>");
                        }
                        break;

                    case "send-ws":
                        if (parts.length == 3) {
                            connectToDiscoveredPeer(parts[1], "ws", parts[2],
                                    discovery.getActivePeers(), quicClient, wsClient, tracker);
                        } else {
                            System.out.println("Usage: send-ws <peerName> <filePath>");
                        }
                        break;

                    case "send-quic-host":
                        if (parts.length == 4) {
                            connectToHost(parts[1], parts[2], "quic", parts[3], quicClient, wsClient, tracker);
                        } else {
                            System.out.println("Usage: send-quic-host <ip> <port> <filePath>");
                        }
                        break;

                    case "send-ws-host":
                        if (parts.length == 4) {
                            connectToHost(parts[1], parts[2], "ws", parts[3], quicClient, wsClient, tracker);
                        } else {
                            System.out.println("Usage: send-ws-host <ip> <port> <filePath>");
                        }
                        break;

                    default:
                        System.out.println("Unknown command: '" + parts[0] + "'. Type 'help' for usage.");
                }
            }
        }
    }

    // ── Helper: parse port from environment ──────────────────────────────────

    private static int getPortFromEnv(String envVar, int defaultPort) {
        String value = System.getenv(envVar);
        if (value != null && !value.isBlank()) {
            try {
                int port = Integer.parseInt(value);
                if (port > 0 && port <= 65535) return port;
            } catch (NumberFormatException ignored) {}
        }
        return defaultPort;
    }

    // ── Help ─────────────────────────────────────────────────────────────────

    private static void printHelp() {
        System.out.println("Available commands:");
        System.out.println("  help                                   Show this help");
        System.out.println("  peers                                  List discovered peers");
        System.out.println("  transfers                              Show active and completed file transfers");
        System.out.println();
        System.out.println("  send-quic <peer> <file>                Send file to discovered peer via QUIC");
        System.out.println("  send-ws   <peer> <file>                Send file to discovered peer via WSS");
        System.out.println("  send-quic-host <ip> <port> <file>      Send file to specific host via QUIC");
        System.out.println("  send-ws-host   <ip> <port> <file>      Send file to specific host via WSS");
        System.out.println();
        System.out.println("  connect-quic <peer>                    Connect to discovered peer via QUIC");
        System.out.println("  connect-ws   <peer>                    Connect to discovered peer via WSS");
        System.out.println("  connect-quic-host <ip> <port>          Connect directly via QUIC");
        System.out.println("  connect-ws-host   <ip> <port>          Connect directly via WSS");
        System.out.println();
        System.out.println("  exit                                   Stop this node");
        System.out.println();
        System.out.println("  Received files are saved to: " + new File("downloads").getAbsolutePath());
    }

    // ── Peers listing ────────────────────────────────────────────────────────

    private static void printPeers(Map<String, ServiceInfo> activePeers, String nodeName) {
        if (activePeers.isEmpty()) {
            System.out.println("No peers discovered yet. (Discovery is active — wait a moment and try again.)");
            return;
        }

        boolean found = false;
        for (Map.Entry<String, ServiceInfo> entry : activePeers.entrySet()) {
            if (nodeName.equals(entry.getKey())) continue;
            if (!found) {
                System.out.println("Discovered peers:");
                found = true;
            }
            ServiceInfo info = entry.getValue();
            String[] addresses = info.getHostAddresses();
            String address  = (addresses != null && addresses.length > 0) ? addresses[0] : "unknown";
            String qPort    = info.getPropertyString("quic_port");
            String wPort    = info.getPropertyString("ws_port");
            System.out.printf("  %-20s  %s  (quic=%s, ws=%s)%n",
                    entry.getKey(), address,
                    qPort != null ? qPort : "n/a",
                    wPort != null ? wPort : "n/a");
        }
        if (!found) {
            System.out.println("No other peers discovered yet.");
        }
    }

    // ── Connection helpers ───────────────────────────────────────────────────

    private static void connectToDiscoveredPeer(
            String peerName, String protocol, String filePath,
            Map<String, ServiceInfo> activePeers,
            QuicClientNode quicClient, WsClientNode wsClient,
            TransferTracker tracker) {

        ServiceInfo info = activePeers.get(peerName);
        if (info == null) {
            System.out.println("Peer not found: '" + peerName + "'. Run 'peers' to list available peers.");
            return;
        }

        String[] addresses = info.getHostAddresses();
        if (addresses == null || addresses.length == 0) {
            System.out.println("Peer '" + peerName + "' has no resolved host address.");
            return;
        }

        String ip       = addresses[0];
        String portText = "quic".equalsIgnoreCase(protocol)
                ? info.getPropertyString("quic_port")
                : info.getPropertyString("ws_port");

        if (portText == null || portText.isBlank()) {
            System.out.println("Peer '" + peerName + "' does not advertise " + protocol.toUpperCase() + " support.");
            return;
        }

        connectToHost(ip, portText, protocol, filePath, quicClient, wsClient, tracker);
    }

    private static void connectToHost(
            String ip, String portText, String protocol, String filePath,
            QuicClientNode quicClient, WsClientNode wsClient,
            TransferTracker tracker) {

        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            System.out.println("Invalid port: '" + portText + "'");
            return;
        }

        File file = null;
        if (filePath != null) {
            file = new File(filePath);
            if (!file.exists()) {
                System.out.println("File not found: '" + filePath + "'");
                return;
            }
            if (!file.isFile()) {
                System.out.println("Not a regular file: '" + filePath + "'");
                return;
            }
        }

        final File fileToSend = file;
        try {
            if ("quic".equalsIgnoreCase(protocol)) {
                quicClient.connectAndSend(ip, port, fileToSend, tracker);
            } else {
                wsClient.connectAndSend(ip, port, fileToSend, tracker);
            }
            System.out.println("Connected via " + protocol.toUpperCase() + " to " + ip + ":" + port
                    + (fileToSend != null ? " — sending " + fileToSend.getName() + " in background" : ""));
        } catch (Exception e) {
            System.err.println("Connection failed: " + e.getMessage());
        }
    }
}
