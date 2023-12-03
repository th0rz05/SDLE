# Shopping list app

## How to run the app

Open the terminal in the project folder and run:

```bash
./gradlew clean clientJar serverJar routerJar
```

To run the router:

```bash
java -jar build/libs/router.jar
```

Then to run the server:

```bash
java -jar build/libs/server.jar <id>
```

And to run the client:

```bash
java -jar build/libs/client.jar
```