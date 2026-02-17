package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Comprehensive API tests to improve TFI coverage from 47% to 75%+
 * 
 * Coverage targets:
 * - Error handling paths
 * - Edge cases and null safety
 * - Change tracking features
 * - Export functionality
 * - Concurrent usage scenarios
 * - Business scenario simulations
 */
@DisplayName("TFI Comprehensive API Tests")
class TFIComprehensiveApiTests {

    @BeforeEach
    void setupEach() {
        TFI.enable();
        TFI.clear();
    }

    @AfterEach
    void cleanupEach() {
        TFI.clear();
    }

    @Nested
    @DisplayName("Error Handling & Edge Cases")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Null and empty parameter handling")
        void handleNullAndEmptyParameters() {
            // Session management
            assertThat(TFI.startSession(null)).isNull();
            assertThat(TFI.startSession("")).isNull();
            assertThat(TFI.startSession("   ")).isNull();
            
            // Task management with null/empty names
            TaskContext nullTask = TFI.start(null);
            assertThat(nullTask).isNotNull(); // Should return NullTaskContext
            
            TaskContext emptyTask = TFI.start("");
            assertThat(emptyTask).isNotNull();
            
            // Message recording with null values
            assertThatCode(() -> TFI.message(null, MessageType.PROCESS)).doesNotThrowAnyException();
            assertThatCode(() -> TFI.message("test", (MessageType) null)).doesNotThrowAnyException();
            assertThatCode(() -> TFI.message("test", (String) null)).doesNotThrowAnyException();
            
            // Error recording
            assertThatCode(() -> TFI.error(null)).doesNotThrowAnyException();
            assertThatCode(() -> TFI.error("test", null)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Operations when disabled")
        void operationsWhenDisabled() {
            TFI.disable();
            
            // All operations should be safe when disabled
            assertThat(TFI.isEnabled()).isFalse();
            assertThat(TFI.startSession("test")).isNull();
            assertThat(TFI.getCurrentSession()).isNull();
            assertThat(TFI.getCurrentTask()).isNull();
            assertThat(TFI.getTaskStack()).isEmpty();
            
            // Task operations should still work for business logic
            AtomicInteger counter = new AtomicInteger(0);
            TFI.run("test", counter::incrementAndGet);
            assertThat(counter.get()).isEqualTo(1);
            
            Integer result = TFI.call("test", () -> 42);
            assertThat(result).isEqualTo(42);
            
            // Export operations should return safe defaults
            assertThat(TFI.exportToJson()).isEqualTo("{}");
            assertThat(TFI.exportToMap()).isEmpty();
            
            TFI.enable(); // Re-enable for cleanup
        }

        @Test
        @DisplayName("Nested task exception handling")
        void nestedTaskExceptionHandling() {
            String sessionId = TFI.startSession("Exception Test Session");
            assertThat(sessionId).isNotNull();
            
            try (TaskContext main = TFI.start("Main Task")) {
                main.message("Starting main task");
                
                try (TaskContext sub = main.subtask("Sub Task")) {
                    sub.message("In sub task");
                    
                    // Simulate exception in nested task
                    try {
                        throw new RuntimeException("Test exception");
                    } catch (Exception e) {
                        sub.error("Caught exception: " + e.getMessage());
                        sub.fail();
                    }
                }
                
                main.message("Sub task completed with error");
                main.warn("Main task continuing despite sub task failure");
                main.success();
            }
            
            Session session = TFI.getCurrentSession();
            assertThat(session).isNotNull();
            assertThat(session.getRootTask().getChildren()).hasSize(1);
            
            TFI.endSession();
        }
    }

    @Nested
    @DisplayName("Change Tracking Features")
    class ChangeTrackingTests {

        @Test
        @DisplayName("Manual change recording")
        void manualChangeRecording() {
            TFI.setChangeTrackingEnabled(true);
            TFI.startSession("Change Recording Session");
            
            try (TaskContext task = TFI.start("Change Task")) {
                // Record different types of changes
                TFI.recordChange("User", "name", "oldName", "newName", ChangeType.UPDATE);
                TFI.recordChange("User", "age", 25, 26, ChangeType.UPDATE);
                TFI.recordChange("Order", "status", null, "CREATED", ChangeType.CREATE);
                TFI.recordChange("Product", "price", 100.0, null, ChangeType.DELETE);
                
                task.success();
            }
            
            // Verify changes are recorded in task messages
            TaskNode rootTask = TFI.getCurrentSession().getRootTask();
            assertThat(rootTask.getChildren()).hasSize(1);
            TaskNode changeTask = rootTask.getChildren().get(0);
            assertThat(changeTask.getMessages()).isNotEmpty();
            
            TFI.endSession();
        }

        @Test
        @DisplayName("Object tracking and change detection")
        void objectTrackingAndChangeDetection() {
            TFI.setChangeTrackingEnabled(true);
            TFI.startSession("Object Tracking Session");
            
            // Create test objects
            TestUser user1 = new TestUser("John", 25);
            TestUser user2 = new TestUser("Jane", 30);
            
            // Track objects
            TFI.track("user1", user1, "name", "age");
            TFI.track("user2", user2);
            
            // Make changes
            user1.setName("Johnny");
            user1.setAge(26);
            user2.setName("Janet");
            
            // Get changes
            List<ChangeRecord> changes = TFI.getChanges();
            assertThat(changes).isNotEmpty();
            
            // Verify change detection
            boolean foundNameChange = changes.stream()
                .anyMatch(c -> "user1".equals(c.getObjectName()) && "name".equals(c.getFieldName()));
            assertThat(foundNameChange).isTrue();
            
            TFI.endSession();
        }

        @Test
        @DisplayName("Batch tracking performance")
        void batchTrackingPerformance() {
            TFI.setChangeTrackingEnabled(true);
            TFI.startSession("Batch Tracking Session");
            
            // Create large batch of objects
            Map<String, Object> largeObjects = new java.util.HashMap<>();
            for (int i = 0; i < 150; i++) {
                largeObjects.put("user" + i, new TestUser("User" + i, 20 + (i % 50)));
            }
            
            long startTime = System.currentTimeMillis();
            TFI.trackAll(largeObjects);
            long duration = System.currentTimeMillis() - startTime;
            
            // Verify batch processing completed within reasonable time
            assertThat(duration).isLessThan(5000); // Should complete within 5 seconds
            
            TFI.endSession();
        }

        @Test
        @DisplayName("withTracked lifecycle management")
        void withTrackedLifecycleManagement() {
            TFI.setChangeTrackingEnabled(true);
            TFI.startSession("Lifecycle Session");
            
            TestUser user = new TestUser("Test", 25);
            
            TFI.withTracked("testUser", user, () -> {
                user.setName("Updated");
                user.setAge(30);
            }, "name", "age");
            
            // Verify tracking was cleaned up automatically
            List<ChangeRecord> remainingChanges = TFI.getChanges();
            assertThat(remainingChanges).isEmpty();
            
            TFI.endSession();
        }
    }

    @Nested
    @DisplayName("Export Functionality")
    class ExportTests {

        @Test
        @DisplayName("Console export with and without timestamps")
        void consoleExportVariations() {
            TFI.startSession("Export Test Session");
            
            try (TaskContext task = TFI.start("Export Task")) {
                task.message("Test message");
                task.attribute("key", "value");
                task.success();
            }
            
            // Test console export without timestamps (default)
            assertThatCode(() -> TFI.exportToConsole()).doesNotThrowAnyException();
            
            // Test console export with timestamps
            assertThatCode(() -> TFI.exportToConsole(true)).doesNotThrowAnyException();
            
            TFI.endSession();
        }

        @Test
        @DisplayName("JSON export validation")
        void jsonExportValidation() {
            TFI.startSession("JSON Export Session");
            
            try (TaskContext task = TFI.start("JSON Task")) {
                task.message("JSON test message");
                task.attribute("jsonKey", "jsonValue");
                task.warn("Test warning");
                task.success();
            }
            
            String json = TFI.exportToJson();
            assertThat(json).isNotNull();
            assertThat(json).contains("JSON Export Session");
            assertThat(json).contains("JSON Task");
            assertThat(json).contains("jsonKey");
            
            TFI.endSession();
        }

        @Test
        @DisplayName("Map export structure validation")
        void mapExportStructureValidation() {
            TFI.startSession("Map Export Session");
            
            try (TaskContext task = TFI.start("Map Task")) {
                task.message("Map test message");
                task.attribute("mapKey", "mapValue");
                task.success();
            }
            
            Map<String, Object> exported = TFI.exportToMap();
            assertThat(exported).isNotNull();
            assertThat(exported).isNotEmpty();
            assertThat(exported).containsKey("sessionId");
            assertThat(exported).containsKey("sessionName");
            
            TFI.endSession();
        }
    }

    @Nested
    @DisplayName("Concurrent Usage Scenarios")
    class ConcurrentTests {

        @Test
        @DisplayName("Multi-threaded session handling")
        void multiThreadedSessionHandling() throws InterruptedException {
            ExecutorService executor = Executors.newFixedThreadPool(5);
            AtomicInteger successCount = new AtomicInteger(0);
            
            // Submit multiple concurrent tasks
            for (int i = 0; i < 10; i++) {
                final int taskId = i;
                executor.submit(() -> {
                    try {
                        String sessionId = TFI.startSession("Concurrent Session " + taskId);
                        if (sessionId != null) {
                            try (TaskContext task = TFI.start("Concurrent Task " + taskId)) {
                                task.message("Processing in thread " + Thread.currentThread().getName());
                                
                                // Simulate some work
                                Thread.sleep(10);
                                
                                task.attribute("threadId", Thread.currentThread().getId());
                                task.success();
                            }
                            TFI.endSession();
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Expected in concurrent scenarios
                    }
                });
            }
            
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            
            // At least some tasks should succeed
            assertThat(successCount.get()).isGreaterThan(0);
        }

        @Test
        @DisplayName("Async execution with CompletableFuture")
        void asyncExecutionWithCompletableFuture() {
            TFI.startSession("Async Session");
            
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                return TFI.call("Async Task", () -> {
                    // Simulate async processing
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "Async Result";
                });
            });
            
            String result = future.join();
            assertThat(result).isEqualTo("Async Result");
            
            TFI.endSession();
        }
    }

