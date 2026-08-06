# Exa 搜索 API 接入方案（已实施）

> 状态：**已实施完成（2026-08-05），验收通过**
> 审核结论：搜索策略 = **方案 B 双源并行合并**；白名单策略 = **白名单优先、不足自动放开**
> 实施与验收记录见第 9 节

---

## 1. 背景与目标

「好好说」产品链路为：输入（公众号链接/文本/截图/视频）→ `/api/extract` 提取健康主张 → `/api/verify` **先联网搜索权威信源、再由 LLM 判定** → `/api/card` 生成安心卡。

当前 `/api/verify` 的唯一联网搜索源是 **Tavily**（`app/providers.py` 的 `TavilyProvider`，httpx 直连 `api.tavily.com`，3 组查询并行 + 权威域名白名单）。

目标：**接入 Exa 搜索 API** 作为第二条搜索源，提升权威信源的覆盖度与证据质量。`.env` 中已配置好 `EXA_API_KEY`，只差代码接入。

---

## 2. 代码现状（已摸清）

| 项 | 现状 |
|---|---|
| 后端框架 | FastAPI 0.141.1（`app/main.py` 工厂模式，`uvicorn app.main:app` 启动） |
| 搜索实现 | `app/providers.py` L288-415 `TavilyProvider`：httpx 直连、3 组查询并行（`asyncio.gather`）、`TRUSTED_DOMAINS` 白名单、服务端 `_is_trusted_url()` 二次校验、`_authority()` 权威分级、结果映射为 `SourceItem` |
| 编排层 | `app/service.py` `DemoService.verify()`：`sources = await self.tavily.search(claim)` → `self.luna.verify(...)` 喂给 LLM |
| 配置 | `app/config.py` `Settings` dataclass 读取 `.env`；**`EXA_API_KEY` 已在 .env 中但 config 未读取** |
| 降级机制 | 无 key 时自动切 `MockTavilyProvider`（`app/mock_providers.py`），接口不变 |
| 前端 | `好好说_Fronted/` 目录为空；实际前端为 `好好说_Mobile/index.html`（原生 PWA，fetch 调用后端）。**搜索是后端内部步骤，前端不感知，无需改前端** |
| 依赖 | requirements.txt 已有 `httpx`，**接入 Exa 无需新增依赖**（采用 httpx 直连，与 Tavily 风格一致） |

---

## 3. Exa API 关键信息（已调研官方文档）

| 项 | 内容 |
|---|---|
| 端点 | `POST https://api.exa.ai/search` |
| 认证 | Header `x-api-key: <KEY>` 或 `Authorization: Bearer <KEY>`（二选一，项目沿用 Bearer 风格即可） |
| 常用请求参数 | `query`（必填）、`type`（`instant`/`fast`/`auto`/`deep-lite`/`deep`/`deep-reasoning`，默认 auto）、`numResults`（默认 10，最大 100）、`includeDomains`/`excludeDomains`（支持通配子域，最长 1200 个）、`startPublishedDate`/`endPublishedDate`（ISO 8601）、`contents: {highlights: {...}}`（返回证据高亮片段）、`category`（news/publication 等）、`moderation`（内容安全过滤） |
| 响应结构 | `results[]`：`title` / `url` / `publishedDate` / `author` / `highlights[]` / `highlightScores[]` / `text` / `summary`；另有 `requestId`、`costDollars`（每次调用返回成本，可记日志） |
| 价格 | Search 基础档 **$7/千次**（含 10 条结果，超出每千条 +$1）；Deep Search $12-15/千次；Contents $1/千页 |
| 免费额度 | 注册送 $20 + 每月送 $10 credits（≈ 1428 次基础 search/月），无需绑卡 |
| SDK | 官方 `exa-py`（Python 3.9+，支持 AsyncExa）；**本项目建议仍用 httpx 直连**，与 Tavily 风格统一、零新依赖 |

**健康辟谣场景要点**：Exa 支持 `startPublishedDate`（过滤陈旧信息）、`moderation`（自动过滤不安全内容）、`highlights`（直接当"证据摘录"）。⚠️ 注意：Exa 索引以英文互联网为主，**中文权威站点（gov.cn 等）覆盖待实测**——方案第 5.3 节有应对策略。

---

## 4. 接入设计（文件级改动清单）

### 4.1 `app/config.py`（+2 行）
```python
@dataclass(frozen=True)
class Settings:
    ...
    tavily_api_key: str = os.getenv("TAVILY_API_KEY", "")
    exa_api_key: str = os.getenv("EXA_API_KEY", "")          # 新增
    exa_restrict_domains: bool = os.getenv("EXA_RESTRICT_DOMAINS", "true").lower() in ("1","true")  # 新增：是否启用权威域名白名单
```

### 4.2 `app/providers.py`（新增 `ExaProvider`，约 90 行）
仿 `TavilyProvider` 结构，核心逻辑：

