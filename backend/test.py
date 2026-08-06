"""好好说 Backend demo 端到端测试入口。

Usage:
    python test.py                          # 离线模式：mock 提供者走通 extract + verify 全链路
    python test.py --real                   # 使用 .env 里的真实 Luna/Tavily 密钥（产生计费）
    python test.py --live http://localhost:8000   # 打已在运行的服务
    python test.py --wechat URL [--download]      # 使用纯 HTTP 解析公众号视频
    python test.py --wechat-playwright URL         # 浏览器兜底捕获视频直链
"""

from __future__ import annotations

import argparse
import asyncio
import base64
import hashlib
import json
import os
from html import unescape
from pathlib import Path
from urllib.parse import urlparse

DEMO_TEXT = (
    "震惊！隔夜菜一定会致癌，专家紧急提醒：赶紧倒掉，"
    "为了孩子和父母的健康，马上转给你爱的人！"
)
ARTICLE_URL = "https://mp.weixin.qq.com/s/UcGLoLyd6vaROx4j18tnkg"
USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/120.0.0.0 Safari/537.36"
)
MAX_VIDEO_BYTES = 80 * 1024 * 1024


def _print(title: str, payload: dict) -> None:
    print(f"\n===== {title} =====")
    print(json.dumps(payload, ensure_ascii=False, indent=2))


def run_offline(real: bool) -> None:
    from fastapi.testclient import TestClient

    from app.config import Settings
    from app.main import create_app
    from app.service import DemoService

    app_settings = Settings() if real else Settings(
        luna_base_url="",
        luna_api_key="",
        tavily_api_key="",
        exa_api_key="",
    )
    service = DemoService(app_settings)
    client = TestClient(create_app(service, app_settings))

    health = client.get("/health")
    health.raise_for_status()
    _print("GET /health", health.json())

    extract = client.post("/api/extract", json={"type": "text", "content": DEMO_TEXT})
    extract.raise_for_status()
    extraction = extract.json()
    _print("POST /api/extract (text)", extraction)
    assert extraction["claim"], "extract 返回空 claim"

    verify = client.post(
        "/api/verify",
        json={
            "claim": extraction["claim"],
            "target": "mother",
            "relationship_state": "recent_conflict",
        },
    )
    verify.raise_for_status()
    report = verify.json()
    _print("POST /api/verify", report)
    assert report["verdict"] in {"credible", "misleading", "uncertain"}
    assert report["communication"]["opening"], "缺少沟通开场白"

    card = client.post(
        "/api/card",
        json={
            "claim": report["claim"],
            "verdict": report["verdict"],
            "risk_level": report["risk_level"],
            "summary": report["summary"],
            "target": "mother",
            "style": "elder",
            "sources": report["sources"],
        },
    )
    card.raise_for_status()
    card_json = card.json()
    _print("POST /api/card (elder)", card_json)
    assert card_json["fact"] and card_json["suggestion"], "核验卡缺少事实或建议"

    notice = client.post(
        "/api/card",
        json={"claim": report["claim"], "verdict": report["verdict"],
              "summary": report["summary"], "style": "group_notice"},
    )
    notice.raise_for_status()
    _print("POST /api/card (group_notice)", notice.json())

    for style, suffix in (("elder", "A"), ("group_notice", "B")):
        image = client.post(
            "/api/card/image",
            json={
                "claim": report["claim"],
                "verdict": report["verdict"],
                "risk_level": report["risk_level"],
                "summary": report["summary"],
                "target": "mother",
                "style": style,
                "sources": report["sources"],
                "scale": 2,
                "include_data_url": style == "elder",
            },
        )
        image.raise_for_status()
        image_json = image.json()
        png_bytes = base64.b64decode(image_json["image_base64"], validate=True)
        assert png_bytes.startswith(b"\x89PNG\r\n\x1a\n"), "图片不是合法 PNG"
        assert image_json["filename"] == f"安心核验卡-{suffix}.png"
        assert image_json["byte_size"] == len(png_bytes)
        assert image_json["sha256"] == hashlib.sha256(png_bytes).hexdigest()
        assert image_json["width"] == 750 and image_json["height"] > 600
        if style == "elder":
            assert image_json["data_url"].startswith("data:image/png;base64,")
        else:
            assert image_json["data_url"] is None
        printable = {key: value for key, value in image_json.items() if key not in {"image_base64", "data_url"}}
        _print(f"POST /api/card/image ({style})", printable)

    # 兼容性：所有 v4 路由仍存在，新路由只做增量添加。
    paths = client.get("/openapi.json").json()["paths"]
    for path in ("/health", "/api/extract", "/api/verify", "/api/card", "/api/card/image"):
        assert path in paths, f"OpenAPI 缺少路由：{path}"

    print(f"\n[OK] V5.0 Preview 全链路与 v4 接口兼容性通过（{'真实密钥' if real else '离线 mock'} 模式）")


