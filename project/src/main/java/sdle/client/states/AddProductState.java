package sdle.client.states;

import sdle.client.utils.Utils;

import java.util.Scanner;

public class AddProductState implements State {
    private final String listUUID;

    private final String user;
    private final Scanner scanner = new Scanner(System.in);

    public AddProductState(String user, String listUUID) {
        this.user = user;
        this.listUUID = listUUID;
    }

    @Override
    public State run() {
        // Get input for product details
        System.out.print("Enter the product name: ");
        String productName = scanner.nextLine().trim();

        boolean productIsZero = false;

        // See if the product already exists in the list
        if (Utils.productExistsInList(user,listUUID, productName)) {
            if(Utils.productCounterIsZero(user,listUUID,productName)){
                productIsZero = true;
            }
            else{
                System.out.println("Product already exists in the shopping list.");
                System.out.println("Press enter to continue...");
                scanner.nextLine();
                Utils.clearConsole();
                return new ListProductsState(user,listUUID);
            }
        }

        System.out.print("Enter the quantity: ");
        int quantity = Integer.parseInt(scanner.nextLine());

        // see if quantity is bigger than 0
        if (quantity <= 0) {
            System.out.println("Quantity must be bigger than 0.");
            System.out.println("Press enter to continue...");
            scanner.nextLine();
            Utils.clearConsole();
            return new ListProductsState(user,listUUID);
        }

        if(productIsZero){
            if (Utils.updateProductInList(user,listUUID, productName, quantity)) {
                System.out.println("Product updated in the shopping list.");
            } else {
                System.out.println("Failed to update product in the shopping list.");
            }

        }
        else {
            // Save the product to the list
            if (Utils.addProductToList(user, listUUID, productName, quantity)) {
                System.out.println("Product added to the shopping list.");
            } else {
                System.out.println("Failed to add product to the shopping list.");
            }
        }

        Utils.clearConsole();

        // Transition back to the menu state
        return new ListProductsState(user,listUUID);
    }



}
