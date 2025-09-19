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