def run_live(base_url: str) -> None:
    import httpx

    base = base_url.rstrip("/")
    with httpx.Client(base_url=base, timeout=180, trust_env=False) as client:
        health = client.get("/health")
        health.raise_for_status()
        _print("GET /health", health.json())

        extract = client.post("/api/extract", json={"type": "text", "content": DEMO_TEXT})
        _print("POST /api/extract (text)", extract.json())
        extract.raise_for_status()

        verify = client.post(
            "/api/verify",
            json={
                "claim": extract.json().get("claim", DEMO_TEXT),
                "target": "mother",
                "relationship_state": "recent_conflict",
            },
        )
        _print("POST /api/verify", verify.json())
        verify.raise_for_status()
        report = verify.json()

        card_payload = {
            "claim": report.get("claim", DEMO_TEXT),
            "verdict": report.get("verdict", "uncertain"),
            "risk_level": report.get("risk_level", "low"),
            "summary": report.get("summary", ""),
            "target": "mother",
            "style": "elder",
            "sources": report.get("sources", []),
        }
        card = client.post("/api/card", json=card_payload)
        _print("POST /api/card (elder)", card.json())
        card.raise_for_status()

        image = client.post("/api/card/image", json={**card_payload, "scale": 2})
        image.raise_for_status()
        image_json = image.json()
        png_bytes = base64.b64decode(image_json["image_base64"], validate=True)
        assert png_bytes.startswith(b"\x89PNG\r\n\x1a\n")
        _print(
            "POST /api/card/image (elder)",
            {key: value for key, value in image_json.items() if key not in {"image_base64", "data_url"}},
        )
    print(f"\n[OK] 远程服务 {base} V5.0 Preview 全链路通过")


def extract_wechat_videos_playwright(
    article_url: str,
) -> tuple[list[str], list[dict[str, object]]]:
    """使用真实浏览器捕获公众号视频地址，适合作为纯 HTTP 解析的兜底。"""
    try:
        from playwright.sync_api import sync_playwright
    except ImportError as exc:
        raise RuntimeError(
            "缺少 Playwright：先执行 pip install playwright，"
            "再执行 playwright install chromium"
        ) from exc

    parsed_article = urlparse(article_url)
    if parsed_article.scheme != "https" or parsed_article.hostname != "mp.weixin.qq.com":
        raise ValueError("仅支持 https://mp.weixin.qq.com/ 公众号链接")

    video_urls: list[str] = []
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(
            headless=True,
            args=["--disable-dev-shm-usage", "--no-sandbox"],
        )
        context = browser.new_context(user_agent=USER_AGENT)
        page = context.new_page()

        def handle_response(response: object) -> None:
            response_url = unescape(str(getattr(response, "url", ""))).replace(r"\/", "/")
            if "mpvideo.qpic.cn/" in response_url and ".mp4" in response_url:
                video_urls.append(response_url)

        page.on("response", handle_response)
        try:
            page.goto(article_url, wait_until="domcontentloaded", timeout=30_000)
            for _ in range(6):
                page.mouse.wheel(0, 1_000)
                page.wait_for_timeout(700)

            for video in page.query_selector_all("video"):
                source = video.evaluate("(el) => el.currentSrc || el.src || ''")
                if source and "mpvideo.qpic.cn/" in source:
                    video_urls.append(unescape(source))

            cookies = context.cookies()
        finally:
            browser.close()

    cleaned_urls = [url.replace("&amp;", "&") for url in video_urls]
    return list(dict.fromkeys(cleaned_urls)), cookies


