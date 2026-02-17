#!/usr/bin/env python3
from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path


def _find_repo_root(start: Path) -> Path:
    for candidate in [start, *start.parents]:
        if (candidate / "src/main/java/com/syy/taskflowinsight").is_dir():
            return candidate
    raise SystemExit(
        "Cannot find repo root (expected src/main/java/com/syy/taskflowinsight). "
        "Run this script from the TaskFlowInsight repo."
    )


def _unique_paths(paths: list[Path]) -> list[Path]:
    seen: set[Path] = set()
    result: list[Path] = []
    for path in paths:
        if path in seen:
            continue
        seen.add(path)
        result.append(path)
    return result


def _collect_sources(repo_root: Path) -> list[Path]:
    include_dirs = [
        "src/main/java/com/syy/taskflowinsight/context",
        "src/main/java/com/syy/taskflowinsight/model",
        "src/main/java/com/syy/taskflowinsight/enums",
        "src/main/java/com/syy/taskflowinsight/exporter/json",
        "src/main/java/com/syy/taskflowinsight/exporter/map",
        "src/main/java/com/syy/taskflowinsight/exporter/text",
    ]
    include_files = [
        "src/main/java/com/syy/taskflowinsight/api/NullTaskContext.java",
        "src/main/java/com/syy/taskflowinsight/api/StageFunction.java",
        "src/main/java/com/syy/taskflowinsight/api/TaskContext.java",
        "src/main/java/com/syy/taskflowinsight/api/TaskContextImpl.java",
        "src/main/java/com/syy/taskflowinsight/annotation/TfiTask.java",
        "src/main/java/com/syy/taskflowinsight/config/resolver/ConfigDefaults.java",
        "src/main/java/com/syy/taskflowinsight/util/DiagnosticLogger.java",
    ]

    sources: list[Path] = []
    for rel_dir in include_dirs:
        abs_dir = repo_root / rel_dir
        if not abs_dir.is_dir():
            raise SystemExit(f"Missing expected source dir: {rel_dir}")
        sources.extend(sorted(abs_dir.rglob("*.java")))

    for rel_file in include_files:
        abs_file = repo_root / rel_file
        if not abs_file.is_file():
            raise SystemExit(f"Missing expected source file: {rel_file}")
        sources.append(abs_file)

    return _unique_paths(sources)


def _remove_method_block(java_text: str, signature_fragment: str) -> str:
    pos = java_text.find(signature_fragment)
    if pos < 0:
        return java_text

    # Include leading Javadoc (if any)
    start = java_text.rfind("/**", 0, pos)
    if start >= 0:
        start = java_text.rfind("\n", 0, start) + 1
    else:
        start = pos

    open_brace = java_text.find("{", pos)
    if open_brace < 0:
        return java_text

    depth = 0
    end = None
    for i in range(open_brace, len(java_text)):
        ch = java_text[i]
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                end = i + 1
                break
    if end is None:
        return java_text

    # Trim following blank lines
    while end < len(java_text) and java_text[end] in ["\n", "\r", " ", "\t"]:
        if java_text[end] == "\n":
            # Stop after removing at most one trailing newline "block"
            # (keeps file formatting predictable)
            end += 1
            break
        end += 1

    return java_text[:start] + java_text[end:]


def _strip_clear_all_tracking_in_thread_context(java_text: str) -> str:
    lines = java_text.splitlines(keepends=True)
    out: list[str] = []
    for line in lines:
        if "TFI.clearAllTracking()" in line or "com.syy.taskflowinsight.api.TFI.clearAllTracking()" in line:
            if out and "清理ChangeTracker" in out[-1]:
                out.pop()
            continue
        out.append(line)
    return "".join(out)


