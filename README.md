# 重大声名：此项目是一个Vibe项目,作为AIY2026参赛作品提交

# 好好说 · 安心核验（MythBusters）

> **帮长辈核验健康信息，给出有人情味的沟通方案。**
> AIY 2026 黑客松参赛项目 · V5.0 Preview

当长辈在家族群、朋友圈转发"隔夜菜致癌""喝醋能软化血管"这类健康信息时，与其生硬反驳伤感情，不如用 **好好说** 一键核验真相，并生成一张**长辈看得懂、不冒犯的「安心核验卡」**和一套**共情式沟通话术**——先照顾情绪，再讲清事实。

---

## 📌 项目简介

「好好说」是一个面向 **中老年健康谣言辟谣场景** 的全栈应用，包含三个端：

| 端 | 目录 | 技术栈 | 定位 |
|---|---|---|---|
| 🖥️ 后端服务 | [`backend/`](./backend) | Python 3.11+ / FastAPI / FFmpeg / Pydantic | 内容解析 → 抽帧 → AI 主张提取 → 双源权威搜索 → 核验报告与卡片渲染 |
| 📱 Android 客户端 | [`client/`](./client) | Kotlin / Jetpack Compose / OkHttp / Gson | 悬浮窗快捷入口、四步分析流程（提取→核验→沟通→卡片）、卡片保存相册 |
| 🌐 Web 移动端 | [`web-ui/`](./web-ui) | 原生 HTML/CSS/JS · PWA（Service Worker 离线可用） | 零安装的移动端页面：粘贴链接/文字/截图即可核验 |

> 💡 **设计初衷**：输入由后端自动分类（公众号链接 → 解析正文或视频；纯文本 → 直接分析；截图 → 视觉模型），客户端不需要关心具体走哪条管线。

---

## 🧩 核心功能

### 后端业务流水线（五步链路）

```
用户输入（公众号链接 / 纯文本 / 截图 Base64）
    │
    ▼
① 内容解析    公众号URL → 纯 HTTP 解析（标题/作者/正文/内嵌视频直链）
             纯文本 → 直接使用；截图 → Base64 交给视觉模型
    │
    ▼
② 关键帧提取  下载视频(mp4) → ffprobe 测时长 → FFmpeg 全片均匀抽帧（≤24帧）
    │
    ▼
③ 主张提取    视觉模型逐帧读字幕/画面 → 输出 1-5 条健康主张(claims)
             附带风险提示、话术标签、检索关键词
    │
    ▼
④ 搜索核验    Exa + Tavily 双源并行检索权威信源（卫健委/疾控/WHO 等白名单）
             合并去重 → 按权威等级排序取前 6 条 → LLM 生成核验报告
    │
    ▼
⑤ 核验报告     verdict(可信/误导/证据不足) + 风险等级 + 沟通渠道建议
    + 安心核验卡   + 共情式话术 + 可渲染成 PNG 图片卡（A卡给长辈 / B卡群公告）
```

### 三端协同流程

1. **Android 悬浮窗** 或 **Web 页面** 输入信息（微信链接 / 文本 / 截图）
2. 调用 `POST /api/extract` 提取核心主张（含风险提示与误导模式标签）
3. 用户勾选主张 → 调用 `POST /api/verify` 生成核验报告（结论 + 信源 + 风险等级）
4. 填写沟通对象与关系状态 → 再次调用 `/api/verify` 生成"沟通处方"（共情 → 事实 → 建议）
5. 调用 `POST /api/card` 生成安心核验卡文案，Web 端可截图分享，Android 端可 `POST /api/card/image` 直接渲染 PNG 保存相册

---

## 📁 仓库结构

