# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

TaskFlowInsight is a Spring Boot 3.5.5 application running on Java 21. It's a minimal Spring Web application with Lombok integration for reducing boilerplate code.

## Development Commands

### Build and Run
- Build the project: `./mvnw clean compile`
- Run the application: `./mvnw spring-boot:run`
- Package the application: `./mvnw clean package`
- Run packaged JAR: `java -jar target/TaskFlowInsight-0.0.1-SNAPSHOT.jar`

### Testing
- Run all tests: `./mvnw test`
- Run a specific test class: `./mvnw test -Dtest=TaskFlowInsightApplicationTests`
- Run tests with coverage: `./mvnw test jacoco:report` (if jacoco is added)

### Development Workflow
- Clean build: `./mvnw clean compile`
- Verify build and tests: `./mvnw clean verify`

## Project Structure

```
src/
├── main/java/com/syy/taskflowinsight/
│   └── TaskFlowInsightApplication.java    # Main Spring Boot application class
├── main/resources/
│   └── application.yml                    # Configuration (server port: 19090)
└── test/java/com/syy/taskflowinsight/
    └── TaskFlowInsightApplicationTests.java # Basic Spring Boot test
```

## Architecture

- **Framework**: Spring Boot 3.5.5 with Spring Web starter
- **Java Version**: Java 21
- **Build Tool**: Maven with wrapper (`mvnw`)
- **Code Generation**: Lombok for reducing boilerplate code
- **Server Port**: 19090 (configured in application.yml)

## Key Dependencies

- `spring-boot-starter-web`: Web MVC framework and embedded Tomcat
- `lombok`: Code generation for getters, setters, constructors, etc.
- `spring-boot-starter-test`: Testing framework including JUnit 5

## Maven Configuration Notes

The project uses Maven compiler plugin with Lombok annotation processing configured. The Spring Boot Maven plugin excludes Lombok from the final JAR since it's compile-time only.