def _strip_clear_all_tracking_in_managed_thread_context(java_text: str) -> str:
    lines = java_text.splitlines(keepends=True)
    needle = "com.syy.taskflowinsight.api.TFI.clearAllTracking()"
    call_idx = None
    for i, line in enumerate(lines):
        if needle in line:
            call_idx = i
            break
    if call_idx is None:
        return java_text

    # Prefer removing the full try/catch block that wraps the call.
    start_idx = call_idx
    while start_idx > 0 and "try {" not in lines[start_idx]:
        start_idx -= 1

    if start_idx == 0 and "try {" not in lines[start_idx]:
        # Fallback: remove just the call line.
        return "".join(lines[:call_idx] + lines[call_idx + 1 :])

    # Optionally include the preceding comment line.
    comment_idx = start_idx - 1
    if comment_idx >= 0 and "清理变更追踪" in lines[comment_idx]:
        start_idx = comment_idx

    brace_depth = 0
    started = False
    end_idx = start_idx
    while end_idx < len(lines):
        line = lines[end_idx]
        for ch in line:
            if ch == "{":
                brace_depth += 1
                started = True
            elif ch == "}":
                brace_depth -= 1
        end_idx += 1

        if started and brace_depth == 0:
            # If next non-empty line begins with catch/finally, keep consuming.
            k = end_idx
            while k < len(lines) and lines[k].strip() == "":
                k += 1
            if k < len(lines):
                next_line = lines[k].lstrip()
                if next_line.startswith("catch") or next_line.startswith("finally"):
                    continue
            break

    return "".join(lines[:start_idx] + lines[end_idx:])