```
any202608-MD0066-Function-5/
├── backend/                  # 🖥️ FastAPI 后端（V5.0 Preview）
│   ├── app/
│   │   ├── main.py           #   应用工厂、5 个 API 路由、错误处理、观测中间件
│   │   ├── config.py         #   .env 配置加载（dataclass Settings）
│   │   ├── schemas.py        #   全部 Pydantic 请求/响应模型与枚举
│   │   ├── service.py        #   DemoService 业务编排（分发→抽帧→主张→搜索→核验→卡片）
│   │   ├── providers.py      #   真实外部服务：Luna(视觉/文本) / Tavily / Exa
│   │   ├── mock_providers.py #   离线演示 Provider（无密钥自动降级）
│   │   ├── wechat_video.py   #   公众号解析（纯标准库 HTMLParser）+ 视频下载
│   │   ├── video_frames.py   #   FFmpeg/ffprobe 全片均匀抽帧
│   │   ├── card_renderer.py  #   Pillow 渲染核验卡 PNG（渐变/圆角/自动换行）
│   │   └── parsers/
│   │       └── dispatcher.py #   InputDispatcher 输入归一化（text/image/wechat_article/wechat_video）
│   ├── docs/                 #   12 份设计/接口/架构文档
│   ├── outputs/              #   测试结果与核验卡样张（安心核验卡-A/B-样张.png）
│   ├── test.py               #   端到端测试入口（CLI 多模式）
│   ├── requirements.txt      #   Python 依赖
│   └── README.md             #   后端独立文档
├── client/                   # 📱 Android 客户端（Kotlin + Compose）
│   ├── app/src/main/java/io/ula/aiy/mb/
│   │   ├── MainActivity.kt           #   主界面（底部导航 + 设置页）
│   │   ├── fw/                       #   悬浮窗前台服务 + Compose UI
│   │   ├── ui/request/               #   四步分析流程（extract/verify/communicate/card）
│   │   ├── utils/                    #   OkHttp 封装 / Base64 / 图片选择 / 内存传递
│   │   └── config/                   #   设置持久化
│   └── build.gradle.kts / gradle/    #   Gradle 8.13 / AGP 8.11.2 / Kotlin 2.0.21
├── web-ui/                   # 🌐 Web 移动端（PWA）
│   ├── index.html            #   单页应用（链接/文字/截图三种输入）
│   ├── sw.js                 #   Service Worker（离线缓存）
│   ├── manifest.webmanifest  #   PWA 清单（"好好说 · 安心核验"）
│   └── icon.svg              #   应用图标
├── tests/                    #   后端单元测试（test_api / test_wechat_video）
├── test.py                   #   后端 e2e 测试（仓库根版本）
├── requirements.txt          #   后端依赖（根目录版本，服务端使用）
├── .env.example              #   环境变量模板（不含真实密钥）
├── .gitignore                #   排除 .env / 缓存 / 视频等
└── README.md                 #   本文件
```

---

## 🚀 快速开始

### 1️⃣ 后端服务（必须先启动，Android/Web 依赖它）

**环境要求**：Python 3.11+、FFmpeg（含 ffprobe）。不需要 Docker 和数据库。

```bash
cd backend
python -m venv .venv
source .venv/Scripts/activate      # Windows Git Bash；Linux/macOS 用 .venv/bin/activate
pip install -r requirements.txt

# 准备配置（复制根目录或 backend 下的模板）
cp ../.env.example .env            # 填入 LUNA_* 与 TAVILY_API_KEY / EXA_API_KEY

# 启动
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

- **交互式 API 文档**：`http://服务器IP:8000/docs`
- **健康检查**：`GET /health`（返回各 Provider 配置状态与 `mock_mode`）

> 🔌 **没有密钥也能跑**：LUNA / TAVILY / EXA 未配置时自动降级为**离线 mock 提供者**，接口结构与真实模式完全一致，结果带"离线演示"标记，适合先联调 UI。

### 2️⃣ Android 客户端

```bash
cd client
# 使用 Android Studio 打开工程，或用命令行构建
./gradlew assembleDebug
```

- 包名：`io.ula.aiy.mb`，minSdk 24 / targetSdk 36
- **后端地址在 `app/src/main/res/values/strings.xml` 的 `net_backend_url` 中配置**（默认 `http://120.79.170.218:7712`）
- 安装后：开启悬浮窗权限 → 全局悬浮球一键发起"输入文本 / 分析链接 / 上传图片"

### 3️⃣ Web 移动端

```bash
# 纯静态页面，任意静态服务器托管即可
cd web-ui
python -m http.server 8080
# 浏览器访问 http://localhost:8080
```

- **后端地址可在页面「设置」中修改**（默认 `https://mb-b.chksz.top`，保存在 localStorage）
- PWA 特性：添加到主屏、离线可用（Service Worker 缓存应用壳）

---