    @Nested
    @DisplayName("Business Scenario Simulations")
    class BusinessScenarioTests {

        @Test
        @DisplayName("E-commerce order processing simulation")
        void ecommerceOrderProcessingSimulation() {
            TFI.setChangeTrackingEnabled(true);
            TFI.startSession("E-commerce Order Processing");
            
            // Simulate order objects
            Order order = new Order("ORD-001", "PENDING");
            Customer customer = new Customer("CUST-001", "john@example.com");
            
            try (TaskContext orderProcessing = TFI.start("Process Order")) {
                orderProcessing.attribute("orderId", order.getId());
                orderProcessing.attribute("customerId", customer.getId());
                
                // Track order and customer changes
                TFI.track("order", order, "status", "total");
                TFI.track("customer", customer, "email", "loyaltyPoints");
                
                try (TaskContext validation = orderProcessing.subtask("Validate Order")) {
                    validation.message("Validating order items");
                    validation.message("Checking inventory");
                    validation.success();
                }
                
                try (TaskContext payment = orderProcessing.subtask("Process Payment")) {
                    payment.message("Processing payment for order: " + order.getId());
                    order.setStatus("PAID");
                    customer.addLoyaltyPoints(10);
                    payment.success();
                }
                
                try (TaskContext fulfillment = orderProcessing.subtask("Prepare Fulfillment")) {
                    fulfillment.message("Preparing items for shipment");
                    order.setStatus("SHIPPED");
                    fulfillment.success();
                }
                
                // Capture all changes
                List<ChangeRecord> changes = TFI.getChanges();
                orderProcessing.message("Order processing completed with " + changes.size() + " changes");
                orderProcessing.success();
            }
            
            // Verify session structure
            Session session = TFI.getCurrentSession();
            assertThat(session).isNotNull();
            TaskNode rootTask = session.getRootTask();
            assertThat(rootTask.getChildren()).hasSize(1);
            TaskNode orderTask = rootTask.getChildren().get(0);
            assertThat(orderTask.getChildren()).hasSize(3); // validation, payment, fulfillment
            
            TFI.exportToConsole(true);
            TFI.endSession();
        }