def _generate_flow_only_tfi() -> str:
    return """package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.exporter.json.JsonExporter;
import com.syy.taskflowinsight.exporter.map.MapExporter;
import com.syy.taskflowinsight.exporter.text.ConsoleExporter;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * TaskFlowInsight Flow-only 门面。
 *
 * <p>说明：该版本只保留 Session/Task/Stage/Message/Export/Context 能力，
 * 不包含 compare/change-tracking 相关 API。
 */
public final class TFI {

    private static final Logger logger = LoggerFactory.getLogger(TFI.class);

    private static volatile boolean enabled = true;

    private TFI() {
        throw new UnsupportedOperationException("TFI is a utility class");
    }

    public static void enable() {
        enabled = true;
    }

    public static void disable() {
        enabled = false;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void clear() {
        if (!isEnabled()) {
            return;
        }

        try {
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context != null) {
                context.close();
            }
        } catch (Throwable t) {
            logInternalError("Failed to clear context", t);
        }
    }

    public static String startSession(String sessionName) {
        if (!isEnabled()) {
            return null;
        }
        if (sessionName == null || sessionName.trim().isEmpty()) {
            return null;
        }

        try {
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context == null) {
                context = ManagedThreadContext.create(sessionName.trim());
                Session session = context.getCurrentSession();
                return session != null ? session.getSessionId() : null;
            }

            Session current = context.getCurrentSession();
            if (current != null && current.isActive()) {
                context.endSession();
            }
            Session session = context.startSession(sessionName.trim());
            return session != null ? session.getSessionId() : null;
        } catch (Throwable t) {
            logInternalError("Failed to start session: " + sessionName, t);
            return null;
        }
    }

    public static void endSession() {
        if (!isEnabled()) {
            return;
        }

        try {
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context != null) {
                context.endSession();
            }
        } catch (Throwable t) {
            logInternalError("Failed to end session", t);
        }
    }

    public static TaskContext stage(String stageName) {
        return start(stageName);
    }

    public static <T> T stage(String stageName, StageFunction<T> stageFunction) {
        if (!isEnabled() || stageFunction == null) {
            try {
                return stageFunction != null ? stageFunction.apply(NullTaskContext.INSTANCE) : null;
            } catch (Exception e) {
                return null;
            }
        }

        try (TaskContext stage = start(stageName)) {
            return stageFunction.apply(stage);
        } catch (Throwable t) {
            logInternalError("Failed to execute stage: " + stageName, t);
            return null;
        }
    }

    public static TaskContext start(String taskName) {
        if (!isEnabled()) {
            return NullTaskContext.INSTANCE;
        }
        if (taskName == null || taskName.trim().isEmpty()) {
            return NullTaskContext.INSTANCE;
        }

        try {
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context == null) {
                context = ManagedThreadContext.create("auto-session");
            }
            if (context.getCurrentSession() == null) {
                context.startSession("auto-session");
            }

            TaskNode taskNode = context.startTask(taskName.trim());
            return taskNode != null ? new TaskContextImpl(taskNode) : NullTaskContext.INSTANCE;
        } catch (Throwable t) {
            logInternalError("Failed to start task: " + taskName, t);
            return NullTaskContext.INSTANCE;
        }
    }

    public static void stop() {
        if (!isEnabled()) {
            return;
        }

        try {
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context != null) {
                context.endTask();
            }
        } catch (Throwable t) {
            logInternalError("Failed to stop task", t);
        }
    }

    public static void run(String taskName, Runnable runnable) {
        if (!isEnabled() || runnable == null) {
            if (runnable != null) {
                runnable.run();
            }
            return;
        }

        try (TaskContext context = start(taskName)) {
            runnable.run();
        } catch (Throwable t) {
            logInternalError("Failed to run task: " + taskName, t);
        }
    }

    public static <T> T call(String taskName, Callable<T> callable) {
        if (!isEnabled() || callable == null) {
            try {
                return callable != null ? callable.call() : null;
            } catch (Exception e) {
                return null;
            }
        }

        try (TaskContext context = start(taskName)) {
            return callable.call();
        } catch (Throwable t) {
            logInternalError("Failed to call task: " + taskName, t);
            return null;
        }
    }

    public static void message(String content, MessageType messageType) {
        if (!isEnabled() || content == null || content.trim().isEmpty() || messageType == null) {
            return;
        }

        try {
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context != null && context.getCurrentTask() != null) {
                context.getCurrentTask().addMessage(content.trim(), messageType);
            }
        } catch (Throwable t) {
            logInternalError("Failed to record message with type", t);
        }
    }

    public static void message(String content, String customLabel) {
        if (!isEnabled() || content == null || content.trim().isEmpty() ||
            customLabel == null || customLabel.trim().isEmpty()) {
            return;
        }

        try {
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context != null && context.getCurrentTask() != null) {
                context.getCurrentTask().addMessage(content.trim(), customLabel.trim());
            }
        } catch (Throwable t) {
            logInternalError("Failed to record message with custom label", t);
        }
    }

    public static void error(String content) {
        message(content, MessageType.ALERT);
    }

    public static void error(String content, Throwable throwable) {
        if (!isEnabled()) {
            return;
        }

        try {
            String errorMessage = content;
            if (throwable != null) {
                errorMessage = content + " - " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
            }
            message(errorMessage, MessageType.ALERT);
        } catch (Throwable t) {
            logInternalError("Failed to record error with exception", t);
        }
    }

    public static Session getCurrentSession() {
        if (!isEnabled()) {
            return null;
        }

        try {
            ManagedThreadContext context = ManagedThreadContext.current();
            return context != null ? context.getCurrentSession() : null;
        } catch (Throwable t) {
            logInternalError("Failed to get current session", t);
            return null;
        }
    }

    public static TaskNode getCurrentTask() {
        if (!isEnabled()) {
            return null;
        }

        try {
            ManagedThreadContext context = ManagedThreadContext.current();
            return context != null ? context.getCurrentTask() : null;
        } catch (Throwable t) {
            logInternalError("Failed to get current task", t);
            return null;
        }
    }

    public static List<TaskNode> getTaskStack() {
        if (!isEnabled()) {
            return List.of();
        }

        try {
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context == null) {
                return List.of();
            }

            List<TaskNode> stack = new ArrayList<>();
            TaskNode current = context.getCurrentTask();
            while (current != null) {
                stack.add(0, current);
                current = current.getParent();
            }
            return List.copyOf(stack);
        } catch (Throwable t) {
            logInternalError("Failed to get task stack", t);
            return List.of();
        }
    }

    public static void exportToConsole() {
        exportToConsole(false);
    }

    public static boolean exportToConsole(boolean showTimestamp) {
        if (!isEnabled()) {
            return false;
        }

        try {
            Session session = getCurrentSession();
            if (session == null) {
                return false;
            }

            ConsoleExporter exporter = new ConsoleExporter();
            if (showTimestamp) {
                exporter.print(session);
            } else {
                exporter.printSimple(session);
            }
            return true;
        } catch (Throwable t) {
            logInternalError("Failed to export to console", t);
            return false;
        }
    }

    public static String exportToJson() {
        if (!isEnabled()) {
            return "{}";
        }

        try {
            Session session = getCurrentSession();
            if (session == null) {
                return "{}";
            }
            return new JsonExporter().export(session);
        } catch (Throwable t) {
            logInternalError("Failed to export to JSON", t);
            return "{}";
        }
    }

    public static Map<String, Object> exportToMap() {
        if (!isEnabled()) {
            return Map.of();
        }

        try {
            Session session = getCurrentSession();
            return session != null ? MapExporter.export(session) : Map.of();
        } catch (Throwable t) {
            logInternalError("Failed to export to Map", t);
            return Map.of();
        }
    }

    private static void logInternalError(String message, Throwable t) {
        try {
            logger.warn(message + ": {}", t.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug(message, t);
            }
        } catch (Throwable ignored) {
            // Never throw from facade
        }
    }
}
"""


