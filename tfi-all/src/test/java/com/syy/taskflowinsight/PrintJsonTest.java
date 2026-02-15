package com.syy.taskflowinsight;

import com.syy.taskflowinsight.api.TFI;
import org.junit.jupiter.api.Test;

class PrintJsonTest {
    static class Product {
        private String name = "Test";
        private Double price = 100.0;
        void setPrice(Double price) { this.price = price; }
    }
    
    @Test
    void printJson() {
        TFI.enable();
        TFI.setChangeTrackingEnabled(true);
        TFI.startSession("TestSession");
        TFI.start("TestTask");
        
        Product p = new Product();
        TFI.track("Product", p, "price");
        p.setPrice(200.0);
        
        TFI.stop();
        String json = TFI.exportToJson();
        System.out.println("JSON Output:\n" + json);
    }
}