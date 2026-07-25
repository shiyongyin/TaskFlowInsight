package com.syy.taskflowinsight.context;

import com.syy.taskflowinsight.internal.FlowConfigDefaults;
import com.syy.taskflowinsight.model.TaskNode;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 断连 nested-depth 能力的 4.0 删除契约。
 *
 * <p>删除镜像 registry 后仍显式验证真实 task stack 的 LIFO 语义，避免把“删除无 owner 状态”
 * 误实现成“删除业务任务嵌套”。
 */
class NestedDepthRemovalTests {

    private static final String ZERO_LEAK_MANAGER =
            "com.syy.taskflowinsight.context.ZeroLeakThreadLocalManager";
    private static final String NESTED_TRACKER =
            "com.syy.taskflowinsight.context.NestedStageTracker";
    private static final String MAX_DEPTH = "NESTED_STAGE_MAX_DEPTH";
    private static final String CLEANUP_BATCH = "NESTED_CLEANUP_BATCH_SIZE";

    @Test
    void removesDisconnectedFacadeTrackerAndSources() {
        assertThatThrownBy(() -> Class.forName(ZERO_LEAK_MANAGER))
                .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName(NESTED_TRACKER))
                .isInstanceOf(ClassNotFoundException.class);
        assertThat(sourceRoot().resolve("com/syy/taskflowinsight/context/ZeroLeakThreadLocalManager.java"))
                .doesNotExist();
        assertThat(sourceRoot().resolve("com/syy/taskflowinsight/context/NestedStageTracker.java"))
                .doesNotExist();
    }

    @Test
    void removesNestedConstantsAndConfigurationKeys() throws Exception {
        assertThat(publicFieldNames(FlowConfigDefaults.class))
                .doesNotContain(MAX_DEPTH, CLEANUP_BATCH);

        try (var paths = Files.walk(sourceRoot())) {
            var javaSources = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
            for (Path source : javaSources) {
                assertThat(Files.readString(source))
                        .as(source.toString())
                        .doesNotContain(
                                "tfi.context.nested-stage.max-depth",
                                "tfi.context.nested-cleanup.batch-size");
            }
        }
    }

    @Test
    void managedContextRemainsTheOnlyNestedTaskOwner() {
        TaskNode root;
        TaskNode outer;
        TaskNode inner;
        try (ManagedThreadContext context = ManagedThreadContext.create("nested-owner")) {
            root = context.getCurrentTask();
            assertThat(context.getTaskDepth()).isOne();
            outer = context.startTask("outer");
            inner = context.startTask("inner");

            assertThat(context.getCurrentTask()).isSameAs(inner);
            assertThat(context.getTaskDepth()).isEqualTo(3);
            assertThat(context.endTask()).isSameAs(inner);
            assertThat(context.getCurrentTask()).isSameAs(outer);
            assertThat(context.endTask()).isSameAs(outer);
            assertThat(context.getCurrentTask()).isSameAs(root);
        }

        assertThat(inner.getStatus().isSuccessful()).isTrue();
        assertThat(outer.getStatus().isSuccessful()).isTrue();
        assertThat(root.getStatus().isSuccessful()).isTrue();
    }

    private static String[] publicFieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers()))
                .map(field -> field.getName())
                .toArray(String[]::new);
    }

    private static Path sourceRoot() {
        Path direct = Path.of("src/main/java");
        return Files.isDirectory(direct) ? direct : Path.of("tfi-flow-core/src/main/java");
    }
}
