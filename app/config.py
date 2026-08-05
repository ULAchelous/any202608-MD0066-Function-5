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
    max_video_mb: int = int(os.getenv("MAX_VIDEO_MB", "80"))
    max_frames: int = int(os.getenv("MAX_FRAMES", "24"))
    frame_interval_seconds: float = float(os.getenv("FRAME_INTERVAL_SECONDS", "2"))


settings = Settings()
