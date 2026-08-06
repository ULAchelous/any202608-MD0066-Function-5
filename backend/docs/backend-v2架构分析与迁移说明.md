# backend-v2 架构分析与“好好说”迁移说明

## 1. 参考项目的后端设计

### 1.1 输入解析与 dispatcher

参考项目先通过 `parse_input()` 统一解析文本、图片、链接等输入，再由 dispatcher 决定后续处理路径。公众号文章会先读取网页；若识别到视频地址，先调用视频模型预处理成文字，再进入主 Agent；无视频时直接使用网页正文。

其价值在于把“输入是什么”与“该调用什么模型”分开，业务编排不再混杂 HTML 解析细节。

### 1.2 模型路由

参考项目按能力划分模型：

- 普通文本：主文本模型；
- 图片：视觉模型；
- 视频：视频模型；
- 无有效密钥：Mock Provider。

路由不是由客户端指定模型，而是由后端根据解析后的媒体类型选择。

### 1.3 工具注册与调用循环

工具以 OpenAI tools schema 注册，通过统一 `execute_tool(name, args)` 执行。Agent 在最大轮次内读取 `tool_calls`、调用工具、把结果作为 `role=tool` 消息送回模型。FIRST 阶段还要求达到最少搜索次数，避免模型未检索就直接下结论。

### 1.4 日志和可观测性

参考项目使用 `structlog` 输出事件型键值日志，记录阶段、模型、工具、耗时和异常，而不是只打印自由文本。API 层同时返回或维护 `elapsed_ms`、`tool_log`，方便排查一次对话中调用过哪些能力。

### 1.5 Agent 编排

其 FIRST / progress 编排适用于多轮聊天：客户端携带完整上下文，服务端尽量无状态；Prompt 外置为 Markdown；orchestrator 决定阶段与工具策略。

## 2. 哪些逻辑已迁移

### 2.1 新增统一输入分发层

新增：

- `app/parsers/dispatcher.py`
- `app/parsers/__init__.py`

统一路由为：

```text
text            -> 文本解析模型
image           -> 视觉解析模型
wechat_article  -> #js_content 正文 -> 文本解析模型
wechat_video    -> 下载视频 -> FFmpeg 均匀抽帧 -> Qwen 视觉解析模型
```

公众号文章同时存在正文和视频时，视频路由优先，保留现有 Qwen 切片处理。

### 2.2 新增结构化日志

新增：

- `app/utils/logging.py`
- API request_id 中间件
- service 阶段日志
- Luna 模型调用日志
- Tavily 搜索批次日志

主要事件包括：

- `request.started` / `request.completed` / `request.failed`
- `input.dispatched`
- `media.download.completed`
- `media.frames.completed`
- `model.call.started` / `model.call.completed` / `model.call.failed`
- `tool.call.started` / `tool.call.completed`
- `search.batch.started` / `search.batch.completed` / `search.batch.failed`
- `verification.completed`
- `card.completed`

日志字段覆盖 request_id、route、source_kind、model、tool、elapsed_ms、result_count、error_category 和 Mock 标识。

隐私约束：不记录公众号正文、用户主张原文、图片 Base64、家庭关系描述、病史或用药原文；只记录类型、长度、数量和状态。

### 2.3 显式展示模型路由

`/health` 新增 `model_routes`，用于 Demo 环境确认文本、图片、公众号图文、公众号视频和权威搜索当前分别由哪个 Provider 处理。原有健康检查字段保持不变。

### 2.4 工具调用追踪

当前 Tavily 搜索仍保留现有 Provider 和三组并行查询，不改 `/api/verify` 请求或响应；服务层将其作为 `authority_search` 工具记录开始、完成、耗时和结果数。这样获得参考项目 tool log 的可观测性，同时不引入不必要的通用 Agent 工具循环。

## 3. 刻意没有照搬的部分

- 不改成参考项目 `/v1/chat`、FIRST/progress API，避免破坏现有前端与接口契约。
- 不使用 BeautifulSoup 整页截取前 5000 字；继续使用当前 `#js_content` / `.rich_media_content` 精确正文提取，减少推荐阅读、脚本和页面噪声。
- 不用参考项目的视频模型替换当前 Qwen；继续下载、FFmpeg 全片均匀抽帧，再交给 Qwen 视觉理解。
- 暂不让模型自行决定是否搜索。健康核验固定先执行三组 Tavily 查询，这比通用 tool loop 更可控，也更容易审计。
- 不把 `tool_log`、`elapsed_ms` 加入现有响应 schema；先输出到结构化日志和健康检查，避免前端重新适配。
- 没有新增 `structlog` 依赖；采用标准库 logging 输出 JSON，降低黑客松环境安装风险。

## 4. 当前接口兼容性

以下接口路径和主体结构均保持不变：

- `POST /api/extract`
- `POST /api/verify`
- `POST /api/card`
- `GET /health`

唯一新增的 HTTP 行为是响应头 `X-Request-ID`；若客户端传入该头，后端会回传同一值，否则自动生成。

## 5. 后续生产化建议

1. 将日志接入 Loki、ELK 或云日志，并设置健康信息脱敏与保存期限。
2. 为外部模型和搜索增加熔断、退避重试、配额、成本和成功率指标。
3. 视频处理进入任务队列，避免长请求占用 Web Worker。
4. 为公众号页面与权威搜索增加短期缓存，但不得长期缓存用户敏感输入。
5. 生产环境收紧 CORS，加入鉴权、限流、SSRF 防护和请求体大小上限。
6. FFmpeg 作为部署镜像的明确依赖；当前机器未安装时，公众号视频接口仍会返回 503。
