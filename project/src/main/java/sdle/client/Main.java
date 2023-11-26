package sdle.client;

import sdle.client.states.LoginState;
import sdle.client.states.MenuState;
import sdle.client.states.State;

public class Main {
    public static void main(String[] args) {

        State state = new MenuState("test");

        while(state != null) {
            state = state.run();
        }
    }
}