# 好好说 · API 接口文档（backend_V5.0_preview）

> 文档版本：V5.0 Preview  
> 对应代码：`E:\server-hk\backend_V5.0_preview`  
> FastAPI 应用版本：`5.0.0-preview`  
> 基线版本：`backend-v4`，旧接口全部保留，仅做向后兼容的增量升级  
> 逆向依据：`backend-v4` 实际路由/模型/服务实现 + `好好说_Mobile_v2/index.html` 实际调用与最终双卡片导出流程

---

## 1. V5.0 Preview 变更摘要

### 1.1 新增能力

新增 `POST /api/card/image`：

1. 接收与 `/api/card` 相同的核验结果参数；
2. 通过 `style` 指定 A 卡或 B 卡；
3. 生成对应卡片文案；
4. 服务端渲染 PNG；
5. 返回不带前缀的 `image_base64`，Android 解码后即可保存/分享；
6. 可选同时返回 `data:image/png;base64,...` 格式的 `data_url`。

卡片映射：

| Web 端卡片 | API `style` | 文件名 | 用途 |
|---|---|---|---|
| 版本 A · 私发长辈版 | `elder` | `安心核验卡-A.png` | 带称呼、共情、亲切表达 |
| 版本 B · 群公告版 | `group_notice` | `安心核验卡-B.png` | 中性科普、适合家族群 |

### 1.2 兼容性承诺

以下 v4 路由、方法、请求模型和核心响应字段保持不变：

- `GET /health`
- `POST /api/extract`
- `POST /api/verify`
- `POST /api/card`

`/health` 仅增量增加 `api_version` 和 `card_rendering_available`，不会删除旧字段。

---

## 2. Web 端真实业务流程逆向结论

`好好说_Mobile_v2/index.html` 当前业务调用链如下：

```text
用户输入公众号链接 / 文本 / 图片
  -> POST /api/extract
  -> 用户选择待核验主张
  -> POST /api/verify（首次核验）
  -> 用户补充沟通对象与关系状态
  -> POST /api/verify（带 target + relationship_state，再生成沟通方案）
  -> 并行 POST /api/card
       ├─ style=elder         -> A 私发长辈版
       └─ style=group_notice  -> B 群公告版
  -> 用户选 A/B
  -> html2canvas(scale=2, backgroundColor=#FAF4EA)
  -> 浏览器下载 PNG
```

Web 端当前 API 基址默认为 `https://mb-b.chksz.top`，可在设置中修改；单次请求超时为 90 秒。

V5 Android 推荐把最后两步替换为：

```text
POST /api/card/image（style=elder 或 group_notice）
  -> 读取 image_base64
  -> Base64 解码为 PNG bytes
  -> 按 filename 保存或分享
```

这样 APK 无需 WebView、DOM、CSS 或 html2canvas。

---

## 3. 通用约定

### 3.1 Base URL

本地开发：`http://127.0.0.1:8000`。部署后替换为服务端地址。

### 3.2 请求与响应

- JSON 接口使用 `Content-Type: application/json; charset=utf-8`。
- CORS 当前允许任意来源、方法和请求头。
- 可选请求头：`X-Request-ID`，最大取前 128 字符；未传时服务端生成 UUID hex。
- 所有响应均带 `X-Request-ID` 响应头。
- 成功通常返回 HTTP 200 和业务 JSON。
- 请求体校验失败返回 HTTP 422。

### 3.3 统一错误结构

```json
{
  "code": "REQUEST_VALIDATION_ERROR",
  "message": "请求内容格式不正确，请检查后重试。",
  "request_id": "9f2c1b3a4d5e6f708192a3b4c5d6e7f8",
  "detail": "请求参数校验失败"
}
```

`detail` 仅在部分错误中出现。

### 3.4 错误码

