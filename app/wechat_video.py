from __future__ import annotations

import html as html_module
import re
from dataclasses import dataclass
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urlparse

import httpx


USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)


class WeChatVideoError(RuntimeError):
    pass


@dataclass(frozen=True)
class WeChatVideoInfo:
    article_url: str
    title: str
    video_ids: tuple[str, ...]
    video_urls: tuple[str, ...]


class _ArticleParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.title = ""
        self.video_ids: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attributes = dict(attrs)
        if tag == "meta" and attributes.get("property") == "og:title":
            self.title = (attributes.get("content") or "").strip()
        if tag == "iframe":
            video_id = attributes.get("data-mpvid")
            if video_id:
                self.video_ids.append(video_id)


class WeChatVideoExtractor:
    def __init__(self, *, max_video_mb: int = 80) -> None:
        self.max_video_bytes = max_video_mb * 1024 * 1024
        self.headers = {
            "User-Agent": USER_AGENT,
            "Referer": "https://mp.weixin.qq.com/",
            "Accept": "text/html,application/xhtml+xml,*/*",
        }

    @staticmethod
    def validate_article_url(article_url: str) -> None:
        parsed = urlparse(article_url)
        if parsed.scheme != "https" or parsed.hostname != "mp.weixin.qq.com":
            raise WeChatVideoError("仅支持 https://mp.weixin.qq.com/ 公众号链接")

    async def extract(self, article_url: str) -> WeChatVideoInfo:
        self.validate_article_url(article_url)
        try:
            async with httpx.AsyncClient(
                headers=self.headers,
                follow_redirects=True,
                timeout=30,
                trust_env=False,
            ) as client:
                response = await client.get(article_url)
                response.raise_for_status()
        except httpx.HTTPError as exc:
            raise WeChatVideoError(f"公众号页面读取失败: {exc}") from exc
        return self.parse_html(response.text, article_url)

    @staticmethod
    def parse_html(page_html: str, article_url: str) -> WeChatVideoInfo:
        parser = _ArticleParser()
        parser.feed(page_html)

        video_ids = list(parser.video_ids)
        video_ids.extend(
            re.findall(r"\bwxv_\d{10,}\b", page_html, flags=re.IGNORECASE)
        )

        url_pattern = re.compile(
            r"(?:(?:https?:)?(?://|\\/\\/))?"
            r"mpvideo\.qpic\.cn/[^\s\"'<>]+?\.mp4\?[^\s\"'<>]+",
            flags=re.IGNORECASE,
        )
        video_urls: list[str] = []
        for raw_url in url_pattern.findall(page_html):
            cleaned = WeChatVideoExtractor._decode_embedded_url(raw_url)
            if cleaned and cleaned not in video_urls:
                video_urls.append(cleaned)

        if not video_urls:
            raise WeChatVideoError("文章中没有找到可下载的视频地址")

        video_urls.sort(key=WeChatVideoExtractor._quality_rank)
        return WeChatVideoInfo(
            article_url=article_url,
            title=parser.title,
            video_ids=tuple(dict.fromkeys(video_ids)),
            video_urls=tuple(video_urls),
        )

    @staticmethod
    def _decode_embedded_url(raw_url: str) -> str:
        value = raw_url
        for end_marker in (r"\x22", r"\u0022", r"\&quot;", r"\'"):
            value = value.split(end_marker, 1)[0]
        value = value.replace(r"\/", "/")
        value = value.replace(r"\x26", "&").replace(r"\u0026", "&")
        value = html_module.unescape(html_module.unescape(value))
        if value.startswith("//"):
            value = "https:" + value
        elif value.startswith("mpvideo.qpic.cn"):
            value = "https://" + value
        elif value.startswith("http://mpvideo.qpic.cn/"):
            value = "https://" + value.removeprefix("http://")
        if not value.startswith("https://mpvideo.qpic.cn/"):
            return ""
        return value

    @staticmethod
    def _quality_rank(url: str) -> tuple[int, str]:
        # f10004 is normally the high-resolution H.264 stream and is widely decodable.
        preferred = (".f10004.", ".f10002.", ".f10104.", ".f10102.")
        return next((index for index, token in enumerate(preferred) if token in url), 9), url

    async def download(
        self,
        video_urls: tuple[str, ...] | list[str],
        output_path: Path,
        *,
        article_url: str,
    ) -> int:
        if not video_urls:
            raise WeChatVideoError("没有可下载的视频地址")

        headers = dict(self.headers)
        headers["Referer"] = article_url
        last_error = ""
        for video_url in video_urls:
            try:
                async with httpx.AsyncClient(
                    headers=headers,
                    follow_redirects=True,
                    timeout=45,
                    trust_env=False,
                ) as client:
                    async with client.stream("GET", video_url) as response:
                        response.raise_for_status()
                        content_type = response.headers.get("content-type", "")
                        if "video" not in content_type and "octet-stream" not in content_type:
                            raise WeChatVideoError(f"视频响应类型异常: {content_type}")
                        declared_size = int(response.headers.get("content-length", "0"))
                        if declared_size > self.max_video_bytes:
                            raise WeChatVideoError("视频超过 Demo 大小限制")

                        downloaded = 0
                        with output_path.open("wb") as output:
                            async for chunk in response.aiter_bytes(1024 * 1024):
                                downloaded += len(chunk)
                                if downloaded > self.max_video_bytes:
                                    raise WeChatVideoError("视频下载过程中超过大小限制")
                                output.write(chunk)
                        return downloaded
            except (httpx.HTTPError, OSError, WeChatVideoError) as exc:
                output_path.unlink(missing_ok=True)
                last_error = str(exc)

        raise WeChatVideoError(f"所有视频地址均下载失败: {last_error}")
