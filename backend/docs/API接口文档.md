# 好好说 · 后端接口文档（backend-v4）

> 文档版本：v4.0 ｜ 对应代码：`E:\server-hk\backend-v4`
> 服务标题：`好好说 Backend Demo` ｜ 应用版本：`0.2.0`
> 框架：FastAPI + Pydantic v2 ｜ 接口风格：REST / JSON

---

## 目录

1. [服务总览](#1-服务总览)
2. [通用约定](#2-通用约定)
3. [接口明细](#3-接口明细)
   - [GET /health](#31-get-health)
   - [POST /api/extract](#32-post-apiextract)
   - [POST /api/verify](#33-post-apiverify)
   - [POST /api/card](#34-post-apicard)
4. [数据结构定义](#4-数据结构定义)
5. [内部处理流程](#5-内部处理流程)
6. [环境变量配置](#6-环境变量配置)
7. [curl 调用示例](#7-curl-调用示例)
8. [常见问题排查](#8-常见问题排查)

---

## 1. 服务总览

### 1.1 产品流程

```
输入（公众号图文 / 公众号视频 / 文字 / 图片截图）
   │
   ▼
POST /api/extract  ──► 提取 1~5 条可核验健康主张（claims）
   │
   ▼
POST /api/verify  ──► 双源搜索证据 + 生成核验报告（verdict / risk / sources / 沟通方案）
   │
   ▼
POST /api/card   ──► 生成给长辈的「安心核验卡」（大字短句文案）
```

前端标准调用链：`extract → verify → card`（与 `test.py` 的端到端测试一致）。

### 1.2 运行模式

| 模式 | 触发条件 | 行为 |
|---|---|---|
| **真实模式** | `.env` 中配置了 `LUNA_BASE_URL + LUNA_API_KEY` 且（`TAVILY_API_KEY` 或 `EXA_API_KEY`）任一配置 | 走真实 LLM + 真实搜索 |
| **离线 mock 模式** | 上述密钥缺失 | 自动降级为确定性启发式提供者，接口结构完全一致，返回内容带「离线演示」标记，`/health` 中 `mock_mode: true` |

> 双搜索源中只要有一个真实配置（Exa 或 Tavily），搜索环节即视为真实模式（`mock_mode` 为 false 的前提是 luna 也配了）。

### 1.3 快速开始

```bash
python -m venv .venv && source .venv/Scripts/activate   # Windows Git Bash
pip install -r requirements.txt
cp .env.example .env                                    # 填入密钥（不填也能跑，自动 mock）
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

- Swagger 交互文档：`http://<host>:8000/docs`
- 健康检查：`http://<host>:8000/health`

---

## 2. 通用约定

### 2.1 Base URL

本地开发：`http://127.0.0.1:8000`；部署后替换为服务器地址。无统一路径前缀（`/health` 与 `/api/*` 平级）。

### 2.2 请求约定

- 所有接口 `Content-Type: application/json`（`/health` 为 GET 无请求体）。
- CORS 已全放开（`allow_origins=["*"]`、任意方法/头），Web 端可直接跨域调用。
- 可选请求头 `X-Request-ID`：用于链路追踪；不传则服务端自动生成 32 位 hex（`uuid4().hex`，截断至 128 字符）。
- 字符编码：UTF-8；中文内容直接传入 JSON 即可。

### 2.3 响应约定

- **成功**：直接返回业务对象（见各接口章节），HTTP 200。
- **所有响应**（成功与失败）都带响应头 `X-Request-ID`，与请求的 request_id 对应，用于排查问题。
- **失败**：HTTP 状态码（4xx/5xx）+ 统一错误结构：

```json
{
  "code": "错误码",
  "message": "给用户看的错误提示（中文）",
  "request_id": "本次请求 ID，排查用",
  "detail": "补充说明（部分错误场景才有此字段）"
}
```

> `detail` 存在时与 `message` 内容相同（业务错误），或为固定文案（校验错误为 `"请求参数校验失败"`）。500 类错误无 `detail`。

### 2.4 错误码总表

| HTTP | code | 触发接口 | 场景说明 |
|---|---|---|---|
| 422 | `REQUEST_VALIDATION_ERROR` | 全部 | 请求体不满足 Pydantic 校验：缺字段、字段类型错误、`content` 为空、URL 非法、图片 base64 前缀不符、`claim` 长度 <2 或 >2000 等 |
| 422 | `WECHAT_CONTENT_UNREADABLE` | /api/extract | 公众号内容无法读取：非 `mp.weixin.qq.com` 域名、页面命中访问验证页、文章既无正文又无视频 |
| 503 | `VIDEO_PROCESSING_UNAVAILABLE` | /api/extract | 视频画面无法处理：服务器未安装 FFmpeg、抽帧失败或超时 |
| 502 | `CONTENT_ANALYSIS_UNAVAILABLE` | /api/extract | 内容解析不可用：LLM 调用失败、模型未返回合法 JSON、**离线模式下提交图片** |
| 502 | `VERIFICATION_UNAVAILABLE` | /api/verify | 核验不可用：核验模型调用失败，或 Exa 与 Tavily 两个真实搜索源同时失败 |
| 502 | `CARD_GENERATION_UNAVAILABLE` | /api/card | 卡片生成模型调用失败 |
| 500 | `INTERNAL_SERVER_ERROR` | 全部 | 未捕获的服务端异常（兜底） |

---

## 3. 接口明细

---

### 3.1 GET /health

服务健康检查 + 运行模式探测。

**请求**：无参数。

**响应字段**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `status` | string | 固定 `"ok"` |
| `ffmpeg_available` | boolean | 服务器是否安装了 FFmpeg（视频抽帧依赖） |
| `luna_configured` | boolean | 是否配置了 LLM 密钥（LUNA_BASE_URL + LUNA_API_KEY） |
| `tavily_configured` | boolean | 是否配置了 Tavily 搜索密钥 |
| `exa_configured` | boolean | 是否配置了 Exa 搜索密钥 |
| `mock_mode` | boolean | `true` = 当前为离线 mock 模式 |
| `model_routes` | object | 各环节实际生效的提供者路由（见下表） |

`model_routes` 说明：

| key | 值（真实模式） | 值（mock 模式） | 含义 |
|---|---|---|---|
| `text` | `LunaProvider` | `MockLunaProvider` | 文本解析所用 LLM 提供者 |
| `image` | `LunaProvider` | `MockLunaProvider` | 图片解析所用提供者 |
| `wechat_article` | `LunaProvider` | `MockLunaProvider` | 公众号图文解析所用提供者 |
| `wechat_video` | `extract_frames -> LunaProvider` | `extract_frames -> MockLunaProvider` | 视频抽帧 → 视觉模型 |
| `verification_search` | `exa+tavily` / `exa` / `tavily` | `mock` | 核验搜索实际生效的搜索源组合 |

**样例 ① 真实模式（Exa + Tavily 双源）**：

```json
{
  "status": "ok",
  "ffmpeg_available": true,
  "luna_configured": true,
  "tavily_configured": true,
  "exa_configured": true,
  "mock_mode": false,
  "model_routes": {
    "text": "LunaProvider",
    "image": "LunaProvider",
    "wechat_article": "LunaProvider",
    "wechat_video": "extract_frames -> LunaProvider",
    "verification_search": "exa+tavily"
  }
}
```

**样例 ② 离线 mock 模式（未配置任何密钥）**：

```json
{
  "status": "ok",
  "ffmpeg_available": false,
  "luna_configured": false,
  "tavily_configured": false,
  "exa_configured": false,
  "mock_mode": true,
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

### 3.2 POST /api/extract

提取输入内容中的健康主张。支持 4 种输入类型：公众号图文链接、公众号视频链接、纯文本、图片截图。

**请求体 `ExtractRequest`**：

| 字段 | 类型 | 必填 | 校验规则 |
|---|---|---|---|
| `type` | string (enum) | 是 | `wechat_url` / `text` / `image` |
| `content` | string | 是 | 最小长度 1。按 `type` 校验见下表 |

`content` 按类型校验：

| type | content 内容 | 校验规则 |
|---|---|---|
| `wechat_url` | 公众号文章链接 | 必须是合法 URL（HttpUrl 校验）；需为 `https://mp.weixin.qq.com/` 域名（否则 422） |
| `text` | 文章/消息正文 | 无额外格式要求 |
| `image` | 图片 Base64 或 Data URL | 必须以 `data:image/`、`iVBOR`（PNG base64 魔数）或 `/9j/`（JPEG base64 魔数）开头 |

**请求样例**：

```json
{
  "type": "text",
  "content": "震惊！隔夜菜一定会致癌，专家紧急提醒：赶紧倒掉，为了孩子和父母的健康，马上转给你爱的人！"
}
```

**响应 `ClaimExtraction`**（HTTP 200）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `claim` | string | **默认主张**：模型判断潜在伤害最高的主张，兼容旧客户端直接用 |
| `claims` | array\<ClaimCandidate\> | 全部候选主张（1~5 条），按潜在伤害从高到低排序 |
| `original_evidence` | array\<string\> | 支撑默认主张的原文原句摘录 |
| `patterns` | array\<string\> | 识别出的谣言套路，枚举：`夸大因果` / `恐惧驱动` / `冒用权威` / `伪科学术语` / `情感绑架` / `制造稀缺` / `否定现代医学` |
| `topic_summary` | string | 2~3 句内容主题概括 |
| `search_keywords` | array\<string\> | 3~5 个检索关键词（供下游搜索直接使用） |
| `audience` | string | 目标受众判断（如"中老年慢性病患者"），无则空串 |
| `emotional_tone` | string | 情感基调（焦虑/温情/恐吓），无则空串 |
| `visual_notes` | string | 视频/图片的关键视觉信息（如冒用台标、专家形象），无则空串 |
| `article_title` | string \| null | 公众号文章标题（仅 wechat 输入类型有值，否则 null） |
| `article_author` | string \| null | 公众号名称/作者（仅 wechat 输入类型有值，否则 null） |
| `source_kind` | string \| null | 输入归类：`text` / `image` / `wechat_article` / `wechat_video` |
| `video_id` | string \| null | 微信视频 ID（`wxv_...`，仅视频文章有值） |

`ClaimCandidate` 结构：

| 字段 | 类型 | 说明 |
|---|---|---|
| `claim` | string | 主张文本（长度 2~2000） |
| `evidence` | string | 支撑该主张的原文证据（模型阶段有值；mock 视频模式可能为空） |
| `risk_hint` | string | 风险提示：`low` / `medium` / `high`，仅用于候选排序 |

**样例 ① type=text（真实模式）**：

```json
{
  "claim": "隔夜菜一定会致癌",
  "claims": [
    {
      "claim": "隔夜菜一定会致癌",
      "evidence": "震惊！隔夜菜一定会致癌，专家紧急提醒：赶紧倒掉，为了孩子和父母的健康，马上转给你爱的人！",
      "risk_hint": "high"
    },
    {
      "claim": "隔夜菜中亚硝酸盐含量会急剧升高",
      "evidence": "……网传隔夜菜放一晚亚硝酸盐翻倍……",
      "risk_hint": "medium"
    }
  ],
  "original_evidence": [
    "震惊！隔夜菜一定会致癌，专家紧急提醒：赶紧倒掉，为了孩子和父母的健康，马上转给你爱的人！"
  ],
  "patterns": ["恐惧驱动", "夸大因果", "情感绑架"],
  "topic_summary": "该内容以惊悚标题传播「隔夜菜致癌」的说法，并借专家名义与亲情话术推动转发，属于典型的健康谣言传播套路。",
  "search_keywords": ["隔夜菜", "亚硝酸盐", "致癌", "食品安全", "国家卫健委"],
  "audience": "中老年人群，尤其关心家人健康的家长",
  "emotional_tone": "恐吓",
  "visual_notes": "",
  "article_title": null,
  "article_author": null,
  "source_kind": "text",
  "video_id": null
}
```

**样例 ② type=wechat_url（公众号图文文章，真实模式）**：

```json
{
  "type": "wechat_url",
  "content": "https://mp.weixin.qq.com/s/UcGLoLyd6vaROx4j18tnkg"
}
```

```json
{
  "claim": "喝醋可以软化血管预防心血管疾病",
  "claims": [
    {
      "claim": "喝醋可以软化血管预防心血管疾病",
      "evidence": "每天一杯醋，血管软下来，坚持一个月血压都稳了……",
      "risk_hint": "medium"
    }
  ],
  "original_evidence": ["每天一杯醋，血管软下来，坚持一个月血压都稳了……"],
  "patterns": ["夸大因果", "伪科学术语"],
  "topic_summary": "文章宣称喝醋能软化血管、预防心血管疾病，用夸张表述吸引中老年读者。",
  "search_keywords": ["喝醋", "软化血管", "心血管", "科学依据"],
  "audience": "中老年人群",
  "emotional_tone": "温情",
  "visual_notes": "",
  "article_title": "每天一杯醋，血管软下来！",
  "article_author": "某某健康小课堂",
  "source_kind": "wechat_article",
  "video_id": null
}
```

> 说明：图文文章会清洗 `#js_content` 正文后交给文本模型；`article_title` 取自页面 `og:title`，`article_author` 取自 `og:article:author` / `profile_nickname`。正文超过 12000 字符的部分会被截断。

**样例 ③ type=wechat_url（公众号内嵌视频文章，真实模式）**：

```json
{
  "type": "wechat_url",
  "content": "https://mp.weixin.qq.com/s/xxxx"
}
```

```json
{
  "claim": "每天吃一把黑芝麻就能白发变黑",
  "claims": [
    {
      "claim": "每天吃一把黑芝麻就能白发变黑",
      "evidence": "视频字幕：坚持吃黑芝麻三个月，白发一根一根变黑",
      "risk_hint": "medium"
    }
  ],
  "original_evidence": ["视频字幕：坚持吃黑芝麻三个月，白发一根一根变黑"],
  "patterns": ["夸大因果", "冒用权威"],
  "topic_summary": "视频以养生博主形象宣称吃黑芝麻可使白发转黑，缺乏科学依据。",
  "search_keywords": ["黑芝麻", "白发", "转黑", "营养学"],
  "audience": "关注白发问题的中老年人群",
  "emotional_tone": "温情",
  "visual_notes": "视频中出现穿着白大褂的「专家」形象",
  "article_title": "黑芝麻这样吃，白发变黑发！",
  "article_author": "某某养生堂",
  "source_kind": "wechat_video",
  "video_id": "wxv_3765284629181046785"
}
```

> 视频文章流程：解析页面中 `mpvideo.qpic.cn` 的 mp4 直链（按清晰度 f10004>f10002>f10104>f10102 排序）→ 下载（上限 80MB，校验 content-type 与大小）→ FFmpeg 全片均匀抽帧（宽 1280，上限默认 24 帧）→ 视觉模型逐帧识别。

**样例 ④ type=image（真实模式）**（`content` 为 base64，此处省略长串）：

```json
{
  "type": "image",
  "content": "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQoJCQoKDBYMDBYtFh4WLf/aAAwDAQACEQMRAAEAAAD/2Q=="
}
```

```json
{
  "claim": "微波炉加热食物会产生致癌物",
  "claims": [
    {
      "claim": "微波炉加热食物会产生致癌物",
      "evidence": "图片文字：微波炉加热会改变食物分子结构，产生致癌物质",
      "risk_hint": "high"
    }
  ],
  "original_evidence": ["微波炉加热会改变食物分子结构，产生致癌物质"],
  "patterns": ["恐惧驱动", "伪科学术语"],
  "topic_summary": "图片内容宣称微波炉加热会产生致癌物，属于常见家电谣言。",
  "search_keywords": ["微波炉", "加热", "致癌", "食品安全"],
  "audience": "家庭主妇及中老年人群",
  "emotional_tone": "恐吓",
  "visual_notes": "",
  "article_title": null,
  "article_author": null,
  "source_kind": "image",
  "video_id": null
}
```

**样例 ⑤ type=text（离线 mock 模式）**——返回内容带演示特征：

```json
{
  "claim": "隔夜菜一定会致癌，专家紧急提醒：赶紧倒掉，为了孩子和父母的健康，马上转给你爱的人",
  "claims": [
    {
      "claim": "隔夜菜一定会致癌，专家紧急提醒：赶紧倒掉，为了孩子和父母的健康，马上转给你爱的人",
      "evidence": "隔夜菜一定会致癌，专家紧急提醒：赶紧倒掉，为了孩子和父母的健康，马上转给你爱的人",
      "risk_hint": "high"
    }
  ],
  "original_evidence": ["隔夜菜一定会致癌，专家紧急提醒：赶紧倒掉，为了孩子和父母的健康，马上转给你爱的人"],
  "patterns": ["夸大因果", "恐惧驱动", "冒用权威", "情感绑架"],
  "topic_summary": "",
  "search_keywords": [],
  "audience": "",
  "emotional_tone": "",
  "visual_notes": "",
  "article_title": null,
  "article_author": null,
  "source_kind": "text",
  "video_id": null
}
```

> mock 模式按关键词启发式提取：命中「一定/致癌/专家/为了孩子/父母」等关键词即触发对应 patterns；`risk_hint` 含「致癌/中毒/停药/伤身」时为 high，否则 medium。**注意：离线模式提交 type=image 会直接返回 502**（`CONTENT_ANALYSIS_UNAVAILABLE`）。

**错误样例 ① 请求校验失败（422）**——`content` 为空 / `type` 非法 / URL 格式错误：

```json
// 请求：{ "type": "wechat_url", "content": "不是网址" }
{
  "code": "REQUEST_VALIDATION_ERROR",
  "message": "请求内容格式不正确，请检查后重试。",
  "request_id": "9f2c1b3a4d5e6f708192a3b4c5d6e7f8",
  "detail": "请求参数校验失败"
}
```

**错误样例 ② 公众号内容不可读（422）**——非微信域名 / 命中访问验证页 / 无正文无视频：

```json
// 请求：{ "type": "wechat_url", "content": "https://example.com/article" }
{
  "code": "WECHAT_CONTENT_UNREADABLE",
  "message": "无法读取该公众号内容，请检查链接或复制正文后重试。",
  "request_id": "9f2c1b3a4d5e6f708192a3b4c5d6e7f8",
  "detail": "无法读取该公众号内容，请检查链接或复制正文后重试。"
}
```

**错误样例 ③ 视频处理不可用（503）**——服务器未装 FFmpeg / 抽帧失败：

```json
{
  "code": "VIDEO_PROCESSING_UNAVAILABLE",
  "message": "视频画面暂时无法处理，请稍后重试或改用文字内容。",
  "request_id": "9f2c1b3a4d5e6f708192a3b4c5d6e7f8",
  "detail": "视频画面暂时无法处理，请稍后重试或改用文字内容。"
}
```

**错误样例 ④ 内容解析不可用（502）**——LLM 调用失败 / 离线模式传图片：

```json
{
  "code": "CONTENT_ANALYSIS_UNAVAILABLE",
  "message": "健康信息解析服务暂时不可用，请稍后重试。",
  "request_id": "9f2c1b3a4d5e6f708192a3b4c5d6e7f8",
  "detail": "健康信息解析服务暂时不可用，请稍后重试。"
}
```

---

### 3.3 POST /api/verify

核验主张：并行调用真实搜索源检索权威证据 → 合并去重排序 → LLM 生成核验结论与沟通方案。

**请求体 `VerifyRequest`**：

| 字段 | 类型 | 必填 | 校验规则 |
|---|---|---|---|
| `claim` | string | 是 | 待核验主张，长度 2~2000 |
| `target` | string | 否（默认 `"elder"`） | 沟通对象描述，最大 100 字符（如 `mother` / `father` / `elder`） |
| `relationship_state` | string | 否（默认 `"normal"`） | 关系状态 + 用户补充信息（用药、病史、群聊背景等完整描述），最大 2000 字符 |
| `search_keywords` | array\<string\> | 否（默认 `[]`） | 检索关键词，最多 10 个。**当前版本预留字段，服务端暂未消费**（前端仍可传，不影响结果） |

**请求样例**：

```json
{
  "claim": "隔夜菜一定会致癌",
  "target": "mother",
  "relationship_state": "妈妈最近总在家族群转发这类养生文章，之前因为别的事吵过一架，关系有点紧张",
  "search_keywords": ["隔夜菜", "亚硝酸盐", "致癌"]
}
```

**响应 `VerificationReport`**（HTTP 200）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `claim` | string | 回显待核验主张 |
| `verdict` | string (enum) | `credible`（基本可信）/ `misleading`（误导/谣言）/ `uncertain`（证据不足） |
| `risk_level` | string (enum) | `low` / `medium` / `high`（综合健康风险、财产风险、情绪/扩散风险三纬度） |
| `summary` | string | 核验摘要（含判断理由、权威来源引用、必要时标注"暂时无法判断"） |
| `patterns` | array\<string\> | 识别出的谣言套路（同 extract） |
| `sources` | array\<SourceItem\> | **可追溯证据来源**（带权威分级，见下） |
| `communication` | object\<Communication\> | 给用户的沟通方案（渠道 + 话术） |
| `medical_notice` | string | 医疗免责声明（固定文案，模型可追加后缀） |

`SourceItem` 结构：

| 字段 | 类型 | 说明 |
|---|---|---|
| `title` | string | 来源标题（截断至 300 字符） |
| `url` | string | 来源链接（唯一去重键） |
| `publisher` | string | 发布域名（如 `www.nhc.gov.cn`） |
| `evidence` | string | 证据摘录（搜索返回的高亮片段/摘要，截断至 3000 字符） |
| `authority_level` | string (enum) | `institution`（政府/权威机构）/ `official_factcheck`（官方辟谣平台）/ `research`（医学研究数据库）/ `authoritative_media`（权威媒体）/ `other`（其他来源） |
| `authority_label` | string | 权威等级中文标签（如 `政府 / 权威机构`） |
| `published_at` | string | 发布日期（截断至 40 字符，可能为空字符串） |

`Communication` 结构：

| 字段 | 类型 | 说明 |
|---|---|---|
| `channel` | string (enum) | 建议沟通渠道：`private_chat`（私下沟通）/ `family_group`（家族群公开回应）/ `via_relative`（请其他亲属转达）/ `no_reply`（暂不回应） |
| `reason` | string | 选择该渠道的理由 |
| `opening` | string | 共情开场白（第一句严禁出现"假/错/谣言/别转/你不懂/被骗"） |
| `fact` | string | 用生活类比讲清楚的事实 |
| `suggestion` | string | 给长辈台阶和替代行动的具体建议 |

**样例 ① 结论 credible（真实模式，信息基本可信）**：

```json
{
  "claim": "老年人接种流感疫苗可以降低重症风险",
  "verdict": "credible",
  "risk_level": "low",
  "summary": "国家卫健委及中国疾控中心官网均发布老年人接种流感疫苗的建议，多个权威信源一致支持该说法。接种流感疫苗可显著降低老年人群流感相关并发症与住院风险，但无法替代专业人士建议，具体接种请咨询医生。",
  "patterns": [],
  "sources": [
    {
      "title": "中国疾控中心：老年人流感疫苗预防接种建议",
      "url": "https://www.chinacdc.cn/jkzt/...",
      "publisher": "www.chinacdc.cn",
      "evidence": "老年人是流感重症高风险人群，接种流感疫苗是预防流感及其严重并发症的有效手段……",
      "authority_level": "institution",
      "authority_label": "政府 / 权威机构",
      "published_at": "2025-09-10T00:00:00.000Z"
    },
    {
      "title": "国家卫健委：流感疫苗常见问题解答",
      "url": "https://www.nhc.gov.cn/wjw/...",
      "publisher": "www.nhc.gov.cn",
      "evidence": "建议 60 岁及以上老年人每年接种流感疫苗……",
      "authority_level": "institution",
      "authority_label": "政府 / 权威机构",
      "published_at": "2025-08-01T00:00:00.000Z"
    }
  ],
  "communication": {
    "channel": "private_chat",
    "reason": "信息本身可信，无需公开辟谣，私下肯定老人的信息渠道即可",
    "opening": "妈，您看到的这个信息是真的，您平时关注这些挺好的。",
    "fact": "疾控中心也建议咱们老年人每年打流感疫苗，确实能少生病。",
    "suggestion": "下次社区组织接种的时候，我陪您一起去咨询一下医生。"
  },
  "medical_notice": "内容仅供健康信息核验，不能替代医生诊断。 \n 由 好好说 生成."
}
```

**样例 ② 结论 misleading（真实模式，谣言）**：

```json
{
  "claim": "隔夜菜一定会致癌",
  "verdict": "misleading",
  "risk_level": "high",
  "summary": "该说法把「隔夜菜在特定条件下亚硝酸盐可能升高」这一有限风险夸大为「一定会致癌」。国家卫健委等权威机构明确表示，合理存放的隔夜菜亚硝酸盐含量远低于安全限量，不会致癌。照做（整盘倒掉）虽不伤身，但若轻信此类说法延误正规饮食指导仍有健康风险。",
  "patterns": ["恐惧驱动", "夸大因果"],
  "sources": [
    {
      "title": "国家卫健委：关于隔夜菜亚硝酸盐的科普",
      "url": "https://www.nhc.gov.cn/...",
      "publisher": "www.nhc.gov.cn",
      "evidence": "隔夜菜中亚硝酸盐含量通常低于国家食品安全标准限量，正常存放不会导致中毒或致癌……",
      "authority_level": "institution",
      "authority_label": "政府 / 权威机构",
      "published_at": "2025-06-18T00:00:00.000Z"
    },
    {
      "title": "中国互联网联合辟谣平台：隔夜菜致癌系谣言",
      "url": "https://www.piyao.org.cn/...",
      "publisher": "www.piyao.org.cn",
      "evidence": "所谓「隔夜菜致癌」的说法已被多次辟谣……",
      "authority_level": "official_factcheck",
      "authority_label": "官方辟谣平台",
      "published_at": "2025-05-22T00:00:00.000Z"
    }
  ],
  "communication": {
    "channel": "private_chat",
    "reason": "关系紧张时避免公开反驳，私下沟通给长辈留面子",
    "opening": "妈，我知道您是担心我们的身体，看到这种消息肯定第一时间想提醒我们。",
    "fact": "其实隔夜菜只要放冰箱、吃之前热透，是安全的，不会致癌。",
    "suggestion": "以后咱们看到这种消息，我先帮您查一查权威的说法再转，您看行不行？"
  },
  "medical_notice": "内容仅供健康信息核验，不能替代医生诊断。 \n 由 好好说 生成."
}
```

**样例 ③ 结论 uncertain（真实模式，证据不足）**——搜索信源不足 2 条时后端强制置为 uncertain：

```json
{
  "claim": "每天喝三七粉可以通血管",
  "verdict": "uncertain",
  "risk_level": "medium",
  "summary": "暂时无法判断。目前检索到的权威信源不足，且说法本身缺乏高质量临床研究支撑，无法替代专业人士建议。",
  "patterns": ["夸大因果"],
  "sources": [
    {
      "title": "关于三七的药品说明书信息",
      "url": "https://www.nmpa.gov.cn/...",
      "publisher": "www.nmpa.gov.cn",
      "evidence": "三七为中药饮片，应遵医嘱使用……",
      "authority_level": "institution",
      "authority_label": "政府 / 权威机构",
      "published_at": "2025-03-01T00:00:00.000Z"
    }
  ],
  "communication": {
    "channel": "private_chat",
    "reason": "证据不足且风险中等，先私下沟通，不建议公开回应",
    "opening": "爸，您对这个挺上心的，我帮您查了一些资料。",
    "fact": "目前还没有足够的权威研究能证明三七粉能通血管。",
    "suggestion": "要不等下次体检的时候，咱们一起问问医生？"
  },
  "medical_notice": "内容仅供健康信息核验，不能替代医生诊断。 \n 由 好好说 生成."
}
```

> 后端强制规则（不可被模型覆盖）：
> 1. 可靠信源少于 2 条 → `verdict` 强制为 `uncertain`，summary 含"暂时无法判断"；
> 2. 信源互相矛盾 → 模型必须输出 `uncertain`；
> 3. 涉医疗/用药/疾病诊断内容 → summary 必须标注"无法替代专业人士建议"；
> 4. `sources` 使用**搜索阶段的原始对象**回填（模型只负责选 URL，不能改写证据/权威等级/日期），模型未选来源时保留搜索得到的前 5 条。

**样例 ④ 离线 mock 模式**：

```json
{
  "claim": "隔夜菜一定会致癌，专家紧急提醒：赶紧倒掉，为了孩子和父母的健康，马上转给你爱的人",
  "verdict": "misleading",
  "risk_level": "medium",
  "summary": "（离线演示结果）该说法包含「夸大因果、恐惧驱动、冒用权威、情感绑架」特征，建议参考权威机构来源进一步核实。",
  "patterns": ["夸大因果", "恐惧驱动", "冒用权威", "情感绑架"],
  "sources": [
    {
      "title": "关于「隔夜菜一定会致癌」的科学解读（演示数据）",
      "url": "https://www.nhc.gov.cn/example/demo-evidence",
      "publisher": "nhc.gov.cn",
      "evidence": "离线演示占位证据：真实模式下此处为 Tavily 检索到的权威机构网页摘要。",
      "authority_level": "other",
      "authority_label": "其他来源",
      "published_at": ""
    },
    {
      "title": "健康谣言识别与辟谣指南（演示数据）",
      "url": "https://www.chinacdc.cn/example/demo-guide",
      "publisher": "chinacdc.cn",
      "evidence": "离线演示占位证据：配置 TAVILY_API_KEY 后将替换为真实检索结果。",
      "authority_level": "other",
      "authority_label": "其他来源",
      "published_at": ""
    }
  ],
  "communication": {
    "channel": "private_chat",
    "reason": "根据关系状态选择沟通渠道（离线演示）",
    "opening": "妈，我知道你是担心我们的健康，看到这种消息肯定想第一时间提醒我们。",
    "fact": "「隔夜菜一定会致癌，专家紧急提醒：赶紧倒掉，为了孩子和父母的健康，马上转给你爱的人」这个说法把一些有条件的风险说得太绝对了。",
    "suggestion": "我把权威机构的说明找给你看，以后咱们先核实再转发，好不好？"
  },
  "medical_notice": "内容仅供健康信息核验，不能替代医生诊断。"
}
```

> mock 模式规则：关键词命中 patterns → `misleading`，否则 `uncertain`；含「恐惧驱动」→ risk=`medium`，否则 `low`；沟通渠道按 `relationship_state` + risk 规则选择（`recent_conflict`/`distant` 且 high → `via_relative`，high → `family_group`，否则 `private_chat`）。

**错误样例（502）**：

```json
{
  "code": "VERIFICATION_UNAVAILABLE",
  "message": "健康信息核验服务暂时不可用，请稍后重试。",
  "request_id": "9f2c1b3a4d5e6f708192a3b4c5d6e7f8",
  "detail": "健康信息核验服务暂时不可用，请稍后重试。"
}
```

---

### 3.4 POST /api/card

生成「安心核验卡」：给长辈看的大字短句文案（每句 ≤25 字，不出现"假/错/谣言/你不懂/被骗"，术语换成生活类比）。

**请求体 `CardRequest`**：

| 字段 | 类型 | 必填 | 校验规则 |
|---|---|---|---|
| `claim` | string | 是 | 主张，长度 2~2000 |
| `verdict` | string | 否（默认 `"uncertain"`） | 核验结论（credible / misleading / uncertain），最大 30 字符 |
| `risk_level` | string | 否（默认 `"low"`） | 风险等级（low / medium / high），最大 30 字符 |
| `summary` | string | 否（默认 `""`） | 核验摘要，最大 5000 字符 |
| `target` | string | 否（默认 `"elder"`） | 沟通对象（用于 elder 版称呼），最大 100 字符 |
| `style` | string (enum) | 否（默认 `"elder"`） | `elder`（给长辈的安心核验卡）/ `group_notice`（群公告版） |
| `sources` | array\<SourceItem\> | 否（默认 `[]`） | 可引用来源（通常传 verify 返回的 sources） |

**请求样例 ① elder 版**（前端标准用法：把 verify 的结果原样传进来）：

```json
{
  "claim": "隔夜菜一定会致癌",
  "verdict": "misleading",
  "risk_level": "high",
  "summary": "该说法把「隔夜菜在特定条件下亚硝酸盐可能升高」这一有限风险夸大为「一定会致癌」……",
  "target": "mother",
  "style": "elder",
  "sources": [
    {
      "title": "国家卫健委：关于隔夜菜亚硝酸盐的科普",
      "url": "https://www.nhc.gov.cn/...",
      "publisher": "www.nhc.gov.cn",
      "evidence": "……",
      "authority_level": "institution",
      "authority_label": "政府 / 权威机构",
      "published_at": "2025-06-18T00:00:00.000Z"
    }
  ]
}
```

**响应 `VerificationCard`**（HTTP 200）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `title` | string | 卡片标题（如 `安心核验卡：这条说法不太准确`） |
| `greeting` | string | 共情开场白（elder 版带称呼与亲昵语气；**group_notice 版固定为空字符串**） |
| `fact` | string | 一句话事实 |
| `suggestion` | string | 具体可以怎么做 |
| `self_verify` | string | 长辈可亲手验证的方法；没有合适则空字符串 |
| `closing` | string | 收尾安抚、维护长辈面子 |
| `sources` | array\<SourceItem\> | 卡片引用来源（**只会保留请求中传入过的 URL**，模型不得新增来源） |
| `medical_notice` | string | 固定：`内容仅供健康信息核验，不能替代医生诊断。` |

**响应样例 ① elder 版（真实模式）**：

```json
{
  "title": "安心核验卡：这条说法不太准确",
  "greeting": "妈，您这条消息我看到啦，您总是第一时间替我们操心。",
  "fact": "隔夜菜只要放冰箱、吃之前热透，就还是安全的，不会致癌。",
  "suggestion": "真正要注意的是剩菜及时放冰箱、吃之前彻底加热，不用整盘倒掉。",
  "self_verify": "您可以去「国家卫健委」官网搜「隔夜菜」，第一条就能看到官方说法。",
  "closing": "以后看到这种消息先别急着转，我帮您查一查，好吗？",
  "sources": [
    {
      "title": "国家卫健委：关于隔夜菜亚硝酸盐的科普",
      "url": "https://www.nhc.gov.cn/...",
      "publisher": "www.nhc.gov.cn",
      "evidence": "隔夜菜中亚硝酸盐含量通常低于国家食品安全标准限量……",
      "authority_level": "institution",
      "authority_label": "政府 / 权威机构",
      "published_at": "2025-06-18T00:00:00.000Z"
    }
  ],
  "medical_notice": "内容仅供健康信息核验，不能替代医生诊断。"
}
```

**请求样例 ② group_notice 版**：

```json
{
  "claim": "隔夜菜一定会致癌",
  "verdict": "misleading",
  "risk_level": "high",
  "summary": "……",
  "style": "group_notice"
}
```

**响应样例 ② group_notice 版（真实模式）**——`greeting` 为空串，语气中性：

```json
{
  "title": "健康信息小科普：这条说法不太准确",
  "greeting": "",
  "fact": "隔夜菜只要合理存放并彻底加热，就不会致癌。",
  "suggestion": "大家看到类似说法，可以先查一查权威机构发布的信息再决定要不要转发。",
  "self_verify": "可在国家卫健委官网搜索「隔夜菜」查看官方说明。",
  "closing": "一起维护靠谱的群环境，谢谢理解。",
  "sources": [],
  "medical_notice": "内容仅供健康信息核验，不能替代医生诊断。"
}
```

> 注意：模型可能返回空 `sources`（未引用任何传入来源），此时为 `[]`，前端可直接展示；若引用则只会包含请求中传入过的 URL。

**响应样例 ③ 离线 mock 模式（elder 版）**：

```json
{
  "title": "安心核验卡：这条说法不太准确",
  "greeting": "妈，我知道你发这个是为了我们好，怕我们吃出不健康。",
  "fact": "「隔夜菜一定会致癌」这个说法把风险说得太绝对了。",
  "suggestion": "真正要注意的是剩菜及时放冰箱、吃之前彻底加热，不用整盘倒掉。",
  "self_verify": "",
  "closing": "以后看到这种消息先别急着转，我帮你查一查，好吗？",
  "sources": [
    {
      "title": "关于「隔夜菜一定会致癌」的科学解读（演示数据）",
      "url": "https://www.nhc.gov.cn/example/demo-evidence",
      "publisher": "nhc.gov.cn",
      "evidence": "离线演示占位证据：真实模式下此处为 Tavily 检索到的权威机构网页摘要。",
      "authority_level": "other",
      "authority_label": "其他来源",
      "published_at": ""
    }
  ],
  "medical_notice": "内容仅供健康信息核验，不能替代医生诊断。"
}
```

**错误样例（502）**：

```json
{
  "code": "CARD_GENERATION_UNAVAILABLE",
  "message": "安心核验卡暂时无法生成，请稍后重试。",
  "request_id": "9f2c1b3a4d5e6f708192a3b4c5d6e7f8",
  "detail": "安心核验卡暂时无法生成，请稍后重试。"
}
```

---

## 4. 数据结构定义

### 4.1 枚举类型

| 枚举 | 取值 | 用途 |
|---|---|---|
| `SourceType` | `wechat_url` / `text` / `image` | extract 输入类型 |
| `risk_hint` | `low` / `medium` / `high` | 候选主张风险提示（仅排序用） |
| `verdict` | `credible` / `misleading` / `uncertain` | 核验结论 |
| `risk_level` | `low` / `medium` / `high` | 核验风险等级 |
| `channel` | `private_chat` / `family_group` / `via_relative` / `no_reply` | 沟通渠道 |
| `authority_level` | `institution` / `official_factcheck` / `research` / `authoritative_media` / `other` | 来源权威等级 |
| `style` | `elder` / `group_notice` | 卡片风格 |
| `source_kind`（响应字段） | `text` / `image` / `wechat_article` / `wechat_video` | 输入归类（响应内字符串，非枚举校验） |

### 4.2 请求模型

| 模型 | 字段 | 类型 | 约束 |
|---|---|---|---|
| `ExtractRequest` | `type` | SourceType | 必填 |
| | `content` | string | min=1；按 type 二次校验（见 3.2） |
| `VerifyRequest` | `claim` | string | 必填，min=2，max=2000 |
| | `target` | string | 默认 `"elder"`，max=100 |
| | `relationship_state` | string | 默认 `"normal"`，max=2000 |
| | `search_keywords` | list\<string\> | 默认 `[]`，max=10（预留未消费） |
| `CardRequest` | `claim` | string | 必填，min=2，max=2000 |
| | `verdict` | string | 默认 `"uncertain"`，max=30 |
| | `risk_level` | string | 默认 `"low"`，max=30 |
| | `summary` | string | 默认 `""`，max=5000 |
| | `target` | string | 默认 `"elder"`，max=100 |
| | `style` | CardStyle | 默认 `elder` |
| | `sources` | list\<SourceItem\> | 默认 `[]` |

### 4.3 响应模型

| 模型 | 字段 | 类型 | 默认值 |
|---|---|---|---|
| `ClaimCandidate` | `claim` | string | 必填（min=2, max=2000） |
| | `evidence` | string | `""` |
| | `risk_hint` | string | `"medium"` |
| `ClaimExtraction` | `claim` | string | 必填（= claims[0].claim） |
| | `claims` | list\<ClaimCandidate\> | `[]` |
| | `original_evidence` | list\<string\> | `[]` |
| | `patterns` | list\<string\> | `[]` |
| | `topic_summary` | string | `""` |
| | `search_keywords` | list\<string\> | `[]` |
| | `audience` | string | `""` |
| | `emotional_tone` | string | `""` |
| | `visual_notes` | string | `""` |
| | `article_title` | string \| null | `null` |
| | `article_author` | string \| null | `null` |
| | `source_kind` | string \| null | `null` |
| | `video_id` | string \| null | `null` |
| `SourceItem` | `title` | string | 必填 |
| | `url` | string | 必填（去重主键） |
| | `publisher` | string | `""` |
| | `evidence` | string | `""` |
| | `authority_level` | string | `"other"` |
| | `authority_label` | string | `"其他来源"` |
| | `published_at` | string | `""` |
| `Communication` | `channel` | string | 必填 |
| | `reason` | string | 必填 |
| | `opening` | string | 必填 |
| | `fact` | string | 必填 |
| | `suggestion` | string | 必填 |
| `VerificationReport` | `claim` | string | 必填 |
| | `verdict` | string | 必填 |
| | `risk_level` | string | 必填 |
| | `summary` | string | 必填 |
| | `patterns` | list\<string\> | `[]` |
| | `sources` | list\<SourceItem\> | `[]` |
| | `communication` | Communication | 必填 |
| | `medical_notice` | string | `"内容仅供健康信息核验，不能替代医生诊断。"` |
| `VerificationCard` | `title` | string | 必填 |
| | `greeting` | string | 必填（group_notice 为 `""`） |
| | `fact` | string | 必填 |
| | `suggestion` | string | 必填 |
| | `self_verify` | string | `""` |
| | `closing` | string | 必填 |
| | `sources` | list\<SourceItem\> | `[]` |
| | `medical_notice` | string | `"内容仅供健康信息核验，不能替代医生诊断。"` |

---

## 5. 内部处理流程

### 5.1 extract 输入分发（InputDispatcher）

```
type=text      ──► source_kind=text            ──► 文本模型 extract_from_text
type=image     ──► source_kind=image           ──► 视觉模型 extract_from_image
type=wechat_url ─► 公众号 HTML 解析
                     ├─ 含 mpvideo.qpic.cn 视频直链 ──► source_kind=wechat_video
                     │     └─ 下载(mp4, ≤80MB) → FFmpeg 全片均匀抽帧 → 视觉模型 extract_from_frames
                     └─ 仅图文 ──► source_kind=wechat_article
                           └─ 清洗 #js_content 正文(≤12000字符) → 文本模型 extract_from_text
```

关键行为：
- 微信公众号解析只支持 `https://mp.weixin.qq.com/` 域名（重试 2 次，第二次加 `hhs_retry=1` 参数绕过临时拦截页）。
- 文章同时含图文与视频时，**视频优先**（有可下载的 mp4 直链即走视频管线）。
- 视频抽帧策略：ffprobe 取时长 → 长视频按 `MAX_FRAMES/时长` 均匀铺满全片（保证模型看到完整内容），短视频按 `FRAME_INTERVAL_SECONDS` 最小间隔抽帧；帧宽缩放至 1280。
- 视频直链按清晰度选择：`.f10004` > `.f10002` > `.f10104` > `.f10102`。

### 5.2 verify 双源搜索（Exa + Tavily）

```
claim
  ├──► Exa.search (3 组 query：科学依据/医学证据/辟谣)
  │       ├─ 第 1 轮：权威域名白名单（EXA_RESTRICT_DOMAINS=true 时）
  │       ├─ 白名单命中 < 2 条 ──► 第 2 轮：放开域名限制 + category=news 补搜
  │       └─ 只取近 3 年内容（startPublishedDate）
  └──► Tavily.search (同样 3 组 query，include_domains 白名单 + 服务端域名二次校验)

  合并：按 URL 去重 → 权威等级排序（institution > official_factcheck > research > authoritative_media > other）
        → 取前 6 条作为可引用来源
```

- 单源失败不整体失败：记日志，用另一源结果。
- **两个真实源都失败才抛错（→ 502）**；双 mock 时返回演示来源。
- 权威域名白名单（TRUSTED_DOMAINS）：gov.cn / nhc.gov.cn / chinacdc.cn / samr.gov.cn / who.int / cdc.gov / nih.gov / pubmed.ncbi.nlm.nih.gov / piyao.org.cn / people.com.cn / xinhuanet.com / cctv.com / chinanews.com.cn。

### 5.3 verify 证据追溯（后端强覆盖）

模型只负责从候选来源里**挑选** URL 并生成文案；`sources` 字段最终由后端用**搜索阶段的原始 SourceItem** 回填（保留完整 evidence / authority_level / published_at，防止模型改写或丢失）。模型未返回来源时保留搜索得到的前 5 条。信源 <2 条强制 `uncertain`。

### 5.4 card 来源白名单

`card.sources` 最终只保留「请求中传入过的 URL」对应的来源，模型不得引入新来源。

### 5.5 LLM 调用降级

`_chat_json` 优先使用 `response_format: {"type": "json_object"}`；遇到不支持该参数的 OpenAI 兼容渠道时**自动降级重试一次**（无该参数）。返回内容不是合法 JSON 时抛 `ProviderError`。

---

## 6. 环境变量配置

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `LUNA_BASE_URL` | `""` | OpenAI 兼容 LLM 端点（如 Qwen3-VL-Flash 渠道） |
| `LUNA_API_KEY` | `""` | LLM 密钥 |
| `LUNA_MODEL` | `gpt-5.6-luna` | 模型名 |
| `TAVILY_API_KEY` | `""` | Tavily 检索密钥 |
| `EXA_API_KEY` | `""` | Exa 检索密钥 |
| `EXA_RESTRICT_DOMAINS` | `"true"` | true=Exa 只搜权威域名白名单（命中不足时自动放开补搜）；false=直接全量搜索 |
| `MAX_VIDEO_MB` | `80` | 视频下载大小上限（MB） |
| `MAX_FRAMES` | `24` | 视频抽帧上限（全片均匀采样） |
| `FRAME_INTERVAL_SECONDS` | `2` | 短视频最小抽帧间隔（秒，支持小数） |

> 项目内 `.env` 实际配置：`MAX_FRAMES=100`、`FRAME_INTERVAL_SECONDS=0.5`（覆盖默认值）。

---

## 7. curl 调用示例

```bash
# 健康检查
curl http://127.0.0.1:8000/health

# 提取主张（文本）
curl -X POST http://127.0.0.1:8000/api/extract \
  -H "Content-Type: application/json" \
  -d '{"type":"text","content":"震惊！隔夜菜一定会致癌，专家紧急提醒：赶紧倒掉，为了孩子和父母的健康，马上转给你爱的人！"}'

# 提取主张（公众号链接）
curl -X POST http://127.0.0.1:8000/api/extract \
  -H "Content-Type: application/json" \
  -d '{"type":"wechat_url","content":"https://mp.weixin.qq.com/s/UcGLoLyd6vaROx4j18tnkg"}'

# 核验
curl -X POST http://127.0.0.1:8000/api/verify \
  -H "Content-Type: application/json" \
  -d '{"claim":"隔夜菜一定会致癌","target":"mother","relationship_state":"recent_conflict"}'

# 生成安心核验卡（elder 版）
curl -X POST http://127.0.0.1:8000/api/card \
  -H "Content-Type: application/json" \
  -d '{"claim":"隔夜菜一定会致癌","verdict":"misleading","risk_level":"high","summary":"……","target":"mother","style":"elder","sources":[]}'

# 生成安心核验卡（群公告版）
curl -X POST http://127.0.0.1:8000/api/card \
  -H "Content-Type: application/json" \
  -d '{"claim":"隔夜菜一定会致癌","verdict":"misleading","summary":"……","style":"group_notice"}'

# 带请求 ID 调用（链路追踪）
curl -X POST http://127.0.0.1:8000/api/verify \
  -H "Content-Type: application/json" -H "X-Request-ID: my-trace-001" \
  -d '{"claim":"喝醋能软化血管"}'
```

---

## 8. 常见问题排查

| 现象 | 可能原因 | 处理 |
|---|---|---|
| `/health` 显示 `mock_mode: true` | 密钥未配置 | 配置 LUNA + Tavily/Exa 密钥后重启；演示阶段可继续用 mock |
| extract 图片返回 502 | 离线模式不支持图片 | 配置视觉模型密钥，或改用 text/wechat_url |
| extract 视频返回 503 | FFmpeg 未安装/抽帧失败 | 安装 FFmpeg 并加入 PATH；检查视频是否损坏 |
| extract 公众号链接返回 422 WECHAT_CONTENT_UNREADABLE | 非微信域名 / 命中验证页 / 无正文无视频 | 换链接重试（服务端会重试 1 次）；复制正文用 type=text |
| verify 返回 `verdict: uncertain` | 可靠信源 <2 条或互相矛盾（后端强制） | 属正常结果，换更具体的主张词可提升搜索命中率 |
| card 返回 `sources: []` | 模型未引用传入来源（合法行为） | 前端兜底展示「来源」为空或展示 verify 的 sources |
| 返回 502 且日志有 `model.call.failed` | LLM 渠道不可用/超时 | 检查 LUNA_BASE_URL 连通性与密钥余额；确认模型支持 json_object 输出（后端会自动降级一次） |
| 响应慢（视频类） | 下载+抽帧+视觉模型串行 | 属预期；视频文章耗时明显高于图文，前端需加 loading 态并放宽超时 |
