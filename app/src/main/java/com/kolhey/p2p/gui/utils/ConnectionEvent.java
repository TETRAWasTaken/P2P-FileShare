package com.kolhey.p2p.gui.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Event for connection-related notifications (peer connections, authentication, etc.)
 * Follows the same pattern as TransferEvent for consistency
 */
public final class ConnectionEvent {

    public enum Type {
        ATTEMPTING_CONNECTION,
        PEER_CONNECTED,
        PEER_DISCONNECTED,
        AUTHENTICATION_SUCCESS,
        AUTHENTICATION_FAILED
    }

    private final Type type;
    private final String peerName;
    private final String ipAddress;
    private final String protocol; // QUIC or WS
    private final String authenticationStatus; // Trusted, New, Failed
    private final String message;
    private final LocalDateTime timestamp;

    private ConnectionEvent(Type type, String peerName, String ipAddress, String protocol,
                            String authenticationStatus, String message) {
        this.type = type;
        this.peerName = peerName;
        this.ipAddress = ipAddress;
        this.protocol = protocol;
        this.authenticationStatus = authenticationStatus;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    // Factory methods
    public static ConnectionEvent attemptingConnection(String peerName, String ipAddress, String protocol) {
        return new ConnectionEvent(Type.ATTEMPTING_CONNECTION, peerName, ipAddress, protocol,
                                   "Pending", "Attempting connection...");
    }

    public static ConnectionEvent peerConnected(String peerName, String ipAddress, String protocol,
                                                String authStatus) {
        return new ConnectionEvent(Type.PEER_CONNECTED, peerName, ipAddress, protocol,
                                   authStatus, "Connected successfully");
    }

    public static ConnectionEvent peerDisconnected(String peerName, String ipAddress, String protocol) {
        return new ConnectionEvent(Type.PEER_DISCONNECTED, peerName, ipAddress, protocol,
                                   "N/A", "Peer disconnected");
    }

    public static ConnectionEvent authenticationSuccess(String peerName, String ipAddress, String protocol,
                                                        String authStatus) {
        return new ConnectionEvent(Type.AUTHENTICATION_SUCCESS, peerName, ipAddress, protocol,
                                   authStatus, "Peer authenticated successfully");
    }

    public static ConnectionEvent authenticationFailed(String peerName, String ipAddress, String protocol,
                                                       String reason) {
        return new ConnectionEvent(Type.AUTHENTICATION_FAILED, peerName, ipAddress, protocol,
                                   "Failed", reason != null ? reason : "Authentication failed");
    }

    // Getters
    public Type getType() {
        return type;
    }

    public String getPeerName() {
        return peerName;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getAuthenticationStatus() {
        return authenticationStatus;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getTimestampFormatted() {
        return timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    @Override
    public String toString() {
        return String.format("[%s] %s | Peer: %s (%s) | Protocol: %s | Auth: %s | %s",
                             getTimestampFormatted(), type, peerName, ipAddress, protocol,
                             authenticationStatus, message);
    }
}
