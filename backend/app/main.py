from __future__ import annotations

import logging
from time import perf_counter
from uuid import uuid4

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.card_renderer import CardRenderingError, rendering_available
from app.config import Settings, settings
from app.providers import ProviderError
from app.schemas import (
    CardImageRequest,
    CardImageResponse,
    CardRequest,
    ClaimExtraction,
    ExtractRequest,
    VerificationCard,
    VerificationReport,
    VerifyRequest,
)
from app.service import DemoService
from app.utils.logging import (
    bind_request_id,
    get_logger,
    get_request_id,
    log_event,
    reset_request_id,
    setup_logging,
)
from app.video_frames import FrameExtractionError, ffmpeg_available
from app.wechat_video import WeChatVideoError


setup_logging()
logger = get_logger("haohuoshuo.api")


class ApiError(HTTPException):
    def __init__(self, status_code: int, code: str, message: str) -> None:
        super().__init__(status_code=status_code, detail=message)
        self.code = code
        self.message = message


def _error_response(
    *,
    status_code: int,
    code: str,
    message: str,
    detail: str | None = None,
) -> JSONResponse:
    request_id = get_request_id()
    content = {"code": code, "message": message, "request_id": request_id}
    if detail:
        content["detail"] = detail
    return JSONResponse(
        status_code=status_code,
        content=content,
        headers={"X-Request-ID": request_id},
    )


