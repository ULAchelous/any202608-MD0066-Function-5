from __future__ import annotations

import asyncio
import logging
import tempfile
from pathlib import Path
from time import perf_counter

from app.card_renderer import render_card_png
from app.config import Settings
from app.mock_providers import MockExaProvider, MockLunaProvider, MockTavilyProvider
from app.parsers.dispatcher import InputDispatcher
from app.providers import ExaProvider, LunaProvider, ProviderError, TavilyProvider
from app.schemas import (
    CardImageRequest,
    CardImageResponse,
    CardRequest,
    ClaimExtraction,
    ExtractRequest,
    SourceItem,
    VerificationCard,
    VerificationReport,
    VerifyRequest,
)
from app.utils.logging import get_logger, log_event
from app.video_frames import extract_frames
from app.wechat_video import WeChatVideoExtractor


class DemoService:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        luna_configured = bool(settings.luna_api_key and settings.luna_base_url)
        tavily_configured = bool(settings.tavily_api_key)
        exa_configured = bool(settings.exa_api_key)
        self.luna = LunaProvider(settings) if luna_configured else MockLunaProvider()
        self.tavily = TavilyProvider(settings) if tavily_configured else MockTavilyProvider()
        self.exa = ExaProvider(settings) if exa_configured else MockExaProvider()
        # 双搜索源只要有一个真实配置，搜索环节即视为真实模式
        self.mock_mode = not (luna_configured and (tavily_configured or exa_configured))
        self.wechat = WeChatVideoExtractor(max_video_mb=settings.max_video_mb)
        self.dispatcher = InputDispatcher(self.wechat)
        self.logger = get_logger("haohuoshuo.service")

    @property
    def model_routes(self) -> dict[str, str]:
        provider_name = self.luna.__class__.__name__
        return {
            "text": provider_name,
            "image": provider_name,
            "wechat_article": provider_name,
            "wechat_video": f"extract_frames -> {provider_name}",
            "verification_search": self._search_providers_desc,
        }

    @property
    def _search_providers_desc(self) -> str:
        """实际生效的搜索源组合描述（exa+tavily / exa / tavily / mock）。"""
        parts = []
        if not isinstance(self.exa, MockExaProvider):
            parts.append("exa")
        if not isinstance(self.tavily, MockTavilyProvider):
            parts.append("tavily")
        return "+".join(parts) if parts else "mock"

    async def extract_claim(self, request: ExtractRequest) -> ClaimExtraction:
        started = perf_counter()
        parsed = await self.dispatcher.parse(request)
        log_event(
            self.logger,
            logging.INFO,
            "input.dispatched",
            input_type=request.type.value,
            source_kind=parsed.source_kind,
            content_length=parsed.content_length,
            has_text=parsed.has_text,
            has_video=parsed.has_video,
            video_count=len(parsed.video_urls),
            elapsed_ms=round((perf_counter() - started) * 1000, 2),
        )

        if parsed.source_kind == "text":
            result = await self.luna.extract_from_text(parsed.content)
            result = result.model_copy(update={"source_kind": "text"})
        elif parsed.source_kind == "image":
            result = await self.luna.extract_from_image(parsed.content)
            result = result.model_copy(update={"source_kind": "image"})
        elif parsed.source_kind == "wechat_article":
            context_title = parsed.title
            if parsed.author:
                context_title = f"{parsed.title}（公众号：{parsed.author}）"
            result = await self.luna.extract_from_text(
                parsed.text_content,
                title=context_title,
            )
            result = result.model_copy(
                update={
                    "article_title": parsed.title,
                    "article_author": parsed.author or None,
                    "source_kind": "wechat_article",
                }
            )
        else:
            result = await self._extract_wechat_video(parsed)

        log_event(
            self.logger,
            logging.INFO,
            "extract.completed",
            source_kind=parsed.source_kind,
            model_provider=self.luna.__class__.__name__,
            model_name=self.settings.luna_model,
            claim_count=len(result.claims),
            elapsed_ms=round((perf_counter() - started) * 1000, 2),
            mock_provider=isinstance(self.luna, MockLunaProvider),
        )
        return result

    async def _extract_wechat_video(self, parsed) -> ClaimExtraction:
        """Keep the existing download -> uniform frames -> Qwen vision route."""

        media_started = perf_counter()
        with tempfile.TemporaryDirectory(prefix="haohuoshuo-") as temp_dir_name:
            temp_dir = Path(temp_dir_name)
            video_path = temp_dir / "source.mp4"
            downloaded_bytes = await self.wechat.download(
                parsed.video_urls,
                video_path,
                article_url=parsed.article_url,
            )
            log_event(
                self.logger,
                logging.INFO,
                "media.download.completed",
                source_kind="wechat_video",
                downloaded_bytes=downloaded_bytes,
                candidate_url_count=len(parsed.video_urls),
                elapsed_ms=round((perf_counter() - media_started) * 1000, 2),
            )

            frame_started = perf_counter()
            frames = await extract_frames(
                video_path,
                temp_dir / "frames",
                interval_seconds=self.settings.frame_interval_seconds,
                max_frames=self.settings.max_frames,
            )
            log_event(
                self.logger,
                logging.INFO,
                "media.frames.completed",
                source_kind="wechat_video",
                frame_count=len(frames),
                max_frames=self.settings.max_frames,
                elapsed_ms=round((perf_counter() - frame_started) * 1000, 2),
            )
            result = await self.luna.extract_from_frames(
                frames,
                title=parsed.title,
                video_id=parsed.video_ids[0] if parsed.video_ids else None,
            )
            return result.model_copy(
                update={
                    "article_author": parsed.author or None,
                    "source_kind": "wechat_video",
                }
            )

    async def _search_authority_sources(self, claim: str) -> list[SourceItem]:
        """方案 B：Exa + Tavily 双源并行搜索，合并去重后按权威等级排序取前 6 条。

        单源失败不整体失败——记日志后使用另一源结果；
        两个真实源都失败才抛 ProviderError；双 mock 时返回演示数据。
        """
        real_providers = [
            (name, provider)
            for name, provider in (("exa", self.exa), ("tavily", self.tavily))
            if not isinstance(provider, (MockExaProvider, MockTavilyProvider))
        ]
        if not real_providers:
            # 双 mock：走演示数据（保持离线链路可用）
            return await self.tavily.search(claim)

        names, tasks = zip(
            *[(name, provider.search(claim)) for name, provider in real_providers]
        )
        results = await asyncio.gather(*tasks, return_exceptions=True)

        batches: list[list[SourceItem]] = []
        for name, result in zip(names, results):
            if isinstance(result, BaseException):
                log_event(
                    self.logger,
                    logging.WARNING,
                    "tool.call.failed",
                    tool="authority_search",
                    provider=name,
                    error_category=result.__class__.__name__,
                )
                continue
            batches.append(result)

        if not batches:
            raise ProviderError("Exa 与 Tavily 搜索均失败")

        return self._merge_search_batches(batches)

    @staticmethod
    def _merge_search_batches(batches: list[list[SourceItem]]) -> list[SourceItem]:
        """按 URL 去重，权威等级排序（institution > factcheck > research > media > other），取前 6 条。"""
        rank = {
            "institution": 0,
            "official_factcheck": 1,
            "research": 2,
            "authoritative_media": 3,
            "other": 4,
        }
        seen: dict[str, SourceItem] = {}
        for items in batches:
            for item in items:
                if item.url and item.url not in seen:
                    seen[item.url] = item
        merged = sorted(
            seen.values(),
            key=lambda s: (rank.get(s.authority_level, 9), s.published_at or ""),
        )
        return merged[:6]

    async def verify(self, request: VerifyRequest) -> VerificationReport:
        started = perf_counter()
        tool_started = perf_counter()
        log_event(
            self.logger,
            logging.INFO,
            "tool.call.started",
            tool="authority_search",
            provider=self._search_providers_desc,
            claim_length=len(request.claim),
        )
        sources = await self._search_authority_sources(request.claim)
        log_event(
            self.logger,
            logging.INFO,
            "tool.call.completed",
            tool="authority_search",
            provider=self._search_providers_desc,
            result_count=len(sources),
            elapsed_ms=round((perf_counter() - tool_started) * 1000, 2),
            mock_provider=all(
                isinstance(p, (MockExaProvider, MockTavilyProvider))
                for p in (self.exa, self.tavily)
            ),
        )
        report = await self.luna.verify(
            request.claim,
            request.target,
            request.relationship_state,
            sources,
        )
        log_event(
            self.logger,
            logging.INFO,
            "verification.completed",
            model_provider=self.luna.__class__.__name__,
            model_name=self.settings.luna_model,
            source_count=len(report.sources),
            verdict=report.verdict,
            risk_level=report.risk_level,
            elapsed_ms=round((perf_counter() - started) * 1000, 2),
            mock_provider=isinstance(self.luna, MockLunaProvider),
        )
        return report

    async def generate_card(self, request: CardRequest) -> VerificationCard:
        started = perf_counter()
        card = await self.luna.generate_card(request)
        log_event(
            self.logger,
            logging.INFO,
            "card.completed",
            model_provider=self.luna.__class__.__name__,
            model_name=self.settings.luna_model,
            style=request.style.value,
            source_count=len(card.sources),
            elapsed_ms=round((perf_counter() - started) * 1000, 2),
            mock_provider=isinstance(self.luna, MockLunaProvider),
        )
        return card

    async def generate_card_image(self, request: CardImageRequest) -> CardImageResponse:
        """Generate card copy and render the selected style to a PNG Base64 payload."""

        card_request = CardRequest.model_validate(
            request.model_dump(exclude={"scale", "include_data_url"})
        )
        card = await self.generate_card(card_request)
        rendered = await asyncio.to_thread(
            render_card_png,
            card,
            request.style,
            scale=request.scale,
            font_path=self.settings.card_font_path,
            bold_font_path=self.settings.card_bold_font_path,
        )
        suffix = "A" if request.style.value == "elder" else "B"
        image_base64 = rendered.image_base64
        log_event(
            self.logger,
            logging.INFO,
            "card.image.completed",
            style=request.style.value,
            width=rendered.width,
            height=rendered.height,
            byte_size=len(rendered.png_bytes),
            scale=request.scale,
        )
        return CardImageResponse(
            card=card,
            style=request.style,
            filename=f"安心核验卡-{suffix}.png",
            width=rendered.width,
            height=rendered.height,
            byte_size=len(rendered.png_bytes),
            sha256=rendered.sha256,
            image_base64=image_base64,
            data_url=(f"data:image/png;base64,{image_base64}" if request.include_data_url else None),
        )
