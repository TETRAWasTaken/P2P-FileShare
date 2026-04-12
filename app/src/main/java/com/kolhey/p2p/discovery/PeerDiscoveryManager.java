package com.kolhey.p2p.discovery;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PeerDiscoveryManager {
    private static final String SERVICE_TYPE = "_p2p._tcp.local.";
    private JmDNS jmdns;
    private final Map<String, ServiceInfo> activePeers = new ConcurrentHashMap<>();

    public void start(String nodeName, int quicPort, int wsPort)
    throws IOException {
        InetAddress localIP = getLocalIPv4Address();
        System.out.println("Binding JmDNS to Interface: " + localIP.getHostAddress());

        jmdns = JmDNS.create(localIP, nodeName);

        Map<String, String> capabilities = new HashMap<>();
        capabilities.put("quic_port", String.valueOf(quicPort));
        capabilities.put("ws_port", String.valueOf(wsPort));
        capabilities.put("app_version", "1.0");

        ServiceInfo serviceInfo = ServiceInfo.create(
                SERVICE_TYPE,
                nodeName,
                wsPort, 
                0, 0,
                capabilities
                );

        jmdns.registerService(serviceInfo);
        jmdns.addServiceListener(SERVICE_TYPE, new PeerListener());

        System.out.println("Node '" + nodeName + "' is actively discovering peers...");
    }

    public void stop() {
        if (jmdns != null) {
            try {
                jmdns.unregisterAllServices();
                jmdns.close();
                System.out.println("Peer discovery stopped and services unregistered.");
            } catch (IOException e) {
                System.err.println("Error while stopping PeerDiscoveryManager: " + e.getMessage());
            }
        }
    }

    public Map<String, ServiceInfo> getActivePeers() {
        return activePeers;
    }

    private InetAddress getLocalIPv4Address() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();

            if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isPointToPoint()) {
                continue;
            }

            String displayName = networkInterface.getDisplayName();
            String name = displayName != null ? displayName.toLowerCase() : "";
            if (name.contains("virtual") || name.contains("docker") || name.contains("vmware") || name.contains("vbox") || name.contains("wsl")) {
                continue;
            }

            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (address instanceof Inet4Address && !address.isLoopbackAddress() && address.isSiteLocalAddress()) {
                    return address;
                }
            }
        }

        throw new RuntimeException("No suitable IPv4 address found on any network interface.");
    }

    private class PeerListener implements ServiceListener {
        @Override
        public void serviceAdded(ServiceEvent event) {
            System.out.println("Service added: " + event.getName() + ". Resolving...");
            jmdns.requestServiceInfo(event.getType(), event.getName(), 1);
        }

        @Override
        public void serviceRemoved(ServiceEvent event) {
            System.out.println("Service removed: " + event.getName());
            activePeers.remove(event.getName());
        }

        @Override
        public void serviceResolved(ServiceEvent event) {
            ServiceInfo info = event.getInfo();
            String peerName = info.getName();

            String[] hostAddresses = info.getHostAddresses();
            if (hostAddresses == null || hostAddresses.length == 0) {
                System.out.println("[Discovery] Ignoring peer '" + peerName + "' because no host address was resolved.");
                return;
            }
            
            String quicPort = info.getPropertyString("quic_port");
            String wsPort = info.getPropertyString("ws_port");
            String ipAddress = hostAddresses[0];

            System.out.println("\n[Discovery] Found Peer: " + peerName);
            System.out.println("   -> IP: " + ipAddress);
            System.out.println("   -> QUIC Port: " + (quicPort != null ? quicPort : "Not Supported"));
            System.out.println("   -> WS Port: " + (wsPort != null ? wsPort : "Not Supported"));
            
            activePeers.put(peerName, info);
        }
    }
}
