package sdle.states;

import sdle.utils.Utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Scanner;

public class ListShoppingListsState implements State {

    @Override
    public State run() {

        // Display all shopping lists from the database
        displayShoppingLists();

        // Press enter to continue
        System.out.println("Press enter to continue...");
        new Scanner(System.in).nextLine();

        Utils.clearConsole();

        // Transition back to the menu state
        return new MenuState();
    }

    private void displayShoppingLists() {
        String url = "jdbc:sqlite:database/shopping.db";

        try (Connection connection = DriverManager.getConnection(url)) {
            if (connection != null) {
                String sql = "SELECT list_uuid, list_name FROM shopping_lists";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    ResultSet rs = pstmt.executeQuery();

                    System.out.println("------ Shopping Lists ------");
                    while (rs.next()) {
                        String listID = rs.getString("list_uuid");
                        String listName = rs.getString("list_name");
                        System.out.println("ID: " + listID + " | Name: " + listName);
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching Shopping Lists: " + e.getMessage());
        }
    }
}