def _write_text(path: Path, content: str, *, overwrite: bool, dry_run: bool) -> bool:
    if path.exists() and not overwrite:
        print(f"SKIP  {path} (exists; use --overwrite)")
        return False
    if dry_run:
        print(f"WRITE {path}")
        return True

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    print(f"WRITE {path}")
    return True


def _copy_file(src: Path, dst: Path, *, overwrite: bool, dry_run: bool) -> bool:
    if dst.exists() and not overwrite:
        print(f"SKIP  {dst} (exists; use --overwrite)")
        return False
    if dry_run:
        print(f"COPY  {src} -> {dst}")
        return True

    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, dst)
    print(f"COPY  {src} -> {dst}")
    return True


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Copy TaskFlowInsight Flow-only sources into another Spring Boot project.\n"
            "This strips compare/change-tracking by omitting related packages and patching a few core files."
        )
    )
    parser.add_argument(
        "--target",
        required=True,
        help="Target project root (the directory that contains src/main/java).",
    )
    parser.add_argument(
        "--overwrite",
        action="store_true",
        help="Overwrite existing files under target.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Print actions without writing/copying files.",
    )

    args = parser.parse_args()

    repo_root = _find_repo_root(Path.cwd())
    target_root = Path(args.target).expanduser().resolve()

    sources = _collect_sources(repo_root)
    copied = 0
    written = 0

    for src in sources:
        rel = src.relative_to(repo_root)
        dst = target_root / rel

        rel_str = rel.as_posix()
        if rel_str == "src/main/java/com/syy/taskflowinsight/context/SafeContextManager.java":
            text = src.read_text(encoding="utf-8")
            text = _remove_method_block(text, "public void configureFromTfiConfig(")
            written += int(_write_text(dst, text, overwrite=args.overwrite, dry_run=args.dry_run))
            continue

        if rel_str in [
            "src/main/java/com/syy/taskflowinsight/context/ManagedThreadContext.java",
            "src/main/java/com/syy/taskflowinsight/context/ThreadContext.java",
        ]:
            text = src.read_text(encoding="utf-8")
            if rel_str.endswith("/ManagedThreadContext.java"):
                text = _strip_clear_all_tracking_in_managed_thread_context(text)
            else:
                text = _strip_clear_all_tracking_in_thread_context(text)
            written += int(_write_text(dst, text, overwrite=args.overwrite, dry_run=args.dry_run))
            continue

        copied += int(_copy_file(src, dst, overwrite=args.overwrite, dry_run=args.dry_run))

    # Generate Flow-only TFI facade (replaces original mixed facade).
    tfi_dst = target_root / "src/main/java/com/syy/taskflowinsight/api/TFI.java"
    written += int(
        _write_text(tfi_dst, _generate_flow_only_tfi(), overwrite=args.overwrite, dry_run=args.dry_run)
    )

    print()
    print(f"Done. copied={copied}, written={written}")
    print(f"Target: {target_root}")
    print()
    print("Notes:")
    print("- This is Flow-only; compare/change-tracking packages are intentionally not copied.")
    print("- Ensure your target project has dependencies for: slf4j, lombok (if enabled), jackson-annotations.")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
