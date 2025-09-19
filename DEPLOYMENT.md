# TaskFlowInsight 生产环境部署指南 🚀

> **企业级部署最佳实践** - 从开发到生产的完整部署方案

## 📋 部署概览

TaskFlowInsight v2.1.0 是一个企业级就绪的Spring Boot应用，支持多种部署方式。本指南涵盖从单机部署到大规模分布式集群的完整方案。

### 支持的部署环境
- ✅ **单机部署** - 适合小型应用和开发环境
- ✅ **Docker容器** - 适合微服务架构和容器化部署
- ✅ **Kubernetes集群** - 适合大规模分布式应用
- ✅ **云原生平台** - AWS ECS、阿里云ACK、腾讯云TKE等

## 🎯 部署前准备

### 系统要求

| 组件 | 最低要求 | 推荐配置 | 生产环境 |
|------|----------|----------|----------|
| **Java版本** | Java 21+ | Java 21 LTS | Java 21 LTS |
| **内存** | 2GB | 4GB | 8GB+ |
| **CPU** | 2 cores | 4 cores | 8+ cores |
| **磁盘空间** | 5GB | 20GB | 100GB+ |
| **网络带宽** | 100Mbps | 1Gbps | 10Gbps+ |

### 环境检查清单

```bash
# 1. 检查Java版本
java -version
# 预期输出: openjdk version "21.x.x"

# 2. 检查可用内存
free -h
# 确保至少有2GB可用内存

# 3. 检查磁盘空间
df -h
# 确保部署目录有足够空间

# 4. 检查网络连接
curl -I https://github.com
# 确保网络正常
```

## 🔧 配置管理

### 环境配置文件

创建不同环境的配置文件：

#### application-dev.yml (开发环境)
```yaml
tfi:
  enabled: true
  auto-export: true
  max-sessions: 100
  session-timeout: 10m
  
  export:
    console:
      enabled: true
      format: tree
    json:
      enabled: true
      
  performance:
    track-memory: true
    track-cpu: true
    
server:
  port: 19090
  
logging:
  level:
    com.syy.taskflowinsight: DEBUG
    
management:
  endpoints:
    web:
      exposure:
        include: "*"
```

#### application-prod.yml (生产环境)
```yaml
tfi:
  enabled: true
  auto-export: false  # 生产环境建议关闭自动导出
  max-sessions: 10000
  session-timeout: 30m
  
  export:
    console:
      enabled: false  # 生产环境关闭控制台输出
    json:
      enabled: true
      include-metadata: false
      
  performance:
    track-memory: true
    track-cpu: false  # 生产环境可关闭CPU追踪
    max-tracking-objects: 1000
    
  security:
    mask-sensitive-data: true
    sensitive-fields:
      - password
      - cardNumber
      - ssn
      - phone
      - email
      
server:
  port: 8080
  shutdown: graceful
  
  # 连接池配置
  tomcat:
    accept-count: 100
    max-connections: 8192
    threads:
      max: 200
      min-spare: 10
      
logging:
  level:
    root: INFO
    com.syy.taskflowinsight: INFO
  file:
    name: /var/log/taskflowinsight/application.log
    max-size: 100MB
    max-history: 30
    
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,tfi
      base-path: /actuator
  endpoint:
    health:
      show-details: when-authorized
    tfi:
      enabled: true
      sensitive: true
      
  # 安全配置
  security:
    enabled: true
    
  # 指标配置
  metrics:
    export:
      prometheus:
        enabled: true
    web:
      server:
        request:
          autotime:
            enabled: true
```

## 🐳 Docker 部署

### Dockerfile

```dockerfile
# 多阶段构建
FROM maven:3.9-openjdk-21-slim AS builder

WORKDIR /app
COPY pom.xml .
COPY src ./src

# 构建应用
RUN mvn clean package -DskipTests

# 运行阶段
FROM openjdk:21-jre-slim

# 安装必要工具
RUN apt-get update && apt-get install -y \
    curl \
    jq \
    && rm -rf /var/lib/apt/lists/*

# 创建应用用户
RUN groupadd -r tfi && useradd --no-log-init -r -g tfi tfi

# 创建应用目录
WORKDIR /app
RUN mkdir -p /app/logs /app/config && \
    chown -R tfi:tfi /app

# 复制应用JAR
COPY --from=builder /app/target/TaskFlowInsight-*.jar app.jar
COPY --chown=tfi:tfi docker/application-docker.yml config/

# 切换到应用用户
USER tfi

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# 暴露端口
EXPOSE 8080

# JVM参数优化
ENV JAVA_OPTS="-Xms2g -Xmx4g -XX:+UseG1GC -XX:G1HeapRegionSize=16m -XX:+UseStringDeduplication"

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.profiles.active=docker"]
```

