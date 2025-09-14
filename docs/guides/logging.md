# Logging Guide

This project uses SLF4J for logging across all modules. Spring Boot’s default Logback configuration is applied unless overridden.

## Package levels

Recommended categories to tune in `application.yml`:

```yaml
logging:
  level:
    # Core context lifecycle
    com.syy.taskflowinsight.context: INFO
    # Change tracking internals (set DEBUG for troubleshooting)
    com.syy.taskflowinsight.tracking: INFO
    # API facade
    com.syy.taskflowinsight.api: INFO
```

For development, you can raise specific areas to DEBUG, for example:

```yaml
logging:
  level:
    com.syy.taskflowinsight.context.ZeroLeakThreadLocalManager: DEBUG
    com.syy.taskflowinsight.context.SafeContextManager: DEBUG
```

## Notes

- Use parameterized messages: `logger.warn("Detected leaked context: id={}", id)`.
- Avoid mixing `System.out` with logs in production paths; the demo code may print to console for illustration.
- If using a different logging backend, ensure SLF4J bindings are present on the classpath.

