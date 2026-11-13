# ACME Salary Manager

An HR workspace for managing annual base salaries and understanding pay across ACME's global roster. It uses Java, Spring Boot, Angular, Angular CDK, and a relational H2 database.

The Angular app lives in `frontend/` and the Spring Boot app lives in `backend/`. Both are part of this repository. Maven packages the compiled Angular app into the Spring Boot JAR.

## Run locally

Prerequisites are JDK 21, Maven 3.9, and Node 22. The backend compiles to Java 17 bytecode.

```sh
cd frontend
npm ci
npm run build
cd ..
mvn -f backend/pom.xml verify
java -jar backend/target/salary-manager-1.0.0.jar --spring.profiles.active=demo
```

Open [http://127.0.0.1:8080](http://127.0.0.1:8080). The loopback demo account is **hr** with password **acme-local-demo**. On a fresh database, the demo profile seeds 10,000 synthetic employees. Stop the server and point `DATABASE_URL` to a new H2 file to start another clean demo.

To run the seed independently, use the built JAR and a fresh database URL if needed.

```sh
java -jar backend/target/salary-manager-1.0.0.jar --spring.profiles.active=demo --spring.main.web-application-type=none
```

## Verify the HR workflow

[Watch the browser walkthrough](docs/acme-salary-demo.mp4). The recording runs against the local packaged app with a fresh demo database.

1. Sign in and search for **AC-00001**. Open the employee record.
2. Change the annual salary to **725000.25** and enter **Annual pay review**. Check the current amount and salary history.
3. Open **Pay insights**. Filter by **India** and **Engineering**. Confirm the INR group and its matching headcount, median, average, lowest and highest amounts.
4. Refresh the page to confirm session continuity. Sign out to end access.

Run `mvn -f backend/pom.xml test` for the deterministic backend suite. It checks validation, exact money and rounding, all 10,000 seed rows, filtered pagination, insights, stale writes, rollback, and access controls.

## Deploy

Build the included Dockerfile. Configure `HR_PASSWORD` as a secret, `HR_USERNAME` if desired, and `SEED_DEMO_DATA=true` for an empty assessment database. Mount a persistent volume at `/var/data`. The entrypoint sets its ownership before starting Java as the app user. Set `SESSION_COOKIE_SECURE=true` for an HTTPS deployment. The container serves the UI at `/` and exposes a health check at `/api/health`.

The included [Render Blueprint](render.yaml) provisions a single Docker web service with a persistent disk. Import it from your Git host and supply `HR_PASSWORD` when prompted. The disk requires a paid Render service. The URL and demo credential can be shared with reviewers through your assessment email.

## Project notes

- [One-page requirements](docs/requirements.md)
- [Design, performance, and AI-assisted development](docs/design.md)
