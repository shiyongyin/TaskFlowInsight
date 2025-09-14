# TaskFlowInsight Configuration Guide

## Table of Contents
1. [Basic Configuration](#basic-configuration)
2. [Phase 1 Features](#phase-1-features)
3. [Phase 2 Features](#phase-2-features)
4. [Phase 3 Features](#phase-3-features)
5. [Performance Tuning](#performance-tuning)

## Basic Configuration

### Application Properties
```yaml
server:
  port: 19090

spring:
  application:
    name: TaskFlowInsight
```

## Phase 1 Features

### Change Tracking
```yaml
tfi:
  change-tracking:
    enabled: true                    # Enable/disable change tracking
    max-tracked-objects: 1000        # Maximum objects to track
    cleanup-interval-minutes: 10     # Cleanup interval in minutes
    include-timestamps: true         # Include timestamps in changes
    deep-tracking: false              # Enable deep object tracking
```

### Thread Context
```yaml
tfi:
  context:
    enabled: true                    # Enable thread context
    max-contexts: 100                # Maximum concurrent contexts
    propagate-to-child-threads: true # Propagate context to child threads
```

## Phase 2 Features

### Export Configuration
```yaml
tfi:
  export:
    xml:
      enabled: true                  # Enable XML export
      pretty-print: true             # Pretty print XML output
      include-metadata: true         # Include metadata in export
    
    csv:
      enabled: true                  # Enable CSV export
      delimiter: ","                 # CSV delimiter
      quote-char: "\""              # Quote character
      include-header: true           # Include header row
```

### Session Management
```yaml
tfi:
  session:
    enabled: true                    # Enable session tracking
    max-sessions: 100                # Maximum concurrent sessions
    session-timeout-minutes: 30     # Session timeout
    cleanup-expired: true            # Auto-cleanup expired sessions
```

### Actuator Endpoints
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,tfi,tfi-advanced
  endpoint:
    tfi:
      enabled: true                  # Enable TFI endpoint
    tfi-advanced:
      enabled: true                  # Enable advanced TFI endpoint
```

## Phase 3 Features

### Deep Snapshot (NEW)
```yaml
tfi:
  change-tracking:
    snapshot:
      enable-deep: false                # Enable deep object traversal
      max-depth: 3                      # Maximum traversal depth
      max-stack-depth: 1000            # Stack depth limit
      time-budget-ms: 50               # Time budget per snapshot
      collection-summary-threshold: 100 # When to summarize collections
      metrics-enabled: true            # Track performance metrics
      include-patterns: []             # Paths to include (empty=all)
      exclude-patterns:                # Paths to exclude
        - "*.password"
        - "*.secret"
        - "*.token"
        - "*.key"
        - "*.credential"
```

### Collection Summary
```yaml
tfi:
  collection-summary:
    enabled: true                    # Enable collection summarization
    max-size: 100                    # Threshold for summarization
    max-examples: 10                 # Maximum examples to include
    compute-statistics: true         # Compute numeric statistics
    include-type-distribution: true  # Include type distribution
    sensitive-words:                 # Words to filter/mask
      - password
      - secret
      - token
      - key
```

### Path Matcher
```yaml
tfi:
  change-tracking:
    path-matcher:
      cache-size: 1000              # Pattern cache size
      pattern-max-length: 256        # Maximum pattern length
      max-wildcards: 10              # Maximum wildcards in pattern
      result-cache-multiplier: 10   # Result cache size multiplier
      preload-patterns:              # Patterns to preload
        - "*.password"
        - "*.secret"
        - "**.internal"
```

### Performance Benchmarking
```yaml
tfi:
  performance:
    enabled: false                   # Enable performance benchmarking
    warmup-iterations: 1000         # Warmup iterations
    measurement-iterations: 10000   # Measurement iterations
    thread-count: 4                  # Thread count for concurrent tests
    endpoint:
      enabled: false                 # Enable benchmark REST endpoint
```

### Metrics & Logging
```yaml
tfi:
  metrics:
    logging:
      enabled: false                 # Enable metrics logging
      interval: 60000                # Logging interval (ms)
      format: json                   # Output format (json/text/compact)
      include-zero: false            # Include zero metrics
```

## Performance Tuning

### Memory Optimization
```yaml
# For high-load environments
tfi:
  change-tracking:
    max-tracked-objects: 5000       # Increase for more tracking
    path-matcher:
      cache-size: 5000               # Larger cache for better hit rate
      result-cache-multiplier: 5    # Reduce for memory savings
  
  collection-summary:
    max-size: 50                     # Lower threshold for earlier summarization
    max-examples: 5                  # Fewer examples to save memory
```

### Low-Latency Configuration
```yaml
# For low-latency requirements
tfi:
  change-tracking:
    deep-tracking: false             # Disable for better performance
    cleanup-interval-minutes: 60    # Less frequent cleanup
  
  performance:
    warmup-iterations: 100           # Reduce warmup time
    measurement-iterations: 1000     # Fewer measurements
  
  metrics:
    logging:
      enabled: false                 # Disable to reduce overhead
```

### High-Throughput Configuration
```yaml
# For high-throughput scenarios
tfi:
  change-tracking:
    max-tracked-objects: 10000      # Track more objects
  
  context:
    max-contexts: 500                # More concurrent contexts
    propagate-to-child-threads: false # Reduce propagation overhead
  
  session:
    max-sessions: 1000               # Support more sessions
    session-timeout-minutes: 5      # Shorter timeout for cleanup
```

## Environment-Specific Profiles

### Development Profile
```yaml
# application-dev.yml
tfi:
  change-tracking:
    enabled: true
    deep-tracking: true
  performance:
    enabled: true
  metrics:
    logging:
      enabled: true
      format: text
```

### Production Profile
```yaml
# application-prod.yml
tfi:
  change-tracking:
    enabled: true
    deep-tracking: false
  performance:
    enabled: false
  metrics:
    logging:
      enabled: true
      format: json
      interval: 300000  # 5 minutes
```

### Testing Profile
```yaml
# application-test.yml
tfi:
  change-tracking:
    enabled: true
    max-tracked-objects: 100
  collection-summary:
    max-size: 10
  performance:
    warmup-iterations: 10
    measurement-iterations: 100
```

## Monitoring & Observability

### Health Checks
```yaml
management:
  health:
    tfi:
      enabled: true
  metrics:
    export:
      simple:
        enabled: true
```

### Custom Metrics
Access metrics via:
- REST API: `GET /tfi/metrics/summary`
- Actuator: `GET /actuator/tfi-metrics`
- Logs: Check `TFI_METRICS` logger

### Performance Monitoring
Monitor key metrics:
- `tfi.change.tracking.count` - Number of change tracking operations
- `tfi.path.match.hit.rate` - Path matcher cache hit rate
- `tfi.collection.summary.count` - Collection summaries created
- `tfi.health.score` - Overall health score (0-100)

## Troubleshooting

### Common Issues

1. **High Memory Usage**
   - Reduce `max-tracked-objects`
   - Lower `cache-size` values
   - Enable more aggressive cleanup

2. **Poor Cache Hit Rate**
   - Increase `cache-size`
   - Review and optimize patterns
   - Use pattern preloading

3. **Slow Performance**
   - Disable `deep-tracking`
   - Reduce `measurement-iterations`
   - Disable unnecessary features

4. **Application Context Failures**
   - Check for duplicate endpoint IDs
   - Verify Spring Boot version compatibility
   - Review actuator configuration

## Best Practices

1. **Start with defaults** - The default configuration is optimized for most use cases
2. **Monitor metrics** - Use the metrics endpoint to understand system behavior
3. **Profile-specific config** - Use Spring profiles for environment-specific settings
4. **Gradual tuning** - Adjust one parameter at a time and measure impact
5. **Regular cleanup** - Ensure cleanup intervals are appropriate for your load

## Support

For issues or questions:
- GitHub Issues: [TaskFlowInsight Issues](https://github.com/yourusername/TaskFlowInsight/issues)
- Documentation: [Full Documentation](./README.md)
- API Reference: [API Documentation](./API.md)