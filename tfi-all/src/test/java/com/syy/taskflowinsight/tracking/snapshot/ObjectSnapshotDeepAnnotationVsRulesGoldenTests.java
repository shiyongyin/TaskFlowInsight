package com.syy.taskflowinsight.tracking.snapshot;

import com.syy.taskflowinsight.annotation.DiffIgnore;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ObjectSnapshotDeepAnnotationVsRulesGoldenTests {

    static class PojoPath {
        @DiffIgnore String secret;
        String visible;
        PojoPath(String s, String v){ this.secret=s; this.visible=v; }
    }

    static class PojoDefault {
        @DiffIgnore transient String temp;
        String visible;
        PojoDefault(String t, String v){ this.temp=t; this.visible=v; }
    }

    static class PojoPackage {
        @DiffIgnore String secret;
        String visible;
        PojoPackage(String s, String v){ this.secret=s; this.visible=v; }
    }

    @Test
    void diffIgnore_vs_path_blacklist_then_include_overrides() {
        SnapshotConfig config = new SnapshotConfig();
        // 路径黑名单命中 secret 与可见字段
        config.setExcludePatterns(new ArrayList<>(List.of("secret", "visible")));

        ObjectSnapshotDeep snapshot = new ObjectSnapshotDeep(config);

        Map<String, Object> flat = snapshot.captureDeep(new PojoPath("S", "V"), 5,
            Collections.emptySet(), Collections.emptySet());

        // 注解优先：secret 不应出现
        assertFalse(flat.containsKey("secret"));
        // visible 被路径黑名单过滤
        assertFalse(flat.containsKey("visible"));

        // Include 精确挽回 secret
        config.setIncludePatterns(new ArrayList<>(List.of("secret")));
        flat = snapshot.captureDeep(new PojoPath("S", "V"), 5,
            Collections.emptySet(), Collections.emptySet());
        assertTrue(flat.containsKey("secret"), "include should override @DiffIgnore and path blacklist");
    }

    @Test
    void diffIgnore_vs_default_exclusions_then_include_overrides() {
        SnapshotConfig config = new SnapshotConfig();
        config.setDefaultExclusionsEnabled(true);
        config.setExcludePatterns(new ArrayList<>());

        ObjectSnapshotDeep snapshot = new ObjectSnapshotDeep(config);
        Map<String, Object> flat = snapshot.captureDeep(new PojoDefault("T", "V"), 5,
            Collections.emptySet(), Collections.emptySet());

        // 注解或默认忽略都将 temp 过滤
        assertFalse(flat.containsKey("temp"));

        // Include 挽回 temp
        config.setIncludePatterns(new ArrayList<>(List.of("temp")));
        flat = snapshot.captureDeep(new PojoDefault("T", "V"), 5,
            Collections.emptySet(), Collections.emptySet());
        assertTrue(flat.containsKey("temp"), "include should override @DiffIgnore and default exclusions");
    }

    @Test
    void diffIgnore_vs_package_blacklist_then_include_overrides() {
        SnapshotConfig config = new SnapshotConfig();
        // 包级黑名单匹配当前测试类所在包
        String currentPkg = this.getClass().getPackageName();
        config.setExcludePackages(new ArrayList<>(List.of(currentPkg + ".**")));

        ObjectSnapshotDeep snapshot = new ObjectSnapshotDeep(config);
        Map<String, Object> flat = snapshot.captureDeep(new PojoPackage("S", "V"), 5,
            Collections.emptySet(), Collections.emptySet());

        // 注解优先：secret 不应出现
        assertFalse(flat.containsKey("secret"));

        // Include 挽回 secret（覆盖包级与注解）
        config.setIncludePatterns(new ArrayList<>(List.of("secret")));
        flat = snapshot.captureDeep(new PojoPackage("S", "V"), 5,
            Collections.emptySet(), Collections.emptySet());
        assertTrue(flat.containsKey("secret"), "include should override @DiffIgnore and package blacklist");
    }
}