| HTTP | code | 接口 | 说明 |
|---|---|---|---|
| 422 | `REQUEST_VALIDATION_ERROR` | 全部 | 参数缺失、类型错误、长度或枚举不合法 |
| 422 | `WECHAT_CONTENT_UNREADABLE` | `/api/extract` | 公众号链接不可读、域名不支持或正文/视频缺失 |
| 503 | `VIDEO_PROCESSING_UNAVAILABLE` | `/api/extract` | FFmpeg 不可用或视频抽帧失败 |
| 502 | `CONTENT_ANALYSIS_UNAVAILABLE` | `/api/extract` | 内容分析模型不可用 |
| 502 | `VERIFICATION_UNAVAILABLE` | `/api/verify` | 搜索或核验模型不可用 |
| 502 | `CARD_GENERATION_UNAVAILABLE` | `/api/card`、`/api/card/image` | 卡片文案生成不可用 |
| 503 | `CARD_RENDERING_UNAVAILABLE` | `/api/card/image` | 中文字体缺失或 PNG 渲染失败 |
| 500 | `INTERNAL_SERVER_ERROR` | 全部 | 未捕获异常兜底 |

---

## 4. 接口总览

| 方法 | 路径 | 功能 | V5 状态 |
|---|---|---|---|
| GET | `/health` | 健康检查、能力探测 | 保留，增量字段 |
| POST | `/api/extract` | 从文本、图片或公众号内容提取健康主张 | 原样保留 |
| POST | `/api/verify` | 搜索证据并生成核验报告/沟通方案 | 原样保留 |
| POST | `/api/card` | 生成指定风格卡片文案 JSON | 原样保留 |
| POST | `/api/card/image` | 生成指定风格卡片并返回 PNG Base64 | **V5 新增** |

---

## 5. GET /health

### 响应字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `status` | string | 固定 `ok` |
| `ffmpeg_available` | boolean | 视频抽帧能力是否可用 |
| `luna_configured` | boolean | LLM 配置是否完整 |
| `tavily_configured` | boolean | Tavily 是否配置 |
| `exa_configured` | boolean | Exa 是否配置 |
| `mock_mode` | boolean | 是否处于演示/降级模式 |
| `model_routes` | object | 各处理环节当前提供者 |
| `card_rendering_available` | boolean | **V5 新增**，中文字体和 PNG 渲染能力是否可用 |
| `api_version` | string | **V5 新增**，固定 `5.0.0-preview` |

### 示例

```json
{
  "status": "ok",
  "ffmpeg_available": true,
  "luna_configured": false,
  "tavily_configured": false,
  "exa_configured": false,
  "mock_mode": true,
  "card_rendering_available": true,
  "api_version": "5.0.0-preview",
  "model_routes": {
    "text": "MockLunaProvider",
    "image": "MockLunaProvider",
    "wechat_article": "MockLunaProvider",
    "wechat_video": "extract_frames -> MockLunaProvider",
    "verification_search": "mock"
  }
}
```

---

## 6. POST /api/extract

从输入内容提取 1～5 条健康主张。

### 请求 `ExtractRequest`

| 字段 | 类型 | 必填 | 规则 |
|---|---|---|---|
| `type` | enum | 是 | `wechat_url` / `text` / `image` |
| `content` | string | 是 | 最少 1 字符 |

按类型规则：

- `wechat_url`：必须为合法 URL；业务仅支持 `https://mp.weixin.qq.com/`。
- `text`：正文字符串。
- `image`：图片 Data URL，或以 `iVBOR`（PNG）、`/9j/`（JPEG）开头的 Base64。

### 请求示例

```json
{
  "type": "text",
  "content": "震惊！隔夜菜一定会致癌，马上转给家人！"
}
```

### 响应 `ClaimExtraction`

| 字段 | 类型 | 说明 |
|---|---|---|
| `claim` | string | 默认/最高风险主张，兼容旧客户端 |
| `claims` | `ClaimCandidate[]` | 1～5 条候选主张 |
| `original_evidence` | `string[]` | 支撑默认主张的原文 |
| `patterns` | `string[]` | 传播/谣言套路 |
| `topic_summary` | string | 主题摘要 |
| `search_keywords` | `string[]` | 搜索关键词 |
| `audience` | string | 目标受众 |
| `emotional_tone` | string | 情绪基调 |
| `visual_notes` | string | 图片/视频视觉信息 |
| `article_title` | string/null | 公众号标题 |
| `article_author` | string/null | 公众号作者 |
| `source_kind` | string/null | `text` / `image` / `wechat_article` / `wechat_video` |
| `video_id` | string/null | 微信视频 ID |

`ClaimCandidate`：

| 字段 | 类型 | 说明 |
|---|---|---|
| `claim` | string | 主张，2～2000 字符 |
| `evidence` | string | 原文证据 |
| `risk_hint` | string | `low` / `medium` / `high` |

