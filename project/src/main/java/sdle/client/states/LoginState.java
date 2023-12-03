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

    @Override
    public State run() {
        System.out.print("Enter username: ");
        String username = scanner.nextLine().trim();


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
                            + "list_uuid TEXT PRIMARY KEY,"
                            + "list_name TEXT UNIQUE NOT NULL"
                            + ");";

                    String createProductsTable = "CREATE TABLE IF NOT EXISTS list_products ("
                            + "list_uuid TEXT NOT NULL,"
                            + "product_name TEXT NOT NULL,"
                            + "quantity INTEGER NOT NULL,"
                            + "PRIMARY KEY (list_uuid, product_name),"
                            + "FOREIGN KEY (list_uuid) REFERENCES shopping_lists(list_uuid)"
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
}
