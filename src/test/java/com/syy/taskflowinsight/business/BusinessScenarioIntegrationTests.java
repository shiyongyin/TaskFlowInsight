package com.syy.taskflowinsight.business;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.api.TaskContext;
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
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Real business scenario integration tests to validate TFI behavior in production-like environments
 * 
 * These tests cover:
 * - Banking transaction processing
 * - E-commerce order fulfillment
 * - Healthcare patient data management
 * - Supply chain tracking
 * - Financial audit trails
 * - Real-time monitoring scenarios
 */
@SpringBootTest
@DisplayName("Business Scenario Integration Tests")
class BusinessScenarioIntegrationTests {

    @BeforeEach
    void setUp() {
        TFI.enable();
        TFI.setChangeTrackingEnabled(true);
        TFI.clear();
    }

    @AfterEach
    void tearDown() {
        TFI.clear();
    }

    @Nested
    @DisplayName("Banking & Financial Services")
    class BankingScenarios {

        @Test
        @DisplayName("Credit card transaction processing with fraud detection")
        void creditCardTransactionProcessingWithFraudDetection() {
            TFI.startSession("Credit Card Transaction Processing");
            
            // Transaction details
            CreditCardTransaction transaction = new CreditCardTransaction(
                "TXN-001", "4532-****-****-1234", 1500.00, "USD", "Online Purchase"
            );
            CustomerAccount account = new CustomerAccount("ACC-123", "John Doe", 5000.00);
            
            try (TaskContext processing = TFI.start("Process Credit Card Transaction")) {
                processing.attribute("transactionId", transaction.getId());
                processing.attribute("amount", transaction.getAmount());
                processing.attribute("currency", transaction.getCurrency());
                processing.attribute("customerId", account.getId());
                
                // Track transaction and account changes
                TFI.track("transaction", transaction, "status", "amount");
                TFI.track("account", account, "balance", "status");
                
                try (TaskContext validation = processing.subtask("Transaction Validation")) {
                    validation.message("Validating card number and CVV");
                    validation.message("Checking card expiry date");
                    validation.message("Validating merchant details");
                    
                    if (transaction.getAmount() > 1000) {
                        validation.warn("High-value transaction detected");
                    }
                    validation.success();
                }
                
                try (TaskContext fraudCheck = processing.subtask("Fraud Detection")) {
                    fraudCheck.message("Analyzing transaction patterns");
                    fraudCheck.message("Checking geographic location");
                    fraudCheck.message("Evaluating risk score");
                    
                    // Simulate fraud detection logic
                    double riskScore = calculateRiskScore(transaction, account);
                    fraudCheck.attribute("riskScore", riskScore);
                    
                    if (riskScore > 0.7) {
                        fraudCheck.warn("High risk transaction - additional verification required");
                        transaction.setStatus("PENDING_VERIFICATION");
                    } else {
                        fraudCheck.message("Transaction approved by fraud detection");
                        transaction.setStatus("APPROVED");
                    }
                    fraudCheck.success();
                }
                
                try (TaskContext authorization = processing.subtask("Authorization")) {
                    if ("APPROVED".equals(transaction.getStatus())) {
                        authorization.message("Authorizing transaction");
                        account.debit(transaction.getAmount());
                        transaction.setStatus("COMPLETED");
                        authorization.message("Transaction authorized successfully");
                    } else {
                        authorization.warn("Transaction requires manual review");
                        transaction.setStatus("PENDING_REVIEW");
                    }
                    authorization.success();
                }
                
                // Record final transaction state
                List<ChangeRecord> changes = TFI.getChanges();
                processing.message("Transaction processing completed with " + changes.size() + " state changes");
                processing.attribute("finalStatus", transaction.getStatus());
                processing.attribute("finalBalance", account.getBalance());
                processing.success();
            }
            
            // Verify transaction flow
            Session session = TFI.getCurrentSession();
            assertThat(session).isNotNull();
            TaskNode rootTask = session.getRootTask();
            assertThat(rootTask.getChildren()).hasSize(1);
            
            TaskNode transactionTask = rootTask.getChildren().get(0);
            assertThat(transactionTask.getChildren()).hasSize(3); // validation, fraud check, authorization
            
            // Export audit trail
            String auditTrail = TFI.exportToJson();
            assertThat(auditTrail).contains("Credit Card Transaction Processing");
            assertThat(auditTrail).contains("transactionId");
            assertThat(auditTrail).contains("riskScore");
            
            TFI.endSession();
        }