---

## 7. POST /api/verify

核验主张，搜索权威证据并生成沟通方案。

### 请求 `VerifyRequest`

| 字段 | 类型 | 必填 | 规则 |
|---|---|---|---|
| `claim` | string | 是 | 2～2000 字符 |
| `target` | string | 否 | 默认 `elder`，最大 100 字符 |
| `relationship_state` | string | 否 | 默认 `normal`，最大 2000 字符 |
| `search_keywords` | `string[]` | 否 | 最多 10 个；当前保留字段，服务端尚未消费 |

### 请求示例

```json
{
  "claim": "隔夜菜一定会致癌",
  "target": "mother",
  "relationship_state": "最近因健康消息争论过，希望私下温和沟通",
  "search_keywords": ["隔夜菜", "亚硝酸盐", "致癌"]
}
```

### 响应 `VerificationReport`

| 字段 | 类型 | 说明 |
|---|---|---|
| `claim` | string | 回显主张 |
| `verdict` | enum | `credible` / `misleading` / `uncertain` |
| `risk_level` | enum | `low` / `medium` / `high` |
| `summary` | string | 核验摘要 |
| `patterns` | `string[]` | 识别出的套路 |
| `sources` | `SourceItem[]` | 可追溯来源 |
| `communication` | `Communication` | 沟通方案 |
| `medical_notice` | string | 医疗免责声明 |

强制规则：可靠来源少于 2 条时结论强制为 `uncertain`；来源对象由搜索阶段原始结果回填，模型不得改写证据。

---

## 8. POST /api/card

保留的 v4 卡片文案接口，只返回 JSON，不返回图片。

### 请求 `CardRequest`

| 字段 | 类型 | 必填 | 默认/规则 |
|---|---|---|---|
| `claim` | string | 是 | 2～2000 字符 |
| `verdict` | string | 否 | `uncertain`，最大 30 |
| `risk_level` | string | 否 | `low`，最大 30 |
| `summary` | string | 否 | 空字符串，最大 5000 |
| `target` | string | 否 | `elder`，最大 100 |
| `style` | enum | 否 | `elder`；也可为 `group_notice` |
| `sources` | `SourceItem[]` | 否 | 默认 `[]` |

### 响应 `VerificationCard`

| 字段 | 类型 | 说明 |
|---|---|---|
| `title` | string | 标题 |
| `greeting` | string | 共情开场；群公告版为空 |
| `fact` | string | 核心事实 |
| `suggestion` | string | 行动建议 |
| `self_verify` | string | 自查方式，无则为空 |
| `closing` | string | 收尾表达 |
| `sources` | `SourceItem[]` | 引用来源，只能来自请求传入 URL |
| `medical_notice` | string | 医疗免责声明 |

---

## 9. POST /api/card/image（V5 新增）

服务端直接生成指定卡片并返回 PNG Base64。推荐 Android 最终交付环节使用。

### 9.1 请求 `CardImageRequest`

继承 `CardRequest` 的全部字段，并新增：

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `scale` | integer | 否 | `2` | 只允许 1～3；逻辑宽度 375 px，输出宽度=`375 × scale` |
| `include_data_url` | boolean | 否 | `false` | 是否额外返回带 MIME 前缀的 `data_url` |

#### A 卡请求示例

```json
{
  "claim": "隔夜菜一定会致癌",
  "verdict": "misleading",
  "risk_level": "high",
  "summary": "合理冷藏并充分加热的隔夜菜，并非一定致癌。",
  "target": "mother",
  "style": "elder",
  "sources": [],
  "scale": 2,
  "include_data_url": false
}
```

#### B 卡请求示例

只需把 `style` 改为：

```json
{
  "style": "group_notice"
}
```

实际请求仍须包含必填的 `claim`；建议把 `/api/verify` 返回的 `verdict`、`risk_level`、`summary`、`sources` 一并传入。

### 9.2 响应 `CardImageResponse`

