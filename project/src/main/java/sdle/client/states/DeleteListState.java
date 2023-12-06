package sdle.client.states;

import sdle.client.utils.Utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.Scanner;

public class DeleteListState implements State {
    private final String listUUID;

    private final String user;
    private final Scanner scanner = new Scanner(System.in);

    public DeleteListState(String user, String listUUID) {
        this.user = user;
        this.listUUID = listUUID;
    }

    @Override
    public State run() {

        // Get the list name
        String listName = getListName(listUUID);

        // Confirm deletion with user
        System.out.println("Are you sure you want to delete the list: " + listName + "? (y/n)");
        String confirmation = scanner.nextLine().trim().toLowerCase();

        if (confirmation.equals("y")) {
            // Delete the list
            if (deleteList(listUUID)) {
                System.out.println("Shopping list deleted.");
            } else {
                System.out.println("Failed to delete shopping list.");
            }
        } else {
            System.out.println("Deletion canceled.");
        }

        // Press enter to continue
        System.out.println("Press enter to continue...");
        scanner.nextLine();

        Utils.clearConsole();

        // Transition back to the menu state
        return new MenuState(user);
    }

    private String getListName(String listUUID) {
        String url = "jdbc:sqlite:database/client/" + user + "_shopping.db";
        String listName = null;

        try (Connection connection = DriverManager.getConnection(url)) {
            if (connection != null) {
                String sql = "SELECT list_name FROM shopping_lists WHERE list_uuid = ?";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setString(1, listUUID);
                    ResultSet rs = pstmt.executeQuery();

                    if (rs.next()) {
                        listName = rs.getString("list_name");
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error retrieving list name: " + e.getMessage());
        }

        return listName;
    }

    private boolean deleteList(String listUUID) {
        String url = "jdbc:sqlite:database/client/" + user + "_shopping.db";

        try (Connection connection = DriverManager.getConnection(url)) {
            if (connection != null) {
                String sql = "DELETE FROM shopping_lists WHERE list_uuid = ?";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setString(1, listUUID);
                    pstmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.out.println("Error deleting shopping list: " + e.getMessage());
            return false;
        }

        return true;
    }
}

