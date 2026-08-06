from __future__ import annotations

import os
from dataclasses import dataclass

from dotenv import load_dotenv

load_dotenv()


@dataclass(frozen=True)
class Settings:
    luna_base_url: str = os.getenv("LUNA_BASE_URL", "")
    luna_api_key: str = os.getenv("LUNA_API_KEY", "")
    luna_model: str = os.getenv("LUNA_MODEL", "gpt-5.6-luna")
    tavily_api_key: str = os.getenv("TAVILY_API_KEY", "")
    exa_api_key: str = os.getenv("EXA_API_KEY", "")
    # true=Exa 只搜权威域名白名单（结果不足时自动放开）；false=直接全量搜索
    exa_restrict_domains: bool = os.getenv("EXA_RESTRICT_DOMAINS", "true").lower() in ("1", "true", "yes")
    max_video_mb: int = int(os.getenv("MAX_VIDEO_MB", "80"))
    max_frames: int = int(os.getenv("MAX_FRAMES", "24"))
    frame_interval_seconds: float = float(os.getenv("FRAME_INTERVAL_SECONDS", "2"))
    card_font_path: str = os.getenv("CARD_FONT_PATH", "")
    card_bold_font_path: str = os.getenv("CARD_BOLD_FONT_PATH", "")


settings = Settings()
