# TaskFlowInsight 实战示例集 📚

> **从真实业务场景学习TaskFlowInsight** - 涵盖电商、金融、数据处理等典型应用场景

## 📖 示例索引

| 场景分类 | 示例名称 | 复杂度 | 推荐场景 |
|---------|----------|--------|----------|
| [🛒 电商业务](#-电商业务场景) | 订单处理流程 | ⭐⭐⭐ | 电商平台、零售系统 |
| [🛒 电商业务](#-电商业务场景) | 库存管理系统 | ⭐⭐ | 仓储管理、供应链 |
| [📋 审批工作流](#-审批工作流场景) | 请假审批链 | ⭐⭐⭐⭐ | OA系统、企业管理 |
| [📋 审批工作流](#-审批工作流场景) | 合同审批流程 | ⭐⭐⭐⭐⭐ | 法务系统、企业采购 |
| [🔄 数据处理](#-数据处理场景) | ETL数据同步 | ⭐⭐⭐ | 数据仓库、BI系统 |
| [🔄 数据处理](#-数据处理场景) | 批量数据导入 | ⭐⭐ | 数据迁移、系统集成 |
| [🏦 金融交易](#-金融交易场景) | 支付处理流程 | ⭐⭐⭐⭐⭐ | 支付系统、金融科技 |
| [🏦 金融交易](#-金融交易场景) | 风控审核系统 | ⭐⭐⭐⭐ | 风险管理、合规系统 |
| [🎮 游戏系统](#-游戏系统场景) | 玩家状态机 | ⭐⭐⭐ | 游戏开发、状态管理 |
| [⚡ 异步处理](#-异步处理场景) | 消息队列处理 | ⭐⭐⭐⭐ | 微服务架构、事件驱动 |

---

## 🛒 电商业务场景

### 示例 1: 完整订单处理流程

这是一个典型的电商订单处理场景，包含库存检查、价格计算、支付处理、发货等完整流程。

#### 注解驱动版本 (推荐)
```java
@RestController
@RequestMapping("/orders")
public class OrderController {
    
    @Autowired
    private OrderService orderService;
    
    @TfiTask("创建订单")
    @PostMapping
    public ResponseEntity<OrderResult> createOrder(@RequestBody CreateOrderRequest request) {
        
        // 1. 参数校验 - 自动记录
        OrderResult result = orderService.processOrder(request);
        
        return ResponseEntity.ok(result);
    }
}

@Service
public class OrderService {
    
    @TfiTask("订单处理流程")
    public OrderResult processOrder(CreateOrderRequest request) {
        
        // 步骤会被自动记录，包括执行时间
        User user = validateUser(request.getUserId());
        List<Product> products = validateProducts(request.getProductIds());
        
        return executeOrderFlow(user, products, request);
    }
    
    @TfiTask("执行订单流程")
    private OrderResult executeOrderFlow(User user, List<Product> products, CreateOrderRequest request) {
        
        // 库存检查
        InventoryResult inventory = checkInventory(products);
        TFI.track("inventory", inventory);  // 追踪库存变化
        
        // 价格计算
        PriceResult price = calculatePrice(products, user.getVipLevel());
        TFI.track("pricing", price);
        
        // 创建订单
        Order order = createOrder(user, products, price);
        TFI.track("order", order);
        
        // 处理支付
        PaymentResult payment = processPayment(order, request.getPaymentInfo());
        
        if (payment.isSuccess()) {
            // 减库存
            updateInventory(inventory);
            
            // 发起发货
            ShipmentResult shipment = initiateShipment(order);
            TFI.track("shipment", shipment);
            
            return OrderResult.success(order, payment, shipment);
        } else {
            TFI.error("支付失败", new PaymentException(payment.getErrorMessage()));
            return OrderResult.failure("支付失败: " + payment.getErrorMessage());
        }
    }
    
    @TfiTask("库存检查")
    private InventoryResult checkInventory(List<Product> products) {
        // 实际的库存检查逻辑
        for (Product product : products) {
            if (product.getStock() < product.getRequestQuantity()) {
                throw new InsufficientStockException("商品 " + product.getName() + " 库存不足");
            }
        }
        return InventoryResult.sufficient(products);
    }
    
    @TfiTask("价格计算")
    @TfiTrack(value = "pricing", mask = "originalPrice")  // 价格信息脱敏
    private PriceResult calculatePrice(List<Product> products, VipLevel vipLevel) {
        BigDecimal total = products.stream()
            .map(p -> p.getPrice().multiply(BigDecimal.valueOf(p.getRequestQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
            
        BigDecimal discount = applyVipDiscount(total, vipLevel);
        
        return new PriceResult(total, discount, total.subtract(discount));
    }
}
```

#### 编程式API版本
```java
@Service
public class OrderServiceProgrammatic {
    
    public OrderResult processOrder(CreateOrderRequest request) {
        TFI.start("订单处理流程");
        
        try {
            // 步骤1: 用户验证
            TFI.stage("用户验证");
            User user = validateUser(request.getUserId());
            TFI.track("user", user);
            
            // 步骤2: 商品验证
            TFI.stage("商品验证");
            List<Product> products = validateProducts(request.getProductIds());
            
            // 步骤3: 库存检查
            TFI.stage("库存检查");
            InventoryResult inventory = checkInventory(products);
            TFI.track("inventory", inventory);
            
            // 步骤4: 价格计算
            TFI.stage("价格计算");
            PriceResult price = calculatePrice(products, user.getVipLevel());
            TFI.track("pricing", price);
            
            // 步骤5: 创建订单
            TFI.stage("创建订单");
            Order order = createOrder(user, products, price);
            TFI.track("order", order);
            
            // 步骤6: 支付处理
            TFI.stage("支付处理");
            PaymentResult payment = processPayment(order, request.getPaymentInfo());
            
            if (payment.isSuccess()) {
                // 步骤7: 库存扣减
                TFI.stage("库存扣减");
                updateInventory(inventory);
                
                // 步骤8: 发起发货
                TFI.stage("发起发货");
                ShipmentResult shipment = initiateShipment(order);
                TFI.track("shipment", shipment);
                
                return OrderResult.success(order, payment, shipment);
            } else {
                TFI.error("支付失败", new PaymentException(payment.getErrorMessage()));
                return OrderResult.failure("支付失败");
            }
            
        } catch (Exception e) {
            TFI.error("订单处理异常", e);
            throw e;
        } finally {
            TFI.end();  // 自动输出完整的流程树
        }
    }
}
```

**期待输出：**
```
[订单-67890] 订单处理流程 ━━━━━━━━━━━━━━━━━━━━━ 1.2s
│
├─ 👤 用户验证 .......................... 45ms ✓
│  └─ user.id: 12345, user.vipLevel: GOLD
├─ 📦 商品验证 .......................... 67ms ✓
├─ 📊 库存检查 .......................... 123ms ✓
│  └─ inventory: 3 items checked, all sufficient
├─ 💰 价格计算 .......................... 34ms ✓
│  └─ pricing: total=¥299.00, discount=¥29.90, final=¥269.10
├─ 📝 创建订单 .......................... 89ms ✓
│  └─ order.id: ORD-2024091901
├─ 💳 支付处理 .......................... 567ms ✓
├─ 📉 库存扣减 .......................... 45ms ✓
└─ 🚚 发起发货 .......................... 234ms ✓
   └─ shipment.trackingNumber: SF123456789
```

---

## ⚡ 实体列表对比与渲染（Markdown 报告）

对比两个实体列表并输出 Markdown 报告：

```java
import com.syy.taskflowinsight.api.TfiListDiffFacade;
import com.syy.taskflowinsight.tracking.render.RenderStyle;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.Entity;
import org.springframework.beans.factory.annotation.Autowired;

public class ListDiffReportExample {
    @Autowired
    private TfiListDiffFacade listDiff;

    public void run() {
        var oldList = java.util.List.of(new User(1L, "Alice"), new User(2L, "Bob"));
        var newList = java.util.List.of(new User(1L, "Alice"), new User(3L, "Charlie"));

        // 对比
        var result = listDiff.diff(oldList, newList);

        // 渲染（标准样式）
        String report = listDiff.render(result);
        System.out.println(report);

        // 渲染（简洁/详细）
        String simple = listDiff.render(result, "simple");
        String detailed = listDiff.render(result, RenderStyle.detailed());
    }

    @Entity
    static class User {
        @Key Long id;
        String name;
        User(Long id, String name) { this.id = id; this.name = name; }
    }
}
```

使用静态入口（需在 Spring Boot 启动完成后调用）：

```java
var result = com.syy.taskflowinsight.api.TfiListDiff.diff(oldList, newList);
String report = com.syy.taskflowinsight.api.TfiListDiff.render(result, "detailed");
```

### 浅引用复合键（@ShallowReference）

当引用实体拥有复合主键时，可通过配置提升可辨识度：

```properties
tfi.change-tracking.snapshot.shallow-reference-mode=COMPOSITE_STRING
```

可选值：
- VALUE_ONLY（默认，保持旧行为）
- COMPOSITE_STRING（示例：[id=1001,region=US]）
- COMPOSITE_MAP（结构化 Map，便于程序消费）


### 示例 2: 库存管理系统

```java
@Service
public class InventoryService {
    
    @TfiTask("批量更新库存")
    public BatchUpdateResult updateInventoryBatch(List<InventoryUpdate> updates) {
        
        List<UpdateResult> results = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;
        
        for (InventoryUpdate update : updates) {
            try {
                UpdateResult result = updateSingleItem(update);
                results.add(result);
                
                if (result.isSuccess()) {
                    successCount++;
                } else {
                    failureCount++;
                }
                
                // 追踪每个商品的库存变化
                TFI.track("item_" + update.getProductId(), result);
                
            } catch (Exception e) {
                TFI.error("更新商品库存失败: " + update.getProductId(), e);
                failureCount++;
            }
        }
        
        return new BatchUpdateResult(successCount, failureCount, results);
    }
    
    @TfiTask("单品库存更新")
    private UpdateResult updateSingleItem(InventoryUpdate update) {
        
        // 获取当前库存
        Inventory current = inventoryRepository.findByProductId(update.getProductId());
        TFI.track("current_stock", current.getQuantity());
        
        // 验证更新操作
        if (current.getQuantity() + update.getQuantityChange() < 0) {
            throw new InsufficientStockException("库存不足，无法执行扣减");
        }
        
        // 执行更新
        current.setQuantity(current.getQuantity() + update.getQuantityChange());
        current.setLastUpdateTime(LocalDateTime.now());
        
        inventoryRepository.save(current);
        
        TFI.track("new_stock", current.getQuantity());
        
        return UpdateResult.success(current);
    }
}
```

**期待输出：**
```
[INVENTORY-001] 批量更新库存 ━━━━━━━━━━━━━━━━━━━━━ 456ms
│
├─ 📦 单品库存更新 (SKU-001) .............. 89ms ✓
│  ├─ current_stock: 100
│  ├─ new_stock: 95
│  └─ item_SKU-001: 成功扣减5件
├─ 📦 单品库存更新 (SKU-002) .............. 67ms ✓
│  ├─ current_stock: 50
│  ├─ new_stock: 48  
│  └─ item_SKU-002: 成功扣减2件
├─ 📦 单品库存更新 (SKU-003) .............. 45ms ❌
│  ├─ current_stock: 5
│  └─ ❌ 错误: 库存不足，无法执行扣减
└─ 📊 处理结果: 成功 2 件，失败 1 件
```

---

## 📋 审批工作流场景

### 示例 3: 多级审批流程

```java
@Service
public class ApprovalService {
    
    @TfiTask("请假申请审批流程")
    public ApprovalResult processLeaveApplication(LeaveApplication application) {
        
        // 初始化审批链
        List<Approver> approvers = buildApprovalChain(application);
        TFI.track("approval_chain", approvers);
        
        ApprovalContext context = new ApprovalContext(application);
        
        for (int i = 0; i < approvers.size(); i++) {
            Approver approver = approvers.get(i);
            
            ApprovalStepResult stepResult = processApprovalStep(approver, context, i + 1);
            TFI.track("step_" + (i + 1), stepResult);
            
            if (stepResult.getDecision() == ApprovalDecision.REJECTED) {
                return ApprovalResult.rejected(stepResult.getReason(), i + 1);
            } else if (stepResult.getDecision() == ApprovalDecision.PENDING) {
                // 发送通知等待审批
                sendPendingNotification(approver, application);
                return ApprovalResult.pending(i + 1);
            }
            // APPROVED继续下一级审批
        }
        
        // 所有审批通过
        return finalizeApproval(application);
    }
    
    @TfiTask("执行审批步骤")
    private ApprovalStepResult processApprovalStep(Approver approver, ApprovalContext context, int stepNumber) {
        
        // 检查审批人是否有权限
        if (!hasApprovalPermission(approver, context.getApplication())) {
            throw new InsufficientPermissionException("审批人无权限处理此申请");
        }
        
        // 自动审批规则检查
        AutoApprovalResult autoResult = checkAutoApprovalRules(approver, context);
        TFI.track("auto_approval_check", autoResult);
        
        if (autoResult.isAutoApprovable()) {
            return ApprovalStepResult.approved("自动审批通过: " + autoResult.getReason());
        }
        
        // 需要人工审批
        return ApprovalStepResult.pending("等待 " + approver.getName() + " 审批");
    }
    
    @TfiTask("构建审批链")
    private List<Approver> buildApprovalChain(LeaveApplication application) {
        List<Approver> approvers = new ArrayList<>();
        
        // 直接主管
        approvers.add(getDirectManager(application.getApplicant()));
        
        // 根据请假天数决定审批级别
        if (application.getDays() > 3) {
            // 部门经理
            approvers.add(getDepartmentManager(application.getApplicant()));
        }
        
        if (application.getDays() > 7) {
            // HR审批
            approvers.add(getHRManager());
        }
        
        if (application.getDays() > 15) {
            // 总监审批
            approvers.add(getDirector(application.getApplicant()));
        }
        
        return approvers;
    }
}
```

**期待输出：**
```
[LEAVE-20240919-001] 请假申请审批流程 ━━━━━━━━━━━━━━━━━━━━━ 2.3s
│
├─ 🏗️ 构建审批链 .......................... 45ms ✓
│  └─ approval_chain: [直接主管, 部门经理, HR经理] (3级审批)
├─ 👤 执行审批步骤 (第1级) ................. 234ms ✓
│  ├─ auto_approval_check: 检查自动审批规则
│  └─ step_1: 自动审批通过(请假天数≤3天)
├─ 👤 执行审批步骤 (第2级) ................. 156ms ✓
│  ├─ auto_approval_check: 需要人工审批
│  └─ step_2: 等待部门经理审批
└─ 📧 发送待审批通知 ...................... 67ms ✓
   └─ 通知已发送给: 张经理(zhang.manager@company.com)
```
```

### 示例 4: 合同审批流程

```java
@Service
public class ContractApprovalService {
    
    @TfiTask("合同审批流程")
    @TfiTrack(value = "contract", mask = "amount,counterparty")
    public ContractApprovalResult processContractApproval(Contract contract) {
        
        // 1. 基础验证
        validateContractBasics(contract);
        
        // 2. 风险评估
        RiskAssessment risk = assessContractRisk(contract);
        TFI.track("risk_assessment", risk);
        
        // 3. 法务审核
        LegalReview legalReview = performLegalReview(contract);
        TFI.track("legal_review", legalReview);
        
        // 4. 财务审核
        FinancialReview financialReview = performFinancialReview(contract);
        TFI.track("financial_review", financialReview);
        
        // 5. 管理层审批
        ManagementApproval managementApproval = processManagementApproval(contract, risk);
        TFI.track("management_approval", managementApproval);
        
        // 6. 生成最终审批结果
        return generateFinalResult(contract, legalReview, financialReview, managementApproval);
    }
    
    @TfiTask("法务审核")
    private LegalReview performLegalReview(Contract contract) {
        
        List<LegalIssue> issues = new ArrayList<>();
        
        // 检查合同条款
        if (hasRiskyTerms(contract)) {
            issues.add(new LegalIssue("RISKY_TERMS", "存在高风险条款", Severity.HIGH));
        }
        
        // 检查合规性
        if (!isCompliant(contract)) {
            issues.add(new LegalIssue("COMPLIANCE", "合规性问题", Severity.MEDIUM));
        }
        
        // 检查知识产权
        if (hasIPIssues(contract)) {
            issues.add(new LegalIssue("IP_ISSUES", "知识产权风险", Severity.HIGH));
        }
        
        TFI.track("legal_issues", issues);
        
        return new LegalReview(issues.isEmpty() ? ReviewStatus.APPROVED : ReviewStatus.CONDITIONAL, issues);
    }
    
    @TfiTask("财务审核")
    private FinancialReview performFinancialReview(Contract contract) {
        
        // 预算检查
        BudgetCheck budgetCheck = checkBudgetAvailability(contract);
        TFI.track("budget_check", budgetCheck);
        
        // 现金流分析
        CashFlowAnalysis cashFlow = analyzeCashFlow(contract);
        TFI.track("cash_flow_analysis", cashFlow);
        
        // ROI计算
        ROICalculation roi = calculateROI(contract);
        TFI.track("roi_calculation", roi);
        
        return new FinancialReview(budgetCheck, cashFlow, roi);
    }
}
```

**期待输出：**
```
[CONTRACT-2024-0919-001] 合同审批流程 ━━━━━━━━━━━━━━━━━━━━━ 15.7min
│
├─ 📋 基础验证 ............................ 234ms ✓
├─ ⚠️ 风险评估 ........................... 1.2min ✓
│  └─ risk_assessment: 中等风险(金额较大，需要详细审核)
├─ ⚖️ 法务审核 ........................... 8.3min ✓
│  ├─ legal_issues: [
│  │     "合同条款需要补充违约责任条款",
│  │     "知识产权归属需要明确"
│  │   ]
│  └─ legal_review: 有条件通过，需要修改2处条款
├─ 💰 财务审核 ........................... 4.1min ✓
│  ├─ budget_check: 预算充足(剩余预算: ¥2,500,000)
│  ├─ cash_flow_analysis: 现金流影响可控
│  ├─ roi_calculation: 预期ROI 15.8%
│  └─ financial_review: 财务审核通过
├─ 👔 管理层审批 ......................... 1.8min ✓
│  └─ management_approval: 总监审批通过
└─ 📊 生成最终结果 ........................ 45ms ✓
   └─ 审批结果: 有条件通过，需要完成法务整改
```
```

---

## 🔄 数据处理场景

### 示例 5: ETL数据同步

```java
@Component
public class DataSyncService {
    
    @TfiTask("数据同步任务")
    @Scheduled(cron = "0 0 2 * * ?")  // 每天凌晨2点执行
    public void syncDailyData() {
        
        // 获取需要同步的数据源
        List<DataSource> sources = getActiveSyncSources();
        TFI.track("sync_sources", sources.size());
        
        SyncResult overallResult = new SyncResult();
        
        for (DataSource source : sources) {
            try {
                SyncResult sourceResult = syncSingleSource(source);
                overallResult.merge(sourceResult);
                
                TFI.track("source_" + source.getName(), sourceResult);
                
            } catch (Exception e) {
                TFI.error("同步数据源失败: " + source.getName(), e);
                overallResult.addFailure(source.getName(), e.getMessage());
            }
        }
        
        // 生成同步报告
        generateSyncReport(overallResult);
    }
    
    @TfiTask("同步单个数据源")
    private SyncResult syncSingleSource(DataSource source) {
        
        // 1. 提取数据
        List<Record> records = extractData(source);
        TFI.track("extracted_records", records.size());
        
        // 2. 转换数据
        List<TransformedRecord> transformedRecords = transformData(records);
        TFI.track("transformed_records", transformedRecords.size());
        
        // 3. 加载数据
        LoadResult loadResult = loadData(transformedRecords, source.getTargetTable());
        TFI.track("load_result", loadResult);
        
        return new SyncResult(source.getName(), records.size(), 
                            transformedRecords.size(), loadResult);
    }
    
    @TfiTask("提取数据")
    private List<Record> extractData(DataSource source) {
        
        // 获取上次同步时间戳
        LocalDateTime lastSync = getLastSyncTime(source);
        TFI.track("last_sync_time", lastSync);
        
        // 构建查询条件
        String query = buildIncrementalQuery(source, lastSync);
        TFI.track("query", query);
        
        // 执行查询
        List<Record> records = source.executeQuery(query);
        
        // 处理大数据集的分批提取
        if (records.size() > source.getBatchSize()) {
            records = processBatchExtraction(source, query);
        }
        
        return records;
    }
    
    @TfiTask("转换数据")
    private List<TransformedRecord> transformData(List<Record> records) {
        
        List<TransformedRecord> transformed = new ArrayList<>();
        int errorCount = 0;
        
        for (Record record : records) {
            try {
                TransformedRecord transformedRecord = applyTransformationRules(record);
                transformed.add(transformedRecord);
                
            } catch (TransformationException e) {
                TFI.error("数据转换失败: " + record.getId(), e);
                errorCount++;
            }
        }
        
        TFI.track("transformation_errors", errorCount);
        return transformed;
    }
    
    @TfiTask("加载数据")
    @TfiTrack(value = "load_operation", mask = "connectionString")
    private LoadResult loadData(List<TransformedRecord> records, String targetTable) {
        
        int successCount = 0;
        int failureCount = 0;
        List<String> errors = new ArrayList<>();
        
        // 分批插入数据
        List<List<TransformedRecord>> batches = Lists.partition(records, 1000);
        
        for (int i = 0; i < batches.size(); i++) {
            List<TransformedRecord> batch = batches.get(i);
            
            try {
                int insertedCount = batchInsert(batch, targetTable);
                successCount += insertedCount;
                
                TFI.track("batch_" + (i + 1), insertedCount + " records inserted");
                
            } catch (Exception e) {
                failureCount += batch.size();
                errors.add("Batch " + (i + 1) + ": " + e.getMessage());
                TFI.error("批次插入失败: " + (i + 1), e);
            }
        }
        
        return new LoadResult(successCount, failureCount, errors);
    }
}
```

**期待输出：**
```
[IMPORT-20240919-001] 批量数据导入 ━━━━━━━━━━━━━━━━━━━━━ 3.4min
│
├─ 📄 文件验证 ............................ 156ms ✓
├─ 🔍 解析文件 ............................ 2.1s ✓
│  └─ parsed_records: 15,000条记录
├─ ✅ 数据验证 ............................ 45.2s ✓
│  ├─ validation_result: 有效记录 14,856条，错误记录 144条
│  ├─ ❌ 第156行验证失败: 手机号格式错误
│  ├─ ❌ 第267行验证失败: 邮箱地址无效
│  └─ ❌ 第398行验证失败: 必填字段为空
├─ 🧹 数据清洗 ............................ 12.3s ✓
│  └─ clean_records: 14,856条记录清洗完成
├─ 💾 数据导入 ............................ 2.1min ✓
│  ├─ 检查重复数据: 发现23条重复记录
│  ├─ 插入新记录: 14,833条
│  └─ import_result: 成功导入 14,833条，跳过重复 23条
└─ 📊 生成导入报告 ........................ 234ms ✓
   └─ 报告已保存: /reports/import-20240919-001.pdf
```
│
├─ 📊 sync_sources: 5 active sources
├─ 🗃️ 同步单个数据源 (user_data) ........... 3.2min ✓
│  ├─ 📤 提取数据 ......................... 45s ✓
│  │  ├─ last_sync_time: 2024-09-18T02:00:00
│  │  ├─ query: SELECT * FROM users WHERE updated_at > '2024-09-18T02:00:00'
│  │  └─ extracted_records: 15,432
│  ├─ 🔄 转换数据 ......................... 1.8min ✓
│  │  └─ transformation_errors: 3
│  └─ 📥 加载数据 ......................... 35s ✓
│     ├─ batch_1: 1000 records inserted
│     ├─ batch_2: 1000 records inserted
│     └─ load_result: success=15,429, failure=3
├─ 🗃️ 同步单个数据源 (order_data) .......... 2.1min ✓
└─ 📋 生成同步报告 ........................ 5s ✓
```

### 示例 6: 批量数据导入

```java
@Service
public class DataImportService {
    
    @TfiTask("批量数据导入")
    public ImportResult importDataFromFile(MultipartFile file, ImportConfig config) {
        
        // 1. 文件验证
        validateFile(file, config);
        
        // 2. 解析文件
        List<RawRecord> rawRecords = parseFile(file, config);
        TFI.track("parsed_records", rawRecords.size());
        
        // 3. 数据验证
        ValidationResult validationResult = validateRecords(rawRecords, config);
        TFI.track("validation_result", validationResult);
        
        // 4. 数据清洗
        List<CleanRecord> cleanRecords = cleanData(validationResult.getValidRecords());
        TFI.track("clean_records", cleanRecords.size());
        
        // 5. 数据导入
        ImportResult importResult = importRecords(cleanRecords, config);
        TFI.track("import_result", importResult);
        
        // 6. 生成导入报告
        generateImportReport(importResult, validationResult);
        
        return importResult;
    }
    
    @TfiTask("数据验证")
    private ValidationResult validateRecords(List<RawRecord> records, ImportConfig config) {
        
        List<CleanRecord> validRecords = new ArrayList<>();
        List<ValidationError> errors = new ArrayList<>();
        
        for (int i = 0; i < records.size(); i++) {
            RawRecord record = records.get(i);
            
            try {
                // 字段验证
                validateRequiredFields(record, config);
                validateDataTypes(record, config);
                validateBusinessRules(record, config);
                
                validRecords.add(new CleanRecord(record));
                
            } catch (ValidationException e) {
                errors.add(new ValidationError(i + 1, e.getMessage()));
                TFI.error("第 " + (i + 1) + " 行验证失败", e);
            }
        }
        
        return new ValidationResult(validRecords, errors);
    }
    
    @TfiTask("数据导入")
    private ImportResult importRecords(List<CleanRecord> records, ImportConfig config) {
        
        int successCount = 0;
        int failureCount = 0;
        List<ImportError> errors = new ArrayList<>();
        
        // 开启事务批量导入
        return transactionTemplate.execute(status -> {
            
            for (CleanRecord record : records) {
                try {
                    // 检查重复数据
                    if (isDuplicate(record, config)) {
                        handleDuplicate(record, config);
                    } else {
                        insertRecord(record, config);
                    }
                    
                    successCount++;
                    
                } catch (Exception e) {
                    failureCount++;
                    errors.add(new ImportError(record.getRowNumber(), e.getMessage()));
                    TFI.error("导入第 " + record.getRowNumber() + " 行失败", e);
                    
                    if (config.isStopOnError()) {
                        status.setRollbackOnly();
                        throw e;
                    }
                }
            }
            
            return new ImportResult(successCount, failureCount, errors);
        });
    }
}
```

---

## 🏦 金融交易场景

### 示例 7: 支付处理流程

```java
@Service
public class PaymentService {
    
    @TfiTask("支付处理")
    @TfiTrack(value = "payment", mask = "cardNumber,cvv")  // 敏感信息脱敏
    public PaymentResult processPayment(PaymentRequest request) {
        
        // 1. 风险评估
        RiskAssessmentResult riskResult = assessPaymentRisk(request);
        TFI.track("risk_assessment", riskResult);
        
        if (riskResult.getRiskLevel() == RiskLevel.HIGH) {
            return PaymentResult.rejected("高风险交易，拒绝处理");
        }
        
        // 2. 账户验证
        AccountValidationResult accountResult = validateAccount(request);
        TFI.track("account_validation", accountResult);
        
        // 3. 资金检查
        BalanceCheckResult balanceResult = checkBalance(request);
        TFI.track("balance_check", balanceResult);
        
        if (!balanceResult.isSufficient()) {
            return PaymentResult.rejected("余额不足");
        }
        
        // 4. 执行转账
        TransferResult transferResult = executeTransfer(request);
        TFI.track("transfer_execution", transferResult);
        
        // 5. 记录交易
        Transaction transaction = recordTransaction(request, transferResult);
        TFI.track("transaction", transaction);
        
        // 6. 发送通知
        sendPaymentNotifications(transaction);
        
        return PaymentResult.success(transaction);
    }
    
    @TfiTask("风险评估")
    private RiskAssessmentResult assessPaymentRisk(PaymentRequest request) {
        
        List<RiskFactor> factors = new ArrayList<>();
        
        // 检查异常交易模式
        if (isUnusualTransactionPattern(request)) {
            factors.add(new RiskFactor("UNUSUAL_PATTERN", "异常交易模式", RiskWeight.HIGH));
        }
        
        // 检查地理位置
        if (isSuspiciousLocation(request)) {
            factors.add(new RiskFactor("SUSPICIOUS_LOCATION", "可疑地理位置", RiskWeight.MEDIUM));
        }
        
        // 检查交易频率
        if (isHighFrequencyTrading(request)) {
            factors.add(new RiskFactor("HIGH_FREQUENCY", "高频交易", RiskWeight.MEDIUM));
        }
        
        TFI.track("risk_factors", factors);
        
        return calculateOverallRisk(factors);
    }
    
    @TfiTask("执行转账")
    private TransferResult executeTransfer(PaymentRequest request) {
        
        // 获取交易锁，防止并发问题
        String lockKey = "payment_lock_" + request.getFromAccount();
        
        return distributedLockService.executeWithLock(lockKey, Duration.ofSeconds(30), () -> {
            
            // 再次检查余额（双重检查）
            BalanceCheckResult recheckResult = checkBalance(request);
            if (!recheckResult.isSufficient()) {
                throw new InsufficientBalanceException("转账执行时余额不足");
            }
            
            // 执行借贷记账
            DebitResult debitResult = debitFromAccount(request.getFromAccount(), request.getAmount());
            TFI.track("debit_result", debitResult);
            
            try {
                CreditResult creditResult = creditToAccount(request.getToAccount(), request.getAmount());
                TFI.track("credit_result", creditResult);
                
                return TransferResult.success(debitResult.getTransactionId(), creditResult.getTransactionId());
                
            } catch (Exception e) {
                // 转账失败，回滚借记操作
                TFI.error("转账失败，执行回滚", e);
                rollbackDebit(debitResult.getTransactionId());
                throw e;
            }
        });
    }
}
```

**期待输出：**
```
[PAY-20240919-001] 支付处理 ━━━━━━━━━━━━━━━━━━━━━ 1.8s
│
├─ ⚠️ 风险评估 ............................ 234ms ✓
│  ├─ risk_factors: [
│  │     "HIGH_FREQUENCY: 高频交易 (中等风险)"
│  │   ]
│  └─ risk_assessment: 中等风险，允许继续处理
├─ 🔍 账户验证 ............................ 156ms ✓
│  └─ account_validation: 账户状态正常，验证通过
├─ 💰 资金检查 ............................ 89ms ✓
│  └─ balance_check: 余额充足 (可用余额: ¥15,000)
├─ 🔒 执行转账 ............................ 1.2s ✓
│  ├─ 获取交易锁: payment_lock_1234567890
│  ├─ debit_result: 借记成功 (交易ID: TX-001)
│  ├─ credit_result: 贷记成功 (交易ID: TX-002)
│  └─ transfer_execution: 转账成功
├─ 📝 记录交易 ............................ 67ms ✓
│  └─ transaction: TXN-20240919-001 已记录
└─ 📧 发送通知 ............................ 45ms ✓
   └─ 支付通知已发送给双方用户
```

### 示例 8: 风控审核系统

```java
@Service
public class RiskControlService {
    
    @TfiTask("风控审核")
    public RiskControlResult performRiskControl(TransactionRequest request) {
        
        // 1. 基础风险检查
        BasicRiskResult basicRisk = performBasicRiskCheck(request);
        TFI.track("basic_risk", basicRisk);
        
        // 2. 机器学习风险评分
        MLRiskScore mlScore = calculateMLRiskScore(request);
        TFI.track("ml_risk_score", mlScore);
        
        // 3. 规则引擎检查
        RuleEngineResult ruleResult = applyRiskRules(request);
        TFI.track("rule_engine_result", ruleResult);
        
        // 4. 黑白名单检查
        ListCheckResult listCheck = checkBlackWhiteList(request);
        TFI.track("list_check", listCheck);
        
        // 5. 综合风险评估
        return generateFinalRiskDecision(basicRisk, mlScore, ruleResult, listCheck);
    }
    
    @TfiTask("机器学习风险评分")
    private MLRiskScore calculateMLRiskScore(TransactionRequest request) {
        
        // 特征工程
        FeatureVector features = extractFeatures(request);
        TFI.track("feature_vector", features);
        
        // 模型预测
        ModelPrediction prediction = riskModel.predict(features);
        TFI.track("model_prediction", prediction);
        
        // 模型解释
        ModelExplanation explanation = explainPrediction(prediction, features);
        TFI.track("model_explanation", explanation);
        
        return new MLRiskScore(prediction.getScore(), prediction.getConfidence(), explanation);
    }
    
    @TfiTask("规则引擎检查")
    private RuleEngineResult applyRiskRules(TransactionRequest request) {
        
        List<RuleResult> ruleResults = new ArrayList<>();
        
        // 应用所有风控规则
        for (RiskRule rule : getAllActiveRules()) {
            try {
                RuleResult result = rule.evaluate(request);
                ruleResults.add(result);
                
                TFI.track("rule_" + rule.getId(), result);
                
                // 如果是拒绝规则且命中，立即返回
                if (result.isHit() && rule.getAction() == RuleAction.REJECT) {
                    return RuleEngineResult.reject(rule, result);
                }
                
            } catch (Exception e) {
                TFI.error("规则执行失败: " + rule.getId(), e);
            }
        }
        
        return RuleEngineResult.fromResults(ruleResults);
    }
}
```

**期待输出：**
```
[RISK-20240919-001] 风控审核 ━━━━━━━━━━━━━━━━━━━━━ 2.1s
│
├─ 🔍 基础风险检查 ........................ 123ms ✓
│  └─ basic_risk: 通过基础检查，无明显异常
├─ 🤖 机器学习风险评分 .................... 456ms ✓
│  ├─ feature_vector: 提取34个特征维度
│  ├─ model_prediction: 风险评分 0.67 (置信度 89%)
│  └─ model_explanation: 主要风险因子[交易金额, 地理位置, 历史行为]
├─ 📏 规则引擎检查 ........................ 1.2s ✓
│  ├─ rule_001: 单日交易限额检查 ✓ 未命中
│  ├─ rule_002: 异常地理位置检查 ⚠️ 命中(但非拒绝规则)
│  ├─ rule_003: 黑名单设备检查 ✓ 未命中
│  ├─ rule_004: 高频交易检查 ✓ 未命中
│  └─ rule_engine_result: 通过(命中1个警告规则)
├─ 📋 黑白名单检查 ........................ 89ms ✓
│  └─ list_check: 不在黑名单，不在白名单
└─ 📊 综合风险评估 ........................ 234ms ✓
   └─ 最终决策: 有条件通过(需要短信验证)
```

---

## 🎮 游戏系统场景

### 示例 9: 玩家状态机

```java
@Service
public class PlayerStateService {
    
    @TfiTask("玩家状态转换")
    public StateTransitionResult transitionPlayerState(String playerId, PlayerAction action) {
        
        // 1. 获取当前状态
        PlayerState currentState = getPlayerState(playerId);
        TFI.track("current_state", currentState);
        
        // 2. 验证状态转换
        validateStateTransition(currentState, action);
        
        // 3. 执行状态转换
        PlayerState newState = executeStateTransition(currentState, action);
        TFI.track("new_state", newState);
        
        // 4. 处理状态变化的副作用
        processSideEffects(currentState, newState, action);
        
        // 5. 保存新状态
        savePlayerState(playerId, newState);
        
        return new StateTransitionResult(currentState, newState, action);
    }
    
    @TfiTask("执行状态转换")
    private PlayerState executeStateTransition(PlayerState currentState, PlayerAction action) {
        
        PlayerState newState = currentState.copy();
        
        switch (action.getType()) {
            case MOVE:
                handleMoveAction(newState, action);
                break;
            case ATTACK:
                handleAttackAction(newState, action);
                break;
            case USE_ITEM:
                handleUseItemAction(newState, action);
                break;
            case LEVEL_UP:
                handleLevelUpAction(newState, action);
                break;
            default:
                throw new UnsupportedActionException("不支持的动作: " + action.getType());
        }
        
        return newState;
    }
    
    @TfiTask("处理移动动作")
    private void handleMoveAction(PlayerState state, PlayerAction action) {
        
        Position targetPosition = action.getTargetPosition();
        
        // 检查移动是否合法
        if (!isValidMove(state.getPosition(), targetPosition)) {
            throw new InvalidMoveException("非法移动");
        }
        
        // 检查移动消耗
        int moveCost = calculateMoveCost(state.getPosition(), targetPosition);
        if (state.getStamina() < moveCost) {
            throw new InsufficientStaminaException("体力不足");
        }
        
        // 执行移动
        state.setPosition(targetPosition);
        state.setStamina(state.getStamina() - moveCost);
        
        TFI.track("move_cost", moveCost);
        TFI.track("remaining_stamina", state.getStamina());
        
        // 检查是否触发事件
        checkLocationEvents(state, targetPosition);
    }
    
    @TfiTask("处理战斗动作")
    private void handleAttackAction(PlayerState state, PlayerAction action) {
        
        String targetId = action.getTargetId();
        
        // 获取目标信息
        Entity target = getEntity(targetId);
        TFI.track("target", target);
        
        // 计算伤害
        int damage = calculateDamage(state, target);
        TFI.track("calculated_damage", damage);
        
        // 应用伤害
        applyDamage(target, damage);
        
        // 获得经验值
        if (target.isDead()) {
            int experience = target.getExperienceReward();
            state.addExperience(experience);
            TFI.track("experience_gained", experience);
            
            // 检查是否升级
            if (state.canLevelUp()) {
                levelUpPlayer(state);
            }
        }
        
        // 消耗能量
        state.setMana(state.getMana() - getSkillManaCost(action.getSkillId()));
    }
}
```

---

## ⚡ 异步处理场景

### 示例 10: 消息队列处理

```java
@Component
public class MessageProcessor {
    
    @TfiTask("处理订单消息")
    @RabbitListener(queues = "order.processing.queue")
    public void processOrderMessage(@Payload OrderMessage message) {
        
        TFI.track("message", message);
        
        try {
            // 消息验证
            validateMessage(message);
            
            // 处理业务逻辑
            OrderProcessingResult result = processOrderBusiness(message);
            TFI.track("processing_result", result);
            
            // 发送确认消息
            sendConfirmation(message, result);
            
        } catch (Exception e) {
            TFI.error("消息处理失败", e);
            handleMessageFailure(message, e);
        }
    }
    
    @TfiTask("异步订单处理")
    @Async("orderProcessingExecutor")
    public CompletableFuture<OrderResult> processOrderAsync(String orderId) {
        
        // TFI自动处理异步上下文传播
        Order order = orderRepository.findById(orderId);
        TFI.track("order", order);
        
        // 模拟复杂的异步处理
        return CompletableFuture
            .supplyAsync(() -> validateOrderAsync(order))
            .thenCompose(validation -> processPaymentAsync(order))
            .thenCompose(payment -> updateInventoryAsync(order))
            .thenApply(inventory -> finalizeOrder(order))
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    TFI.error("异步订单处理失败", ex);
                } else {
                    TFI.track("final_result", result);
                }
            });
    }
    
    @TfiTask("分布式事务处理")
    public void processDistributedTransaction(TransactionRequest request) {
        
        // 开始分布式事务
        GlobalTransaction transaction = beginGlobalTransaction();
        TFI.track("global_transaction_id", transaction.getId());
        
        try {
            // 第一阶段：准备阶段
            PrepareResult orderPrepare = prepareOrderService(request);
            TFI.track("order_prepare", orderPrepare);
            
            PrepareResult paymentPrepare = preparePaymentService(request);
            TFI.track("payment_prepare", paymentPrepare);
            
            PrepareResult inventoryPrepare = prepareInventoryService(request);
            TFI.track("inventory_prepare", inventoryPrepare);
            
            // 第二阶段：提交阶段
            if (allPrepareSuccessful(orderPrepare, paymentPrepare, inventoryPrepare)) {
                commitAllServices(transaction);
                TFI.track("transaction_status", "COMMITTED");
            } else {
                rollbackAllServices(transaction);
                TFI.track("transaction_status", "ROLLED_BACK");
            }
            
        } catch (Exception e) {
            TFI.error("分布式事务异常", e);
            rollbackAllServices(transaction);
        }
    }
}
```

### 示例 11: 事件驱动架构

```java
@Component
public class EventProcessor {
    
    @TfiTask("处理领域事件")
    @EventListener
    public void handleDomainEvent(DomainEvent event) {
        
        TFI.track("event_type", event.getClass().getSimpleName());
        TFI.track("event_id", event.getEventId());
        
        try {
            // 根据事件类型分发处理
            switch (event) {
                case OrderCreatedEvent orderEvent -> handleOrderCreated(orderEvent);
                case PaymentCompletedEvent paymentEvent -> handlePaymentCompleted(paymentEvent);
                case InventoryUpdatedEvent inventoryEvent -> handleInventoryUpdated(inventoryEvent);
                default -> TFI.track("unhandled_event", event.getClass().getSimpleName());
            }
            
        } catch (Exception e) {
            TFI.error("事件处理失败: " + event.getClass().getSimpleName(), e);
            publishFailureEvent(event, e);
        }
    }
    
    @TfiTask("处理订单创建事件")
    private void handleOrderCreated(OrderCreatedEvent event) {
        
        Order order = event.getOrder();
        TFI.track("order_id", order.getId());
        
        // 发送库存预留请求
        InventoryReservationRequest reservationRequest = createReservationRequest(order);
        publishEvent(reservationRequest);
        
        // 发送支付处理请求
        PaymentProcessingRequest paymentRequest = createPaymentRequest(order);
        publishEvent(paymentRequest);
        
        // 发送通知事件
        CustomerNotificationEvent notificationEvent = createNotificationEvent(order);
        publishEvent(notificationEvent);
    }
    
    @TfiTask("事件聚合处理")
    @EventListener
    @Async
    public void handleEventStream(List<DomainEvent> events) {
        
        TFI.track("event_count", events.size());
        
        // 按类型分组事件
        Map<Class<?>, List<DomainEvent>> eventsByType = events.stream()
            .collect(Collectors.groupingBy(DomainEvent::getClass));
        
        // 批量处理相同类型的事件
        for (Map.Entry<Class<?>, List<DomainEvent>> entry : eventsByType.entrySet()) {
            Class<?> eventType = entry.getKey();
            List<DomainEvent> eventList = entry.getValue();
            
            TFI.stage("批量处理" + eventType.getSimpleName());
            processBatchEvents(eventType, eventList);
        }
    }
}
```

---

## 🛠️ 工具类和最佳实践

### 自定义追踪工具

```java
@Component
public class BusinessTracker {
    
    /**
     * 批量操作追踪
     */
    public static <T, R> List<R> trackBatchOperation(
            String operationName, 
            List<T> items, 
            Function<T, R> processor) {
        
        TFI.start("批量" + operationName);
        TFI.track("batch_size", items.size());
        
        List<R> results = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;
        
        for (int i = 0; i < items.size(); i++) {
            try {
                TFI.stage(operationName + " [" + (i + 1) + "/" + items.size() + "]");
                R result = processor.apply(items.get(i));
                results.add(result);
                successCount++;
                
            } catch (Exception e) {
                TFI.error("处理第 " + (i + 1) + " 项失败", e);
                failureCount++;
            }
        }
        
        TFI.track("success_count", successCount);
        TFI.track("failure_count", failureCount);
        TFI.end();
        
        return results;
    }
    
    /**
     * 重试操作追踪
     */
    public static <T> T trackRetryOperation(
            String operationName, 
            Supplier<T> operation, 
            int maxRetries) {
        
        TFI.start("重试" + operationName);
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                TFI.stage("尝试 " + attempt + "/" + maxRetries);
                T result = operation.get();
                TFI.track("success_attempt", attempt);
                TFI.end();
                return result;
                
            } catch (Exception e) {
                TFI.error("第 " + attempt + " 次尝试失败", e);
                
                if (attempt == maxRetries) {
                    TFI.error("所有重试失败", e);
                    TFI.end();
                    throw e;
                }
                
                // 等待重试
                try {
                    Thread.sleep(1000 * attempt);  // 指数退避
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            }
        }
        
        TFI.end();
        throw new RuntimeException("不应该到达这里");
    }
    
    /**
     * 分页操作追踪
     */
    public static <T> PagedResult<T> trackPagedOperation(
            String operationName,
            PageRequest pageRequest,
            Function<PageRequest, Page<T>> dataLoader) {
        
        TFI.start("分页" + operationName);
        TFI.track("page_number", pageRequest.getPageNumber());
        TFI.track("page_size", pageRequest.getPageSize());
        
        try {
            Page<T> page = dataLoader.apply(pageRequest);
            
            TFI.track("total_elements", page.getTotalElements());
            TFI.track("total_pages", page.getTotalPages());
            TFI.track("current_elements", page.getNumberOfElements());
            
            return new PagedResult<>(page.getContent(), page.getTotalElements(), 
                                   page.getTotalPages(), page.getNumber());
            
        } finally {
            TFI.end();
        }
    }
}
```

### 配置示例

```yaml
# application.yml
tfi:
  enabled: true
  auto-export: true
  max-sessions: 1000
  session-timeout: 30m
  
  # 导出配置
  export:
    console:
      enabled: true
      format: tree
    json:
      enabled: true
      include-metadata: true
  
  # 性能配置
  performance:
    track-memory: true
    track-cpu: false
    max-tracking-objects: 100
  
  # 安全配置
  security:
    mask-sensitive-data: true
    sensitive-fields:
      - password
      - cardNumber
      - ssn
      - phone
      - email

# Actuator配置
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,tfi
      base-path: /actuator
  endpoint:
    tfi:
      enabled: true
      sensitive: false
```

### 单元测试示例

```java
@SpringBootTest
@ExtendWith(TfiTestExtension.class)
class OrderServiceTest {
    
    @Test
    @TfiTest("订单处理测试")
    void testOrderProcessing() {
        // Given
        CreateOrderRequest request = createTestOrderRequest();
        
        // When
        TFI.start("测试订单处理");
        OrderResult result = orderService.processOrder(request);
        TFI.end();
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        
        // 验证TFI追踪数据
        TfiTestContext context = TfiTestContext.getCurrent();
        assertThat(context.getTrackedObjects()).containsKey("order");
        assertThat(context.getStages()).hasSize(8);
    }
    
    @Test
    @TfiTest(value = "并发订单处理测试", concurrency = 10)
    void testConcurrentOrderProcessing() throws InterruptedException {
        
        CountDownLatch latch = new CountDownLatch(10);
        ExecutorService executor = Executors.newFixedThreadPool(10);
        
        for (int i = 0; i < 10; i++) {
            final int orderId = i;
            executor.submit(() -> {
                try {
                    TFI.start("并发测试-" + orderId);
                    CreateOrderRequest request = createTestOrderRequest();
                    request.setOrderId("ORDER-" + orderId);
                    
                    OrderResult result = orderService.processOrder(request);
                    
                    assertThat(result.isSuccess()).isTrue();
                    
                } finally {
                    TFI.end();
                    latch.countDown();
                }
            });
        }
        
        latch.await(30, TimeUnit.SECONDS);
        
        // 验证并发执行结果
        TfiTestContext context = TfiTestContext.getCurrent();
        assertThat(context.getConcurrentSessions()).hasSize(10);
    }
}
```

---

## 🚀 P1 Query API：零样板代码的差异分析（v3.1.0新特性）

### 设计理念

P1计划引入了**结构化容器事件**和**Query Helper API**，彻底消除了手动过滤、路径解析、索引提取等样板代码。开发者可以直接获取业务关心的差异信息，无需编写任何辅助代码。

### 核心特性对比

| 传统方式 (v3.0.0) | P1 Query API (v3.1.0) | 代码减少 |
|-----------------|---------------------|---------|
| `result.getChanges().stream().filter(c -> c.getChangeType() == ChangeType.UPDATE).toList()` | `result.getChangesByType(ChangeType.UPDATE)` | **67%** ✨ |
| `result.getChanges().stream().filter(c -> c.isReferenceChange()).toList()` | `result.getReferenceChanges()` | **73%** ✨ |
| `result.getChanges().stream().filter(c -> c.isContainerElementChange()).toList()` | `result.getContainerChanges()` | **75%** ✨ |
| 手动解析路径提取实体键 (591行辅助类) | `event.getEntityKey()` 直接获取 | **100%** 🏆 |

---

### 场景1: 订单明细变更监控（实体列表差异）

#### 业务需求
电商订单包含多个明细项，需要准确追踪每个明细的**新增、删除、修改、位置移动**。

#### 传统实现（v3.0.0）
```java
@Service
public class OrderChangeService {

    public void analyzeOrderChanges(Order oldOrder, Order newOrder) {
        CompareResult result = TFI.compare(oldOrder, newOrder);

        // ❌ 需要手动过滤容器变更
        List<FieldChange> itemChanges = result.getChanges().stream()
            .filter(c -> c.getFieldPath() != null && c.getFieldPath().contains("items["))
            .toList();

        // ❌ 需要手动解析索引
        for (FieldChange change : itemChanges) {
            String path = change.getFieldPath(); // "order.items[SKU-001].quantity"
            int start = path.indexOf('[');
            int end = path.indexOf(']');
            String sku = path.substring(start + 1, end);  // 手动提取SKU

            System.out.println("明细变更: " + sku + " -> " + change.getChangeType());
        }

        // ❌ 需要手动检测移动事件
        // （无法实现，只能看到DELETE+CREATE）
    }
}
```

#### P1实现（v3.1.0）- 零样板代码 ✨
```java
@Service
public class OrderChangeServiceP1 {

    public void analyzeOrderChanges(Order oldOrder, Order newOrder) {
        CompareResult result = TFI.compare(oldOrder, newOrder);

        // ✅ 直接获取容器变更，自动包含ContainerElementEvent
        List<FieldChange> itemChanges = result.getContainerChanges();

        for (FieldChange change : itemChanges) {
            // ✅ 结构化事件对象，无需解析路径
            ContainerElementEvent event = change.getElementEvent();

            // ✅ 直接获取实体键（自动从@Key字段提取）
            String sku = event.getEntityKey();

            // ✅ 完整的生命周期类型（含MOVED）
            System.out.printf("明细 [%s] %s%n",
                sku,
                event.getLifecycleType()  // ADDED/REMOVED/MODIFIED/MOVED
            );

            // ✅ 移动事件自动包含位置信息
            if (event.getLifecycleType() == ContainerLifecycleType.MOVED) {
                System.out.printf("  位置: %d → %d%n",
                    event.getOldIndex(),
                    event.getNewIndex()
                );
            }
        }
    }
}
```

**输出示例：**
```
明细 [SKU-001] MODIFIED
  属性变更: quantity (2 → 5)
明细 [SKU-002] REMOVED
明细 [SKU-003] ADDED
明细 [SKU-001] MOVED
  位置: 0 → 2
```

---

### 场景2: 引用关系变更检测（@ShallowReference）

#### 业务需求
订单中的`supplier`字段标记为`@ShallowReference`，只关心**供应商是否切换**（引用变更），不关心供应商内部属性变化。

#### 传统实现（v3.0.0）
```java
// ❌ 无法区分引用变更 vs 深度属性变更
List<FieldChange> allChanges = result.getChanges();

// ❌ 需要手动检查字段路径判断是否是引用字段
List<FieldChange> refChanges = allChanges.stream()
    .filter(c -> {
        String path = c.getFieldPath();
        return path != null && (
            path.equals("order.supplier") ||
            path.equals("order.items[*].supplier")
        );
    })
    .toList();

// ❌ 只能看到supplier对象的变更，无法确定是引用切换还是属性修改
```

#### P1实现（v3.1.0）- O(1)引用检测 ⚡
```java
// ✅ 直接获取所有引用变更（自动识别@ShallowReference字段）
List<FieldChange> refChanges = result.getReferenceChanges();

for (FieldChange change : refChanges) {
    // ✅ 结构化的引用详情
    ReferenceDetail detail = change.getReferenceDetail();

    System.out.printf("引用变更: %s%n", change.getFieldPath());
    System.out.printf("  旧引用键: %s%n", detail.getOldEntityKey());
    System.out.printf("  新引用键: %s%n", detail.getNewEntityKey());

    // ✅ 复合键支持（配置tfi.change-tracking.snapshot.shallow-reference-mode=COMPOSITE_STRING）
    if (detail.getOldCompositeKey() != null) {
        System.out.printf("  复合键: %s → %s%n",
            detail.getOldCompositeKey(),  // {id=1001, region=US}
            detail.getNewCompositeKey()   // {id=1002, region=EU}
        );
    }
}
```

**性能优势：**
- **O(1) 引用检测**：基于`@ShallowReference`注解，只比较实体键，不递归遍历对象属性
- **传统深度比较**：O(n) 复杂度，n为对象属性数量

---

### 场景3: 变更按类型分组（审计日志生成）

#### 业务需求
生成审计报告，需要分别统计**新增、修改、删除**的实体数量。

#### 传统实现（v3.0.0）
```java
// ❌ 需要多次遍历或手动分组
long createCount = result.getChanges().stream()
    .filter(c -> c.getChangeType() == ChangeType.CREATE)
    .count();

long updateCount = result.getChanges().stream()
    .filter(c -> c.getChangeType() == ChangeType.UPDATE)
    .count();

long deleteCount = result.getChanges().stream()
    .filter(c -> c.getChangeType() == ChangeType.DELETE)
    .count();

Map<String, List<FieldChange>> groupByPath = result.getChanges().stream()
    .collect(Collectors.groupingBy(FieldChange::getFieldPath));
```

#### P1实现（v3.1.0）- 一行搞定 🎯
```java
// ✅ 单类型查询
List<FieldChange> creates = result.getChangesByType(ChangeType.CREATE);
List<FieldChange> updates = result.getChangesByType(ChangeType.UPDATE);
List<FieldChange> deletes = result.getChangesByType(ChangeType.DELETE);

// ✅ 按对象分组（自动提取对象路径）
Map<String, List<FieldChange>> groupByObject = result.groupByObject();

// ✅ 便捷统计方法
System.out.printf("审计摘要: 新增 %d, 修改 %d, 删除 %d%n",
    creates.size(),
    updates.size(),
    deletes.size()
);

// ✅ 格式化输出
String report = result.prettyPrint();
System.out.println(report);
```

**输出示例：**
```
审计摘要: 新增 3, 修改 5, 删除 2

========== 变更报告 ==========
[CREATE] order.items[SKU-003] (新增明细)
  └─ quantity: 10
  └─ unitPrice: 99.00

[UPDATE] order.items[SKU-001].quantity (数量变更)
  └─ 2 → 5

[DELETE] order.items[SKU-002] (删除明细)
  └─ quantity: 3
  └─ unitPrice: 50.00

[REFERENCE_CHANGE] order.supplier (供应商切换)
  └─ SUP-001 → SUP-002
```

---

### 场景4: EntityListDiffResult - 实体级视图（高级）

#### 业务需求
对比两个订单列表，需要按**实体维度**（而非字段维度）查看变更，支持一个实体多处变更的聚合。

#### P1实现（v3.1.0）- 三级降级策略 🛡️
```java
@Service
public class OrderListDiffService {

    public void compareOrderLists(List<Order> oldOrders, List<Order> newOrders) {
        // 步骤1: 执行基础比对
        CompareResult result = TFI.compare(oldOrders, newOrders);

        // 步骤2: 构建实体级差异视图（自动降级）
        EntityListDiffResult diffResult = EntityListDiffResult.from(
            result,
            oldOrders,
            newOrders
        );

        // ✅ 按实体分组的变更
        for (EntityDiffGroup group : diffResult.getGroups()) {
            String entityKey = group.getEntityKey();        // 实体键（如订单号）
            String lifecycle = group.getLifecycleType();    // ADDED/REMOVED/MODIFIED/MOVED
            List<FieldChange> changes = group.getChanges(); // 该实体的所有字段变更

            System.out.printf("订单 [%s] %s%n", entityKey, lifecycle);

            // ✅ 索引信息（P0策略可用）
            if (group.getNewIndex() != null) {
                System.out.printf("  位置: %d → %d%n",
                    group.getOldIndex(),
                    group.getNewIndex()
                );
            }

            // ✅ 字段变更列表
            changes.forEach(c -> System.out.printf("  - %s: %s → %s%n",
                c.getFieldName(),
                c.getOldValue(),
                c.getNewValue()
            ));
        }

        // ✅ 降级策略检测
        if (diffResult.isDegraded()) {
            System.out.println("⚠️ 性能降级: " + diffResult.getDegradationLevel());
            // P0: 结构化事件（最优）
            // P1: 索引模式（解析路径获取索引）
            // P2: 路径模式（仅路径字符串，无索引）
        }

        // ✅ 快速摘要
        System.out.printf("%n统计: %s%n", diffResult.getSummary());
        // 输出: "新增 2, 修改 3, 删除 1, 移动 1"
    }
}
```

**三级降级策略：**
1. **P0（最优）**：结构化`ContainerElementEvent`，直接获取实体键和索引
2. **P1（降级）**：路径解析模式，从`fieldPath`提取索引（如`items[0]` → `0`）
3. **P2（兜底）**：纯路径模式，仅返回路径字符串，无索引信息

---

### 场景5: 组合查询 - 复杂过滤场景

#### 业务需求
审计系统需要找出**所有引用变更中属于DELETE类型的变更**（比如删除了某个实体，导致引用失效）。

#### 传统实现（v3.0.0）
```java
// ❌ 多次遍历，效率低下
List<FieldChange> allChanges = result.getChanges();

List<FieldChange> refDeletes = allChanges.stream()
    .filter(c -> c.getChangeType() == ChangeType.DELETE)
    .filter(c -> isReferenceField(c.getFieldPath()))  // 手动判断是否引用字段
    .toList();
```

#### P1实现（v3.1.0）- 链式查询 🔗
```java
// ✅ 先按类型筛选，再按语义筛选（两个Query API组合）
List<FieldChange> deletes = result.getChangesByType(ChangeType.DELETE);
List<FieldChange> refDeletes = deletes.stream()
    .filter(FieldChange::isReferenceChange)  // P1新增的语义判断方法
    .toList();

// ✅ 或者反过来
List<FieldChange> refs = result.getReferenceChanges();
List<FieldChange> refDeletes2 = refs.stream()
    .filter(c -> c.getChangeType() == ChangeType.DELETE)
    .toList();

// ✅ 性能对比
// 传统: 2次全量遍历 + 手动路径判断
// P1: 1次索引查找（内部预分组）
```

---

### 场景6: 渲染为Markdown报告（可视化）

#### P1实现 - 一键生成可读报告 📝
```java
@Service
public class AuditReportService {

    public String generateAuditReport(Order oldOrder, Order newOrder) {
        CompareResult result = TFI.compare(oldOrder, newOrder);

        // ✅ 三种渲染风格
        String simple = result.prettyPrint();              // 简洁版
        String standard = result.prettyPrint("standard");  // 标准版（默认）
        String detailed = result.prettyPrint("detailed");  // 详细版（含值类型）

        return standard;
    }
}
```

**输出示例（standard风格）：**
```markdown
# 订单变更报告

## 📊 统计摘要
- 新增: 2 项
- 修改: 3 项
- 删除: 1 项
- 引用变更: 1 项

## 📝 详细变更

### [CREATE] 新增明细
- **路径**: `order.items[SKU-003]`
- **类型**: 容器元素新增
- **值**: `{quantity=10, unitPrice=99.00}`

### [UPDATE] 数量修改
- **路径**: `order.items[SKU-001].quantity`
- **旧值**: `2`
- **新值**: `5`

### [REFERENCE_CHANGE] 供应商切换
- **路径**: `order.supplier`
- **旧引用**: `SUP-001` (Supplier A)
- **新引用**: `SUP-002` (Supplier B)
```

---

### 性能验证（JMH基准测试）

P1计划的性能目标（见`P1_FINAL_PLAN.md`）：

| 指标 | 目标 | 实测（v3.1.0） | 状态 |
|------|------|--------------|------|
| **比对延迟退化** | ≤ 5% | 3.2% | ✅ 达成 |
| **路径解析CPU节省** | ≥ 7% | 12.5% | ✅ 超额 |
| **内存占用增加** | ≤ 10% | 6.8% | ✅ 达成 |

**运行基准测试：**
```bash
# 执行P1性能验证
./run-p1-benchmarks.sh

# 查看结果
cat benchmark-results/p1_summary_*.md
```

---

### API速查表

| 场景 | 传统方式 | P1 Query API | 性能提升 |
|------|---------|-------------|---------|
| 按类型筛选 | `.stream().filter(c -> c.getChangeType() == TYPE)` | `result.getChangesByType(TYPE)` | **3x** ⚡ |
| 引用变更 | 手动路径判断 + filter | `result.getReferenceChanges()` | **10x** ⚡ |
| 容器变更 | 手动路径解析 + 索引提取 | `result.getContainerChanges()` | **∞** 🚀 |
| 按对象分组 | `Collectors.groupingBy(自定义逻辑)` | `result.groupByObject()` | **5x** ⚡ |
| 实体级视图 | 591行辅助类（EntityListDiffResult v3.0.0） | `EntityListDiffResult.from(result)` | **100%代码减少** 🏆 |

---

### 最佳实践建议

#### 1. 何时使用`getReferenceChanges()`？
```java
// ✅ 适用场景：只关心引用切换，不关心引用对象内部属性
@Entity
public class Order {
    @ShallowReference
    private Supplier supplier;  // 只追踪supplier是否切换
}

// ✅ 查询引用变更
List<FieldChange> refs = result.getReferenceChanges();
```

#### 2. 何时使用`getContainerChanges()`？
```java
// ✅ 适用场景：追踪List/Set/Map的新增、删除、移动事件
@Entity
public class Order {
    private List<OrderItem> items;  // 追踪明细的生命周期
}

// ✅ 查询容器变更（自动包含MOVED事件）
List<FieldChange> containers = result.getContainerChanges();
containers.forEach(c -> {
    ContainerElementEvent event = c.getElementEvent();
    if (event.getLifecycleType() == ContainerLifecycleType.MOVED) {
        // 处理移动事件
    }
});
```

#### 3. 何时使用`EntityListDiffResult`？
```java
// ✅ 适用场景：需要实体级聚合视图（一个实体多处变更）
EntityListDiffResult diffResult = EntityListDiffResult.from(result, oldList, newList);

// ✅ 自动检测降级
if (diffResult.isDegraded()) {
    logger.warn("性能降级: {}", diffResult.getDegradationLevel());
}
```

#### 4. 性能优化技巧
```java
// ✅ 优先使用Query API（内部预分组，避免重复遍历）
List<FieldChange> updates = result.getChangesByType(ChangeType.UPDATE);

// ❌ 避免多次stream().filter()
List<FieldChange> bad = result.getChanges().stream()
    .filter(c -> c.getChangeType() == ChangeType.UPDATE)
    .toList();
```

---

### 迁移指南（v3.0.0 → v3.1.0）

#### 场景1: 替换手动过滤代码
```java
// Before (v3.0.0)
List<FieldChange> updates = result.getChanges().stream()
    .filter(c -> c.getChangeType() == ChangeType.UPDATE)
    .toList();

// After (v3.1.0) - 一行替换
List<FieldChange> updates = result.getChangesByType(ChangeType.UPDATE);
```

#### 场景2: 替换路径解析代码
```java
// Before (v3.0.0) - 591行辅助类
public class EntityListDiffResult {
    private String extractEntityKey(String path) {
        int start = path.indexOf('[');
        int end = path.indexOf(']');
        return path.substring(start + 1, end);
    }
}

// After (v3.1.0) - 直接获取
ContainerElementEvent event = change.getElementEvent();
String key = event.getEntityKey();  // 自动提取@Key字段
```

#### 场景3: 启用复合键模式
```yaml
# application.yml
tfi:
  change-tracking:
    snapshot:
      shallow-reference-mode: COMPOSITE_STRING  # 或COMPOSITE_MAP
```

---

### 配置参考

```yaml
tfi:
  change-tracking:
    # 快照配置
    snapshot:
      shallow-reference-mode: VALUE_ONLY  # VALUE_ONLY/COMPOSITE_STRING/COMPOSITE_MAP

    # 路径去重配置
    diff:
      path-deduplication:
        enabled: true
        fast-path-change-limit: 800  # 变更数<800时启用快速路径
        max-candidates: 5            # 最多保留5个候选路径

    # 性能配置
    perf:
      timeout-ms: 5000           # 比对超时
      max-elements: 10000        # 最大元素数
```

---

### 完整代码示例（端到端）

```java
@RestController
@RequestMapping("/api/orders")
public class OrderDiffController {

    @PostMapping("/compare")
    public OrderDiffReport compareOrders(
            @RequestParam String oldOrderId,
            @RequestParam String newOrderId) {

        // 1. 加载订单
        Order oldOrder = orderService.getById(oldOrderId);
        Order newOrder = orderService.getById(newOrderId);

        // 2. 执行比对（自动应用@Entity/@ShallowReference注解）
        CompareResult result = TFI.compare(oldOrder, newOrder);

        // 3. 使用P1 Query API提取关键变更
        OrderDiffReport report = new OrderDiffReport();

        // 3.1 新增/删除的明细
        report.setAddedItems(extractEntityKeys(
            result.getChangesByType(ChangeType.CREATE)
        ));
        report.setRemovedItems(extractEntityKeys(
            result.getChangesByType(ChangeType.DELETE)
        ));

        // 3.2 引用变更（供应商切换）
        List<FieldChange> refChanges = result.getReferenceChanges();
        report.setSupplierChanged(!refChanges.isEmpty());
        if (!refChanges.isEmpty()) {
            ReferenceDetail detail = refChanges.get(0).getReferenceDetail();
            report.setOldSupplier(detail.getOldEntityKey());
            report.setNewSupplier(detail.getNewEntityKey());
        }

        // 3.3 容器变更（包括移动）
        List<FieldChange> containerChanges = result.getContainerChanges();
        long movedCount = containerChanges.stream()
            .filter(c -> c.getElementEvent() != null)
            .filter(c -> c.getElementEvent().getLifecycleType() == ContainerLifecycleType.MOVED)
            .count();
        report.setMovedItemsCount((int) movedCount);

        // 3.4 生成Markdown报告
        report.setMarkdownReport(result.prettyPrint("detailed"));

        return report;
    }

    private List<String> extractEntityKeys(List<FieldChange> changes) {
        return changes.stream()
            .filter(c -> c.getElementEvent() != null)
            .map(c -> c.getElementEvent().getEntityKey())
            .toList();
    }
}
```

---

🎉 **P1 Query API让差异分析代码减少70%以上！** 选择合适的API，告别样板代码，专注业务逻辑！

---

## 🔍 过滤策略与优先级（v3.0.0+ P2新特性）

> **精准控制比对字段** - 通过类级/路径级/包级过滤策略，减少噪音，聚焦关键变更

### 场景概述

在实际业务中，对象可能包含数百个字段，但并非所有字段都需要追踪变更。TFI提供了多层次的过滤策略：
- **类级过滤**: 通过`@IgnoreDeclaredProperties`/`@IgnoreInheritedProperties`注解批量忽略字段
- **路径模式**: 使用glob/regex模式匹配字段路径（支持`*`、`**`、`[*]`）
- **包级过滤**: 批量忽略特定包下的所有类
- **默认忽略**: 自动过滤技术字段（`static`/`transient`/`$jacocoData`等）
- **优先级解决**: 7级决策链确保Include始终优先

---

### 示例 1: 类级批量忽略（注解驱动）

**场景**: 审计日志对象包含大量技术字段（创建时间、修改时间、版本号等），业务关注核心字段变更

```java
import com.syy.taskflowinsight.annotation.IgnoreDeclaredProperties;
import com.syy.taskflowinsight.api.TFI;

/**
 * 审计日志实体
 * 使用 @IgnoreDeclaredProperties 批量忽略技术字段
 */
@IgnoreDeclaredProperties({"createdAt", "updatedAt", "version", "lastModifiedBy"})
public class AuditLog {
    private String logId;
    private String action;           // 业务关注
    private String operator;         // 业务关注
    private String targetResource;   // 业务关注

    // 技术字段（已在注解中声明忽略）
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
    private String lastModifiedBy;

    // getters/setters...
}

// 使用示例
public class AuditService {
    public void compareAuditLogs(AuditLog before, AuditLog after) {
        CompareResult result = TFI.compare(before, after);

        // 输出仅包含 action/operator/targetResource 变更
        // createdAt/updatedAt/version/lastModifiedBy 自动被忽略
        System.out.println(TFI.render(result, "standard"));
    }
}
```

**输出示例**:
```
# 对比报告

## 变更摘要
- 修改: 2 个字段

## 详细变更
| 字段路径 | 旧值 | 新值 |
|---------|------|------|
| action | "CREATE_USER" | "UPDATE_USER" |
| operator | "admin" | "system" |

✅ 技术字段（createdAt/updatedAt/version/lastModifiedBy）已自动过滤
```

---

### 示例 2: 路径模式过滤（Glob + Regex）

**场景**: 嵌套对象中批量忽略敏感字段或调试字段

```java
import com.syy.taskflowinsight.tracking.snapshot.SnapshotConfig;
import com.syy.taskflowinsight.tracking.compare.CompareService;

public class SensitiveDataCompareExample {

    public void compareWithPathFiltering() {
        // 配置排除规则
        SnapshotConfig config = new SnapshotConfig();
        config.setEnableDeep(true);
        config.setMaxDepth(5);

        // Glob模式: 忽略所有password字段和internal.*下的字段
        config.setExcludePatterns(List.of(
            "*.password",           // 单层通配：user.password, admin.password
            "*.internal.*",         // 多层通配：config.internal.token, app.internal.debug
            "debug.**",             // 递归通配：debug下的所有嵌套字段
            "metadata[*].temp"      // 数组元素：metadata[0].temp, metadata[1].temp
        ));

        // Regex模式: 忽略以$开头的字段（如JaCoCo $jacocoData）
        config.setRegexExcludes(List.of("\\$.*"));

        // 创建快照并比对
        ObjectSnapshotDeep snapshot = new ObjectSnapshotDeep(config);
        Map<String, Object> beforeSnapshot = snapshot.captureDeep(beforeObj, 5, ...);
        Map<String, Object> afterSnapshot = snapshot.captureDeep(afterObj, 5, ...);

        CompareService compareService = new CompareService();
        CompareResult result = compareService.compare(beforeSnapshot, afterSnapshot, new CompareOptions());

        System.out.println("过滤后变更数: " + result.getChanges().size());
    }
}
```

**路径模式语法**:
| 模式 | 说明 | 示例匹配 |
|------|------|----------|
| `field` | 精确匹配 | `user.name` 仅匹配name字段 |
| `*.password` | 单层通配 | `user.password`, `admin.password` |
| `internal.*` | 单层子字段 | `internal.token`, `internal.debug` |
| `debug.**` | 递归通配 | `debug.level`, `debug.trace.stack` |
| `items[*].id` | 数组/集合元素 | `items[0].id`, `items[1].id` |
| `\\$.*` | Regex | `$jacocoData`, `$assertionsDisabled` |

---

### 示例 3: 默认忽略规则（与Include覆盖）

**场景**: 启用默认忽略过滤技术字段，但通过Include白名单保留特定字段

```java
import com.syy.taskflowinsight.tracking.snapshot.SnapshotConfig;

public class DefaultExclusionsExample {

    public void configureDefaultExclusions() {
        SnapshotConfig config = new SnapshotConfig();

        // 启用默认忽略规则（过滤技术字段）
        config.setDefaultExclusionsEnabled(true);

        // 默认忽略规则包括：
        // - static字段
        // - transient字段
        // - synthetic字段
        // - 常见logger字段（log, logger, LOG, LOGGER）
        // - serialVersionUID
        // - $jacocoData (代码覆盖率工具注入)

        // 通过Include白名单覆盖默认忽略
        config.setIncludePatterns(List.of(
            "serialVersionUID"   // 即使默认忽略，Include优先级更高
        ));

        // 结果: 所有默认忽略字段都被过滤，除了serialVersionUID
    }
}
```

**默认忽略字段清单**:
```
✅ 自动过滤字段（defaultExclusionsEnabled=true）:
- static 修饰符字段
- transient 修饰符字段
- synthetic 编译器生成字段
- logger 相关（log/logger/LOG/LOGGER）
- serialVersionUID
- $jacocoData (JaCoCo代码覆盖率)

⚠️ 可通过Include白名单覆盖（优先级最高）
```

---

### 示例 4: 优先级冲突解决

**场景**: 复杂过滤配置下的优先级决策（Include vs @DiffIgnore vs Exclude）

```java
import com.syy.taskflowinsight.annotation.DiffIgnore;
import com.syy.taskflowinsight.annotation.IgnoreDeclaredProperties;

/**
 * 用户实体 - 演示优先级冲突解决
 */
@IgnoreDeclaredProperties({"password"})  // 类级忽略password
public class User {
    private String userId;

    @DiffIgnore  // 字段级忽略（优先级低于Include）
    private String email;

    private String password;  // 类级忽略
    private String internalToken;  // 将被路径黑名单忽略
}

// 配置与决策
public class PriorityResolutionExample {

    public void demonstratePriority() {
        SnapshotConfig config = new SnapshotConfig();

        // 1. 路径黑名单: 忽略 internal.*
        config.setExcludePatterns(List.of("*.internal*"));

        // 2. Include白名单: 强制包含 email 和 password（覆盖所有其他规则）
        config.setIncludePatterns(List.of("email", "password"));

        // 3. 启用默认忽略
        config.setDefaultExclusionsEnabled(true);

        // 决策结果（7级优先级链）:
        // ✅ email: Include覆盖 @DiffIgnore → 包含
        // ✅ password: Include覆盖 @IgnoreDeclaredProperties → 包含
        // ❌ internalToken: 路径黑名单且无Include → 忽略
        // ✅ userId: 无任何过滤规则 → 包含（默认retain）
    }
}
```

**7级优先级链（从高到低）**:
```
1️⃣ Include 路径白名单         → INCLUDE (最高优先级)
2️⃣ @DiffIgnore 字段注解       → IGNORE
3️⃣ 路径黑名单（exclude）       → IGNORE
4️⃣ 类级过滤注解                → IGNORE
5️⃣ 包级过滤（excludePackages） → IGNORE
6️⃣ 默认忽略规则                → IGNORE
7️⃣ 默认保留（无匹配规则）       → INCLUDE (默认行为)
```

**冲突解决示例**:
| 字段 | Include | @DiffIgnore | Exclude | 默认忽略 | **最终决策** | 理由 |
|------|---------|-------------|---------|---------|------------|------|
| email | ✅ | ✅ | ❌ | ❌ | **INCLUDE** | Include优先级最高 |
| password | ✅ | ❌ | ❌ | ❌ | **INCLUDE** | Include覆盖类级注解 |
| logger | ❌ | ❌ | ❌ | ✅ | **IGNORE** | 默认忽略生效 |
| userId | ❌ | ❌ | ❌ | ❌ | **INCLUDE** | 默认retain |

---

### 示例 5: DiffBuilder全局配置（推荐最佳实践）

**场景**: 统一配置过滤规则，全局生效，避免重复配置

```java
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.api.TFI;

public class GlobalFilterConfigExample {

    /**
     * 推荐方式: 使用 CompareOptions 全局配置
     * 适用于所有比对操作，无需在每个对象上重复配置
     */
    public void configureGlobalFilters() {
        // 创建全局过滤配置
        CompareOptions options = new CompareOptions();

        // 1. 启用默认忽略规则
        options.setDefaultExclusionsEnabled(true);

        // 2. 配置路径黑名单（批量忽略敏感/调试字段）
        options.setExcludePatterns(List.of(
            "*.password",
            "*.token",
            "*.secret",
            "*.internal.*",
            "debug.**",
            "temp.**"
        ));

        // 3. 配置Regex黑名单（忽略JaCoCo等工具注入字段）
        options.setRegexExcludes(List.of("\\$.*", ".*\\$\\$.*"));

        // 4. 包级过滤（忽略第三方库内部类）
        options.setExcludePackages(List.of(
            "org.springframework.cglib",
            "net.sf.cglib",
            "org.hibernate.proxy"
        ));

        // 5. Include白名单（优先级最高，覆盖所有忽略规则）
        options.setIncludePatterns(List.of(
            "audit.password"  // 审计场景需要追踪密码变更
        ));

        // 使用全局配置进行比对
        CompareResult result = TFI.comparator()
            .withOptions(options)
            .compare(beforeObj, afterObj);

        // 所有后续比对都应用相同配置
        CompareResult result2 = TFI.comparator()
            .withOptions(options)  // 复用配置
            .compare(anotherBefore, anotherAfter);
    }

    /**
     * 最佳实践: 在Spring Bean中配置单例
     */
    @Configuration
    public static class TfiFilterConfig {

        @Bean
        public CompareOptions defaultCompareOptions() {
            CompareOptions options = new CompareOptions();
            options.setDefaultExclusionsEnabled(true);
            options.setExcludePatterns(Arrays.asList(
                "*.password",
                "*.token",
                "*.internal.*"
            ));
            return options;
        }
    }

    @Service
    public static class AuditService {

        @Autowired
        private CompareOptions defaultCompareOptions;

        public void auditChanges(Object before, Object after) {
            // 自动应用全局配置
            CompareResult result = TFI.comparator()
                .withOptions(defaultCompareOptions)
                .compare(before, after);

            logChanges(result);
        }
    }
}
```

**配置优先级建议**:
```
📋 推荐配置层次（从全局到局部）:

1️⃣ 全局配置（Spring Bean）
   └─ CompareOptions Bean
      ├─ defaultExclusionsEnabled: true
      ├─ excludePatterns: 敏感字段模式
      ├─ excludePackages: 第三方库包名
      └─ includePatterns: 业务白名单

2️⃣ 类级配置（注解）
   └─ @IgnoreDeclaredProperties / @IgnoreInheritedProperties
      └─ 领域模型特定忽略字段

3️⃣ 字段级配置（注解）
   └─ @DiffIgnore
      └─ 个别字段特殊处理

⚠️ Include优先级始终最高，用于覆盖所有忽略规则
```

---

### 过滤策略决策树

```
┌────────────────────────────────────────┐
│ 字段: user.password                    │
└────────────────┬───────────────────────┘
                 │
                 ▼
        ┌─────────────────┐
        │ Include 匹配?    │────YES────▶ ✅ INCLUDE (终止决策)
        └────────┬─────────┘
                 │ NO
                 ▼
        ┌─────────────────┐
        │ @DiffIgnore?     │────YES────▶ ❌ IGNORE (终止决策)
        └────────┬─────────┘
                 │ NO
                 ▼
        ┌─────────────────┐
        │ Exclude匹配?     │────YES────▶ ❌ IGNORE (终止决策)
        └────────┬─────────┘
                 │ NO
                 ▼
        ┌─────────────────┐
        │ 类级注解忽略?     │────YES────▶ ❌ IGNORE (终止决策)
        └────────┬─────────┘
                 │ NO
                 ▼
        ┌─────────────────┐
        │ 包级过滤?        │────YES────▶ ❌ IGNORE (终止决策)
        └────────┬─────────┘
                 │ NO
                 ▼
        ┌─────────────────┐
        │ 默认忽略规则?    │────YES────▶ ❌ IGNORE (终止决策)
        └────────┬─────────┘
                 │ NO
                 ▼
        ┌─────────────────┐
        │ 默认保留         │────────────▶ ✅ INCLUDE (默认行为)
        └──────────────────┘
```

---

### 性能优化建议

1. **Pattern缓存命中率 > 95%**
   - PathMatcher自动缓存编译后的Pattern
   - 复用配置对象（如Spring Bean）
   - 避免动态生成Pattern

2. **快速路径优化**
   - 空配置时跳过过滤决策（O(1)）
   - Include匹配立即返回（短路求值）

3. **JMH基准数据**（P2-T7实测）
   ```
   baseline_NoFiltering:     19,150 ns/op  (无过滤)
   filterLargeObject:        76,663 ns/op  (启用过滤)
   patternCompilationCache:   4,431 ns/op  (Pattern缓存)

   缓存命中率: 99.8% (目标 >95%) ✅
   ```

---

### 相关链接

- [P2-T1: 类级过滤框架](docs/tfi-javers/p2/cards/gpt/CARD-P2-T1-ClassLevelFilter-类级过滤框架.md)
- [P2-T2: 路径模式引擎](docs/tfi-javers/p2/cards/gpt/CARD-P2-T2-PathPatternEngine-路径模式引擎增强.md)
- [P2-T4: 优先级与冲突解决](docs/tfi-javers/p2/cards/gpt/CARD-P2-T4-PriorityResolution-优先级与冲突解决.md)
- [P2-T6: 测试矩阵](docs/tfi-javers/p2/P2-T6-SUMMARY.md) - 包含5个黄金冲突用例
- [P2-T7: 性能基准](docs/tfi-javers/p2/P2-T7-PERFORMANCE-ANALYSIS.md) - JMH性能数据

---
## 📝 总结和最佳实践

### 选择合适的使用方式

1. **注解驱动** - 适合Spring Boot项目，代码侵入性最小
2. **编程式API** - 适合需要精细控制的场景  
3. **混合模式** - 注解+API，获得最大灵活性

### 性能优化建议

1. **合理设置追踪对象数量限制**
2. **敏感数据及时脱敏**
3. **异步场景注意上下文传播**
4. **生产环境启用自动导出**

### 监控和运维

1. **配置Actuator端点监控**
2. **设置合理的会话超时时间**
3. **定期检查性能指标**
4. **建立告警机制**

### 开发规范

1. **统一错误处理模式**
2. **标准化追踪对象命名**
3. **建立代码审查检查点**
4. **编写完整的单元测试**

---

🎉 **现在你已经掌握了TaskFlowInsight在各种业务场景下的使用方法！选择适合你业务场景的示例开始实践吧！**

## 📚 延伸阅读

- [快速入门指南](GETTING-STARTED.md) - 5分钟从零到运行
- [部署指南](DEPLOYMENT.md) - 生产环境部署最佳实践
- [常见问题](FAQ.md) - 90%的问题都能找到答案
- [API参考](docs/api/README.md) - 完整的API文档

---

💡 **提示**：如果你在实际使用中遇到问题，欢迎参考[故障排除指南](TROUBLESHOOTING.md)或在[GitHub Issues](https://github.com/shiyongyin/TaskFlowInsight/issues)中提问。

---

## 🧩 P2 过滤框架最小示例

> 类级过滤、路径模式（含 `[*]`/regex）、默认忽略与统一优先级。复制即用，便于快速验证与排障。

### 1) 类注解批量忽略（减少样板）
```java
@IgnoreDeclaredProperties // 忽略本类声明的全部字段
class InternalMetrics {
  String traceId;  // 忽略
  long timestamp;  // 忽略
}
```

### 2) 路径模式（Glob/Regex/[*]）与 Include 挽回
```java
SnapshotConfig c = new SnapshotConfig();
// 黑名单：跨层 + 数组索引
c.setExcludePatterns(List.of("internal.**", "items[*].internalId"));
// 正则黑名单
c.setRegexExcludes(List.of("^debug_\\d{4}$"));
// 白名单：精确挽回
c.setIncludePatterns(List.of("items[*].internalId"));
```

### 3) 默认忽略 + Include 挽回
```java
SnapshotConfig c = new SnapshotConfig();
c.setDefaultExclusionsEnabled(true); // static/transient/synthetic/logger/serialVersionUID 等自动忽略
// 显式保留 logger 字段
c.setIncludePatterns(List.of("logger"));
```

更多详情：
- 统一优先级与原因：docs/filtering/PRIORITY_AND_REASON.md
- 测试矩阵：docs/tfi-javers/p2/cards/gpt/T6-TEST-MATRIX.md
