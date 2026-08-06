# 重大声名：此项目是一个Vibe项目,作为AIY2026参赛作品提交

# 好好说 Backend Demo

轻量 FastAPI 后端：解析公众号内嵌视频、全片均匀抽取关键帧、调用视觉大模型提取健康主张，再用 Tavily 搜索并生成核验报告。

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

提取公众号视频主张：

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
