# tfi-flow/1 Schema

`tfi-flow/1` 是 `tfi-kernel` 的机器输出格式。一个完成或活动 Session 对应一个 JSON object；字段顺序固定，
但消费方必须按字段名读取并容忍后续新增字段。既有字段不会改变语义或类型。

## Session

| 字段 | 类型 | 语义 |
|---|---|---|
| `schema` | string | 固定为 `tfi-flow/1`。 |
| `sessionId` | string | 当前业务流转的唯一标识。 |
| `parentSessionId` | string/null | `capture().wrap(...)` 创建的链接子 Session 指向父 Session；根为 null。 |
| `name` | string | 调用方在 `begin` 或父链接中给出的业务流名称。 |
| `status` | string | `RUNNING`、`OK`、`ERROR` 或 `ABANDONED`。正常关闭只向 Sink 发布 `OK/ERROR`；`ABANDONED` 会被丢弃并诊断。 |
| `startMs` | integer | Session 开始时的 epoch 毫秒；不得用于计算 duration。 |
| `durMs` | integer | 单调时钟差向下取整到毫秒，并钳制为非负数。 |
| `truncated` | boolean | 任一 `incompleteReasons` 存在时为 true。 |
| `incompleteReasons` | string[] | 去重后按固定声明顺序输出的完整性原因。 |
| `attrs` | object | 根 Stage 句柄写入的 Session 属性；保持首次 key 插入顺序。 |
| `root` | Stage | 与 Session 同名的根阶段。 |

## Stage

| 字段 | 类型 | 语义 |
|---|---|---|
| `name` | string | 阶段名称。 |
| `status` | string | `RUNNING`、`OK`、`ERROR` 或 `ABANDONED`；后代 ERROR 会向祖先归并。 |
| `startMs` | integer | Stage 开始时的 epoch 毫秒。 |
| `durMs` | integer | 基于单调时钟的非负持续毫秒。 |
| `attrs` | object | Stage 属性；保持首次 key 插入顺序。 |
| `records` | Record[] | 按接纳顺序排列的事实。 |
| `children` | Stage[] | 按创建顺序排列的子阶段。 |

## Record

| 字段 | 类型 | 语义 |
|---|---|---|
| `type` | string | `MESSAGE`、`CHANGE` 或 `ERROR`。 |
| `code` | string | 稳定机器码；机器消费必须以 `type + code + data` 判定语义。 |
| `text` | string/null | 仅供人读，不得解析该字段恢复机器事实。 |
| `data` | object | 已在接纳时深复制的 JSON-like 数据。 |
| `atMs` | integer | Record 接纳时的 epoch 毫秒。 |

内置机器码：

| code | type | data |
|---|---|---|
| `MANUAL_MESSAGE` | `MESSAGE` | `{}` |
| `MANUAL_CHANGE` | `CHANGE` | `path`、`before`、`after`，顺序固定 |
| `MANUAL_ERROR` | `ERROR` | 无 Throwable 时 `{}`；有 Throwable 时为 `errorType`、`errorMessage` |
| `CALLBACK_ERROR` | `ERROR` | `errorType`、`errorMessage` |
| `KERNEL_NESTED_BEGIN` | `MESSAGE` | `{}` |

## 完整性原因

固定顺序为：`STAGE_LIMIT`、`STACK_DEPTH_LIMIT`、`SESSION_BYTES_LIMIT`、`RECORD_BYTES_LIMIT`、
`ATTR_LIMIT`、`STRUCTURED_DATA_INVALID`、`INPUT_TOO_LARGE`、`DISABLED_MID_SESSION`、
`NON_LIFO_CLOSE`、`RECORDING_FAILURE`。

出现 reason 表示输出仍是合法 JSON，但不能被当作完整业务事实。`SESSION_BYTES_LIMIT` 首次出现后，本 Session
不再接受后续 Stage、Record 或 attr；其他 reason 只拒绝当前候选或对应能力。

## 确定性与数值

- Session、Stage、Record 字段顺序按本文表格固定；List 保持调用顺序。
- generic `data` 的 Map key 按 UTF-8 bytes 无符号升序；手工 change 和 Throwable data 使用上表固定顺序。
- `BigDecimal` 使用 `toPlainString()` 并保留 scale；有限浮点数在接纳时固化为十进制。
- 字符串按 JSON escaping 后的 UTF-8 bytes 计入预算；未配对 surrogate 会拒绝整条候选。
- 不支持的业务对象固化为 `{"representation":"UNSUPPORTED","type":"<binary-class-name>"}`，不会调用业务
  `toString()`。

## 数据边界

`attrs`、`text`、`data` 和 `errorMessage` 都可能包含敏感业务数据。内核默认没有 Sink，不自动外发；调用方读取
current renderer 或装配 Sink 后，负责脱敏、授权、传输和留存策略。分析多个线程的业务流时，以
`sessionId/parentSessionId` 聚合独立 Session，不假设 Stage 树跨线程合并。