| 字段 | 类型 | 说明 |
|---|---|---|
| `card` | `VerificationCard` | 本次实际用于渲染的卡片文案，便于客户端预览/留档 |
| `style` | enum | `elder` 或 `group_notice` |
| `mime_type` | string | 固定 `image/png` |
| `filename` | string | A 卡或 B 卡的建议文件名 |
| `width` | integer | PNG 实际像素宽度 |
| `height` | integer | PNG 实际像素高度，随文案自动扩展 |
| `byte_size` | integer | Base64 解码后的 PNG 字节数 |
| `sha256` | string | PNG 字节 SHA-256，小写十六进制 |
| `image_base64` | string | **纯 Base64，不包含 `data:image/png;base64,` 前缀** |
| `data_url` | string/null | `include_data_url=true` 时返回完整 Data URL，否则 `null` |

### 9.3 响应示例（Base64 已截断）

```json
{
  "card": {
    "title": "安心核验卡：这条说法不太准确",
    "greeting": "妈，我知道您是担心我们的身体。",
    "fact": "合理冷藏并充分加热，并不是一定致癌。",
    "suggestion": "剩菜及时冷藏，食用前彻底加热。",
    "self_verify": "可在国家卫健委网站查看相关科普。",
    "closing": "以后看到类似消息，我们一起查一查。",
    "sources": [],
    "medical_notice": "内容仅供健康信息核验，不能替代医生诊断。"
  },
  "style": "elder",
  "mime_type": "image/png",
  "filename": "安心核验卡-A.png",
  "width": 750,
  "height": 1180,
  "byte_size": 152340,
  "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
  "image_base64": "iVBORw0KGgoAAAANSUhEUgAA...",
  "data_url": null
}
```

### 9.4 Android 保存示例（Kotlin）

```kotlin
val pngBytes = android.util.Base64.decode(
    response.imageBase64,
    android.util.Base64.DEFAULT
)

// 建议先校验 pngBytes.size == response.byteSize；如有需要再校验 SHA-256。
contentResolver.openOutputStream(targetUri)?.use { output ->
    output.write(pngBytes)
}
```

注意：服务端返回的是图片内容，不会替客户端写入手机相册；Android 仍需通过 MediaStore/用户选择的 URI 完成设备侧保存。

### 9.5 curl 示例

```bash
curl -X POST http://127.0.0.1:8000/api/card/image \
  -H "Content-Type: application/json" \
  -H "X-Request-ID: android-card-001" \
  -d '{
    "claim":"隔夜菜一定会致癌",
    "verdict":"misleading",
    "risk_level":"high",
    "summary":"合理冷藏并充分加热的隔夜菜，并非一定致癌。",
    "target":"mother",
    "style":"elder",
    "sources":[],
    "scale":2,
    "include_data_url":false
  }'
```

---

## 10. 公共数据结构

### `SourceItem`

| 字段 | 类型 | 默认/说明 |
|---|---|---|
| `title` | string | 必填 |
| `url` | string | 必填，来源去重键 |
| `publisher` | string | 默认空 |
| `evidence` | string | 默认空 |
| `authority_level` | string | 默认 `other` |
| `authority_label` | string | 默认 `其他来源` |
| `published_at` | string | 默认空 |

### `Communication`

| 字段 | 类型 | 说明 |
|---|---|---|
| `channel` | string | `private_chat` / `family_group` / `via_relative` / `no_reply` |
| `reason` | string | 渠道理由 |
| `opening` | string | 共情开场 |
| `fact` | string | 事实表达 |
| `suggestion` | string | 行动建议 |

---

## 11. 部署与字体配置

新增图片接口依赖 Pillow 和中文字体：

```bash
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Windows 会自动探测微软雅黑/黑体/宋体。Linux 推荐安装 Noto CJK；如自动探测不到，配置：

```text
CARD_FONT_PATH=/path/to/NotoSansCJK-Regular.ttc
CARD_BOLD_FONT_PATH=/path/to/NotoSansCJK-Bold.ttc
```

部署后先检查：

```text
GET /health
card_rendering_available == true
```

---

## 12. 验证结论与边界

本版本的自动验证覆盖：

- v4 标准链路：`health -> extract -> verify -> card(elder) -> card(group_notice)`；
- V5 图片链路：`card/image(elder)` 与 `card/image(group_notice)`；
- Base64 严格解码；
- PNG 魔数校验；
- `byte_size` 和 SHA-256 一致性；
- A/B 文件名与尺寸；
- OpenAPI 中 4 个旧路由继续存在，新路由为纯增量。

未自动执行真实公众号视频、真实 LLM 和真实双搜索源测试，原因是这些路径依赖外部网络、密钥、模型额度及具体公众号页面状态；原实现未被修改。