        @Test
        @DisplayName("Data processing pipeline simulation")
        void dataProcessingPipelineSimulation() {
            TFI.startSession("Data Processing Pipeline");
            
            AtomicInteger recordsProcessed = new AtomicInteger(0);
            AtomicInteger errorsEncountered = new AtomicInteger(0);
            
            try (TaskContext pipeline = TFI.start("Data Pipeline")) {
                pipeline.attribute("batchSize", 1000);
                pipeline.attribute("source", "database");
                
                // Simulate data extraction
                try (TaskContext extraction = pipeline.subtask("Data Extraction")) {
                    extraction.message("Connecting to data source");
                    extraction.message("Extracting 1000 records");
                    recordsProcessed.set(1000);
                    extraction.attribute("recordsExtracted", recordsProcessed.get());
                    extraction.success();
                }
                
                // Simulate data transformation
                try (TaskContext transformation = pipeline.subtask("Data Transformation")) {
                    transformation.message("Applying transformation rules");
                    
                    // Simulate some errors
                    for (int i = 0; i < 50; i++) {
                        if (i % 10 == 0) {
                            errorsEncountered.incrementAndGet();
                            transformation.warn("Transformation error for record " + i);
                        }
                    }
                    
                    transformation.attribute("errorsFound", errorsEncountered.get());
                    transformation.message("Transformation completed with " + errorsEncountered.get() + " errors");
                    transformation.success();
                }
                
                // Simulate data loading
                try (TaskContext loading = pipeline.subtask("Data Loading")) {
                    loading.message("Loading transformed data");
                    int successfulRecords = recordsProcessed.get() - errorsEncountered.get();
                    loading.attribute("recordsLoaded", successfulRecords);
                    loading.success();
                }
                
                pipeline.attribute("totalProcessed", recordsProcessed.get());
                pipeline.attribute("successfulRecords", recordsProcessed.get() - errorsEncountered.get());
                pipeline.attribute("errorRate", (double) errorsEncountered.get() / recordsProcessed.get());
                pipeline.success();
            }
            
            // Export results
            String jsonReport = TFI.exportToJson();
            assertThat(jsonReport).contains("Data Processing Pipeline");
            assertThat(jsonReport).contains("recordsLoaded");
            
            TFI.endSession();
        }
    }

