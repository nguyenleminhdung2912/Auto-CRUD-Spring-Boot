Short description:
A small Spring Boot tool that generates full-stack CRUD projects from a SQL schema. Upload a .sql schema (and optional overrides) via the built-in web UI and download a ready-to-build Spring Boot project ZIP.

Quick start
- Build: use the included Maven wrapper to build the project (Windows: .\\mvnw.cmd package, Linux/Mac: ./mvnw package).
- Run: java -jar target/AutoCRUD-1.0.0.jar or use the wrapper: ./mvnw spring-boot:run
- Open the UI: http://localhost:8080 and upload your .sql file, optional overrides, and a project name. The generated ZIP will be downloaded.

Important configuration notes
- BEFORE running the generator app, update database connection settings in src/main/resources/application.properties to match your local PostgreSQL (url, username, password).
- IMPORTANT: the projects you generate and download will include src/main/resources/application.yml — you MUST edit that generated application.yml to configure the database for the generated project before building/running it. The generator's own application.properties is only for the generator app itself.

Files of interest
- src/main/java/com/project/autocrud — main app and generator logic
- src/main/resources/static — small frontend (index.html, app.js, app.css)
- src/main/resources/templates — FreeMarker templates used to generate code

Contributing
- Feel free to open issues or PRs. Keep changes small and focused (template improvements, support for other DBs, expanded UI).

License
MIT
