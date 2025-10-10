# 🚀 TaskFlowInsight 快速开始指南

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](.)
[![Java](https://img.shields.io/badge/Java-21%2B-orange)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-green)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

## 📋 前置要求

- Java 21 或更高版本
- Maven 3.6+ 或使用项目内置的 Maven Wrapper

## ⚡ 3分钟快速体验

### 1️⃣ 获取项目（30秒）

```bash
# 克隆项目
git clone https://github.com/your-org/TaskFlowInsight.git
cd TaskFlowInsight

# 或者下载 ZIP 包
wget https://github.com/your-org/TaskFlowInsight/archive/main.zip
unzip main.zip && cd TaskFlowInsight-main
```

### 2️⃣ 运行验证脚本（2分钟）

我们提供了一个自动化验证脚本，帮助您快速验证环境和功能：

```bash
# 运行3分钟快速验证脚本
chmod +x quickstart-verify.sh
./quickstart-verify.sh
```

脚本会自动完成：
- ✅ 环境检查（Java、Maven）
- ✅ 配置验证
- ✅ 项目编译
- ✅ 应用启动
- ✅ 功能测试（健康/端点/演示API/指标）
- ✅ 自动清理

### 3️⃣ 手动启动（备选方案）

如果您想手动控制启动过程：

```bash
# 编译项目
./mvnw clean compile

# 启动应用
./mvnw spring-boot:run
```

等待看到以下日志表示启动成功：
```
Started TaskFlowInsightApplication in X.XXX seconds
```

## 🔍 验证功能

### 检查健康状态
```bash
curl http://localhost:19090/actuator/health
```

预期响应：
```json
{
  "status": "UP",
  "components": {
    "ping": {"status": "UP"}
  }
}
```

### 访问管理端点
```bash
# 查看可用端点
curl http://localhost:19090/actuator

# 访问 TaskFlow 只读端点（默认暴露，ID: taskflow）
curl http://localhost:19090/actuator/taskflow

# 查看指标
curl http://localhost:19090/actuator/metrics | grep tfi
```

### 测试演示功能
```bash
# Hello 示例（注解 + 指标）
curl http://localhost:19090/api/demo/hello/World

# 异步上下文传播示例（查看日志观察上下文ID传播）
curl -X POST http://localhost:19090/api/demo/async \
  -H 'Content-Type: application/json' \
  -d '{"data":"sample"}'

# 处理示例
curl -X POST http://localhost:19090/api/demo/process \
  -H 'Content-Type: application/json' \
  -d '{"data":"payload"}'
```

## 💎 推荐使用方式：Facade API（3分钟可复制）

### 1️⃣ 基本对比+渲染
```java
import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.tracking.compare.CompareResult;

// 对比两个对象
CompareResult result = TFI.compare(oldObject, newObject);

// 渲染为 Markdown（标准样式）
String report = TFI.render(result, "standard");
System.out.println(report);
```

### 2️⃣ 使用模板对比
```java
import com.syy.taskflowinsight.api.ComparisonTemplate;

// AUDIT 模板：审计模式（完整记录）
CompareResult auditResult = TFI.comparator()
    .useTemplate(ComparisonTemplate.AUDIT)
    .compare(before, after);

// FAST 模板：快速模式（性能优先）
CompareResult fastResult = TFI.comparator()
    .useTemplate(ComparisonTemplate.FAST)
    .compare(before, after);

// DEBUG 模板：调试模式（详细诊断）
CompareResult debugResult = TFI.comparator()
    .useTemplate(ComparisonTemplate.DEBUG)
    .compare(before, after);
```

### 3️⃣ 可选：本地运行验证
```bash
# 运行 Spring Boot 应用
./mvnw spring-boot:run

# 运行测试验证 Facade
./mvnw test -Dtest=TfiListDiffFacadeTests
```

---

## ⚙️ 配置即用

### JVM 参数配置
```bash
# 启用 Facade API（默认已开启）
-Dtfi.api.facade.enabled=true

# 配置掩码字段
-Dtfi.render.mask-fields=password,secret,token

# 一次性启动示例
java -jar app.jar \
  -Dtfi.api.facade.enabled=true \
  -Dtfi.render.mask-fields=password,secret
```

### YAML 配置
```yaml
tfi:
  # Facade API 配置
  api:
    facade:
      enabled: true  # 默认开启

  # 渲染配置
  render:
    masking:
      enabled: true  # 默认启用掩码
    mask-fields:
      - password
      - secret
      - token
      - internal*  # 支持通配符

  # 变更追踪配置
  change-tracking:
    enabled: true
    snapshot:
      enable-deep: true
      max-depth: 10
```

---

## 🎯 补充：注解驱动方式（可选）

### 启用 TFI 注解支持

在 `application.yml` 中添加：

```yaml
tfi:
  enabled: true                    # 启用 TFI
  annotation:
    enabled: true                  # 启用注解支持
  change-tracking:
    enabled: true                  # 启用变更追踪
```

### 使用 @TfiTask 注解

```java
@Service
public class YourService {

    @TfiTask("processOrder")
    public Order processOrder(String orderId) {
        // 方法会自动被追踪
        return orderRepository.findById(orderId);
    }
}
```

> 📝 **提示**: Facade API 是推荐方式，注解方式适合需要 AOP 追踪的场景

## 📊 可选：监控集成验证

### Prometheus 指标（可选验证）

配置已自动启用，访问：
```
http://localhost:19090/actuator/prometheus
```

### Grafana 仪表板（可选）

1. 导入 `docs/grafana/tfi-dashboard.json`
2. 配置 Prometheus 数据源
3. 查看 TFI 专属指标

## ⚠️ 端点说明

项目提供以下管理端点：

- 安全只读端点：`taskflow`（默认开启，已在 management.endpoints.web.exposure.include 中暴露）
- 基础端点：`basic-tfi`（默认关闭，如需启用需设置 `tfi.endpoint.basic.enabled=true`，并在 exposure.include 中添加 `basic-tfi`）
- 上下文诊断端点：`taskflow-context`（受 `taskflow.monitoring.endpoint.enabled` 控制，若需对外暴露需在 exposure.include 中添加 `taskflow-context`）

## 🛠️ 常见问题

### Q: 为什么看不到 TFI 端点？

检查以下配置：
1. 确保 `tfi.enabled=true`
2. 在 `management.endpoints.web.exposure.include` 中添加端点
3. 选择性启用端点（避免冲突）

### Q: 如何提高性能？

1. 调整采样率：
```yaml
@TfiTask(samplingRate = 0.1)  # 10% 采样
```

2. 禁用深度快照：
```yaml
tfi:
  change-tracking:
    snapshot:
      enable-deep: false
```

### Q: 内存占用过高？

限制缓存大小：
```yaml
tfi:
  change-tracking:
    max-cached-classes: 512
```

## 📚 下一步

- 📖 查看[完整文档](docs/product/README.md)
- 🎨 探索[示例代码](src/main/java/com/syy/taskflowinsight/demo)
- 🔧 了解[高级配置](docs/CONFIGURATION.md)
- 🚦 配置[生产部署](DEPLOYMENT.md)

## 💡 快速提示

1. **默认端口**：19090（可在 `application.yml` 中修改）
2. **默认关闭**：TFI 功能默认关闭，需显式启用
3. **性能优先**：零采样时开销 <50ns
4. **安全设计**：敏感信息自动脱敏

## 🆘 获取帮助

- 🐛 [提交问题](https://github.com/your-org/TaskFlowInsight/issues)
- 💬 [讨论区](https://github.com/your-org/TaskFlowInsight/discussions)
- 📧 联系：taskflow-insight@example.com

---

**恭喜！** 🎉 您已成功启动 TaskFlowInsight。现在可以开始集成到您的应用中了。

> 提示：运行 `./quickstart-verify.sh` 可以随时验证配置是否正确。
