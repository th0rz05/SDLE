package sdle.client;

import sdle.client.states.LoginState;
import sdle.client.states.State;

public class Client {
    public static void main(String[] args) {

        State state = new LoginState();

        while(state != null) {
            state = state.run();
        }
    }
}