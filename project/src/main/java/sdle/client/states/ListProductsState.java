package sdle.client.states;

import sdle.client.utils.Utils;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

public class ListProductsState implements State {
    private final Scanner scanner = new Scanner(System.in);

    private final String user;
    private String listUUID;

    public ListProductsState(String user, String listUUID) {
        this.user = user;
        this.listUUID = listUUID;
    }

    @Override
    public State run(){
        //see if null
        if (listUUID == null){
            // Get input for the shopping list ID
            System.out.print("Enter the shopping list name: ");
            String listName = scanner.nextLine().trim();

            // See if the shopping list exists
            if (!Utils.shoppingListExists(user,listName)) {
                System.out.println("Shopping list does not exist.");
                System.out.println("Press enter to continue...");
                scanner.nextLine();
                Utils.clearConsole();
                return new MenuState(user);
            }

            listUUID = getListUUID(listName);
        }

        Utils.updateShoppingListInServer(user,listUUID);

        boolean updated = false;

        if(Utils.updateListFromServer(user,listUUID)){
            updated = true;
        }

        long lastUpdateTime = System.currentTimeMillis();


        Utils.clearConsole();

        if(updated){
            System.out.println("Shopping list updated from server.");
        }
        else{
            System.out.println("Shopping list not updated from server.");
        }

        // Display products in the specified list
        Utils.displayListProducts(user,listUUID);

        displayOptions();


        while (true) {
            long currentTime = System.currentTimeMillis();
            long elapsedTime = currentTime - lastUpdateTime;

            // Check if one second has elapsed
            if (elapsedTime >= 2000) { // 1000 milliseconds = 1 second

                return new ListProductsState(user,listUUID);
            }

            // Check if the user has typed anything without blocking
            try {
                if (System.in.available() > 0) {
                    byte[] inputBytes = new byte[System.in.available()];
                    System.in.read(inputBytes);
                    String input = new String(inputBytes).trim().toLowerCase();

                    switch (input) {
                        case "1" -> {
                            Utils.clearConsole();
                            return new AddProductState(user,listUUID);
                        }
                        case "2" -> {
                            Utils.clearConsole();
                            return new RemoveProductState(user,listUUID);
                        }
                        case "3" -> {
                            Utils.clearConsole();
                            return new UpdateProductState(user,listUUID);
                        }
                        case "4" -> {
                            Utils.clearConsole();
                            return new DeleteListState(user,listUUID);
                        }
                        case "q" -> {
                            Utils.clearConsole();
                            // Transition back to the menu state
                            return new MenuState(user);
                        }
                        default -> {
                            Utils.clearConsole();
                            System.out.println("Invalid input. Please try again.");
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private String getListUUID(String listName) {
        String url = "jdbc:sqlite:database/client/" + user + "_shopping.db";

        try (Connection connection = DriverManager.getConnection(url)) {
            if (connection != null) {
                String sql = "SELECT list_uuid FROM shopping_lists WHERE list_name = ?";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setString(1, listName);
                    ResultSet rs = pstmt.executeQuery();

                    return rs.getString("list_uuid");
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching shopping list ID: " + e.getMessage());
        }

        return null;
    }

    private void displayOptions() {
        System.out.println("\n\n\n");
        System.out.println("------ Menu ------");
        System.out.println("1 - Add products");
        System.out.println("2 - Remove products");
        System.out.println("3 - Update products");
        System.out.println("4 - Delete shopping list");
        System.out.println("Enter 'Q' to return to the main menu");
        System.out.print("Your choice: ");
    }

}
