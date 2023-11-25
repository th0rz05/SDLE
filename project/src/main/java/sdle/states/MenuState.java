package sdle.states;

import sdle.utils.Utils;

import java.util.Scanner;

public class MenuState implements State {
    private final Scanner scanner = new Scanner(System.in);

    @Override
    public State run() {
        while (true) {
            display();
            String input = scanner.nextLine().trim().toLowerCase();

            switch (input) {
                case "1" -> {
                    Utils.clearConsole();
                    return new ListProductsState(0);
                }
                case "2" -> {
                    Utils.clearConsole();
                    return new CreateListState();
                }
                case "3" -> {
                    Utils.clearConsole();
                    return new ListShoppingListsState();
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

