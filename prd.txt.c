Enable the Quarkus application to automatically provision a temporary database container when the application is started in **Quarkus dev mode**.

The expected developer experience is:

```bash
./mvnw quarkus:dev
```

should automatically start the required temporary database without requiring the developer to manually start a database container.

The database should be automatically available to the application using the appropriate Quarkus dev-services configuration.

## 2. Problem Statement

The application currently has a `dev` profile configured, but starting the application in Quarkus dev mode does not automatically start the database container.

This creates additional manual setup for developers. 

## 3. Goals

* Automatically start a temporary database when running Quarkus in dev mode.
* Avoid requiring developers to manually run a database container.
* Ensure the application connects to the automatically provisioned database.
* Keep production database configuration independent from dev-mode configuration.
* Make the setup work consistently for new developers and CI-based development environments where applicable.

## 4. Output 

* Identify the root cause why DB is not spinning up automatically? 
* Provide the concrete minimal plan to fix the issue. 



