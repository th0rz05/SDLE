package sdle;

import sdle.states.MenuState;
import sdle.states.State;

public class Main {
    public static void main(String[] args) {

        State state = new MenuState();

        while(state != null) {
            state = state.run();
        }
    }
}