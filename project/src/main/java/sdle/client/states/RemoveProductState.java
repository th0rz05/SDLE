package sdle.client.states;

import sdle.client.utils.Utils;

import java.util.Scanner;

public class RemoveProductState implements State {
    private final String listUUID;

    private final String user;
    private final Scanner scanner = new Scanner(System.in);

    public RemoveProductState(String user, String listUUID) {
        this.user = user;
        this.listUUID = listUUID;
    }

    @Override
    public State run() {
        // Get input for product details to be removed
        System.out.print("Enter the product name to remove: ");
        String productName = scanner.nextLine().trim();

        // See if the product exists in the list
        if (!Utils.productExistsInList(user,listUUID, productName) ||
                Utils.productCounterIsZero(user,listUUID,productName)) {
            System.out.println("Product does not exist in the shopping list.");
            System.out.println("Press enter to continue...");
            scanner.nextLine();
            Utils.clearConsole();
            return new ListProductsState(user,listUUID);
        }

        // Remove the product from the list
        if (Utils.removeProductFromList(user,listUUID, productName)) {
            System.out.println("Product removed from the shopping list.");
        } else {
            System.out.println("Failed to remove product from the shopping list.");
        }

        Utils.clearConsole();

        // Transition back to the menu state
        return new ListProductsState(user,listUUID);
    }

}

