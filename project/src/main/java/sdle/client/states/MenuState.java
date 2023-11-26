package sdle.client.states;

import sdle.client.utils.Utils;

import java.util.Scanner;

public class MenuState implements State {
    private final Scanner scanner = new Scanner(System.in);
    private final String user;

    public MenuState(String user) {
        this.user = user;
        System.out.println("Welcome, " + user + "!\n\n\n");

    }

    @Override
    public State run() {
        while (true) {
            display();
            String input = scanner.nextLine().trim().toLowerCase();

            switch (input) {
                case "1" -> {
                    Utils.clearConsole();
                    return new ListProductsState(user,0);
                }
                case "2" -> {
                    Utils.clearConsole();
                    return new CreateListState(user);
                }
                case "3" -> {
                    Utils.clearConsole();
                    return new ListShoppingListsState(user);
                }
                case "q" -> {
                    Utils.clearConsole();
                    System.out.println("Exiting...");
                    return null;
                }
                default -> {
                    Utils.clearConsole();
                    System.out.println("Invalid input. Please try again.");
                }
            }
        }
    }

    private void display() {
        System.out.println("------ Menu ------");
        System.out.println("1 - Open a shopping list");
        System.out.println("2 - Create a shopping list");
        System.out.println("3 - List all shopping lists");
        System.out.println("Enter 'Q' to quit");
        System.out.print("Your choice: ");
    }
}