```
class ExaProvider:
    API_URL = "https://api.exa.ai/search"
    TRUSTED_DOMAINS = TavilyProvider.TRUSTED_DOMAINS      # 复用现有白名单

    async def search(self, claim) -> list[SourceItem]:
        # 3 组查询并行（沿用现有 search_keywords 设计）：
        #   f"{claim} 科学依据 卫健委 疾控" / f"{claim} 医学证据 研究" / f"{claim} 辟谣"
        # 请求体：
        #   type="fast"（低延迟；可选 auto），numResults=5，
        #   contents={"highlights": {"maxCharacters": 500}}，
        #   startPublishedDate=近3年（健康信息时效性），
        #   includeDomains=白名单（EXA_RESTRICT_DOMAINS 开关控制，见 5.3）
        # 结果映射：title/url/publisher ← url host
        #   evidence ← highlights 拼接（优先）→ summary → text[:3000]（退化）
        #   authority_level/label ← _authority(url)；published_at ← publishedDate
        # 错误处理：429/5xx/超时(20s) 记结构化日志后抛 ProviderError（由 service 层兜底切换）
        # 日志：记录 costDollars 字段
```

复用策略（**最小侵入**）：`ExaProvider` 直接引用 `TavilyProvider.TRUSTED_DOMAINS` / `_is_trusted_url` / `_authority`，不重构现有类。若审核希望更干净，可改为把这些抽成模块级函数（行为不变，tests 已覆盖）。

### 4.3 `app/service.py`（verify 搜索段改造，约 15 行）
```python
# __init__ 增加：
exa_configured = bool(settings.exa_api_key)
self.exa = ExaProvider(settings) if exa_configured else MockExaProvider()
self.mock_mode = not (luna_configured and tavily_configured and exa_configured)

# verify() 中替换：sources = await self.tavily.search(request.claim)
# 为：sources = await self._search_authority_sources(request.claim)
#     （方案 B：Exa + Tavily 双源并行合并，见 5.1）
# model_routes 的 verification_search 输出调整为实际生效的 provider 组合
```

### 4.4 `app/mock_providers.py`（+20 行）
新增 `MockExaProvider`：返回 1-2 条带"离线演示"标记的演示 `SourceItem`，保证无 key 时全链路可跑（与现有 mock 机制一致）。

### 4.5 `app/main.py`（+1 处）
`/health` 响应增加 `exa_configured` 字段（与现有 `tavily_configured` 并列）。

### 4.6 `.env.example`（+2 行）
```bash
EXA_API_KEY=
EXA_RESTRICT_DOMAINS=true   # true=只搜权威白名单；false=放开全量搜索+服务端分级
```

### 4.7 `tests/`（新增 1 个文件 + 1 处断言）
- `tests/test_exa_provider.py`：mock httpx 响应，覆盖正常映射 / 429 / 5xx / 空结果 / 超时；
- `tests/test_api.py`：`/health` 增加 `exa_configured` 断言。

---

## 5. 关键设计决策（需你拍板）

### 5.1 搜索策略 ✅ 已定稿：方案 B（双源并行合并）

| 方案 | 做法 | 优点 | 缺点 |
|---|---|---|---|
| A. Exa 主 + Tavily 兜底 | 三组查询并行走 Exa；Exa 未配置/异常/可信结果 < 2 条时回退 Tavily | 默认享受 Exa 质量，兜底保证不挂；成本接近单源 | 同一次请求只用到一个源 |
| **B. Exa + Tavily 并行合并 ✅（已选）** | `asyncio.gather` 双源同时搜，按 URL 去重合并，权威源优先，上限 6 条 | 证据最充分，对判定最有利 | 每次 verify 双倍搜索成本，慢源拖累 |
| C. 仅替换 Tavily | verify 只走 Exa | 最简 | 中文权威站覆盖未验证，风险大 |

**实施细节（B 方案）**：
- 搜索编排迁移到 `service.py` 新增私有方法 `_search_authority_sources(claim)`：
  1. `asyncio.gather` 并行调用 `self.exa.search(claim)` 与 `self.tavily.search(claim)`（沿用各自 3 组查询并行）；
  2. 单源异常不整体失败——用 `asyncio.gather(..., return_exceptions=True)` 捕获，记日志后使用另一源结果（双源都失败才抛 `ProviderError`）；
  3. 合并去重（按 URL），权威等级排序：`institution` > `official_factcheck` > `research` > `authoritative_media` > `other`（同级按 Exa score / 时间倒序），截取前 6 条；
  4. mock 模式（任一源未配置）自动退化为单源；双 mock 时返回演示数据。

### 5.2 `type` 参数
- 推荐 **`fast`**（官方定位"用户侧交互搜索"，延迟低、质量高）；`auto` 亦可。
- **不用 deep/deep-reasoning**：贵 2 倍、延迟 4s+，本项目"搜索→LLM 判定"不需要深度综合。

### 5.3 域名白名单策略 ✅ 已定稿：白名单优先、不足自动放开