### docker-compose.yml

```yaml
version: '3.8'

services:
  taskflowinsight:
    build: .
    image: taskflowinsight:2.1.0
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - JAVA_OPTS=-Xms2g -Xmx4g -XX:+UseG1GC
    volumes:
      - ./logs:/app/logs
      - ./config:/app/config
    restart: unless-stopped
    
    # 资源限制
    deploy:
      resources:
        limits:
          memory: 6G
          cpus: '4'
        reservations:
          memory: 2G
          cpus: '2'
    
    # 健康检查
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
      
  # 可选：添加监控服务
  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--web.console.libraries=/etc/prometheus/console_libraries'
      - '--web.console.templates=/etc/prometheus/consoles'
      - '--web.enable-lifecycle'
      
  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - grafana-storage:/var/lib/grafana
      - ./monitoring/grafana/dashboards:/etc/grafana/provisioning/dashboards
      - ./monitoring/grafana/datasources:/etc/grafana/provisioning/datasources

volumes:
  grafana-storage:

networks:
  default:
    name: taskflowinsight-network
```

### Docker 部署命令

```bash
# 1. 构建镜像
docker build -t taskflowinsight:2.1.0 .

# 2. 运行容器
docker run -d \
  --name taskflowinsight \
  -p 8080:8080 \
  -v $(pwd)/logs:/app/logs \
  -v $(pwd)/config:/app/config \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e JAVA_OPTS="-Xms2g -Xmx4g" \
  --restart unless-stopped \
  taskflowinsight:2.1.0

# 3. 使用docker-compose
docker-compose up -d

# 4. 查看日志
docker logs -f taskflowinsight

# 5. 健康检查
docker exec taskflowinsight curl -f http://localhost:8080/actuator/health
```

## ☸️ Kubernetes 部署

### k8s-namespace.yaml

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: taskflowinsight
  labels:
    name: taskflowinsight
```

### k8s-configmap.yaml

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: taskflowinsight-config
  namespace: taskflowinsight
data:
  application-k8s.yml: |
    tfi:
      enabled: true
      auto-export: false
      max-sessions: 50000
      session-timeout: 1h
      
      export:
        json:
          enabled: true
          include-metadata: false
          
      performance:
        track-memory: true
        track-cpu: false
        max-tracking-objects: 5000
        
      security:
        mask-sensitive-data: true
        
    server:
      port: 8080
      shutdown: graceful
      
    management:
      endpoints:
        web:
          exposure:
            include: health,info,metrics,tfi
      endpoint:
        health:
          probes:
            enabled: true
      metrics:
        export:
          prometheus:
            enabled: true
    
    logging:
      level:
        root: INFO
        com.syy.taskflowinsight: INFO
```

### k8s-deployment.yaml

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: taskflowinsight
  namespace: taskflowinsight
  labels:
    app: taskflowinsight
    version: v2.1.0
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 1
      maxSurge: 1
  selector:
    matchLabels:
      app: taskflowinsight
  template:
    metadata:
      labels:
        app: taskflowinsight
        version: v2.1.0
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8080"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      # 安全上下文
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        fsGroup: 1000
        
      containers:
      - name: taskflowinsight
        image: taskflowinsight:2.1.0
        imagePullPolicy: IfNotPresent
        
        ports:
        - containerPort: 8080
          name: http
          protocol: TCP
          
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "k8s"
        - name: JAVA_OPTS
          value: "-Xms4g -Xmx8g -XX:+UseG1GC -XX:MaxRAMPercentage=75"
        - name: POD_NAME
          valueFrom:
            fieldRef:
              fieldPath: metadata.name
        - name: POD_IP
          valueFrom:
            fieldRef:
              fieldPath: status.podIP
              
        # 资源配置
        resources:
          requests:
            memory: "4Gi"
            cpu: "1000m"
          limits:
            memory: "10Gi"
            cpu: "4000m"
            
        # 健康检查
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
          timeoutSeconds: 10
          failureThreshold: 3
          
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
          
        # 启动探测
        startupProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 12
          
        # 配置挂载
        volumeMounts:
        - name: config-volume
          mountPath: /app/config
          readOnly: true
        - name: logs-volume
          mountPath: /app/logs
          
        # 优雅关闭
        lifecycle:
          preStop:
            exec:
              command: ["sh", "-c", "sleep 15"]
              
      volumes:
      - name: config-volume
        configMap:
          name: taskflowinsight-config
      - name: logs-volume
        emptyDir: {}
        
      # Pod 反亲和性
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
          - weight: 100
            podAffinityTerm:
              labelSelector:
                matchExpressions:
                - key: app
                  operator: In
                  values:
                  - taskflowinsight
              topologyKey: kubernetes.io/hostname