        @Test
        @DisplayName("Loan application processing with credit scoring")
        void loanApplicationProcessingWithCreditScoring() {
            TFI.startSession("Loan Application Processing");
            
            LoanApplication application = new LoanApplication(
                "LOAN-001", "Jane Smith", 50000.00, "HOME", 36
            );
            CreditReport creditReport = new CreditReport("CR-001", 720, "GOOD");
            
            try (TaskContext processing = TFI.start("Process Loan Application")) {
                processing.attribute("applicationId", application.getId());
                processing.attribute("requestedAmount", application.getAmount());
                processing.attribute("loanType", application.getType());
                
                TFI.track("application", application, "status", "approvedAmount", "interestRate");
                TFI.track("creditReport", creditReport, "score", "status");
                
                try (TaskContext creditCheck = processing.subtask("Credit Score Analysis")) {
                    creditCheck.message("Retrieving credit report");
                    creditCheck.message("Analyzing credit history");
                    creditCheck.attribute("creditScore", creditReport.getScore());
                    
                    if (creditReport.getScore() >= 700) {
                        creditCheck.message("Excellent credit score - prime rate eligible");
                        application.setInterestRate(3.5);
                    } else if (creditReport.getScore() >= 650) {
                        creditCheck.message("Good credit score - standard rate");
                        application.setInterestRate(4.5);
                    } else {
                        creditCheck.warn("Below average credit score - high risk");
                        application.setInterestRate(6.5);
                    }
                    creditCheck.success();
                }
                
                try (TaskContext riskAssessment = processing.subtask("Risk Assessment")) {
                    riskAssessment.message("Evaluating debt-to-income ratio");
                    riskAssessment.message("Analyzing employment history");
                    riskAssessment.message("Checking collateral value");
                    
                    // Simulate risk calculation
                    double riskScore = calculateLoanRisk(application, creditReport);
                    riskAssessment.attribute("riskScore", riskScore);
                    
                    if (riskScore < 0.3) {
                        application.setStatus("APPROVED");
                        application.setApprovedAmount(application.getAmount());
                        riskAssessment.message("Low risk - full amount approved");
                    } else if (riskScore < 0.6) {
                        application.setStatus("CONDITIONALLY_APPROVED");
                        application.setApprovedAmount(application.getAmount() * 0.8);
                        riskAssessment.warn("Medium risk - partial approval");
                    } else {
                        application.setStatus("DECLINED");
                        application.setApprovedAmount(0.0);
                        riskAssessment.error("High risk - application declined");
                    }
                    riskAssessment.success();
                }
                
                processing.attribute("finalStatus", application.getStatus());
                processing.attribute("approvedAmount", application.getApprovedAmount());
                processing.attribute("interestRate", application.getInterestRate());
                processing.success();
            }
            
            // Verify comprehensive audit trail
            String jsonReport = TFI.exportToJson();
            assertThat(jsonReport).contains("Loan Application Processing");
            assertThat(jsonReport).contains("creditScore");
            assertThat(jsonReport).contains("riskScore");
            
            TFI.endSession();
        }
    }

    @Nested
    @DisplayName("E-commerce & Retail")
    class EcommerceScenarios {

        @Test
        @DisplayName("Multi-vendor order fulfillment with inventory management")
        void multiVendorOrderFulfillmentWithInventoryManagement() {
            TFI.startSession("Multi-Vendor Order Fulfillment");
            
            Order order = new Order("ORD-001", "CUST-123");
            order.addItem(new OrderItem("PROD-001", "Laptop", 2, 999.99, "VENDOR-A"));
            order.addItem(new OrderItem("PROD-002", "Mouse", 5, 29.99, "VENDOR-B"));
            order.addItem(new OrderItem("PROD-003", "Keyboard", 2, 79.99, "VENDOR-A"));
            
            Inventory inventory = new Inventory();
            inventory.setStock("PROD-001", 10);
            inventory.setStock("PROD-002", 100);
            inventory.setStock("PROD-003", 5);
            
            try (TaskContext fulfillment = TFI.start("Fulfill Multi-Vendor Order")) {
                fulfillment.attribute("orderId", order.getId());
                fulfillment.attribute("customerId", order.getCustomerId());
                fulfillment.attribute("totalItems", order.getItems().size());
                fulfillment.attribute("totalValue", order.calculateTotal());
                
                TFI.track("order", order, "status", "shipmentTracking");
                TFI.track("inventory", inventory);
                
                // Group items by vendor
                Map<String, List<OrderItem>> itemsByVendor = order.getItemsByVendor();
                
                for (Map.Entry<String, List<OrderItem>> vendorEntry : itemsByVendor.entrySet()) {
                    String vendorId = vendorEntry.getKey();
                    List<OrderItem> vendorItems = vendorEntry.getValue();
                    
                    try (TaskContext vendorFulfillment = fulfillment.subtask("Process Vendor " + vendorId)) {
                        vendorFulfillment.attribute("vendorId", vendorId);
                        vendorFulfillment.attribute("itemCount", vendorItems.size());
                        
                        boolean allItemsAvailable = true;
                        double vendorTotal = 0.0;
                        
                        for (OrderItem item : vendorItems) {
                            try (TaskContext itemCheck = vendorFulfillment.subtask("Check Item " + item.getProductId())) {
                                itemCheck.attribute("productId", item.getProductId());
                                itemCheck.attribute("requestedQty", item.getQuantity());
                                
                                int availableStock = inventory.getStock(item.getProductId());
                                itemCheck.attribute("availableStock", availableStock);
                                
                                if (availableStock >= item.getQuantity()) {
                                    inventory.reserve(item.getProductId(), item.getQuantity());
                                    itemCheck.message("Item reserved successfully");
                                    vendorTotal += item.getQuantity() * item.getUnitPrice();
                                } else {
                                    allItemsAvailable = false;
                                    itemCheck.warn("Insufficient stock - only " + availableStock + " available");
                                }
                                itemCheck.success();
                            }
                        }
                        
                        vendorFulfillment.attribute("vendorTotal", vendorTotal);
                        
                        if (allItemsAvailable) {
                            vendorFulfillment.message("All items available - creating shipment");
                            String trackingNumber = "TRACK-" + vendorId + "-" + System.currentTimeMillis();
                            vendorFulfillment.attribute("trackingNumber", trackingNumber);
                            vendorFulfillment.success();
                        } else {
                            vendorFulfillment.warn("Partial fulfillment - some items backordered");
                            vendorFulfillment.fail();
                        }
                    }
                }
                
                // Update order status based on fulfillment results
                order.setStatus("PARTIALLY_FULFILLED");
                List<ChangeRecord> changes = TFI.getChanges();
                fulfillment.message("Order processing completed with " + changes.size() + " inventory changes");
                fulfillment.success();
            }
            
            // Verify multi-vendor processing structure
            Session session = TFI.getCurrentSession();
            TaskNode rootTask = session.getRootTask();
            TaskNode orderTask = rootTask.getChildren().get(0);
            
            // Should have vendor subtasks
            assertThat(orderTask.getChildren().size()).isGreaterThan(0);
            
            String exportedData = TFI.exportToJson();
            assertThat(exportedData).contains("Multi-Vendor Order Fulfillment");
            assertThat(exportedData).contains("trackingNumber");
            
            TFI.endSession();
        }

