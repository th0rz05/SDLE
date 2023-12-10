# Shopping list app

## How to run the app

Open the terminal in the project folder and run:

```bash
mkdir -p database/client
mkdir -p database/server
````

```bash
./gradlew clean clientJar serverJar routerJar
```

To run the router:

```bash
java -jar build/libs/router.jar <id> 
```

Then to run the server:

```bash
java -jar build/libs/server.jar <id> [JoinHashRing]
```

And to run the client:

```bash
java -jar build/libs/client.jar
```

## Demo

[![Demo Video](https://img.youtube.com/vi/9sGed0RnRms/0.jpg)](https://www.youtube.com/watch?v=9sGed0RnRms)