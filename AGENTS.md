# Repository Guidelines

## Project Structure & Module Organization
- `src/main/java`: Application code (Spring Boot, package `com.syy.taskflowinsight`).
- `src/main/resources`: Config and assets (e.g., `application.yml`).
- `src/test/java`: JUnit 5 tests; mirror package structure.
- `docs/`: Architecture, specs, and guides (start at `docs/architecture/README.md`).
- `pom.xml`: Maven config (Java 21, Spring Boot 3.5.x). Use the Maven Wrapper in this repo.

## Build, Test, and Development Commands
- `./mvnw clean verify`: Full build with tests.
- `./mvnw test`: Run unit/integration tests.
- `./mvnw spring-boot:run`: Start the app locally.
- `./mvnw package`: Create runnable JAR in `target/`.
- Example: `JAVA_TOOL_OPTIONS="-Dspring.profiles.active=dev" ./mvnw spring-boot:run` to use a profile.

## Coding Style & Naming Conventions
- Java 21, 4‑space indentation, 120‑col soft wrap.
- Packages: lowercase, dot‑separated (e.g., `com.syy.taskflowinsight.core`).
- Classes: PascalCase; methods/fields: camelCase; constants: UPPER_SNAKE_CASE.
- Prefer constructor injection; use Lombok (`@Getter`, `@Setter`, `@RequiredArgsConstructor`) sparingly and explicitly.
- Public APIs: Javadoc class/method summary; include parameter/return tags when non‑trivial.

## Testing Guidelines
- Framework: JUnit 5 via `spring-boot-starter-test`.
- Test classes mirror sources and end with `Tests` (e.g., `TaskServiceTests`).
- Use `@SpringBootTest` only when context is required; otherwise prefer slice/unit tests.
- Run locally with `./mvnw test`; add assertions that cover edge cases.

## Commit & Pull Request Guidelines
- Commits: present tense, concise subject (≤72 chars), details in body when needed.
  - Example: `feat(api): add task querying by sessionId`.
- PRs: clear description, linked issues (`Closes #123`), test evidence, and screenshots for user‑facing changes.
- Keep PRs focused and small; include `docs/` updates when behavior or APIs change.

## Security & Configuration Tips
- Externalize secrets via env vars or profiles; never commit secrets to `application.yml`.
- Prefer `application-<profile>.yml`; select with `SPRING_PROFILES_ACTIVE`.
- Validate inputs at boundaries; avoid exposing internal exceptions in controllers.
