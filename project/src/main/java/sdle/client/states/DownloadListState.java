package sdle.client.states;

import sdle.client.utils.Utils;

import java.util.Scanner;

public class DownloadListState implements State{

    private final String user;

    private final Scanner scanner = new Scanner(System.in);

    public DownloadListState(String user) {
        this.user = user;
    }

    @Override
    public State run() {
        System.out.println("Downloading a shopping list...");

        // input the uuid of the shopping list to be downloaded
        System.out.print("Enter the uuid of the shopping list to be downloaded: ");
        String shoppingListUUID = scanner.nextLine().trim();

        // Perform actions for downloading a shopping list here
        if (Utils.getListFromServer(user,shoppingListUUID)) {
            System.out.println("Shopping List downloaded with ID: " + shoppingListUUID);
        } else {
            System.out.println("Failed to download Shopping List from the server.");
        }

        return new ListProductsState(user,shoppingListUUID);

    }

}