        @Test
        @DisplayName("Real-time inventory synchronization across channels")
        void realTimeInventorySynchronizationAcrossChannels() {
            TFI.startSession("Real-time Inventory Sync");
            
            Product product = new Product("PROD-SYNC-001", "Smart Watch", 299.99);
            InventoryChannel onlineChannel = new InventoryChannel("ONLINE", 100);
            InventoryChannel storeChannel = new InventoryChannel("STORE-001", 50);
            InventoryChannel warehouseChannel = new InventoryChannel("WAREHOUSE-A", 500);
            
            try (TaskContext sync = TFI.start("Synchronize Inventory Across Channels")) {
                sync.attribute("productId", product.getId());
                sync.attribute("productName", product.getName());
                
                TFI.track("product", product, "totalStock");
                TFI.track("onlineChannel", onlineChannel, "stock", "reserved");
                TFI.track("storeChannel", storeChannel, "stock", "reserved");
                TFI.track("warehouseChannel", warehouseChannel, "stock", "reserved");
                
                // Simulate concurrent inventory updates
                try (TaskContext onlineOrder = sync.subtask("Process Online Order")) {
                    onlineOrder.message("Customer ordered 3 units online");
                    onlineChannel.reserve(3);
                    onlineOrder.attribute("reservedOnline", 3);
                    onlineOrder.success();
                }
                
                try (TaskContext storeTransfer = sync.subtask("Store Transfer Request")) {
                    storeTransfer.message("Store requesting 25 units from warehouse");
                    if (warehouseChannel.getStock() >= 25) {
                        warehouseChannel.transfer(storeChannel, 25);
                        storeTransfer.message("Transfer completed successfully");
                    } else {
                        storeTransfer.warn("Insufficient warehouse stock for transfer");
                    }
                    storeTransfer.success();
                }
                
                try (TaskContext restock = sync.subtask("Warehouse Restock")) {
                    restock.message("Receiving new inventory shipment");
                    warehouseChannel.addStock(200);
                    restock.attribute("restockQuantity", 200);
                    restock.success();
                }
                
                // Calculate and update total available stock
                int totalStock = onlineChannel.getAvailableStock() + 
                               storeChannel.getAvailableStock() + 
                               warehouseChannel.getAvailableStock();
                product.setTotalStock(totalStock);
                
                sync.attribute("finalTotalStock", totalStock);
                sync.attribute("onlineAvailable", onlineChannel.getAvailableStock());
                sync.attribute("storeAvailable", storeChannel.getAvailableStock());
                sync.attribute("warehouseAvailable", warehouseChannel.getAvailableStock());
                
                List<ChangeRecord> changes = TFI.getChanges();
                sync.message("Inventory sync completed with " + changes.size() + " updates");
                sync.success();
            }
            
            String inventoryReport = TFI.exportToJson();
            assertThat(inventoryReport).contains("Synchronize Inventory");
            assertThat(inventoryReport).contains("finalTotalStock");
            
            TFI.endSession();
        }
    }

    @Nested
    @DisplayName("Healthcare & Medical")
    class HealthcareScenarios {

