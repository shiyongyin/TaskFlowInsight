## 1) SYSTEM
你是资深 Java 开发工程师与项目管理者。请完成 M2‑M0 Backlog 清理与未决项拍板：收敛 OPEN QUESTIONS、生成决策记录、提出后续 issue 建议。

## 2) CONTEXT & SOURCES（务必具体）
- 合并卡：../cards-final/*.md（每张卡尾部含“OPEN QUESTIONS & ACTIONS”）
- 阶段提示词：../prompts/PROMPT-PHASE-*.md（核对差异与建议）
- 代码（只读校验）：src/main/java/**, src/test/java/**

## 3) GOALS
- 业务目标：清零未决项，并输出可执行的后续任务清单（若确需进入 M1）。
- 技术目标：逐卡收集 OPEN QUESTIONS，形成“决策结论/实施策略/责任人/时限”，并落为 issue 模板或文档。

## 4) SCOPE
- In Scope：
  - [ ] 汇总所有卡片的 OPEN QUESTIONS & ACTIONS
  - [ ] 对每一项给出“结论/建议/影响范围/实施时机（M0 or M1）/责任人/截止日”
  - [ ] 形成 issue 建议列表（标题/描述/路径/验收标准）
- Out of Scope：
  - [ ] 改动生产代码（除非为修复明显缺陷）

## 5) EXECUTION PLAN
1. 解析：读取 `../cards-final/*.md`，提取“OPEN QUESTIONS & ACTIONS”各条目。
2. 归并：按主题归并常见问题（如 valueType/valueKind 填充范围、反射缓存上限、WeakReference vs API 传递 after）。
3. 拍板：对每个条目给出“推荐决策 + 理由 + 影响范围 + 时机（M0/M1）”。
4. 生成：输出两份 Markdown：
   - Backlog-DECISIONS.md（决策记录与拍板清单）
   - Backlog-ISSUES.md（建议创建的 issue 列表，含验收标准）

## 6) DELIVERABLES（输出必须包含）
- Backlog-DECISIONS.md：
  - 表头：主题 | 决策 | 理由 | 影响范围 | 时机 | 责任人 | 截止日 | 证据路径
- Backlog-ISSUES.md：
  - 表头：标题 | 描述 | 关联路径 | 验收标准 | 优先级 | 里程碑（M0/M1）

## 7) ACCEPTANCE（核对清单，默认空）
- [ ] OPEN QUESTIONS 已全部纳入 Backlog-DECISIONS.md
- [ ] issue 建议清单完整可执行
- [ ] 不存在遗漏或与现有代码/卡片冲突的条目

## 8) RISKS & MITIGATIONS
- 文档与代码脱节 → 以现有代码为准并在“差异与建议”记录修订意见
- 过度堆积到 M1 → 给出轻重缓急与边界收敛建议

## 9) OUTPUT（一次性回传）
- Backlog-DECISIONS.md 与 Backlog-ISSUES.md 两份文件的完整内容
- 每个决策条目的证据路径（所依据的卡片或代码路径#符号）

