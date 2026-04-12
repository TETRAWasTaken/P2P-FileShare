package com.kolhey.p2p.discovery;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class NetworkUtil {
    public static InetAddress getLocalIPv4Address()
    throws SocketException {
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
                if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                    if (address.isSiteLocalAddress()) {
                        return address;
                    }
                }
            }
        }
        throw new RuntimeException("No suitable IPv4 address found on any network interface.");
    }
}
