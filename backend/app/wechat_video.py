from __future__ import annotations

import asyncio
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
    """公众号文章解析结果，兼容纯图文文章与内嵌视频文章。"""

    article_url: str
    title: str
    video_ids: tuple[str, ...]
    video_urls: tuple[str, ...]
    text_content: str = ""
    author: str = ""

    @property
    def has_video(self) -> bool:
        return bool(self.video_urls)

    @property
    def has_text(self) -> bool:
        return bool(self.text_content.strip())


class _ArticleParser(HTMLParser):
    """只依赖标准库的微信文章正文解析器。

    微信公众号正文的稳定容器为 #js_content；标题优先取 og:title，
    作者/公众号名优先取 og:article:author 或 profile_nickname。
    """

    _SKIP_TAGS = {"script", "style", "noscript", "svg"}
    _VOID_TAGS = {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"}
    _BLOCK_TAGS = {
        "p", "div", "section", "article", "h1", "h2", "h3", "h4",
        "li", "ul", "ol", "blockquote", "br", "table", "tr", "td",
    }

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.title = ""
        self.author = ""
        self.video_ids: list[str] = []
        self._content_depth = 0
        self._skip_depth = 0
        self._text_parts: list[str] = []

    @property
    def text_content(self) -> str:
        raw = "".join(self._text_parts)
        lines = [re.sub(r"[ \t\u00a0\u200b]+", " ", line).strip() for line in raw.splitlines()]
        return "\n".join(line for line in lines if line)

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attributes = dict(attrs)
        if tag == "meta":
            key = attributes.get("property") or attributes.get("name")
            value = (attributes.get("content") or "").strip()
            if key == "og:title" and value:
                self.title = value
            elif key in {"og:article:author", "author"} and value:
                self.author = value

        if tag == "iframe":
            video_id = attributes.get("data-mpvid")
            if video_id:
                self.video_ids.append(video_id)

        element_id = attributes.get("id", "")
        classes = (attributes.get("class") or "").split()
        enters_content = element_id == "js_content" or "rich_media_content" in classes
        if enters_content:
            self._content_depth = 1
        elif self._content_depth and tag not in self._VOID_TAGS:
            self._content_depth += 1

        if self._content_depth:
            if tag in self._SKIP_TAGS:
                self._skip_depth += 1
            elif not self._skip_depth and tag in self._BLOCK_TAGS:
                self._text_parts.append("\n")

    def handle_startendtag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        was_in_content = bool(self._content_depth)
        self.handle_starttag(tag, attrs)
        if self._content_depth and tag in self._BLOCK_TAGS:
            self._text_parts.append("\n")
        if self._content_depth and not was_in_content and tag not in self._VOID_TAGS:
            self._content_depth -= 1

    def handle_endtag(self, tag: str) -> None:
        if not self._content_depth:
            return
        if self._skip_depth and tag in self._SKIP_TAGS:
            self._skip_depth -= 1
        elif not self._skip_depth and tag in self._BLOCK_TAGS:
            self._text_parts.append("\n")
        if tag not in self._VOID_TAGS:
            self._content_depth -= 1

    def handle_data(self, data: str) -> None:
        if self._content_depth and not self._skip_depth:
            self._text_parts.append(data)



class WeChatVideoExtractor:
    def __init__(self, *, max_video_mb: int = 80) -> None:
        self.max_video_bytes = max_video_mb * 1024 * 1024
        self.headers = {
            "User-Agent": USER_AGENT,
            "Referer": "https://mp.weixin.qq.com/",
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.5",
            "Cache-Control": "no-cache",
            "Pragma": "no-cache",
        }

    @staticmethod
    def validate_article_url(article_url: str) -> None:
        parsed = urlparse(article_url)
        if parsed.scheme != "https" or parsed.hostname != "mp.weixin.qq.com":
            raise WeChatVideoError("仅支持 https://mp.weixin.qq.com/ 公众号链接")

    async def extract(self, article_url: str) -> WeChatVideoInfo:
        self.validate_article_url(article_url)
        last_error: Exception | None = None
        async with httpx.AsyncClient(
            headers=self.headers,
            follow_redirects=True,
            timeout=30,
            trust_env=False,
        ) as client:
            for attempt in range(2):
                try:
                    # 微信偶尔会对相同短链返回临时拦截页；第二次请求增加无害的
                    # cache-buster，避免命中该空页面缓存。
                    url = article_url
                    if attempt:
                        separator = "&" if "?" in article_url else "?"
                        url = f"{article_url}{separator}hhs_retry=1"
                    response = await client.get(url)
                    response.raise_for_status()
                    return self.parse_html(response.text, article_url)
                except (httpx.HTTPError, WeChatVideoError) as exc:
                    last_error = exc
                    if attempt == 0:
                        await asyncio.sleep(0.25)

        if isinstance(last_error, WeChatVideoError):
            raise WeChatVideoError(
                "公众号页面暂时返回了访问验证页，请稍后重试或复制文章正文提交"
            ) from last_error
        raise WeChatVideoError(f"公众号页面读取失败: {last_error}") from last_error

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

        video_urls.sort(key=WeChatVideoExtractor._quality_rank)

        author = parser.author
        if not author:
            author_match = re.search(
                r"(?:profile_nickname|nickname)\s*[:=]\s*[\"']([^\"']+)",
                page_html,
            )
            if author_match:
                author = html_module.unescape(author_match.group(1)).strip()

        text_content = parser.text_content
        if not video_urls and len(text_content) < 20:
            raise WeChatVideoError("文章中没有找到可读取的正文或视频内容")

        return WeChatVideoInfo(
            article_url=article_url,
            title=parser.title,
            video_ids=tuple(dict.fromkeys(video_ids)),
            video_urls=tuple(video_urls),
            text_content=text_content,
            author=author,
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
