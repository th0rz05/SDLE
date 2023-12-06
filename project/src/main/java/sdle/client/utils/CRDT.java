package sdle.client.utils;

import java.util.HashMap;
import java.util.Map;
import java.lang.reflect.Type;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class CRDT {

    // PNCounter subclass
    public static class PNCounter {
        private final Map<String, Integer> P;
        private final Map<String, Integer> N;

        public PNCounter() {
            P = new HashMap<>();
            N = new HashMap<>();
        }

        public void increment(String userId, int amount) {
            P.put(userId, P.getOrDefault(userId, 0) + amount);
        }

        public void decrement(String userId, int amount) {
            N.put(userId, N.getOrDefault(userId, 0) + amount);
        }

        public int value() {
            int sumP = P.values().stream().mapToInt(Integer::intValue).sum();
            int sumN = N.values().stream().mapToInt(Integer::intValue).sum();
            return sumP - sumN;
        }

        public static PNCounter merge(PNCounter X, PNCounter Y) {
            PNCounter Z = new PNCounter();
            for (String key : X.P.keySet()) {
                Z.P.put(key, Math.max(X.P.getOrDefault(key, 0), Y.P.getOrDefault(key, 0)));
            }
            for (String key : Y.P.keySet()) {
                Z.P.putIfAbsent(key, Y.P.get(key));
            }
            for (String key : X.N.keySet()) {
                Z.N.put(key, Math.max(X.N.getOrDefault(key, 0), Y.N.getOrDefault(key, 0)));
            }
            for (String key : Y.N.keySet()) {
                Z.N.putIfAbsent(key, Y.N.get(key));
            }
            return Z;
        }
    }

    // MapPNCounter subclass
    public static class MapPNCounter {
        private final Map<String, PNCounter> state;

        public MapPNCounter() {
            state = new HashMap<>();
        }

        public Map<String, PNCounter> value() {
            return state;
        }

        public void insert(String itemName){
            PNCounter counter = new PNCounter();
            state.put(itemName, counter);
        }
        public void insert(String itemName, PNCounter counter) {
            state.put(itemName, counter);
        }

        public void insert(String itemName, String userID, int amount){
            PNCounter counter = new PNCounter();
            counter.increment(userID,amount);
            state.put(itemName, counter);
        }

        public void increment(String itemName, String userID, int amount) {
            PNCounter counter = state.getOrDefault(itemName, new PNCounter());
            counter.increment(userID,amount);
            state.put(itemName, counter);
        }

        public void decrement(String itemName, String userID, int amount){
            PNCounter counter = state.getOrDefault(itemName, new PNCounter());
            counter.decrement(userID,amount);
            state.put(itemName, counter);
        }

        public void remove(String itemName, String userID) {
            PNCounter counter = state.getOrDefault(itemName, new PNCounter());
            counter.decrement(userID, counter.value());
            state.put(itemName, counter);
        }

        public int itemValue(String itemName) {
            PNCounter counter = state.getOrDefault(itemName, new PNCounter());
            return counter.value();
        }

        public boolean contains(String itemName) {
            return state.containsKey(itemName);
        }


        public static MapPNCounter merge(MapPNCounter local ,MapPNCounter remote) {
            MapPNCounter merged = new MapPNCounter();
            for (String key : local.value().keySet()) {
                merged.state.put(key, PNCounter.merge(local.value().getOrDefault(key, new PNCounter()), remote.value().getOrDefault(key, new PNCounter())));
            }
            for (String key : remote.value().keySet()) {
                merged.state.putIfAbsent(key, remote.value().get(key));
            }
            return merged;
        }

        public String toJson() {
            Gson gson = new Gson();
            return gson.toJson(this.state);
        }

        public void display() {
            state.forEach((itemName, counter) -> {
                if(counter.value() > 0)
                    System.out.println(itemName + " : " + counter.value());
            });
        }
    }

    public static MapPNCounter toMapPNCounter(String json) {
        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, PNCounter>>(){}.getType();
        Map<String, PNCounter> map = gson.fromJson(json, type);

        MapPNCounter mapCounter = new MapPNCounter();
        map.forEach(mapCounter::insert);
        return mapCounter;
    }

    // Main method for testing
    public static void main(String[] args) {
        // List creation on Map format
        MapPNCounter natal = new MapPNCounter();
        // List updated to json
        System.out.println(natal.toJson());

        // Item creation and increment from Tiago
        natal.insert("Aletria", "Tiago", 7);
        // Item decrement from Guilherme
        natal.decrement("Aletria", "Guilherme", 2);
        // Item decrement from Guilherme
        natal.decrement("Aletria", "Guilherme", 2);
        // Item update increment from Andre
        natal.increment("Aletria", "Andre", 5);


        // New list creation
        MapPNCounter natal2 = new MapPNCounter();
        // Item creation
        natal2.insert("Aletria");
        // Item increment from Tiago
        natal2.increment("Aletria", "Tiago", 1);
        // Item increment from Tiago
        natal2.increment("Aletria", "Tiago", 2);
        // Item increment from Tiago
        natal2.increment("Aletria", "Tiago", 2);
        // Item decrement from Guilherme
        natal2.decrement("Aletria", "Guilherme", 5);
        // Item creation and increment from Andre
        natal2.insert("Rabanadas", "Andre", 2);
        // Item increment from Andre
        natal2.increment("Rabanadas", "Andre", 5);
        // Item increment from Andre
        natal2.decrement("Rabanadas", "Andre", 3);
        // Item increment from Andre
        natal2.increment("Aletria", "Andre", 1);
        // Item decrement from Tiago
        natal2.decrement("Rabanadas", "Tiago", 4);

        // Quantity of Aletria in natal list
        System.out.println(natal.itemValue("Aletria"));
        // Quantity of Aletria in natal2 list
        System.out.println(natal2.itemValue("Aletria"));
        // Quantity of Rabanadas in natal2 list
        System.out.println(natal2.itemValue("Rabanadas"));


        // For with all items in natal2 list
        natal2.value().forEach((itemName, counter) -> {
            System.out.println(itemName + ": " + counter.value());
        });

        // Transform natal list to json
        String json1 = natal.toJson();
        System.out.println(json1);

        // Transform natal2 list to json
        String json2 = natal2.toJson();
        System.out.println(json2);

        // Transform json1 to natal3 list
        MapPNCounter natal3 = toMapPNCounter(json1);


        // Transform natal list to json
        String json3 = natal.toJson();
        System.out.println(json3);

        // Remove item Aletria from natal list
        natal.remove("Aletria", "Tiago");
        // Transform natal list to json
        String json4 = natal.toJson();
        System.out.println(json4);

        // For with all items in natal list
        natal.value().forEach((itemName, counter) -> {
            System.out.println(itemName + ": " + counter.value());
        });




    }
}