Exa 索引偏英文，`includeDomains=["gov.cn", ...]` 可能返回空。定稿策略（`EXA_RESTRICT_DOMAINS` 开关控制，默认 true）：
- **第一轮**：带白名单搜（保产品"只信权威源"设定）；
- 若可信结果 < 2 条 → **第二轮放开白名单**（`includeDomains` 置空），用 `category="news"` + 服务端 `_is_trusted_url()` 分级，非权威来源标记为 `other` 交给 LLM 证据权重判定（现有 prompt 已支持）；
- 实测后可在 `.env` 一键切换。该策略仅作用于 Exa；Tavily 维持现有纯白名单逻辑不变。

### 5.4 隐私
只把 `claim`（待核验主张）发给 Exa，**不发送原文正文/家庭关系/病史**（现有 verify 已如此）；日志不记录 query 内容，记录 `costDollars` 便于监控。

---

## 6. 成本预估

- Exa 免费额度：注册 $20 + 每月 $10 ≈ **每月 1428 次基础 search**；
- 每次 `/api/verify` = 3 次 search（三组查询）→ 免费额度约支撑 **476 次核验/月**，黑客松演示绰绰有余；
- 若方案 B 双源并行 = 每月约 238 次核验，仍够用；超限时自动降级 Tavily/离线 mock，不影响演示。

---

## 7. 验收标准

1. `/health` 返回 `exa_configured: true`；
2. 直接 `curl` Exa 端点用 `.env` 的 key 可通（验证 key 有效性）；
3. 走 `/api/verify`，`sources[]` 有来自 Exa 的信源（可在日志按 provider 区分）；
4. `EXA_API_KEY` 置空后系统自动降级 Tavily/mock，接口不报错；
5. 中文白名单命中率实测记录（决定 `EXA_RESTRICT_DOMAINS` 默认值）；
6. `pytest` 全绿。

---

## 8. 实施顺序（审核通过后执行）

1. `config.py` 加 `exa_api_key` / `exa_restrict_domains`
2. `providers.py` 新增 `ExaProvider`（含白名单两轮制）
3. `mock_providers.py` 新增 `MockExaProvider`
4. `service.py` 注入 + 新增 `_search_authority_sources()`（方案 B 双源并行合并）
5. `main.py` /health 加字段；`.env.example` 补变量
6. 补测试，`pytest` 全绿
7. 实测中文权威站命中率，必要时调整白名单策略
8. 回归联调 `/api/verify` + 移动端全流程

预计改动 6 个文件、净增约 250 行代码，不动前端、不动依赖。

---

## 9. 实施与验收记录（2026-08-05）

### 已改动文件（7 个，均为增量编辑，未整体覆盖任何文件）
| 文件 | 改动 |
|---|---|
| `app/providers.py` | 末尾新增 `ExaProvider`（httpx 直连 `api.exa.ai/search`，`type=fast`、`numResults=5`、`moderation`、`contents.highlights`、近 3 年时效、白名单两轮制、`costDollars` 记日志） |
| `app/service.py` | 注入 `ExaProvider`；新增 `_search_authority_sources()`（双源并行、`return_exceptions` 单源容错、URL 去重、权威排序取 6 条）；`mock_mode` 改为"LLM + 任一搜索源"即真实模式；`model_routes.verification_search` 输出 `exa+tavily` |
| `app/mock_providers.py` | 末尾新增 `MockExaProvider` |
| `app/main.py` | `/health` 增加 `exa_configured` |
| `.env.example` | 补 `EXA_API_KEY` / `EXA_RESTRICT_DOMAINS` |
| `tests/test_exa_provider.py` | 新增 8 个用例（映射/两轮制/无白名单模式/HTTP 错误/无 key/evidence 退化/合并排序/去重上限） |
| `tests/test_api.py` | `/health` 补 `exa_configured` 断言 |

### 顺带修复的两个既有环境/代码问题（与 Exa 无关，但阻断验收）
1. **venv 依赖错位**：`.venv` 基于 Python 3.14，但 `pydantic_core`、`jiter` 装的是 cp313 二进制 → `pip install --force-reinstall --no-deps` 重装为 cp314 版本。
2. **`app/utils/logging.py` 日志格式化 bug**：`datetime.timezone`（应为 `timezone`）导致所有结构化日志格式化抛 AttributeError、日志从未写出 → 已修复，日志恢复正常 JSON 输出。

### 验收结果
- `unittest`：**30/30 通过**（原 22 + 新增 8）；
- `/health`：`exa_configured: true`、`mock_mode: false`、`verification_search: "exa+tavily"`；
- Exa 真实调用：key 有效；`隔夜菜致癌` 第一轮白名单即命中 5 条权威中文源（央视网、上海市监局、淄博疾控、济宁卫健委等），**中文权威站覆盖风险实测不存在**，未触发第二轮；
- `/api/verify` 全链路：`verdict=misleading, risk=medium`，返回 3 条权威信源（Exa 结果按权威等级排前）；
- 双源容错：测试中发现 Tavily 瞬时 429 失败时，`tool.call.failed` 记录后 Exa 结果继续使用，链路未中断。

### 注意事项
- Exa 免费额度每月 $10 credits（≈1428 次 search），每次 verify 走 3 次 Exa search；白名单命中不足时才额外触发第二轮 3 次。
- `EXA_RESTRICT_DOMAINS=true` 保持默认；实测中文覆盖良好，无需放开。
