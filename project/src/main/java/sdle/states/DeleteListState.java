package sdle.states;

import sdle.utils.Utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.Scanner;

public class DeleteListState implements State {
    private final int listId;
    private final Scanner scanner = new Scanner(System.in);

    public DeleteListState(int listId) {
        this.listId = listId;
    }

    @Override
    public State run() {

        // Get the list name
        String listName = getListName(listId);

        // Confirm deletion with user
        System.out.println("Are you sure you want to delete the list: " + listName + "? (y/n)");
        String confirmation = scanner.nextLine().trim().toLowerCase();

        if (confirmation.equals("y")) {
            // Delete the list
            if (deleteList(listId)) {
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
        return new MenuState();
    }

    private String getListName(int listId) {
        String url = "jdbc:sqlite:database/shopping.db"; // Replace with your database URL
        String listName = null;

        try (Connection connection = DriverManager.getConnection(url)) {
            if (connection != null) {
                String sql = "SELECT list_name FROM shopping_lists WHERE id = ?";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setInt(1, listId);
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

    private boolean deleteList(int listId) {
        String url = "jdbc:sqlite:database/shopping.db"; // Replace with your database URL

        try (Connection connection = DriverManager.getConnection(url)) {
            if (connection != null) {
                String deleteProductsSql = "DELETE FROM list_products WHERE list_id = ?";
                String deleteListSql = "DELETE FROM shopping_lists WHERE id = ?";

                // Delete products associated with the list
                try (PreparedStatement pstmtProducts = connection.prepareStatement(deleteProductsSql)) {
                    pstmtProducts.setInt(1, listId);
                    pstmtProducts.executeUpdate();
                }

                // Delete the list
                try (PreparedStatement pstmtList = connection.prepareStatement(deleteListSql)) {
                    pstmtList.setInt(1, listId);
                    int rowsAffected = pstmtList.executeUpdate();
                    return rowsAffected > 0;
                }
            }
        } catch (SQLException e) {
            System.out.println("Error deleting shopping list: " + e.getMessage());
        }
        return false;
    }
}

