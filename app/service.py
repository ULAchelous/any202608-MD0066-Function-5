from __future__ import annotations

import tempfile
from pathlib import Path

from app.config import Settings
from app.mock_providers import MockLunaProvider, MockTavilyProvider
from app.providers import LunaProvider, TavilyProvider
from app.schemas import (
    CardRequest,
    ClaimExtraction,
    ExtractRequest,
    SourceType,
    VerificationCard,
    VerificationReport,
    VerifyRequest,
)
from app.video_frames import extract_frames
from app.wechat_video import WeChatVideoExtractor


class DemoService:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        luna_configured = bool(settings.luna_api_key and settings.luna_base_url)
        tavily_configured = bool(settings.tavily_api_key)
        self.luna = LunaProvider(settings) if luna_configured else MockLunaProvider()
        self.tavily = TavilyProvider(settings) if tavily_configured else MockTavilyProvider()
        self.mock_mode = not (luna_configured and tavily_configured)
        self.wechat = WeChatVideoExtractor(max_video_mb=settings.max_video_mb)

    async def extract_claim(self, request: ExtractRequest) -> ClaimExtraction:
        if request.type == SourceType.TEXT:
            return await self.luna.extract_from_text(request.content)
        if request.type == SourceType.IMAGE:
            return await self.luna.extract_from_image(request.content)

        info = await self.wechat.extract(request.content)
        with tempfile.TemporaryDirectory(prefix="haohuoshuo-") as temp_dir_name:
            temp_dir = Path(temp_dir_name)
            video_path = temp_dir / "source.mp4"
            await self.wechat.download(
                info.video_urls, video_path, article_url=request.content
            )
            frames = await extract_frames(
                video_path,
                temp_dir / "frames",
                interval_seconds=self.settings.frame_interval_seconds,
                max_frames=self.settings.max_frames,
            )
            return await self.luna.extract_from_frames(
                frames,
                title=info.title,
                video_id=info.video_ids[0] if info.video_ids else None,
            )

    async def verify(self, request: VerifyRequest) -> VerificationReport:
        sources = await self.tavily.search(request.claim)
        return await self.luna.verify(
            request.claim, request.target, request.relationship_state, sources
        )

    async def generate_card(self, request: CardRequest) -> VerificationCard:
        return await self.luna.generate_card(request)
