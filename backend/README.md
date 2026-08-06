# 好好说 Backend V5.0 Preview

基于 backend-v4 的隔离预览版本。完整保留 `/health`、`/api/extract`、`/api/verify`、`/api/card`，并新增 `POST /api/card/image`：服务端生成指定风格卡片文案、渲染 PNG，并以纯 Base64（可选 Data URL）返回，Android 客户端无需实现 HTML 截图。

## 启动

服务器需要 Python 3.11+ 和 FFmpeg，不需要 Docker 或数据库。

```bash
python -m venv .venv
source .venv/Scripts/activate   # Windows Git Bash；Linux/macOS 用 .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env            # 填入 LUNA_* 与 TAVILY_API_KEY
```

`.env` 会被自动加载（python-dotenv），直接运行：

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

API 文档：`http://服务器IP:8000/docs`

**没有密钥也能跑**：LUNA/TAVILY 未配置时自动降级为离线 mock 提供者（`/health` 返回 `mock_mode: true`），接口结构完全一致，演示结果带"离线演示"标记。

## 配置说明

| 环境变量 | 说明 |
|---|---|
| `LUNA_BASE_URL` / `LUNA_API_KEY` / `LUNA_MODEL` | OpenAI 兼容的视觉模型端点（如 Qwen3-VL-Flash 渠道） |
| `TAVILY_API_KEY` | Tavily 检索密钥 |
| `MAX_FRAMES` | 抽帧上限，**全片均匀采样**（默认 24） |
| `FRAME_INTERVAL_SECONDS` | 短视频的最小帧间隔（默认 2，支持小数） |
| `MAX_VIDEO_MB` | 视频下载大小上限 |
| `CARD_FONT_PATH` | 可选，服务端卡片正文中文字体文件路径 |
| `CARD_BOLD_FONT_PATH` | 可选，服务端卡片标题/标签中文粗体字体路径 |

抽帧策略：先用 ffprobe 取视频时长，长视频按 `MAX_FRAMES/时长` 均匀铺满全片，短视频按最小间隔抽——保证模型看到完整内容而不是只有开头几十秒。

## 快速测试

```bash
python test.py                              # 离线 e2e：mock 走通 extract + verify 全链路
python test.py --real                       # 用 .env 真实密钥跑 e2e（产生计费）
python test.py --live http://localhost:8000 # 打已在运行的服务
python test.py --wechat <公众号URL> --download
python -m unittest discover -s tests -v     # 单元测试
```

## Android 请求

提取公众号图文或视频文章中的核心主张：

```json
POST /api/extract
{
  "type": "wechat_url",
  "content": "https://mp.weixin.qq.com/s/..."
}
```

也可以传 `type=text`，或者传 `type=image` 并在 `content` 中放 Base64/Data URL 截图。

核验主张：

```json
POST /api/verify
{
  "claim": "隔夜菜一定会致癌",
  "target": "mother",
  "relationship_state": "recent_conflict"
}
```

直接获取指定卡片 PNG Base64：

```json
POST /api/card/image
{
  "claim": "隔夜菜一定会致癌",
  "verdict": "misleading",
  "risk_level": "high",
  "summary": "该说法把有限风险夸大为一定致癌。",
  "target": "mother",
  "style": "elder",
  "sources": [],
  "scale": 2,
  "include_data_url": false
}
```

响应中的 `image_base64` 是不带前缀的 PNG Base64；Android 解码后按 `filename` 保存即可。`style=elder` 对应 A 卡，`style=group_notice` 对应 B 卡。
