package testing;

import java.util.Scanner;
import testing.exceptions.SocketException;

public class Client {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter host: ");
        String host = scanner.nextLine();
        System.out.print("Enter port: ");
        int port = scanner.nextInt();
        scanner.nextLine();

        SocketWrapper socket;
        try {
            socket = new SocketWrapper(host, port);
            socket.startListenThread();
        } catch (Exception e) {
            System.err.println("Error creating socket: " + e.getMessage());
            return;
        }

        while (true) {
            System.out.print("You: ");
            String message = scanner.nextLine();
            try {
                socket.sendData(message);
            } catch (SocketException e) {
                System.err.println("Error sending message: " + e.getMessage());
                break;
            }
        }

    }
}
