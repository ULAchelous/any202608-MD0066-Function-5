from __future__ import annotations

from dataclasses import dataclass

from app.schemas import ExtractRequest, SourceType
from app.wechat_video import WeChatVideoExtractor


@dataclass(frozen=True)
class ParsedInput:
    """Normalized input for service-level model routing.

    The original content is kept only in memory for the current request. Logs should
    use content_length and routing metadata, never content itself.
    """

    input_type: SourceType
    source_kind: str
    content: str = ""
    article_url: str = ""
    title: str = ""
    author: str = ""
    text_content: str = ""
    video_ids: tuple[str, ...] = ()
    video_urls: tuple[str, ...] = ()

    @property
    def has_video(self) -> bool:
        return bool(self.video_urls)

    @property
    def has_text(self) -> bool:
        return bool(self.text_content.strip())

    @property
    def content_length(self) -> int:
        if self.source_kind == "wechat_article":
            return len(self.text_content)
        return len(self.content)


class InputDispatcher:
    """Classify source inputs before selecting a model or media pipeline."""

    def __init__(self, wechat: WeChatVideoExtractor) -> None:
        self.wechat = wechat

    async def parse(self, request: ExtractRequest) -> ParsedInput:
        if request.type == SourceType.TEXT:
            return ParsedInput(
                input_type=request.type,
                source_kind="text",
                content=request.content,
            )
        if request.type == SourceType.IMAGE:
            return ParsedInput(
                input_type=request.type,
                source_kind="image",
                content=request.content,
            )

        article = await self.wechat.extract(request.content)
        # A downloadable mpvideo URL takes precedence even when the article also
        # contains text. This preserves the existing Qwen frame-analysis pipeline.
        source_kind = "wechat_video" if article.has_video else "wechat_article"
        return ParsedInput(
            input_type=request.type,
            source_kind=source_kind,
            article_url=article.article_url,
            title=article.title,
            author=article.author,
            text_content=article.text_content,
            video_ids=article.video_ids,
            video_urls=article.video_urls,
        )
