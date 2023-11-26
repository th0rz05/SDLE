package sdle.client.states;


import sdle.client.utils.Utils;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Scanner;

import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZContext;

public class LoginState implements State {
    private final Scanner scanner = new Scanner(System.in);

    private final String SERVER_ADDRESS = "tcp://127.0.0.1:5555";

    @Override
    public State run() {
        System.out.print("Enter username: ");
        String username = scanner.nextLine().trim();

        sendUsernameToServer(username);

        if (createUserDatabase(username)) {
            System.out.println("Database created/connected for user: " + username);
        } else {
            System.out.println("Failed to create/connect to the database.");
        }

        // Press enter to continue
        System.out.println("Press enter to continue...");
        scanner.nextLine();

        Utils.clearConsole();

        // Transition to the menu state or other state as needed
        return new MenuState(username);
    }

    private boolean createUserDatabase(String username) {
        String dbUrl = "jdbc:sqlite:database/client/" + username + "_shopping.db";

        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            if (conn != null) {
                File dbFile = new File(username + "_shopping.db");
                if (!dbFile.exists()) {
                    String createListsTable = "CREATE TABLE IF NOT EXISTS shopping_lists ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                            + "list_uuid TEXT NOT NULL,"
                            + "list_name TEXT NOT NULL"
                            + ");";

                    String createProductsTable = "CREATE TABLE IF NOT EXISTS list_products ("
                            + "product_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                            + "list_id INTEGER NOT NULL,"
                            + "product_name TEXT NOT NULL,"
                            + "quantity INTEGER NOT NULL,"
                            + "FOREIGN KEY (list_id) REFERENCES shopping_lists(id)"
                            + ");";

                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute(createListsTable);
                        stmt.execute(createProductsTable);
                    }
                }
                return true;
            }
        } catch (SQLException e) {
            System.out.println("Error creating/connecting to database: " + e.getMessage());
        }
        return false;
    }

    private void sendUsernameToServer(String username) {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REQ);
            socket.connect(SERVER_ADDRESS);

            // Sending the username to the server
            socket.send(username.getBytes(ZMQ.CHARSET), 0);

            // Waiting for a response (optional, based on server logic)
            byte[] response = socket.recv(0);
            System.out.println("Server response: " + new String(response, ZMQ.CHARSET));
        }
    }
}
