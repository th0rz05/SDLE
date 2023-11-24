package sdle.states;

import sdle.utils.Utils;

import java.util.Scanner;
import java.util.UUID;

public class CreateListState implements State {
    private final Scanner scanner = new Scanner(System.in);

    @Override
    public State run() {

        System.out.println("Creating a new shopping list...");
        // Perform actions for creating a shopping list here
        String shoppingListID = generateUniqueID();
        System.out.println("Shopping List created with ID: " + shoppingListID);

        //press enter to continue
        System.out.println("Press enter to continue...");
        scanner.nextLine();

        Utils.clearConsole();

        // Transition back to the menu state
        return new MenuState();
    }

    private String generateUniqueID() {
        return UUID.randomUUID().toString();
    }
}

