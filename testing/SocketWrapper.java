package testing;

import java.io.*;
import java.net.*;
import testing.exceptions.SocketException;

class SocketWrapper {
    private Socket socket;
    private String host;
    private int port;
    private Thread listener;

    public SocketWrapper(String host, int port) throws SocketException {
        this.host = host;
        this.port = port;
        System.out.println("Creating socket to " + host + ":" + port);
        try (socket = new Socket()){
            SocketAddress socketAddress = new InetSocketAddress(host, port);
            socket.connect(socketAddress, 5000); // 5 second timeout
            System.out.println("Socket created successfully.");
        } catch (IOException e) {
            throw new SocketException("Failed to create socket: " + e.getMessage());
        }
    }

    public void sendData(String data) throws SocketException {
        System.out.println("Sending data: " + data);
        try {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            out.println(data);
            System.out.println("Data sent successfully.");
        } catch (IOException e) {
            throw new SocketException("Failed to send data: " + e.getMessage());
        }
    }

    private void listen() throws SocketException {
        System.out.println("Listening for incoming data...");
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String receivedData;
            while ((receivedData = in.readLine()) != null) {
                System.out.println("Received data: " + receivedData);
            }
        } catch (IOException e) {
            throw new SocketException("Failed to listen for data: " + e.getMessage());
        }
    }

    public void close() throws SocketException {
        System.out.println("Closing socket.");
        try {
            socket.close();
            System.out.println("Socket closed successfully.");
        } catch (IOException e) {
            throw new SocketException("Failed to close socket: " + e.getMessage());
        }
    }

    public void startListenThread() {
        listener =  new Thread(() -> {
            try {
                listen();
            } catch (SocketException e) {
                System.err.println("Error in listen thread: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("Unexpected error in listen thread: " + e.getMessage());
            }
        });
        listener.start();
        System.out.println("Listen thread started.");
    }
}