        @Test
        @DisplayName("Patient care workflow with medical record updates")
        void patientCareWorkflowWithMedicalRecordUpdates() {
            TFI.startSession("Patient Care Workflow");
            
            Patient patient = new Patient("PAT-001", "Alice Johnson", "1985-03-15");
            MedicalRecord record = new MedicalRecord("MR-001", patient.getId());
            Appointment appointment = new Appointment("APT-001", patient.getId(), "Dr. Smith", "Routine Checkup");
            
            try (TaskContext careWorkflow = TFI.start("Patient Care Session")) {
                careWorkflow.attribute("patientId", patient.getId());
                careWorkflow.attribute("patientName", patient.getName());
                careWorkflow.attribute("appointmentId", appointment.getId());
                careWorkflow.attribute("appointmentType", appointment.getType());
                
                TFI.track("patient", patient, "status", "lastVisit");
                TFI.track("record", record, "diagnoses", "medications", "vitals");
                TFI.track("appointment", appointment, "status", "notes");
                
                try (TaskContext checkin = careWorkflow.subtask("Patient Check-in")) {
                    checkin.message("Verifying patient identity");
                    checkin.message("Confirming insurance coverage");
                    checkin.message("Updating contact information");
                    patient.setStatus("CHECKED_IN");
                    appointment.setStatus("IN_PROGRESS");
                    checkin.success();
                }
                
                try (TaskContext vitals = careWorkflow.subtask("Record Vital Signs")) {
                    vitals.message("Taking blood pressure");
                    vitals.message("Measuring heart rate");
                    vitals.message("Recording temperature");
                    vitals.message("Checking weight and height");
                    
                    VitalSigns newVitals = new VitalSigns("120/80", 72, 98.6, 150, 65);
                    record.updateVitals(newVitals);
                    vitals.attribute("bloodPressure", newVitals.getBloodPressure());
                    vitals.attribute("heartRate", newVitals.getHeartRate());
                    vitals.attribute("temperature", newVitals.getTemperature());
                    vitals.success();
                }
                
                try (TaskContext consultation = careWorkflow.subtask("Medical Consultation")) {
                    consultation.message("Doctor consultation started");
                    consultation.message("Reviewing medical history");
                    consultation.message("Conducting physical examination");
                    
                    // Simulate medical decisions
                    if (record.getVitals().getBloodPressure().equals("120/80")) {
                        consultation.message("Blood pressure normal");
                    } else {
                        consultation.warn("Blood pressure requires monitoring");
                    }
                    
                    // Add diagnosis and medication
                    record.addDiagnosis("Routine checkup - patient healthy");
                    record.addMedication("Vitamin D supplement - daily");
                    
                    appointment.addNote("Patient appears healthy, continue current health regimen");
                    consultation.success();
                }
                
                try (TaskContext checkout = careWorkflow.subtask("Patient Checkout")) {
                    checkout.message("Scheduling follow-up appointment");
                    checkout.message("Processing billing information");
                    checkout.message("Providing discharge instructions");
                    
                    patient.setStatus("DISCHARGED");
                    patient.setLastVisit(LocalDateTime.now());
                    appointment.setStatus("COMPLETED");
                    checkout.success();
                }
                
                List<ChangeRecord> changes = TFI.getChanges();
                careWorkflow.message("Patient care completed with " + changes.size() + " record updates");
                careWorkflow.attribute("totalDiagnoses", record.getDiagnoses().size());
                careWorkflow.attribute("totalMedications", record.getMedications().size());
                careWorkflow.success();
            }
            
            // Verify medical record integrity
            Session session = TFI.getCurrentSession();
            TaskNode rootTask = session.getRootTask();
            TaskNode careTask = rootTask.getChildren().get(0);
            assertThat(careTask.getChildren()).hasSize(4); // checkin, vitals, consultation, checkout
            
            String medicalReport = TFI.exportToJson();
            assertThat(medicalReport).contains("Patient Care Workflow");
            assertThat(medicalReport).contains("bloodPressure");
            assertThat(medicalReport).contains("totalDiagnoses");
            
            TFI.endSession();
        }
    }

    @Nested
    @DisplayName("Supply Chain & Logistics")
    class SupplyChainScenarios {

