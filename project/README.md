# Shopping list app

## How to run the app

Open the terminal in the project folder and run:

```bash
./gradlew clean clientJar
./gradlew clean serverJar
```

Then to run the server:

```bash
java -jar build/libs/server.jar
```

And to run the client:

```bash
java -jar build/libs/client.jar
```