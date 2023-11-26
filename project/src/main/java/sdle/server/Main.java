package sdle.server;

import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZContext;

public class Main {
    public static void main(String[] args) {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REP);
            socket.bind("tcp://*:5555");

            // Announce that the server is running
            System.out.println("Server is running on port 5555\n\n");

            while (!Thread.currentThread().isInterrupted()) {
                byte[] request = socket.recv();
                String username = new String(request);
                System.out.println("Received username: " + username);

                // You can add logic here to process the username or perform actions based on it

                // Sending a response back to the client (optional)
                String response = "Server received username: " + username;
                socket.send(response.getBytes(ZMQ.CHARSET));
            }
        }
    }
}

