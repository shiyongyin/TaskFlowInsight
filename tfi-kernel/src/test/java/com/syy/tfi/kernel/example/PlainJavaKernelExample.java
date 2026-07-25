package com.syy.tfi.kernel.example;

import com.syy.tfi.kernel.KernelConfig;
import com.syy.tfi.kernel.Stage;
import com.syy.tfi.kernel.Tfi;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** 只使用公开 API 的纯 Java 接入样例，不依赖 Spring 或生产外发 adapter。 */
public final class PlainJavaKernelExample {

    private PlainJavaKernelExample() {
    }

    /** 运行一条带 typed result 和显式 change 的业务流，并输出冻结终态 JSON。 */
    public static void main(String[] args) {
        run(System.out::println);
    }

    static void run(Consumer<String> output) {
        Objects.requireNonNull(output, "output");
        AtomicReference<String> completedJson = new AtomicReference<>();
        KernelConfig defaults = KernelConfig.defaults();
        Tfi.configure(new KernelConfig(
                true,
                List.of(session -> completedJson.set(Tfi.toJson(session))),
                defaults.sampler(),
                defaults.idGenerator(),
                defaults.clock(),
                defaults.maxStages(),
                defaults.maxSessionEncodedBytes(),
                defaults.maxRecordEncodedBytes(),
                defaults.maxAttrs()));

        try {
            try (Stage flow = Tfi.begin("order.submit")) {
                flow.attr("requestId", "demo-request");
                String reservation = Tfi.call("inventory.reserve", () -> "RESERVED");
                flow.change("order.status", "CREATED", reservation);
                flow.message("order accepted");
            }
            output.accept(Objects.requireNonNull(completedJson.get(), "completed session JSON"));
        } finally {
            Tfi.clear();
            Tfi.configure(KernelConfig.defaults());
            Tfi.setEnabled(true);
        }
    }
}