        @Test
        @DisplayName("End-to-end supply chain tracking with quality control")
        void endToEndSupplyChainTrackingWithQualityControl() {
            TFI.startSession("Supply Chain Tracking");
            
            Shipment shipment = new Shipment("SHIP-001", "SUPPLIER-A", "WAREHOUSE-B");
            shipment.addItem(new ShipmentItem("PART-001", "Electronic Component", 1000, 5.99));
            shipment.addItem(new ShipmentItem("PART-002", "Plastic Housing", 500, 12.99));
            
            QualityControlReport qcReport = new QualityControlReport("QC-001", shipment.getId());
            
            try (TaskContext tracking = TFI.start("Track Supply Chain Shipment")) {
                tracking.attribute("shipmentId", shipment.getId());
                tracking.attribute("origin", shipment.getOrigin());
                tracking.attribute("destination", shipment.getDestination());
                tracking.attribute("totalItems", shipment.getItems().size());
                tracking.attribute("totalValue", shipment.calculateTotalValue());
                
                TFI.track("shipment", shipment, "status", "location", "estimatedArrival");
                TFI.track("qcReport", qcReport, "status", "defectCount", "approvalStatus");
                
                try (TaskContext departure = tracking.subtask("Shipment Departure")) {
                    departure.message("Loading items onto transport vehicle");
                    departure.message("Conducting pre-departure inspection");
                    departure.message("Generating shipping documents");
                    
                    shipment.setStatus("IN_TRANSIT");
                    shipment.setLocation("SUPPLIER-A Loading Dock");
                    shipment.setEstimatedArrival(LocalDateTime.now().plusDays(3));
                    departure.success();
                }
                
                try (TaskContext transit = tracking.subtask("In-Transit Monitoring")) {
                    transit.message("Monitoring GPS location");
                    transit.message("Checking temperature and humidity");
                    transit.message("Verifying security seals");
                    
                    // Simulate transit updates
                    shipment.setLocation("Highway Checkpoint Alpha");
                    transit.attribute("currentLocation", shipment.getLocation());
                    
                    // Simulate potential issues
                    if (Math.random() > 0.8) {
                        transit.warn("Minor delay due to traffic");
                        shipment.setEstimatedArrival(shipment.getEstimatedArrival().plusHours(2));
                    }
                    transit.success();
                }
                
                try (TaskContext arrival = tracking.subtask("Arrival and Inspection")) {
                    arrival.message("Shipment arrived at destination");
                    arrival.message("Unloading and initial inspection");
                    
                    shipment.setStatus("ARRIVED");
                    shipment.setLocation("WAREHOUSE-B Receiving Dock");
                    
                    // Quality control inspection
                    qcReport.setStatus("IN_PROGRESS");
                    for (ShipmentItem item : shipment.getItems()) {
                        arrival.message("Inspecting " + item.getDescription());
                        
                        // Simulate random defects (5% chance)
                        int defects = (int) (item.getQuantity() * 0.05 * Math.random());
                        if (defects > 0) {
                            qcReport.addDefect(item.getPartNumber(), defects);
                            arrival.warn("Found " + defects + " defective units of " + item.getPartNumber());
                        }
                    }
                    
                    // Determine QC approval
                    if (qcReport.getDefectCount() < 10) {
                        qcReport.setApprovalStatus("APPROVED");
                        shipment.setStatus("ACCEPTED");
                        arrival.message("Quality control passed - shipment accepted");
                    } else {
                        qcReport.setApprovalStatus("REJECTED");
                        shipment.setStatus("REJECTED");
                        arrival.error("Quality control failed - shipment rejected");
                    }
                    
                    qcReport.setStatus("COMPLETED");
                    arrival.success();
                }
                
                List<ChangeRecord> changes = TFI.getChanges();
                tracking.message("Supply chain tracking completed with " + changes.size() + " status updates");
                tracking.attribute("finalStatus", shipment.getStatus());
                tracking.attribute("finalLocation", shipment.getLocation());
                tracking.attribute("qcApproval", qcReport.getApprovalStatus());
                tracking.attribute("totalDefects", qcReport.getDefectCount());
                tracking.success();
            }
            
            String supplyChainReport = TFI.exportToJson();
            assertThat(supplyChainReport).contains("Supply Chain Tracking");
            assertThat(supplyChainReport).contains("qcApproval");
            assertThat(supplyChainReport).contains("totalDefects");
            
            TFI.endSession();
        }
    }

    @Nested
    @DisplayName("Concurrent Business Operations")
    class ConcurrentBusinessOperations {

