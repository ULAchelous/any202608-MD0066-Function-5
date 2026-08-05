from __future__ import annotations

from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware

from app.config import Settings, settings
from app.providers import ProviderError
from app.schemas import (
    CardRequest,
    ClaimExtraction,
    ExtractRequest,
    VerificationCard,
    VerificationReport,
    VerifyRequest,
)
from app.service import DemoService
from app.video_frames import FrameExtractionError, ffmpeg_available
from app.wechat_video import WeChatVideoError


def create_app(service: DemoService | None = None, app_settings: Settings = settings) -> FastAPI:
    app = FastAPI(title="好好说 Backend Demo", version="0.1.0")
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_methods=["*"],
        allow_headers=["*"],
    )
    app.state.service = service or DemoService(app_settings)
    app.state.settings = app_settings

    @app.get("/health")
    async def health() -> dict[str, object]:
        return {
            "status": "ok",
            "ffmpeg_available": ffmpeg_available(),
            "luna_configured": bool(
                app.state.settings.luna_api_key and app.state.settings.luna_base_url
            ),
            "tavily_configured": bool(app.state.settings.tavily_api_key),
            "mock_mode": bool(getattr(app.state.service, "mock_mode", False)),
        }

    @app.post("/api/extract", response_model=ClaimExtraction)
    async def extract_claim(payload: ExtractRequest, request: Request) -> ClaimExtraction:
        try:
            return await request.app.state.service.extract_claim(payload)
        except WeChatVideoError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc
        except FrameExtractionError as exc:
            raise HTTPException(status_code=503, detail=str(exc)) from exc
        except ProviderError as exc:
            raise HTTPException(status_code=502, detail=str(exc)) from exc

    @app.post("/api/verify", response_model=VerificationReport)
    async def verify(payload: VerifyRequest, request: Request) -> VerificationReport:
        try:
            return await request.app.state.service.verify(payload)
        except ProviderError as exc:
            raise HTTPException(status_code=502, detail=str(exc)) from exc

    @app.post("/api/card", response_model=VerificationCard)
    async def card(payload: CardRequest, request: Request) -> VerificationCard:
        try:
            return await request.app.state.service.generate_card(payload)
        except ProviderError as exc:
            raise HTTPException(status_code=502, detail=str(exc)) from exc

    return app


app = create_app()
