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
                case "c" -> {
                    Utils.clearConsole();
                    return new CreateListState();
                }
                case "l" -> {
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
        System.out.println("Enter 'C' to create a shopping list");
        System.out.println("Enter 'L' to list all shopping lists");
        System.out.println("Enter 'Q' to quit");
        System.out.print("Your choice: ");
    }
}