        @Test
        @DisplayName("Concurrent order processing with resource contention")
        void concurrentOrderProcessingWithResourceContention() throws InterruptedException {
            TFI.startSession("Concurrent Order Processing");
            
            SharedInventory sharedInventory = new SharedInventory();
            sharedInventory.setStock("PROD-CONCURRENT", 100);
            
            ExecutorService executor = Executors.newFixedThreadPool(5);
            CountDownLatch latch = new CountDownLatch(10);
            AtomicInteger successfulOrders = new AtomicInteger(0);
            AtomicInteger failedOrders = new AtomicInteger(0);
            
            // Submit 10 concurrent order processing tasks
            for (int i = 0; i < 10; i++) {
                final int orderId = i + 1;
                executor.submit(() -> {
                    try {
                        String sessionId = TFI.startSession("Order Processing " + orderId);
                        
                        try (TaskContext orderProcessing = TFI.start("Process Order " + orderId)) {
                            orderProcessing.attribute("orderId", "ORD-" + orderId);
                            orderProcessing.attribute("requestedQuantity", 15);
                            
                            // Simulate order processing with inventory contention
                            synchronized (sharedInventory) {
                                if (sharedInventory.getStock("PROD-CONCURRENT") >= 15) {
                                    sharedInventory.reserve("PROD-CONCURRENT", 15);
                                    orderProcessing.message("Inventory reserved successfully");
                                    orderProcessing.success();
                                    successfulOrders.incrementAndGet();
                                } else {
                                    orderProcessing.warn("Insufficient inventory");
                                    orderProcessing.fail();
                                    failedOrders.incrementAndGet();
                                }
                            }
                        }
                        
                        TFI.endSession();
                    } catch (Exception e) {
                        failedOrders.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
            executor.shutdown();
            
            // Verify results
            assertThat(successfulOrders.get() + failedOrders.get()).isEqualTo(10);
            assertThat(successfulOrders.get()).isLessThanOrEqualTo(6); // 100/15 = 6.67, so max 6 successful
            
            TFI.endSession();
        }

        @Test
        @DisplayName("High-throughput data processing with monitoring")
        void highThroughputDataProcessingWithMonitoring() {
            TFI.startSession("High-Throughput Data Processing");
            
            final int BATCH_SIZE = 1000;
            final int TOTAL_RECORDS = 10000;
            AtomicInteger processedRecords = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);
            
            try (TaskContext processing = TFI.start("Process Large Dataset")) {
                processing.attribute("totalRecords", TOTAL_RECORDS);
                processing.attribute("batchSize", BATCH_SIZE);
                
                // Process data in batches
                for (int batch = 0; batch < TOTAL_RECORDS / BATCH_SIZE; batch++) {
                    final int batchNumber = batch + 1;
                    
                    try (TaskContext batchProcessing = processing.subtask("Process Batch " + batchNumber)) {
                        batchProcessing.attribute("batchNumber", batchNumber);
                        batchProcessing.attribute("startRecord", batch * BATCH_SIZE + 1);
                        batchProcessing.attribute("endRecord", (batch + 1) * BATCH_SIZE);
                        
                        // Simulate batch processing
                        for (int record = 0; record < BATCH_SIZE; record++) {
                            try {
                                // Simulate processing logic with random errors (1% chance)
                                if (Math.random() < 0.01) {
                                    throw new RuntimeException("Processing error for record " + (record + 1));
                                }
                                processedRecords.incrementAndGet();
                            } catch (Exception e) {
                                errorCount.incrementAndGet();
                                batchProcessing.warn("Error processing record: " + e.getMessage());
                            }
                        }
                        
                        batchProcessing.attribute("processedInBatch", BATCH_SIZE - (errorCount.get() % BATCH_SIZE));
                        batchProcessing.attribute("errorsInBatch", errorCount.get() % BATCH_SIZE);
                        batchProcessing.success();
                        
                        // Progress monitoring
                        if (batchNumber % 3 == 0) {
                            processing.message("Progress: " + (batchNumber * BATCH_SIZE) + "/" + TOTAL_RECORDS + " records processed");
                        }
                    }
                }
                
                // Final statistics
                processing.attribute("totalProcessed", processedRecords.get());
                processing.attribute("totalErrors", errorCount.get());
                processing.attribute("successRate", (double) processedRecords.get() / TOTAL_RECORDS);
                processing.attribute("errorRate", (double) errorCount.get() / TOTAL_RECORDS);
                
                processing.message("Data processing completed");
                processing.message("Success rate: " + String.format("%.2f%%", 
                    ((double) processedRecords.get() / TOTAL_RECORDS) * 100));
                processing.success();
            }
            
            // Verify high-throughput processing
            Session session = TFI.getCurrentSession();
            TaskNode rootTask = session.getRootTask();
            TaskNode processingTask = rootTask.getChildren().get(0);
            
            // Should have batch subtasks
            assertThat(processingTask.getChildren()).hasSize(TOTAL_RECORDS / BATCH_SIZE);
            
            String processingReport = TFI.exportToJson();
            assertThat(processingReport).contains("High-Throughput Data Processing");
            assertThat(processingReport).contains("successRate");
            assertThat(processingReport).contains("errorRate");
            
            TFI.endSession();
        }
    }

    // Business domain model classes for realistic testing
    
    private static class CreditCardTransaction {
        private String id;
        private String cardNumber;
        private double amount;
        private String currency;
        private String description;
        private String status = "PENDING";
        
        public CreditCardTransaction(String id, String cardNumber, double amount, String currency, String description) {
            this.id = id;
            this.cardNumber = cardNumber;
            this.amount = amount;
            this.currency = currency;
            this.description = description;
        }
        
        // Getters and setters
        public String getId() { return id; }
        public String getCardNumber() { return cardNumber; }
        public double getAmount() { return amount; }
        public String getCurrency() { return currency; }
        public String getDescription() { return description; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
    
    private static class CustomerAccount {
        private String id;
        private String name;
        private double balance;
        private String status = "ACTIVE";
        
        public CustomerAccount(String id, String name, double balance) {
            this.id = id;
            this.name = name;
            this.balance = balance;
        }
        
        public void debit(double amount) {
            this.balance -= amount;
        }
        
        // Getters and setters
        public String getId() { return id; }
        public String getName() { return name; }
        public double getBalance() { return balance; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
    
    private static class LoanApplication {
        private String id;
        private String applicantName;
        private double amount;
        private String type;
        private int termMonths;
        private String status = "PENDING";
        private double approvedAmount = 0.0;
        private double interestRate = 0.0;
        
        public LoanApplication(String id, String applicantName, double amount, String type, int termMonths) {
            this.id = id;
            this.applicantName = applicantName;
            this.amount = amount;
            this.type = type;
            this.termMonths = termMonths;
        }
        
        // Getters and setters
        public String getId() { return id; }
        public String getApplicantName() { return applicantName; }
        public double getAmount() { return amount; }
        public String getType() { return type; }
        public int getTermMonths() { return termMonths; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public double getApprovedAmount() { return approvedAmount; }
        public void setApprovedAmount(double approvedAmount) { this.approvedAmount = approvedAmount; }
        public double getInterestRate() { return interestRate; }
        public void setInterestRate(double interestRate) { this.interestRate = interestRate; }
    }
    
    private static class CreditReport {
        private String id;
        private int score;
        private String status;
        
        public CreditReport(String id, int score, String status) {
            this.id = id;
            this.score = score;
            this.status = status;
        }
        
        public String getId() { return id; }
        public int getScore() { return score; }
        public String getStatus() { return status; }
    }
    
    private static class Order {
        private String id;
        private String customerId;
        private String status = "PENDING";
        private String shipmentTracking;
        private List<OrderItem> items = new ArrayList<>();
        
        public Order(String id, String customerId) {
            this.id = id;
            this.customerId = customerId;
        }
        
        public void addItem(OrderItem item) {
            this.items.add(item);
        }
        
        public double calculateTotal() {
            return items.stream().mapToDouble(item -> item.getQuantity() * item.getUnitPrice()).sum();
        }
        
        public Map<String, List<OrderItem>> getItemsByVendor() {
            return items.stream().collect(
                java.util.stream.Collectors.groupingBy(OrderItem::getVendorId)
            );
        }
        
        // Getters and setters
        public String getId() { return id; }
        public String getCustomerId() { return customerId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getShipmentTracking() { return shipmentTracking; }
        public void setShipmentTracking(String shipmentTracking) { this.shipmentTracking = shipmentTracking; }
        public List<OrderItem> getItems() { return items; }
    }
    
    private static class OrderItem {
        private String productId;
        private String description;
        private int quantity;
        private double unitPrice;
        private String vendorId;
        
        public OrderItem(String productId, String description, int quantity, double unitPrice, String vendorId) {
            this.productId = productId;
            this.description = description;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.vendorId = vendorId;
        }
        
        // Getters
        public String getProductId() { return productId; }
        public String getDescription() { return description; }
        public int getQuantity() { return quantity; }
        public double getUnitPrice() { return unitPrice; }
        public String getVendorId() { return vendorId; }
    }
    
    private static class Inventory {
        private Map<String, Integer> stock = new java.util.HashMap<>();
        private Map<String, Integer> reserved = new java.util.HashMap<>();
        
        public void setStock(String productId, int quantity) {
            stock.put(productId, quantity);
            reserved.putIfAbsent(productId, 0);
        }
        
        public int getStock(String productId) {
            return stock.getOrDefault(productId, 0);
        }
        
        public void reserve(String productId, int quantity) {
            int current = reserved.getOrDefault(productId, 0);
            reserved.put(productId, current + quantity);
        }
        
        public int getAvailable(String productId) {
            return getStock(productId) - reserved.getOrDefault(productId, 0);
        }
    }
    
    // Additional helper classes for comprehensive business scenarios...
    private static class Product {
        private String id;
        private String name;
        private double price;
        private int totalStock;
        
        public Product(String id, String name, double price) {
            this.id = id;
            this.name = name;
            this.price = price;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public double getPrice() { return price; }
        public int getTotalStock() { return totalStock; }
        public void setTotalStock(int totalStock) { this.totalStock = totalStock; }
    }
    
    private static class InventoryChannel {
        private String channelId;
        private int stock;
        private int reserved = 0;
        
        public InventoryChannel(String channelId, int stock) {
            this.channelId = channelId;
            this.stock = stock;
        }
        
        public void reserve(int quantity) {
            this.reserved += quantity;
        }
        
        public void addStock(int quantity) {
            this.stock += quantity;
        }
        
        public void transfer(InventoryChannel target, int quantity) {
            this.stock -= quantity;
            target.stock += quantity;
        }
        
        public int getStock() { return stock; }
        public int getReserved() { return reserved; }
        public int getAvailableStock() { return stock - reserved; }
    }
    
    private static class Patient {
        private String id;
        private String name;
        private String dateOfBirth;
        private String status = "REGISTERED";
        private LocalDateTime lastVisit;
        
        public Patient(String id, String name, String dateOfBirth) {
            this.id = id;
            this.name = name;
            this.dateOfBirth = dateOfBirth;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public String getDateOfBirth() { return dateOfBirth; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public LocalDateTime getLastVisit() { return lastVisit; }
        public void setLastVisit(LocalDateTime lastVisit) { this.lastVisit = lastVisit; }
    }
    
    private static class MedicalRecord {
        private String id;
        private String patientId;
        private List<String> diagnoses = new ArrayList<>();
        private List<String> medications = new ArrayList<>();
        private VitalSigns vitals;
        
        public MedicalRecord(String id, String patientId) {
            this.id = id;
            this.patientId = patientId;
        }
        
        public void addDiagnosis(String diagnosis) { diagnoses.add(diagnosis); }
        public void addMedication(String medication) { medications.add(medication); }
        public void updateVitals(VitalSigns vitals) { this.vitals = vitals; }
        
        public String getId() { return id; }
        public String getPatientId() { return patientId; }
        public List<String> getDiagnoses() { return diagnoses; }
        public List<String> getMedications() { return medications; }
        public VitalSigns getVitals() { return vitals; }
    }
    
    private static class VitalSigns {
        private String bloodPressure;
        private int heartRate;
        private double temperature;
        private int weight;
        private int height;
        
        public VitalSigns(String bloodPressure, int heartRate, double temperature, int weight, int height) {
            this.bloodPressure = bloodPressure;
            this.heartRate = heartRate;
            this.temperature = temperature;
            this.weight = weight;
            this.height = height;
        }
        
        public String getBloodPressure() { return bloodPressure; }
        public int getHeartRate() { return heartRate; }
        public double getTemperature() { return temperature; }
        public int getWeight() { return weight; }
        public int getHeight() { return height; }
    }
    
    private static class Appointment {
        private String id;
        private String patientId;
        private String doctorName;
        private String type;
        private String status = "SCHEDULED";
        private List<String> notes = new ArrayList<>();
        
        public Appointment(String id, String patientId, String doctorName, String type) {
            this.id = id;
            this.patientId = patientId;
            this.doctorName = doctorName;
            this.type = type;
        }
        
        public void addNote(String note) { notes.add(note); }
        
        public String getId() { return id; }
        public String getPatientId() { return patientId; }
        public String getDoctorName() { return doctorName; }
        public String getType() { return type; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public List<String> getNotes() { return notes; }
    }
    
    private static class Shipment {
        private String id;
        private String origin;
        private String destination;
        private String status = "PENDING";
        private String location;
        private LocalDateTime estimatedArrival;
        private List<ShipmentItem> items = new ArrayList<>();
        
        public Shipment(String id, String origin, String destination) {
            this.id = id;
            this.origin = origin;
            this.destination = destination;
        }
        
        public void addItem(ShipmentItem item) { items.add(item); }
        
        public double calculateTotalValue() {
            return items.stream().mapToDouble(item -> item.getQuantity() * item.getUnitPrice()).sum();
        }
        
        public String getId() { return id; }
        public String getOrigin() { return origin; }
        public String getDestination() { return destination; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        public LocalDateTime getEstimatedArrival() { return estimatedArrival; }
        public void setEstimatedArrival(LocalDateTime estimatedArrival) { this.estimatedArrival = estimatedArrival; }
        public List<ShipmentItem> getItems() { return items; }
    }
    
    private static class ShipmentItem {
        private String partNumber;
        private String description;
        private int quantity;
        private double unitPrice;
        
        public ShipmentItem(String partNumber, String description, int quantity, double unitPrice) {
            this.partNumber = partNumber;
            this.description = description;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }
        
        public String getPartNumber() { return partNumber; }
        public String getDescription() { return description; }
        public int getQuantity() { return quantity; }
        public double getUnitPrice() { return unitPrice; }
    }
    
    private static class QualityControlReport {
        private String id;
        private String shipmentId;
        private String status = "PENDING";
        private Map<String, Integer> defects = new java.util.HashMap<>();
        private String approvalStatus = "PENDING";
        
        public QualityControlReport(String id, String shipmentId) {
            this.id = id;
            this.shipmentId = shipmentId;
        }
        
        public void addDefect(String partNumber, int count) {
            defects.put(partNumber, defects.getOrDefault(partNumber, 0) + count);
        }
        
        public int getDefectCount() {
            return defects.values().stream().mapToInt(Integer::intValue).sum();
        }
        
        public String getId() { return id; }
        public String getShipmentId() { return shipmentId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getApprovalStatus() { return approvalStatus; }
        public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }
        public Map<String, Integer> getDefects() { return defects; }
    }
    
    private static class SharedInventory {
        private Map<String, Integer> stock = new java.util.concurrent.ConcurrentHashMap<>();
        
        public void setStock(String productId, int quantity) {
            stock.put(productId, quantity);
        }
        
        public int getStock(String productId) {
            return stock.getOrDefault(productId, 0);
        }
        
        public synchronized boolean reserve(String productId, int quantity) {
            int current = stock.getOrDefault(productId, 0);
            if (current >= quantity) {
                stock.put(productId, current - quantity);
                return true;
            }
            return false;
        }
    }
    
    // Business logic helper methods
    private double calculateRiskScore(CreditCardTransaction transaction, CustomerAccount account) {
        double riskScore = 0.0;
        
        // High amount increases risk
        if (transaction.getAmount() > 1000) {
            riskScore += 0.3;
        }
        
        // Low balance increases risk
        if (account.getBalance() < transaction.getAmount() * 2) {
            riskScore += 0.2;
        }
        
        // Random factor for testing
        riskScore += Math.random() * 0.3;
        
        return Math.min(riskScore, 1.0);
    }
    
    private double calculateLoanRisk(LoanApplication application, CreditReport creditReport) {
        double riskScore = 0.0;
        
        // Credit score factor (inverse relationship)
        if (creditReport.getScore() < 600) {
            riskScore += 0.5;
        } else if (creditReport.getScore() < 700) {
            riskScore += 0.3;
        } else {
            riskScore += 0.1;
        }
        
        // Loan amount factor
        if (application.getAmount() > 100000) {
            riskScore += 0.2;
        }
        
        // Random factor
        riskScore += Math.random() * 0.2;
        
        return Math.min(riskScore, 1.0);
    }
}