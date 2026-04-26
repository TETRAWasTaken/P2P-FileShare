package com.kolhey.p2p.gui.utils;

import com.kolhey.p2p.database.PeerDatabase;
import com.kolhey.p2p.database.SqlitePeerDatabase;
import com.kolhey.p2p.discovery.PeerDiscoveryManager;
import com.kolhey.p2p.io.FileIOService;
import com.kolhey.p2p.quic.QuicServerNode;
import com.kolhey.p2p.quic.QuicClientNode;
import com.kolhey.p2p.ws.WsClientNode;
import com.kolhey.p2p.ws.WsServerNode;
import javax.jmdns.ServiceInfo;
import java.io.File;
import java.util.Map;
import java.util.Observable;

/**
 * Backend service manager for GUI integration
 */
public class P2PServiceManager extends Observable {
    
    private static P2PServiceManager instance;
    private PeerDiscoveryManager discoveryManager;
    private FileIOService fileIOService;
    private String nodeName;
    private boolean isRunning;
    private PeerDatabase peerDatabase;
    private QuicServerNode quicServerNode;
    private WsServerNode wsServerNode;
    private String protocolPreference = "WS"; // Default to WebSocket

    private P2PServiceManager() {
        this.fileIOService = new FileIOService();
        this.isRunning = false;
    }
    
    public static synchronized P2PServiceManager getInstance() {
        if (instance == null) {
            instance = new P2PServiceManager();
        }
        return instance;
    }
    
    /**
     * Start the P2P node with discovery
     */
    public void startNode(String nodeName, int quicPort, int wsPort) throws Exception {
        if (isRunning) {
            throw new IllegalStateException("Node is already running");
        }

        this.nodeName = nodeName;
        this.peerDatabase = new SqlitePeerDatabase("data/" + nodeName);
        this.quicServerNode = new QuicServerNode("0.0.0.0", quicPort, peerDatabase, this);
        this.wsServerNode = new WsServerNode("0.0.0.0", wsPort, peerDatabase, this);
        this.discoveryManager = new PeerDiscoveryManager();

        try {
            quicServerNode.start();
            wsServerNode.start();
            discoveryManager.start(nodeName, quicPort, wsPort);
            this.isRunning = true;
        } catch (Exception e) {
            stopNode();
            throw e;
        }
        
        setChanged();
        notifyObservers("node_started");
    }

    /**
     * Broadcast a file transfer event to GUI observers.
     */
    public void notifyTransferEvent(TransferEvent event) {
        if (event == null) {
            return;
        }

        setChanged();
        notifyObservers(event);
    }

    /**
     * Broadcast a connection event to GUI observers.
     */
    public void notifyConnectionEvent(ConnectionEvent event) {
        if (event == null) {
            return;
        }

        setChanged();
        notifyObservers(event);
    }

    /**
     * Send a file to a selected peer using the peer's discovered WebSocket or QUIC endpoint.
     */
    public void sendFileToPeer(String peerName, File file) throws Exception {
        if (!isRunning) {
            throw new IllegalStateException("Node is not running");
        }
        if (file == null || !file.isFile()) {
            throw new IllegalArgumentException("Selected file is invalid");
        }

        ServiceInfo peerInfo = getActivePeers().get(peerName);
        if (peerInfo == null) {
            throw new IllegalStateException("Selected peer is no longer available: " + peerName);
        }

        String[] addresses = peerInfo.getHostAddresses();
        if (addresses == null || addresses.length == 0) {
            throw new IllegalStateException("Selected peer does not have a resolvable IP address");
        }

        String protocol = protocolPreference;

        // Notify that we're attempting connection
        notifyConnectionEvent(ConnectionEvent.attemptingConnection(peerName, addresses[0], protocol));

        if ("QUIC".equals(protocol)) {
            // Use QUIC protocol
            int quicPort;
            try {
                String quicPortStr = peerInfo.getPropertyString("quic_port");
                quicPort = quicPortStr != null ? Integer.parseInt(quicPortStr) : 9000;
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Selected peer has an invalid QUIC port", e);
            }

            QuicClientNode quicClient = new QuicClientNode(peerDatabase);
            try {
                quicClient.connectAndSend(addresses[0], quicPort, file);
                notifyConnectionEvent(ConnectionEvent.peerConnected(peerName, addresses[0], protocol, "Trusted"));
            } catch (Exception e) {
                System.err.println("[P2PServiceManager] QUIC connection failed: " + e.getMessage());
                notifyConnectionEvent(ConnectionEvent.authenticationFailed(peerName, addresses[0], protocol, e.getMessage()));
                throw e;
            } finally {
                quicClient.stop();
            }
            return;
        }

        if ("WS".equals(protocol)) {
            // Use WebSocket protocol
            int wsPort;
            try {
                String wsPortStr = peerInfo.getPropertyString("ws_port");
                wsPort = wsPortStr != null ? Integer.parseInt(wsPortStr) : peerInfo.getPort();
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Selected peer has an invalid WebSocket port", e);
            }

            WsClientNode client = new WsClientNode(peerDatabase);
            try {
                client.connectAndSend(addresses[0], wsPort, file);
            } finally {
                client.stop();
            }
        }
    }
    
    /**
     * Stop the P2P node
     */
    public void stopNode() {
        if (quicServerNode != null) {
            quicServerNode.stop();
            quicServerNode = null;
        }
        if (wsServerNode != null) {
            wsServerNode.stop();
            wsServerNode = null;
        }
        if (discoveryManager != null) {
            discoveryManager.stop();
            discoveryManager = null;
        }
        isRunning = false;

        setChanged();
        notifyObservers("node_stopped");
    }
    
    /**
     * Get active peers from discovery manager
     */
    public Map<String, ServiceInfo> getActivePeers() {
        if (discoveryManager != null) {
            return discoveryManager.getActivePeers();
        }
        return Map.of();
    }
    
    /**
     * Get node name
     */
    public String getNodeName() {
        return nodeName;
    }
    
    /**
     * Check if node is running
     */
    public boolean isNodeRunning() {
        return isRunning;
    }
    
    /**
     * Get file IO service
     */
    public FileIOService getFileIOService() {
        return fileIOService;
    }
    
    /**
     * Get discovery manager
     */
    public PeerDiscoveryManager getDiscoveryManager() {
        return discoveryManager;
    }

    /**
     * Get the current protocol preference (QUIC or WS)
     */
    public String getProtocolPreference() {
        return protocolPreference;
    }

    /**
     * Set the protocol preference for file transfers
     */
    public void setProtocolPreference(String protocol) {
        if ("QUIC".equalsIgnoreCase(protocol) || "WS".equalsIgnoreCase(protocol)) {
            this.protocolPreference = protocol.toUpperCase();
        }
    }

    /**
     * Get peer authentication status from the peer database
     */
    public String getPeerAuthenticationStatus(String peerName) {
        if (peerDatabase == null) {
            return "Unknown";
        }
        // This would need peer database implementation to look up by name
        // For now, return generic status
        return "Unknown";
    }
}
