package sdle.client.states;

import sdle.client.utils.Utils;

import java.util.Scanner;

public class UpdateProductState implements State {
    private final String listUUID;

    private final String user;

    private final Scanner scanner = new Scanner(System.in);

    public UpdateProductState(String user,String listUUID) {
        this.user = user;
        this.listUUID = listUUID;
    }

    @Override
    public State run() {

        // Get input for product details to be updated
        System.out.print("Enter the product name to update: ");
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

        System.out.print("Enter the new quantity: ");
        int newQuantity = Integer.parseInt(scanner.nextLine());

        // see if quantity is bigger than 0
        if (newQuantity <= 0) {
            System.out.println("Quantity must be bigger than 0.");
            System.out.println("Press enter to continue...");
            scanner.nextLine();
            Utils.clearConsole();
            return new ListProductsState(user,listUUID);
        }

        // Update the product in the list
        if (Utils.updateProductInList(user,listUUID, productName, newQuantity)) {
            Utils.updateShoppingListInServer(user,listUUID);
            System.out.println("Product updated in the shopping list.");
        } else {
            System.out.println("Failed to update product in the shopping list.");
        }

        Utils.clearConsole();

        // Transition back to the menu state
        return new ListProductsState(user,listUUID);
    }



}