def create_app(service: DemoService | None = None, app_settings: Settings = settings) -> FastAPI:
    app = FastAPI(title="好好说 Backend V5.0 Preview", version="5.0.0-preview")
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_methods=["*"],
        allow_headers=["*"],
    )
    app.state.service = service or DemoService(app_settings)
    app.state.settings = app_settings

    @app.exception_handler(RequestValidationError)
    async def validation_exception_handler(
        request: Request, exc: RequestValidationError
    ) -> JSONResponse:
        log_event(
            logger,
            logging.WARNING,
            "request.validation_failed",
            method=request.method,
            route=request.url.path,
            status_code=422,
            error_count=len(exc.errors()),
        )
        return _error_response(
            status_code=422,
            code="REQUEST_VALIDATION_ERROR",
            message="请求内容格式不正确，请检查后重试。",
            detail="请求参数校验失败",
        )

    @app.exception_handler(HTTPException)
    async def http_exception_handler(request: Request, exc: HTTPException) -> JSONResponse:
        code = getattr(exc, "code", None) or f"HTTP_{exc.status_code}"
        message = getattr(exc, "message", None) or (
            exc.detail if isinstance(exc.detail, str) else "请求处理失败，请稍后重试。"
        )
        detail = exc.detail if isinstance(exc.detail, str) else None
        return _error_response(
            status_code=exc.status_code,
            code=code,
            message=message,
            detail=detail,
        )

    @app.exception_handler(Exception)
    async def unhandled_exception_handler(request: Request, exc: Exception) -> JSONResponse:
        log_event(
            logger,
            logging.ERROR,
            "request.unhandled_error",
            method=request.method,
            route=request.url.path,
            status_code=500,
            error_category=exc.__class__.__name__,
        )
        return _error_response(
            status_code=500,
            code="INTERNAL_SERVER_ERROR",
            message="服务暂时出现问题，请稍后重试或凭请求编号反馈。",
        )

    @app.middleware("http")
    async def request_observability(request: Request, call_next):
        request_id = request.headers.get("X-Request-ID", "").strip()[:128] or uuid4().hex
        token = bind_request_id(request_id)
        started = perf_counter()
        log_event(
            logger,
            logging.INFO,
            "request.started",
            method=request.method,
            route=request.url.path,
        )
        try:
            response = await call_next(request)
            response.headers["X-Request-ID"] = request_id
            log_event(
                logger,
                logging.INFO,
                "request.completed",
                method=request.method,
                route=request.url.path,
                status_code=response.status_code,
                elapsed_ms=round((perf_counter() - started) * 1000, 2),
            )
            return response
        except Exception as exc:
            log_event(
                logger,
                logging.ERROR,
                "request.failed",
                method=request.method,
                route=request.url.path,
                error_category=exc.__class__.__name__,
                elapsed_ms=round((perf_counter() - started) * 1000, 2),
            )
            raise
        finally:
            reset_request_id(token)

    @app.get("/health")
    async def health() -> dict[str, object]:
        model_routes = getattr(app.state.service, "model_routes", {})
        return {
            "status": "ok",
            "ffmpeg_available": ffmpeg_available(),
            "luna_configured": bool(
                app.state.settings.luna_api_key and app.state.settings.luna_base_url
            ),
            "tavily_configured": bool(app.state.settings.tavily_api_key),
            "exa_configured": bool(app.state.settings.exa_api_key),
            "mock_mode": bool(getattr(app.state.service, "mock_mode", False)),
            "card_rendering_available": rendering_available(
                app.state.settings.card_font_path,
                app.state.settings.card_bold_font_path,
            ),
            "api_version": "5.0.0-preview",
            "model_routes": model_routes,
        }

    @app.post("/api/extract", response_model=ClaimExtraction)
    async def extract_claim(payload: ExtractRequest, request: Request) -> ClaimExtraction:
        try:
            return await request.app.state.service.extract_claim(payload)
        except WeChatVideoError as exc:
            log_event(
                logger,
                logging.WARNING,
                "extract.rejected",
                error_category=exc.__class__.__name__,
                status_code=422,
                input_type=payload.type.value,
            )
            raise ApiError(
                status_code=422,
                code="WECHAT_CONTENT_UNREADABLE",
                message="无法读取该公众号内容，请检查链接或复制正文后重试。",
            ) from exc
        except FrameExtractionError as exc:
            log_event(
                logger,
                logging.ERROR,
                "extract.failed",
                error_category=exc.__class__.__name__,
                status_code=503,
                input_type=payload.type.value,
            )
            raise ApiError(
                status_code=503,
                code="VIDEO_PROCESSING_UNAVAILABLE",
                message="视频画面暂时无法处理，请稍后重试或改用文字内容。",
            ) from exc
        except ProviderError as exc:
            log_event(
                logger,
                logging.ERROR,
                "extract.failed",
                error_category=exc.__class__.__name__,
                status_code=502,
                input_type=payload.type.value,
            )
            raise ApiError(
                status_code=502,
                code="CONTENT_ANALYSIS_UNAVAILABLE",
                message="健康信息解析服务暂时不可用，请稍后重试。",
            ) from exc

    @app.post("/api/verify", response_model=VerificationReport)
    async def verify(payload: VerifyRequest, request: Request) -> VerificationReport:
        try:
            return await request.app.state.service.verify(payload)
        except ProviderError as exc:
            log_event(
                logger,
                logging.ERROR,
                "verification.failed",
                error_category=exc.__class__.__name__,
                status_code=502,
                claim_length=len(payload.claim),
            )
            raise ApiError(
                status_code=502,
                code="VERIFICATION_UNAVAILABLE",
                message="健康信息核验服务暂时不可用，请稍后重试。",
            ) from exc

    @app.post("/api/card", response_model=VerificationCard)
    async def card(payload: CardRequest, request: Request) -> VerificationCard:
        try:
            return await request.app.state.service.generate_card(payload)
        except ProviderError as exc:
            log_event(
                logger,
                logging.ERROR,
                "card.failed",
                error_category=exc.__class__.__name__,
                status_code=502,
                claim_length=len(payload.claim),
            )
            raise ApiError(
                status_code=502,
                code="CARD_GENERATION_UNAVAILABLE",
                message="安心核验卡暂时无法生成，请稍后重试。",
            ) from exc

    @app.post("/api/card/image", response_model=CardImageResponse)
    async def card_image(payload: CardImageRequest, request: Request) -> CardImageResponse:
        try:
            return await request.app.state.service.generate_card_image(payload)
        except ProviderError as exc:
            log_event(
                logger,
                logging.ERROR,
                "card.image.failed",
                error_category=exc.__class__.__name__,
                status_code=502,
                claim_length=len(payload.claim),
            )
            raise ApiError(
                status_code=502,
                code="CARD_GENERATION_UNAVAILABLE",
                message="安心核验卡暂时无法生成，请稍后重试。",
            ) from exc
        except CardRenderingError as exc:
            log_event(
                logger,
                logging.ERROR,
                "card.image.failed",
                error_category=exc.__class__.__name__,
                status_code=503,
                claim_length=len(payload.claim),
            )
            raise ApiError(
                status_code=503,
                code="CARD_RENDERING_UNAVAILABLE",
                message="安心核验卡图片暂时无法生成，请稍后重试。",
            ) from exc

    return app


app = create_app()