    @Nested
    @DisplayName("Stage API Coverage")
    class StageApiTests {

        @Test
        @DisplayName("Stage with exception handling")
        void stageWithExceptionHandling() {
            TFI.startSession("Stage Exception Session");
            
            // Test stage that throws exception
            String result = TFI.stage("Exception Stage", stage -> {
                stage.message("Before exception");
                try {
                    throw new RuntimeException("Test exception");
                } catch (Exception e) {
                    stage.error("Caught: " + e.getMessage());
                    stage.fail();
                    return "error_handled";
                }
            });
            
            assertThat(result).isEqualTo("error_handled");
            TFI.endSession();
        }

        @Test
        @DisplayName("Stage with null function")
        void stageWithNullFunction() {
            String result = TFI.stage("Null Function Stage", null);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Stage AutoCloseable behavior")
        void stageAutoCloseableBehavior() {
            TFI.startSession("AutoCloseable Session");
            
            TaskNode currentBefore = TFI.getCurrentTask();
            
            try (TaskContext stage = TFI.stage("Auto Stage")) {
                assertThat(stage).isNotNull();
                stage.message("Inside stage");
                
                TaskNode currentInside = TFI.getCurrentTask();
                assertThat(currentInside).isNotEqualTo(currentBefore);
            } // Auto-close should happen here
            
            TaskNode currentAfter = TFI.getCurrentTask();
            assertThat(currentAfter).isEqualTo(currentBefore);
            
            TFI.endSession();
        }
    }

    // Test helper classes
    private static class TestUser {
        private String name;
        private int age;
        
        public TestUser(String name, int age) {
            this.name = name;
            this.age = age;
        }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
    }
    
    private static class Order {
        private String id;
        private String status;
        private double total;
        
        public Order(String id, String status) {
            this.id = id;
            this.status = status;
            this.total = 0.0;
        }
        
        public String getId() { return id; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public double getTotal() { return total; }
        public void setTotal(double total) { this.total = total; }
    }
    
    private static class Customer {
        private String id;
        private String email;
        private int loyaltyPoints;
        
        public Customer(String id, String email) {
            this.id = id;
            this.email = email;
            this.loyaltyPoints = 0;
        }
        
        public String getId() { return id; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public int getLoyaltyPoints() { return loyaltyPoints; }
        public void addLoyaltyPoints(int points) { this.loyaltyPoints += points; }
    }
}