## 🔌 API 参考

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/health` | 健康检查：FFmpeg/Provider/字体可用性、`mock_mode`、各输入类型的实际 Provider 路由 |
| `POST` | `/api/extract` | 提取健康主张（输入类型：`wechat_url` / `text` / `image`） |
| `POST` | `/api/verify` | 核验主张 → 生成核验报告；带 `target`+`relationship_state` 时生成沟通处方 |
| `POST` | `/api/card` | 生成安心核验卡文案（`style`: `elder` 长辈版 / `group_notice` 群公告版） |
| `POST` | `/api/card/image` | 直接渲染核验卡 PNG（返回 Base64 + sha256，V5.0 新增） |

### 请求示例

**① 提取主张**
```json
POST /api/extract
{
  "type": "wechat_url",
  "content": "https://mp.weixin.qq.com/s/..."
}
```
响应包含：`claim`（默认/最高风险主张）、`claims[]`（1-5 条候选，含 `evidence`/`risk_hint`）、`patterns[]`（话术标签：夸大因果/恐惧驱动/冒用权威等）、`search_keywords[]`、`topic_summary`、`audience`、`emotional_tone`、`visual_notes` 等。

**② 核验主张**
```json
POST /api/verify
{
  "claim": "隔夜菜一定会致癌",
  "target": "mother",
  "relationship_state": "recent_conflict"
}
```
响应核心：`verdict`（`credible` 基本可信 / `misleading` 误导 / `uncertain` 证据不足）、`risk_level`（low/medium/high）、`summary`、`sources[]`（含权威等级 `authority_level`）、`communication`（沟通渠道/共情开场/事实/建议）、`medical_notice`（医疗免责声明）。

**③ 生成卡片**
```json
POST /api/card
{
  "claim": "隔夜菜一定会致癌",
  "verdict": "misleading",
  "risk_level": "high",
  "summary": "该说法把有限风险夸大为一定致癌。",
  "target": "mother",
  "style": "elder"
}
```
响应：`title` / `greeting`（共情开场）/ `fact`（一句话事实）/ `suggestion`（具体怎么做）/ `self_verify`（长辈可亲手验证的方法）/ `closing`。文案约束：**每句 ≤25 字，不出现"假、错、谣言、你不懂、被骗"等刺激字眼**。

### 统一错误格式

```json
{
  "code": "VIDEO_PROCESSING_UNAVAILABLE",
  "message": "服务器缺少 FFmpeg，视频抽帧功能暂不可用",
  "request_id": "…"
}
```
常见错误码：`WECHAT_CONTENT_UNREADABLE`(422) / `VIDEO_PROCESSING_UNAVAILABLE`(503) / `CONTENT_ANALYSIS_UNAVAILABLE`(502) / `VERIFICATION_UNAVAILABLE`(500)。每个响应都带 `X-Request-ID` 头，便于排查。

---

## ⚙️ 环境变量配置

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `LUNA_BASE_URL` | `""` | OpenAI 兼容的视觉/文本模型端点（如 Qwen3-VL-Flash 中转渠道） |
| `LUNA_API_KEY` | `""` | 视觉/文本模型密钥 |
| `LUNA_MODEL` | `gpt-5.6-luna` | 模型名（实测常用 `qwen3-vl-flash`） |
| `TAVILY_API_KEY` | `""` | Tavily 检索密钥 |
| `EXA_API_KEY` | `""` | Exa 检索密钥 |
| `EXA_RESTRICT_DOMAINS` | `true` | true=Exa 只搜权威白名单（命中不足时自动放开并标记 `other`） |
| `MAX_VIDEO_MB` | `80` | 视频下载大小上限（MB） |
| `MAX_FRAMES` | `24` | 抽帧上限，**全片均匀采样** |
| `FRAME_INTERVAL_SECONDS` | `2` | 短视频最小帧间隔（支持小数） |
| `CARD_FONT_PATH` / `CARD_BOLD_FONT_PATH` | `""` | 卡片渲染中文字体路径（可选，自动探测系统字体） |
| `LOG_LEVEL` | `INFO` | 日志级别 |

**抽帧策略亮点**：先用 ffprobe 测视频时长，长视频按 `MAX_FRAMES / 时长` 的帧率**均匀铺满全片**，短视频按最小间隔采样——保证模型看到完整内容而不是只看到开头几十秒（旧方案 16帧×3秒只覆盖前 48 秒，而谣言核心主张普遍在视频后半段）。

**权威信源白名单**：`gov.cn` / `nhc.gov.cn`(卫健委) / `chinacdc.cn`(中国疾控) / `samr.gov.cn` / `who.int` / `cdc.gov` / `nih.gov` / `pubmed.ncbi.nlm.nih.gov` / `piyao.org.cn`(中国互联网联合辟谣平台) / `people.com.cn` / `xinhuanet.com` / `cctv.com` / `chinanews.com.cn`。

---

## 🧠 关键技术设计

### 防幻觉（三重信源过滤）
1. 模型返回的 `sources` 会**按 URL 回查搜索阶段的原始对象**，替换为可追溯的权威等级/证据/日期元数据；
2. 模型未返回来源时，保留搜索阶段的前 5 条；
3. 可靠信源 < 2 条时**强制 `verdict = "uncertain"`**（宁可不判，不可误判）。

### 双源搜索容错
- Exa + Tavily 并行检索（`asyncio.gather`），**单源失败不导致整体失败**；
- 结果按 URL 去重，按权威等级排序（政府机构 > 官方辟谣 > 医学研究库 > 权威媒体 > 其他）取前 6 条；
- Exa 记录每次搜索成本（`costDollars`）。

### 隐私与安全
- 视频下载限制 80MB、仅允许微信域名（SSRF 防护）、临时文件自动清理；
- 日志**不记录正文/主张/图片/关系描述/病史用药原文**，只记类型、长度、数量、状态（JSON 结构化日志 + `request_id` 追踪）；
- `.env` 被 `.gitignore` 排除，仓库内**不含任何真实密钥**；
- Android 客户端对后端明文 HTTP 做了 `network_security_config` 定向放行。

### 兼容与降级
- V5 完全保留 V4 的四个端点契约，仅新增 `/api/card/image` 与 `X-Request-ID`；
- 无密钥自动降级 mock，前后端可离线联调；
- FFmpeg 缺失时视频抽帧接口返回 503，文本/图片链路仍可用。

---

## 🧪 测试

```bash
# 后端（backend/ 目录下）
python test.py                               # 离线 e2e：mock 走通 extract + verify + card 全链路
python test.py --real                        # 用 .env 真实密钥跑 e2e（产生计费）
python test.py --live http://localhost:8000  # 打已在运行的服务
python test.py --wechat <公众号URL> --download # 纯 HTTP 解析公众号视频
python -m unittest discover -s tests -v      # 单元测试（tests/）
```

Android 端：`./gradlew test`（含 JUnit / Espresso / Compose UI Test）。

---

## 🛠️ 技术栈一览

| 端 | 技术 |
|---|---|
| 后端 | FastAPI · Uvicorn · Pydantic v2 · httpx · AsyncOpenAI · Pillow · python-dotenv · FFmpeg |
| Android | Kotlin 2.0.21 · Jetpack Compose · Material3 · Navigation Compose · OkHttp · Gson · Conscrypt |
| Web | 原生 HTML/CSS/JS · PWA（Service Worker + Web App Manifest）· Lucide 图标 |

---

## 🗺️ Roadmap / 生产化差距

> 详见 [`backend/docs/生产化差距与黑客松补强清单.md`](./backend/docs/生产化差距与黑客松补强清单.md)

- [ ] 接口鉴权与限流
- [ ] 任务队列 + SSE 流式进度（长任务体验优化）
- [ ] 更严格的 SSRF 防护与输入风控
- [ ] 多环境后端地址下发（当前 Android 端硬编码于 `strings.xml`）
- [ ] 历史记录与核验报告存档
- [ ] Android「消息自动监测」「图片检索」两个预留设置项落地

---

## 📄 文档导航

- [后端 API 接口文档（V5.0 Preview）](./backend/docs/API接口文档_V5.0_preview.md)
- [后端 API 接口文档（V4）](./backend/docs/API接口文档.md)
- [后端架构分析与迁移说明](./backend/docs/backend-v2架构分析与迁移说明.md)
- [Exa 搜索 API 接入方案](./backend/docs/Exa搜索API接入方案.md)
- [前端接口调用文档](./backend/docs/前端接口调用文档.md)
- [后端开发记录](./backend/docs/后端开发记录.md)
- [安心核验卡样张 A（长辈版）](./backend/outputs/安心核验卡-A-样张.png) · [样张 B（群公告版）](./backend/outputs/安心核验卡-B-样张.png)

---

## 📜 License

[MIT](./LICENSE) · AIY 2026 · 好好说团队