```

### k8s-service.yaml

```yaml
apiVersion: v1
kind: Service
metadata:
  name: taskflowinsight-service
  namespace: taskflowinsight
  labels:
    app: taskflowinsight
  annotations:
    prometheus.io/scrape: "true"
    prometheus.io/port: "8080"
    prometheus.io/path: "/actuator/prometheus"
spec:
  type: ClusterIP
  ports:
  - port: 8080
    targetPort: 8080
    protocol: TCP
    name: http
  selector:
    app: taskflowinsight
---
apiVersion: v1
kind: Service
metadata:
  name: taskflowinsight-headless
  namespace: taskflowinsight
  labels:
    app: taskflowinsight
spec:
  clusterIP: None
  ports:
  - port: 8080
    targetPort: 8080
    protocol: TCP
    name: http
  selector:
    app: taskflowinsight
```

### k8s-ingress.yaml

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: taskflowinsight-ingress
  namespace: taskflowinsight
  annotations:
    kubernetes.io/ingress.class: "nginx"
    nginx.ingress.kubernetes.io/rewrite-target: /
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/force-ssl-redirect: "true"
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
spec:
  tls:
  - hosts:
    - tfi.yourdomain.com
    secretName: taskflowinsight-tls
  rules:
  - host: tfi.yourdomain.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: taskflowinsight-service
            port:
              number: 8080
```

### HPA (水平伸缩)

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: taskflowinsight-hpa
  namespace: taskflowinsight
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: taskflowinsight
  minReplicas: 3
  maxReplicas: 20
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
      - type: Percent
        value: 10
        periodSeconds: 60
    scaleUp:
      stabilizationWindowSeconds: 120
      policies:
      - type: Percent
        value: 50
        periodSeconds: 60
```

### Kubernetes 部署命令

```bash
# 1. 创建命名空间
kubectl apply -f k8s-namespace.yaml

# 2. 创建配置
kubectl apply -f k8s-configmap.yaml

# 3. 部署应用
kubectl apply -f k8s-deployment.yaml

# 4. 创建服务
kubectl apply -f k8s-service.yaml

# 5. 创建Ingress
kubectl apply -f k8s-ingress.yaml

# 6. 创建HPA
kubectl apply -f k8s-hpa.yaml

# 7. 验证部署
kubectl get pods -n taskflowinsight
kubectl get svc -n taskflowinsight
kubectl get ingress -n taskflowinsight

# 8. 查看日志
kubectl logs -f deployment/taskflowinsight -n taskflowinsight

# 9. 端口转发测试
kubectl port-forward svc/taskflowinsight-service 8080:8080 -n taskflowinsight
```

## 📊 监控配置

### Prometheus配置 (prometheus.yml)

```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'taskflowinsight'
    static_configs:
      - targets: ['taskflowinsight:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 30s
    
  - job_name: 'kubernetes-pods'
    kubernetes_sd_configs:
      - role: pod
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_scrape]
        action: keep
        regex: true
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_path]
        action: replace
        target_label: __metrics_path__
        regex: (.+)
```

### Grafana Dashboard配置

创建 `grafana-dashboard.json`：

```json
{
  "dashboard": {
    "id": null,
    "title": "TaskFlowInsight Metrics",
    "tags": ["taskflowinsight"],
    "timezone": "browser",
    "panels": [
      {
        "id": 1,
        "title": "TFI Sessions",
        "type": "stat",
        "targets": [
          {
            "expr": "tfi_sessions_active_total",
            "legendFormat": "Active Sessions"
          }
        ]
      },
      {
        "id": 2,
        "title": "QPS Performance",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(tfi_operations_total[5m])",
            "legendFormat": "Operations/sec"
          }
        ]
      }
    ]
  }
}
```

## 🔒 安全配置

### 应用安全配置

```yaml
# application-security.yml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://your-auth-server.com
          
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when-authorized
  security:
    enabled: true
    
tfi:
  security:
    mask-sensitive-data: true
    endpoint-authentication: true
    rate-limiting:
      enabled: true
      requests-per-minute: 100
```

### 网络安全

```yaml
# NetworkPolicy for Kubernetes
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: taskflowinsight-network-policy
  namespace: taskflowinsight
spec:
  podSelector:
    matchLabels:
      app: taskflowinsight
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: ingress-nginx
    ports:
    - protocol: TCP
      port: 8080
  egress:
  - to: []
    ports:
    - protocol: TCP
      port: 443  # HTTPS
    - protocol: TCP
      port: 53   # DNS
    - protocol: UDP
      port: 53   # DNS
