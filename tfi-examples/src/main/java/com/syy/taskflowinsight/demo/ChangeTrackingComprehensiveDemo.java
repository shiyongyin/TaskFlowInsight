package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.annotation.*;
import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot;
import com.syy.taskflowinsight.tracking.compare.CompareService;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor;
import com.syy.taskflowinsight.tracking.compare.list.SimpleListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.AsSetListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.LevenshteinListStrategy;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.sql.Timestamp;
import java.math.BigDecimal;

/**
 * 全面的变更追踪功能演示
 * 展示所有支持的数据类型和比较方式
 *
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
public class ChangeTrackingComprehensiveDemo {

    // ==================== 测试枚举 ====================
    public enum Status {
        PENDING, PROCESSING, COMPLETED, CANCELLED
    }

    // ==================== Entity示例（带@Key） ====================
    @Entity(name = "User")
    public static class User {
        @Key
        private Long userId;

        @DiffInclude
        private String username;

        @DiffInclude
        private String email;

        @ShallowReference
        private Department department;

        @DiffIgnore
        private Date lastLoginTime;

        private Status status;

        public User(Long userId, String username, String email) {
            this.userId = userId;
            this.username = username;
            this.email = email;
            this.status = Status.PENDING;
        }

        // Getters and Setters
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public Department getDepartment() { return department; }
        public void setDepartment(Department department) { this.department = department; }
        public Date getLastLoginTime() { return lastLoginTime; }
        public void setLastLoginTime(Date lastLoginTime) { this.lastLoginTime = lastLoginTime; }
        public Status getStatus() { return status; }
        public void setStatus(Status status) { this.status = status; }
    }

    // ==================== ValueObject示例 ====================
    @ValueObject
    public static class Address {
        @DiffInclude
        private String street;

        @DiffInclude
        private String city;

        @DiffInclude
        private String zipCode;

        @DiffIgnore
        private Date createdAt;

        public Address(String street, String city, String zipCode) {
            this.street = street;
            this.city = city;
            this.zipCode = zipCode;
            this.createdAt = new Date();
        }

        // Getters and Setters
        public String getStreet() { return street; }
        public void setStreet(String street) { this.street = street; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getZipCode() { return zipCode; }
        public void setZipCode(String zipCode) { this.zipCode = zipCode; }
    }

    // ==================== Department示例（ShallowReference） ====================
    @Entity(name = "Department")
    public static class Department {
        @Key
        private String deptId;

        private String deptName;

        private List<User> employees = new ArrayList<>();

        public Department(String deptId, String deptName) {
            this.deptId = deptId;
            this.deptName = deptName;
        }

        public String getDeptId() { return deptId; }
        public void setDeptId(String deptId) { this.deptId = deptId; }
        public String getDeptName() { return deptName; }
        public void setDeptName(String deptName) { this.deptName = deptName; }
    }

    // ==================== 包含所有类型的测试对象 ====================
    public static class TestDataObject {
        // 原始类型
        private int intValue;
        private long longValue;
        private double doubleValue;
        private float floatValue;
        private boolean booleanValue;
        private char charValue;
        private byte byteValue;
        private short shortValue;

        // 包装类型
        private Integer integerValue;
        private Long longWrapperValue;
        private Double doubleWrapperValue;
        private Float floatWrapperValue;
        private Boolean booleanWrapperValue;
        private Character characterValue;
        private Byte byteWrapperValue;
        private Short shortWrapperValue;

        // 字符串和枚举
        private String stringValue;
        private Status enumValue;

        // 日期类型
        private Date dateValue;
        private LocalDateTime localDateTimeValue;
        private LocalDate localDateValue;
        private LocalTime localTimeValue;
        private ZonedDateTime zonedDateTimeValue;
        private Instant instantValue;
        private Timestamp timestampValue;

        // 自定义格式日期（使用注解）
        @DateFormat(pattern = "yyyy-MM-dd")
        private Date customDateFormat;

        @DateFormat(pattern = "HH:mm:ss")
        private LocalTime customTimeFormat;

        @DateFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime customDateTimeFormat;

        // 集合类型
        private List<String> stringList;
        private Set<Integer> integerSet;
        private Map<String, String> stringMap;

        // 自定义对象集合
        private List<Address> addressList;
        private Set<User> userSet;
        private Map<String, User> userMap;

        // BigDecimal（精度比较）
        @NumericPrecision(absoluteTolerance = 0.01, relativeTolerance = 0.001)
        private BigDecimal bigDecimalValue;

        // 构造函数
        public TestDataObject() {
            // 初始化集合
            this.stringList = new ArrayList<>();
            this.integerSet = new HashSet<>();
            this.stringMap = new HashMap<>();
            this.addressList = new ArrayList<>();
            this.userSet = new HashSet<>();
            this.userMap = new HashMap<>();
        }

        // 初始化示例数据
        public void initializeSampleData() {
            // 原始类型
            this.intValue = 100;
            this.longValue = 1000L;
            this.doubleValue = 3.14159;
            this.floatValue = 2.718f;
            this.booleanValue = true;
            this.charValue = 'A';
            this.byteValue = 127;
            this.shortValue = 32767;

            // 包装类型
            this.integerValue = 200;
            this.longWrapperValue = 2000L;
            this.doubleWrapperValue = 6.28318;
            this.floatWrapperValue = 1.414f;
            this.booleanWrapperValue = false;
            this.characterValue = 'B';
            this.byteWrapperValue = -128;
            this.shortWrapperValue = -32768;

            // 字符串和枚举
            this.stringValue = "Hello TaskFlowInsight";
            this.enumValue = Status.PENDING;

            // 日期类型
            this.dateValue = new Date();
            this.localDateTimeValue = LocalDateTime.now();
            this.localDateValue = LocalDate.now();
            this.localTimeValue = LocalTime.now();
            this.zonedDateTimeValue = ZonedDateTime.now();
            this.instantValue = Instant.now();
            this.timestampValue = new Timestamp(System.currentTimeMillis());

            // 自定义格式日期
            this.customDateFormat = new Date();
            this.customTimeFormat = LocalTime.now();
            this.customDateTimeFormat = LocalDateTime.now();

            // 集合数据
            this.stringList.addAll(Arrays.asList("item1", "item2", "item3"));
            this.integerSet.addAll(Arrays.asList(1, 2, 3, 4, 5));
            this.stringMap.put("key1", "value1");
            this.stringMap.put("key2", "value2");

            // BigDecimal
            this.bigDecimalValue = new BigDecimal("123.456");
        }

        // 创建修改后的版本
        public void makeChanges() {
            // 修改原始类型
            this.intValue = 200;
            this.doubleValue = 2.71828;
            this.booleanValue = false;

            // 修改包装类型
            this.integerValue = 300;
            this.characterValue = 'C';

            // 修改字符串和枚举
            this.stringValue = "Modified TaskFlowInsight";
            this.enumValue = Status.COMPLETED;

            // 修改日期（加1天）
            Calendar cal = Calendar.getInstance();
            cal.setTime(this.dateValue);
            cal.add(Calendar.DAY_OF_MONTH, 1);
            this.dateValue = cal.getTime();

            this.localDateTimeValue = this.localDateTimeValue.plusDays(1);
            this.localDateValue = this.localDateValue.plusDays(1);

            // 修改集合
            this.stringList.remove("item2");
            this.stringList.add("item4");
            this.integerSet.remove(3);
            this.integerSet.add(6);
            this.stringMap.put("key2", "value2-modified");
            this.stringMap.put("key3", "value3");
            this.stringMap.remove("key1");

            // 修改BigDecimal（微小变化，测试精度）
            this.bigDecimalValue = new BigDecimal("123.457");
        }

        // Getters for all fields (省略setter以节省空间)
        public int getIntValue() { return intValue; }
        public long getLongValue() { return longValue; }
        public double getDoubleValue() { return doubleValue; }
        public float getFloatValue() { return floatValue; }
        public boolean isBooleanValue() { return booleanValue; }
        public char getCharValue() { return charValue; }
        public byte getByteValue() { return byteValue; }
        public short getShortValue() { return shortValue; }
        public Integer getIntegerValue() { return integerValue; }
        public Long getLongWrapperValue() { return longWrapperValue; }
        public Double getDoubleWrapperValue() { return doubleWrapperValue; }
        public Float getFloatWrapperValue() { return floatWrapperValue; }
        public Boolean getBooleanWrapperValue() { return booleanWrapperValue; }
        public Character getCharacterValue() { return characterValue; }
        public Byte getByteWrapperValue() { return byteWrapperValue; }
        public Short getShortWrapperValue() { return shortWrapperValue; }
        public String getStringValue() { return stringValue; }
        public Status getEnumValue() { return enumValue; }
        public Date getDateValue() { return dateValue; }
        public LocalDateTime getLocalDateTimeValue() { return localDateTimeValue; }
        public LocalDate getLocalDateValue() { return localDateValue; }
        public LocalTime getLocalTimeValue() { return localTimeValue; }
        public ZonedDateTime getZonedDateTimeValue() { return zonedDateTimeValue; }
        public Instant getInstantValue() { return instantValue; }
        public Timestamp getTimestampValue() { return timestampValue; }
        public List<String> getStringList() { return stringList; }
        public Set<Integer> getIntegerSet() { return integerSet; }
        public Map<String, String> getStringMap() { return stringMap; }
        public List<Address> getAddressList() { return addressList; }
        public Set<User> getUserSet() { return userSet; }
        public Map<String, User> getUserMap() { return userMap; }
        public BigDecimal getBigDecimalValue() { return bigDecimalValue; }
    }

    // ==================== 真实组件实例 ====================
    private static CompareService compareService;
    private static ListCompareExecutor listCompareExecutor;
    
    // ==================== 主演示方法 ====================
    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("TaskFlowInsight 变更追踪功能全面演示（使用真实组件）");
        System.out.println("=".repeat(80));

        // 启用TFI
        TFI.enable();
        
        // 初始化真实组件（不依赖Spring）
        initializeRealComponents();

        // 运行各个演示
        demoBasicTypes();
        demoDates();
        demoCustomObjects();
        demoCollections();
        demoComplexScenario();

        System.out.println("\n" + "=".repeat(80));
        System.out.println("演示完成！所有功能都使用真实项目组件");
        System.out.println("=".repeat(80));
    }
    
    /**
     * 初始化真实的组件实例（独立于Spring）
     */
    private static void initializeRealComponents() {
        try {
            // 创建List比较策略实例
            List<com.syy.taskflowinsight.tracking.compare.list.ListCompareStrategy> strategies = Arrays.asList(
                new SimpleListStrategy(),
                new AsSetListStrategy(),
                new LevenshteinListStrategy()
            );
            
            // 创建ListCompareExecutor
            listCompareExecutor = new ListCompareExecutor(strategies);
            
            // 创建CompareService
            compareService = new CompareService(listCompareExecutor);
            
            System.out.println("✅ 真实组件初始化成功：CompareService + ListCompareExecutor");
            
        } catch (Exception e) {
            System.err.println("❌ 组件初始化失败: " + e.getMessage());
            System.err.println("将降级使用DiffDetector作为备选方案");
            compareService = null;
            listCompareExecutor = null;
        }
    }

    // ==================== 1. 基本类型演示 ====================
    private static void demoBasicTypes() {
        System.out.println("\n" + "-".repeat(60));
        System.out.println("1. 基本类型和包装类型比较演示");
        System.out.println("-".repeat(60));

        TestDataObject before = new TestDataObject();
        before.initializeSampleData();

        TestDataObject after = new TestDataObject();
        after.initializeSampleData();
        after.makeChanges();

        // 使用DiffDetector进行比较
        List<ChangeRecord> changes = DiffDetector.diff("BasicTypes",
                createCompleteSnapshot("before", before),
                createCompleteSnapshot("after", after));

        System.out.println("\n✨ 基本类型字段变更检测结果：");
        for (ChangeRecord change : changes) {
            String fieldName = change.getFieldName();
            if (fieldName.contains("Value") && !fieldName.contains("date") &&
                !fieldName.contains("List") && !fieldName.contains("Set") &&
                !fieldName.contains("Map")) {
                System.out.printf("  - %s: '%s' → '%s' [%s]\n",
                    fieldName,
                    change.getOldValue(),
                    change.getNewValue(),
                    change.getChangeType());
            }
        }
    }

    // ==================== 2. 日期类型演示 ====================
    private static void demoDates() {
        System.out.println("\n" + "-".repeat(60));
        System.out.println("2. 日期类型比较演示");
        System.out.println("-".repeat(60));

        // 创建测试数据
        DateTestObject before = new DateTestObject();
        DateTestObject after = new DateTestObject();
        after.addOneDay();

        // 比较
        List<ChangeRecord> changes = DiffDetector.diff("Dates",
                createCompleteSnapshot("before", before),
                createCompleteSnapshot("after", after));

        System.out.println("\n📅 日期时间字段变更详情：");

        // 分类显示不同格式的日期字段
        System.out.println("\n  🕐 标准格式日期时间字段：");
        for (ChangeRecord change : changes) {
            String fieldName = change.getFieldName();
            if ((fieldName.contains("date") || fieldName.contains("time") ||
                 fieldName.contains("Date") || fieldName.contains("Time")) &&
                !fieldName.contains("custom")) {
                System.out.printf("    - %s: %s → %s\n",
                    fieldName,
                    formatValue(change.getOldValue()),
                    formatValue(change.getNewValue()));
            }
        }

        System.out.println("\n  🎨 自定义格式日期时间字段：");
        for (ChangeRecord change : changes) {
            String fieldName = change.getFieldName();
            if (fieldName.contains("custom")) {
                String formatInfo = "";
                String oldValueFormatted = "";
                String newValueFormatted = "";

                if (fieldName.contains("customDate")) {
                    formatInfo = " (@DateFormat: yyyy-MM-dd)";
                    oldValueFormatted = formatCustomDate(change.getOldValue());
                    newValueFormatted = formatCustomDate(change.getNewValue());
                } else if (fieldName.contains("customTime")) {
                    formatInfo = " (@DateFormat: HH:mm:ss)";
                    oldValueFormatted = formatCustomTime(change.getOldValue());
                    newValueFormatted = formatCustomTime(change.getNewValue());
                }

                System.out.printf("    - %s%s: %s → %s\n",
                    fieldName,
                    formatInfo,
                    oldValueFormatted,
                    newValueFormatted);
            }
        }
    }

    // ==================== 3. 自定义对象演示 ====================
    private static void demoCustomObjects() {
        System.out.println("\n" + "-".repeat(60));
        System.out.println("3. 自定义对象（Entity和ValueObject）比较演示");
        System.out.println("-".repeat(60));

        // Entity示例
        System.out.println("\n🏢 Entity对象比较（带@Key主键）：");
        User user1 = new User(1L, "john_doe", "john@example.com");
        user1.setStatus(Status.PENDING);
        user1.setDepartment(new Department("DEPT001", "Engineering"));

        User user2 = new User(1L, "john_smith", "john.smith@example.com");
        user2.setStatus(Status.COMPLETED);
        user2.setDepartment(new Department("DEPT002", "Marketing")); // @ShallowReference：只检查对象引用变化，不深度比较内容

        List<ChangeRecord> userChanges = DiffDetector.diff("User",
                createCompleteSnapshot("user1", user1),
                createCompleteSnapshot("user2", user2));
        for (ChangeRecord change : userChanges) {
            String oldValue = formatEntityValue(change.getOldValue(), change.getFieldName());
            String newValue = formatEntityValue(change.getNewValue(), change.getFieldName());
            System.out.printf("  - %s: %s → %s\n",
                change.getFieldName(),
                oldValue,
                newValue);
        }

        System.out.println("  💡 说明：department字段使用@ShallowReference注解");
        System.out.println("      - 只检查对象引用是否变化，不深度比较对象内容");
        System.out.println("      - 这里检测到变更是因为创建了两个不同的Department实例");

        // ValueObject示例
        System.out.println("\n💼 ValueObject对象比较（值对象）：");
        Address addr1 = new Address("123 Main St", "New York", "10001");
        Address addr2 = new Address("456 Broadway", "New York", "10002");

        List<ChangeRecord> addressChanges = DiffDetector.diff("Address",
                createCompleteSnapshot("addr1", addr1),
                createCompleteSnapshot("addr2", addr2));
        for (ChangeRecord change : addressChanges) {
            System.out.printf("  - %s: %s → %s\n",
                change.getFieldName(),
                change.getOldValue(),
                change.getNewValue());
        }
    }

    // ==================== 4. 集合类型演示 ====================
    private static void demoCollections() {
        System.out.println("\n" + "-".repeat(60));
        System.out.println("4. 集合类型比较演示");
        System.out.println("-".repeat(60));

        // 4.1 List比较策略全面演示
        demoListStrategies();
        
        // 4.2 其他集合类型演示
        demoOtherCollectionTypes();
    }

    private static void demoListStrategies() {
        System.out.println("\n4.1 List比较策略全覆盖测试（使用真实组件）：");
        System.out.println("验证3种List比较策略的完整功能\n");

        // 测试用例1：元素移动场景
        System.out.println("📋 测试场景1：元素移动");
        List<String> moveList1 = Arrays.asList("A", "B", "C", "D");
        List<String> moveList2 = Arrays.asList("A", "D", "B", "C"); // B和D交换位置
        demonstrateRealListStrategies("移动检测", moveList1, moveList2);

        // 测试用例2：增删场景
        System.out.println("\n📋 测试场景2：增删操作");
        List<String> crudList1 = Arrays.asList("apple", "banana", "cherry");
        List<String> crudList2 = Arrays.asList("apple", "grape", "cherry", "date"); // banana删除，grape和date新增
        demonstrateRealListStrategies("增删检测", crudList1, crudList2);

        // 测试用例3：复杂混合场景
        System.out.println("\n📋 测试场景3：复杂混合操作");
        List<String> complexList1 = Arrays.asList("X", "Y", "Z", "W");
        List<String> complexList2 = Arrays.asList("Y", "A", "X", "B"); // Y移到前面，Z和W删除，A和B新增
        demonstrateRealListStrategies("复杂混合", complexList1, complexList2);
    }

    /**
     * 使用真实项目组件演示List策略
     */
    private static void demonstrateRealListStrategies(String scenario, List<String> list1, List<String> list2) {
        System.out.printf("  场景：%s\n", scenario);
        System.out.printf("  变更：%s → %s\n", list1, list2);

        if (compareService == null || listCompareExecutor == null) {
            System.out.println("  ❌ 真实组件未初始化，跳过演示");
            return;
        }

        // 演示各种策略的真实输出 - 显示原始组件比对结果
        System.out.println("  🔍 真实组件原始比对结果详情：");

        // Simple策略（真实组件）
        System.out.println("    🔸 Simple策略（真实组件）：");
        demonstrateRealStrategy("SIMPLE", list1, list2);

        // AsSet策略（真实组件）
        System.out.println("    🔸 AsSet策略（真实组件）：");
        demonstrateRealStrategy("AS_SET", list1, list2);

        // Levenshtein策略（真实组件）
        System.out.println("    🔸 Levenshtein策略（真实组件）：");
        demonstrateRealStrategy("LEVENSHTEIN", list1, list2);
    }
    
    /**
     * 使用真实的项目组件执行List比较 - 显示原始比对结果
     */
    private static void demonstrateRealStrategy(String strategyName, List<String> list1, List<String> list2) {
        try {
            // 创建比较选项，指定策略
            CompareOptions options = CompareOptions.builder()
                .strategyName(strategyName)
                .detectMoves(true)  // 启用移动检测
                .build();

            // 使用真实的ListCompareExecutor
            CompareResult result = listCompareExecutor.compare(list1, list2, options);

            System.out.println("      📋 CompareResult对象详细信息 📋");
            System.out.println("      - isIdentical(): " + result.isIdentical());
            System.out.println("      - getChanges().size(): " + result.getChanges().size());
            System.out.println("      - CompareResult.class: " + result.getClass().getName());

            if (result.isIdentical()) {
                System.out.println("      ✅ 比对结果：两个列表内容相同（忽略顺序）");
                return;
            }

            // 显示真实的FieldChange对象原始信息
            System.out.println("      🔧 FieldChange变更对象详情 🔧");
            int changeIndex = 0;
            for (FieldChange change : result.getChanges()) {
                changeIndex++;
                System.out.println("      Change #" + changeIndex + ":");
                System.out.println("        - FieldChange.class: " + change.getClass().getName());
                System.out.println("        - toString(): " + change.toString());

                // 显示所有getter方法的返回值
                try {
                    java.lang.reflect.Method[] allMethods = change.getClass().getMethods();
                    System.out.println("        - 📝 所有getter方法及其返回值:");

                    for (java.lang.reflect.Method method : allMethods) {
                        if ((method.getName().startsWith("get") || method.getName().startsWith("is"))
                            && method.getParameterCount() == 0
                            && !method.getName().equals("getClass")) {
                            try {
                                Object value = method.invoke(change);
                                String valueStr = (value != null) ? value.toString() : "null";
                                String typeStr = (value != null) ? value.getClass().getSimpleName() : "null";
                                System.out.println("          " + method.getName() + "(): " + valueStr + " (type: " + typeStr + ")");
                            } catch (Exception e) {
                                System.out.println("          " + method.getName() + "(): [调用失败: " + e.getMessage() + "]");
                            }
                        }
                    }

                    // 显示所有字段
                    System.out.println("        - 📦 所有字段内容:");
                    java.lang.reflect.Field[] fields = change.getClass().getDeclaredFields();
                    for (java.lang.reflect.Field field : fields) {
                        field.setAccessible(true);
                        try {
                            Object value = field.get(change);
                            String valueStr = (value != null) ? value.toString() : "null";
                            String typeStr = (value != null) ? value.getClass().getSimpleName() : "null";
                            System.out.println("          " + field.getName() + ": " + valueStr + " (type: " + typeStr + ")");
                        } catch (Exception e) {
                            System.out.println("          " + field.getName() + ": [访问失败: " + e.getMessage() + "]");
                        }
                    }

                } catch (Exception e) {
                    System.out.println("        - 反射失败: " + e.getMessage());
                }
                System.out.println();
            }

        } catch (Exception e) {
            System.out.printf("      ❌ 策略 %s 执行失败: %s\n", strategyName, e.getMessage());
            e.printStackTrace();
        }
    }

    private static void demoOtherCollectionTypes() {
        System.out.println("\n4.2 其他集合类型比较：");

        // Set演示
        System.out.println("\n🎯 Set<Integer> 集合比较（无序集合）：");
        Set<Integer> set1 = new HashSet<>(Arrays.asList(1, 2, 3, 4));
        Set<Integer> set2 = new HashSet<>(Arrays.asList(2, 3, 4, 5, 6));

        CollectionTestObject obj1 = new CollectionTestObject();
        CollectionTestObject obj2 = new CollectionTestObject();
        obj1.integerSet = set1;
        obj2.integerSet = set2;

        List<ChangeRecord> setChanges = DiffDetector.diff("Set",
                createCompleteSnapshot("obj1", obj1),
                createCompleteSnapshot("obj2", obj2));
        for (ChangeRecord change : setChanges) {
            if (change.getFieldName().contains("integerSet")) {
                System.out.printf("  - %s: %s\n", change.getChangeType(), change.getFieldName());
                if (change.getOldValue() != null || change.getNewValue() != null) {
                    System.out.printf("    旧值: %s\n    新值: %s\n",
                        formatCollectionValue(change.getOldValue()), formatCollectionValue(change.getNewValue()));
                }
            }
        }

        // Map演示
        System.out.println("\n🗺️ Map<String,String> 映射比较（键值对）：");
        Map<String, String> map1 = new HashMap<>();
        map1.put("name", "John");
        map1.put("age", "30");
        map1.put("city", "NYC");

        Map<String, String> map2 = new HashMap<>();
        map2.put("name", "John");
        map2.put("age", "31");
        map2.put("country", "USA");

        obj1.stringMap = map1;
        obj2.stringMap = map2;

        List<ChangeRecord> mapChanges = DiffDetector.diff("Map",
                createCompleteSnapshot("obj1", obj1),
                createCompleteSnapshot("obj2", obj2));
        for (ChangeRecord change : mapChanges) {
            if (change.getFieldName().contains("stringMap")) {
                System.out.printf("  - %s: %s\n", change.getChangeType(), change.getFieldName());
                if (change.getOldValue() != null || change.getNewValue() != null) {
                    System.out.printf("    旧值: %s\n    新值: %s\n",
                        formatCollectionValue(change.getOldValue()), formatCollectionValue(change.getNewValue()));
                }
            }
        }

        System.out.println("\n💡 集合比较策略总结：");
        System.out.println("  • List：支持3种比较策略，可根据场景选择");
        System.out.println("  • Set：基于元素差异，忽略顺序");
        System.out.println("  • Map：比较键值对的增删改");
    }

    // ==================== 5. 复杂场景演示 ====================
    private static void demoComplexScenario() {
        System.out.println("\n" + "-".repeat(60));
        System.out.println("5. 复杂场景：Entity在集合中的比较");
        System.out.println("-".repeat(60));

        // List<Entity>演示
        System.out.println("\n📊 List<User> Entity集合比较：");
        List<User> userList1 = Arrays.asList(
            new User(1L, "alice", "alice@example.com"),
            new User(2L, "bob", "bob@example.com")
        );

        List<User> userList2 = Arrays.asList(
            new User(1L, "alice_updated", "alice.new@example.com"), // 同ID，字段变化
            new User(3L, "charlie", "charlie@example.com")          // 新增用户
            // bob (ID=2) 被删除
        );

        ComplexTestObject complex1 = new ComplexTestObject();
        complex1.userList = userList1;
        ComplexTestObject complex2 = new ComplexTestObject();
        complex2.userList = userList2;

        List<ChangeRecord> complexChanges = DiffDetector.diff("ComplexList",
                createCompleteSnapshot("complex1", complex1),
                createCompleteSnapshot("complex2", complex2));
        for (ChangeRecord change : complexChanges) {
            System.out.printf("  - [%s] %s\n", change.getChangeType(), change.getFieldName());
            if (change.getOldValue() != null || change.getNewValue() != null) {
                System.out.printf("    旧: %s\n    新: %s\n",
                    formatCollectionValue(change.getOldValue()), formatCollectionValue(change.getNewValue()));
            }
        }

        // Map<String, Entity>演示
        System.out.println("\n🗂️ Map<String, User> Entity映射比较：");
        Map<String, User> userMap1 = new HashMap<>();
        userMap1.put("user1", new User(1L, "alice", "alice@example.com"));
        userMap1.put("user2", new User(2L, "bob", "bob@example.com"));

        Map<String, User> userMap2 = new HashMap<>();
        userMap2.put("user1", new User(1L, "alice_modified", "alice@example.com"));
        userMap2.put("user3", new User(3L, "charlie", "charlie@example.com"));

        complex1.userMap = userMap1;
        complex2.userMap = userMap2;

        List<ChangeRecord> mapEntityChanges = DiffDetector.diff("MapEntity",
                createCompleteSnapshot("complex1", complex1),
                createCompleteSnapshot("complex2", complex2));
        for (ChangeRecord change : mapEntityChanges) {
            System.out.printf("  - [%s] %s\n", change.getChangeType(), change.getFieldName());
            if (change.getOldValue() != null || change.getNewValue() != null) {
                System.out.printf("    旧: %s\n    新: %s\n",
                    formatCollectionValue(change.getOldValue()), formatCollectionValue(change.getNewValue()));
            }
        }
    }

    // ==================== 辅助类 ====================

    static class DateTestObject {
        private Date date = new Date();
        private LocalDateTime localDateTime = LocalDateTime.now();
        private LocalDate localDate = LocalDate.now();
        private LocalTime localTime = LocalTime.now();

        @DateFormat(pattern = "yyyy-MM-dd")
        private Date customDate = new Date();

        @DateFormat(pattern = "HH:mm:ss")
        private LocalTime customTime = LocalTime.now();

        void addOneDay() {
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            cal.add(Calendar.DAY_OF_MONTH, 1);
            date = cal.getTime();

            localDateTime = localDateTime.plusDays(1);
            localDate = localDate.plusDays(1);
            localTime = localTime.plusHours(1);

            cal.setTime(customDate);
            cal.add(Calendar.DAY_OF_MONTH, 1);
            customDate = cal.getTime();

            customTime = customTime.plusMinutes(30);
        }

        // Getters
        public Date getDate() { return date; }
        public LocalDateTime getLocalDateTime() { return localDateTime; }
        public LocalDate getLocalDate() { return localDate; }
        public LocalTime getLocalTime() { return localTime; }
        public Date getCustomDate() { return customDate; }
        public LocalTime getCustomTime() { return customTime; }
    }

    static class CollectionTestObject {
        List<String> stringList = new ArrayList<>();
        Set<Integer> integerSet = new HashSet<>();
        Map<String, String> stringMap = new HashMap<>();
    }

    static class ComplexTestObject {
        List<User> userList = new ArrayList<>();
        Map<String, User> userMap = new HashMap<>();
    }

    // 格式化输出值
    private static String formatValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Date) {
            return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format((Date) value);
        }
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        if (value instanceof LocalDate) {
            return ((LocalDate) value).format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        if (value instanceof LocalTime) {
            return ((LocalTime) value).format(DateTimeFormatter.ISO_LOCAL_TIME);
        }
        return value.toString();
    }

    // 格式化自定义日期字段 (yyyy-MM-dd)
    private static String formatCustomDate(Object value) {
        if (value == null) return "null";
        if (value instanceof Date) {
            return new java.text.SimpleDateFormat("yyyy-MM-dd").format((Date) value);
        }
        return value.toString();
    }

    // 格式化自定义时间字段 (HH:mm:ss)
    private static String formatCustomTime(Object value) {
        if (value == null) return "null";
        if (value instanceof LocalTime) {
            return ((LocalTime) value).format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        }
        return value.toString();
    }

    // 格式化Entity对象显示
    private static String formatEntityValue(Object value, String fieldName) {
        if (value == null) return "null";

        // 特殊处理Department对象
        if (value instanceof Department) {
            Department dept = (Department) value;
            return String.format("Department{id='%s', name='%s'}",
                dept.getDeptId(), dept.getDeptName());
        }

        // 其他情况使用默认格式
        return value.toString();
    }

    /**
     * 创建完整的对象快照（包括复杂字段和集合）
     */
    private static Map<String, Object> createCompleteSnapshot(String name, Object target) {
        Map<String, Object> snapshot = new HashMap<>();
        
        if (target == null) {
            return snapshot;
        }
        
        try {
            Class<?> clazz = target.getClass();
            java.lang.reflect.Field[] fields = clazz.getDeclaredFields();
            
            for (java.lang.reflect.Field field : fields) {
                field.setAccessible(true);
                try {
                    Object value = field.get(target);
                    // 包含所有字段，不仅仅是标量字段
                    snapshot.put(field.getName(), value);
                } catch (IllegalAccessException e) {
                    // 忽略无法访问的字段
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to create complete snapshot for " + name + ": " + e.getMessage());
            // 降级到标准快照
            return ObjectSnapshot.capture(name, target);
        }
        
        return snapshot;
    }

    /**
     * 格式化集合值的显示
     */
    private static String formatCollectionValue(Object value) {
        if (value == null) return "null";
        if (value instanceof java.util.Collection) {
            java.util.Collection<?> collection = (java.util.Collection<?>) value;
            if (collection.size() <= 5) {
                return collection.toString();
            } else {
                return collection.getClass().getSimpleName() + "[size=" + collection.size() + "]";
            }
        }
        if (value instanceof java.util.Map) {
            java.util.Map<?, ?> map = (java.util.Map<?, ?>) value;
            if (map.size() <= 3) {
                return map.toString();
            } else {
                return map.getClass().getSimpleName() + "[size=" + map.size() + "]";
            }
        }
        return formatValue(value);
    }
}