def download_playwright_video(
    video_url: str,
    article_url: str,
    output_path: Path,
    cookies: list[dict[str, object]],
) -> int:
    """复用浏览器会话并将视频流安全写入临时文件。"""
    import httpx

    parsed_video = urlparse(video_url)
    if parsed_video.scheme != "https" or parsed_video.hostname != "mpvideo.qpic.cn":
        raise ValueError("不允许下载非 HTTPS 微信视频域名")

    cookie_jar = httpx.Cookies()
    for cookie in cookies:
        cookie_jar.set(
            str(cookie["name"]),
            str(cookie["value"]),
            domain=str(cookie.get("domain") or "mp.weixin.qq.com"),
            path=str(cookie.get("path") or "/"),
        )

    output_path.parent.mkdir(parents=True, exist_ok=True)
    temporary_path = output_path.with_suffix(output_path.suffix + ".part")
    downloaded = 0
    try:
        with httpx.Client(
            headers={
                "User-Agent": USER_AGENT,
                "Referer": article_url,
                "Accept": "*/*",
            },
            cookies=cookie_jar,
            follow_redirects=True,
            timeout=httpx.Timeout(60, connect=10),
            trust_env=False,
        ) as client:
            with client.stream("GET", video_url) as response:
                response.raise_for_status()
                if urlparse(str(response.url)).hostname != "mpvideo.qpic.cn":
                    raise ValueError("视频请求被重定向到非允许域名")

                content_type = response.headers.get("Content-Type", "")
                if "video" not in content_type and "octet-stream" not in content_type:
                    raise ValueError(f"响应不是视频: {content_type}")

                declared_size = int(response.headers.get("Content-Length", "0"))
                if declared_size > MAX_VIDEO_BYTES:
                    raise ValueError("视频超过 80 MB 大小限制")

                with temporary_path.open("wb") as output:
                    for chunk in response.iter_bytes(1024 * 1024):
                        if not chunk:
                            continue
                        downloaded += len(chunk)
                        if downloaded > MAX_VIDEO_BYTES:
                            raise ValueError("下载过程中超过 80 MB 大小限制")
                        output.write(chunk)

        os.replace(temporary_path, output_path)
        return downloaded
    except Exception:
        temporary_path.unlink(missing_ok=True)
        raise


def run_wechat_playwright(article_url: str, download: bool) -> None:
    video_urls, cookies = extract_wechat_videos_playwright(article_url)
    result: dict[str, object] = {
        "article_url": article_url,
        "video_count": len(video_urls),
        "selected_video_url": video_urls[0] if video_urls else None,
    }
    if not video_urls:
        raise RuntimeError("浏览器没有捕获到公众号视频地址")
    if download:
        output_path = Path("downloaded_video.mp4").resolve()
        result["downloaded_bytes"] = download_playwright_video(
            video_urls[0], article_url, output_path, cookies
        )
        result["output_path"] = str(output_path)
    _print("公众号视频 Playwright 解析", result)


async def run_wechat(article_url: str, download: bool) -> None:
    from app.wechat_video import WeChatVideoExtractor

    extractor = WeChatVideoExtractor()
    info = await extractor.extract(article_url)
    result: dict[str, object] = {
        "article_url": info.article_url,
        "title": info.title,
        "video_ids": list(info.video_ids),
        "video_count": len(info.video_urls),
        "selected_video_url": info.video_urls[0] if info.video_urls else None,
    }
    if download:
        output_path = Path("downloaded_video.mp4").resolve()
        result["downloaded_bytes"] = await extractor.download(
            info.video_urls, output_path, article_url=article_url
        )
        result["output_path"] = str(output_path)
    _print("公众号视频解析", result)


def main() -> None:
    parser = argparse.ArgumentParser(description="好好说 Backend demo 测试入口")
    parser.add_argument("--real", action="store_true", help="使用 .env 中的真实密钥")
    parser.add_argument("--live", metavar="BASE_URL", help="测试已运行的服务")
    parser.add_argument("--wechat", metavar="URL", nargs="?", const=ARTICLE_URL,
                        help="只检查公众号视频解析")
    parser.add_argument("--wechat-playwright", metavar="URL", nargs="?", const=ARTICLE_URL,
                        help="使用 Playwright 捕获公众号视频直链")
    parser.add_argument("--download", action="store_true",
                        help="配合 --wechat 或 --wechat-playwright 下载视频")
    args = parser.parse_args()

    if args.wechat_playwright:
        run_wechat_playwright(args.wechat_playwright, args.download)
    elif args.wechat:
        asyncio.run(run_wechat(args.wechat, args.download))
    elif args.live:
        run_live(args.live)
    else:
        run_offline(args.real)


if __name__ == "__main__":
    main()