```

## 🚀 性能调优

### JVM参数优化

```bash
# 开发环境
JAVA_OPTS="-Xms1g -Xmx2g -XX:+UseG1GC"

# 生产环境
JAVA_OPTS="
-Xms4g 
-Xmx8g 
-XX:+UseG1GC 
-XX:G1HeapRegionSize=16m 
-XX:+UseStringDeduplication 
-XX:+OptimizeStringConcat 
-XX:MaxGCPauseMillis=200 
-XX:G1NewSizePercent=30 
-XX:G1MaxNewSizePercent=40 
-XX:+UnlockExperimentalVMOptions 
-XX:+UseJVMCICompiler
"

# 大内存环境 (32GB+)
JAVA_OPTS="
-Xms16g 
-Xmx24g 
-XX:+UseG1GC 
-XX:G1HeapRegionSize=32m 
-XX:MaxGCPauseMillis=100 
-XX:+UseStringDeduplication
"
```

### 应用性能配置

```yaml
tfi:
  performance:
    # 核心配置
    max-sessions: 50000           # 根据内存调整
    session-timeout: 1h           # 根据业务调整
    max-tracking-objects: 5000    # 根据内存调整
    
    # 缓存配置
    cache:
      path-matcher:
        maximum-size: 10000
        expire-after-access: 1h
      reflection-meta:
        maximum-size: 5000
        expire-after-write: 2h
        
    # 并发配置
    executor:
      core-pool-size: 10
      maximum-pool-size: 50
      queue-capacity: 1000
      
server:
  tomcat:
    # 连接配置
    accept-count: 200
    max-connections: 20000
    threads:
      max: 400
      min-spare: 20
    connection-timeout: 20000
    
    # 压缩配置
    compression:
      enabled: true
      mime-types: application/json,application/xml,text/html,text/xml,text/plain
```

## 📋 部署检查清单

### 部署前检查

- [ ] **环境准备**
  - [ ] Java 21 已安装
  - [ ] 内存、CPU、磁盘满足要求
  - [ ] 网络连接正常
  - [ ] 防火墙端口已开放

- [ ] **配置文件**
  - [ ] 环境特定配置文件已准备
  - [ ] 敏感信息已加密或外部化
  - [ ] 日志配置符合要求
  - [ ] 监控配置已启用

- [ ] **安全配置**
  - [ ] 敏感数据脱敏已启用
  - [ ] 端点访问控制已配置
  - [ ] 网络安全策略已应用
  - [ ] TLS证书已配置

### 部署后验证

- [ ] **功能验证**
  - [ ] 健康检查端点正常
  - [ ] 主要API功能正常
  - [ ] 监控指标正常上报
  - [ ] 日志正常输出

- [ ] **性能验证**
  - [ ] 内存使用在合理范围
  - [ ] CPU使用率正常
  - [ ] 响应时间符合预期
  - [ ] 并发处理能力达标

- [ ] **监控验证**
  - [ ] Prometheus指标采集正常
  - [ ] Grafana仪表板显示正常
  - [ ] 告警规则已配置
  - [ ] 日志聚合正常

## 🚨 故障恢复

### 回滚策略

```bash
# Kubernetes 回滚
kubectl rollout undo deployment/taskflowinsight -n taskflowinsight

# Docker 回滚
docker tag taskflowinsight:2.0.0 taskflowinsight:latest
docker-compose down && docker-compose up -d

# 传统部署回滚
cp /backup/TaskFlowInsight-2.0.0.jar /app/TaskFlowInsight.jar
systemctl restart taskflowinsight
```

### 紧急处理

```bash
# 1. 快速重启
kubectl delete pod -l app=taskflowinsight -n taskflowinsight

# 2. 扩容应急
kubectl scale deployment taskflowinsight --replicas=10 -n taskflowinsight

# 3. 临时禁用功能
kubectl patch configmap taskflowinsight-config -n taskflowinsight -p '{"data":{"tfi.enabled":"false"}}'
kubectl rollout restart deployment/taskflowinsight -n taskflowinsight
```

## 📞 支持联系

遇到部署问题？

- 📖 [故障排除指南](TROUBLESHOOTING.md)
- 💬 [GitHub Discussions](https://github.com/shiyongyin/TaskFlowInsight/discussions)
- 🐛 [提交Issue](https://github.com/shiyongyin/TaskFlowInsight/issues)

---

🎉 **恭喜！你现在已经掌握了TaskFlowInsight的完整部署方案。选择适合你环境的部署方式开始吧！**