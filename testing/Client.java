package testing;

import java.io.*;
import java.net.*;
import java.util.Scanner;
import testing.exceptions.SocketException;

public class Client {
    private static SocketWrapper socket;
    private static volatile Thread connectionThread;
    private static volatile Thread serverThread;
    private static volatile boolean connectionEstablished = false;
    private static int serverPort;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter port to listen on (for incoming connections): ");
        String portInput = scanner.nextLine();
        serverPort = Integer.parseInt(portInput);
        
        System.out.print("Enter destination IP address: ");
        String host = scanner.nextLine();
        
        System.out.print("Enter destination port: ");
        String remotePortInput = scanner.nextLine();
        int remotePort = Integer.parseInt(remotePortInput);
        
        System.out.println("Starting P2P client...");
        System.out.println("Server listening on port " + serverPort);

        // Start server to listen for incoming connections
        serverThread = new Thread(() -> listenForConnections());
        serverThread.setName("ServerAcceptThread");
        serverThread.start();

        // Start thread to connect to remote peer
        connectionThread = new Thread(() -> connectToRemote(host, remotePort));
        connectionThread.setName("ConnectionRequestThread");
        connectionThread.start();

        // Wait for connection to be established
        while (!connectionEstablished) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (socket == null) {
            System.err.println("Failed to establish connection");
            return;
        }

        // Connection established, start listening for messages
        socket.startListenThread();

        // Message input loop
        while (true) {
            System.out.print("You: ");
            String message = scanner.nextLine();
            if (message.equalsIgnoreCase("exit")) {
                break;
            }
            try {
                socket.sendData(message);
            } catch (SocketException e) {
                System.err.println("Error sending message: " + e.getMessage());
                break;
            }
        }

        scanner.close();
    }

    private static void listenForConnections() {
        try (ServerSocket serverSocket = new ServerSocket(serverPort)) {
            System.out.println("Server socket created, waiting for incoming connections...");
            while (!connectionEstablished) {
                try {
                    Socket acceptedSocket = serverSocket.accept();
                    
                    synchronized (Client.class) {
                        if (!connectionEstablished) {
                            System.out.println("Incoming connection received from: " + 
                                    acceptedSocket.getInetAddress().getHostAddress());
                            connectionEstablished = true;
                            try {
                                socket = new SocketWrapper(acceptedSocket);
                            } catch (SocketException e) {
                                System.err.println("Error wrapping accepted socket: " + e.getMessage());
                                connectionEstablished = false;
                                continue;
                            }
                            
                            // Interrupt the outgoing connection thread
                            if (connectionThread != null && connectionThread.isAlive()) {
                                connectionThread.interrupt();
                            }
                        }
                    }
                } catch (SocketTimeoutException e) {
                    // Timeout, try again
                    continue;
                } catch (InterruptedIOException e) {
                    System.out.println("Server accept thread interrupted - connection established");
                    break;
                }
            }
        } catch (IOException e) {
            if (!connectionEstablished) {
                System.err.println("Server socket error: " + e.getMessage());
            }
        }
    }

    private static void connectToRemote(String host, int port) {
        int maxRetries = 30; // Try for ~30 seconds with 1 second intervals
        int retryCount = 0;
        
        while (!connectionEstablished && retryCount < maxRetries) {
            try {
                System.out.println("Attempting to connect to " + host + ":" + port + "... (attempt " + (retryCount + 1) + ")");
                socket = new SocketWrapper(host, port);
                
                synchronized (Client.class) {
                    if (!connectionEstablished) {
                        connectionEstablished = true;
                        System.out.println("Outgoing connection established!");
                        
                        // Interrupt the server accept thread
                        if (serverThread != null && serverThread.isAlive()) {
                            serverThread.interrupt();
                        }
                    }
                }
                break; // Connection successful, exit loop
            } catch (SocketException e) {
                retryCount++;
                if (retryCount < maxRetries && !connectionEstablished) {
                    try {
                        Thread.sleep(1000); // Wait 1 second before retrying
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        if (!connectionEstablished && retryCount >= maxRetries) {
            System.err.println("Failed to connect after " + maxRetries + " attempts");
        }
    }
}
