package sdle.client.states;

import sdle.client.utils.Utils;

import java.sql.*;
import java.util.Scanner;
import java.util.UUID;



public class CreateListState implements State {
    private final Scanner scanner = new Scanner(System.in);


    private final String user;

    public CreateListState(String user) {
        this.user = user;
    }

    @Override
    public State run() {
        System.out.println("Creating a new shopping list...");

        // Get the name for the shopping list from the user
        System.out.print("Enter the name for the shopping list: ");
        String shoppingListName = scanner.nextLine().trim();


        // See if the shopping list already exists
        if (Utils.shoppingListExists(user, shoppingListName)){
            System.out.println("Shopping List already exists.");
            System.out.println("Press enter to continue...");
            scanner.nextLine();
            Utils.clearConsole();
            return new MenuState(user);
        }

        // Perform actions for creating a shopping list here
        String shoppingListUUID = generateUniqueID();

        // Save the shopping list to the database
        if (saveShoppingListToDatabase(shoppingListUUID, shoppingListName)) {
            Utils.sendShoppingListToServer(shoppingListUUID, shoppingListName);
            System.out.println("Shopping List created with ID: " + shoppingListUUID);
        } else {
            System.out.println("Failed to save Shopping List to the database.");
        }

        // Press enter to continue
        System.out.println("Press enter to continue...");
        scanner.nextLine();

        Utils.clearConsole();

        // Transition back to the menu state
        return new MenuState(user);
    }


    private String generateUniqueID() {
        return UUID.randomUUID().toString();
    }

    private boolean saveShoppingListToDatabase(String shoppingListUUID, String shoppingListName) {
        String url = "jdbc:sqlite:database/client/" + user + "_shopping.db";

        try (Connection connection = DriverManager.getConnection(url)) {
            if (connection != null) {
                String sql = "INSERT INTO shopping_lists (list_uuid, list_name) VALUES (?, ?)";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setString(1, shoppingListUUID);
                    pstmt.setString(2, shoppingListName);
                    pstmt.executeUpdate();
                    return true;
                }
            }
        } catch (SQLException e) {
            System.out.println("Error saving Shopping List to the database: " + e.getMessage());
        }
        return false;
    }
}


