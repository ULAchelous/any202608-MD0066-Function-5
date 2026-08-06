from __future__ import annotations

import base64
import hashlib
import io
import os
import re
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

from app.schemas import CardStyle, VerificationCard


class CardRenderingError(RuntimeError):
    """Raised when a card cannot be rendered as a PNG."""


@dataclass(frozen=True)
class RenderedCard:
    png_bytes: bytes
    width: int
    height: int
    sha256: str

    @property
    def image_base64(self) -> str:
        return base64.b64encode(self.png_bytes).decode("ascii")


_REGULAR_FONT_CANDIDATES = (
    "C:/Windows/Fonts/msyh.ttc",
    "C:/Windows/Fonts/simhei.ttf",
    "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
    "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
    "/System/Library/Fonts/PingFang.ttc",
)
_BOLD_FONT_CANDIDATES = (
    "C:/Windows/Fonts/msyhbd.ttc",
    "C:/Windows/Fonts/simhei.ttf",
    "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc",
    "/usr/share/fonts/truetype/noto/NotoSansCJK-Bold.ttc",
    "/System/Library/Fonts/PingFang.ttc",
)
_SERIF_FONT_CANDIDATES = (
    "C:/Windows/Fonts/simsun.ttc",
    "/usr/share/fonts/opentype/noto/NotoSerifCJK-Bold.ttc",
    "/usr/share/fonts/truetype/noto/NotoSerifCJK-Bold.ttc",
    "/System/Library/Fonts/Songti.ttc",
)


def _resolve_font(explicit: str, candidates: tuple[str, ...]) -> str:
    search = (explicit,) + candidates if explicit else candidates
    for candidate in search:
        if candidate and Path(candidate).is_file():
            return candidate
    raise CardRenderingError(
        "未找到可用的中文字体，请配置 CARD_FONT_PATH/CARD_BOLD_FONT_PATH，"
        "或在 Linux 安装 fonts-noto-cjk。"
    )


def rendering_available(font_path: str = "", bold_font_path: str = "") -> bool:
    try:
        _resolve_font(font_path, _REGULAR_FONT_CANDIDATES)
        _resolve_font(bold_font_path, _BOLD_FONT_CANDIDATES)
        return True
    except CardRenderingError:
        return False


def _font(path: str, size: int) -> ImageFont.FreeTypeFont:
    try:
        return ImageFont.truetype(path, size=size)
    except OSError as exc:
        raise CardRenderingError(f"字体加载失败：{path}") from exc


def _text_width(draw: ImageDraw.ImageDraw, text: str, font: ImageFont.FreeTypeFont) -> float:
    if not text:
        return 0
    box = draw.textbbox((0, 0), text, font=font)
    return float(box[2] - box[0])


def _wrap_text(
    draw: ImageDraw.ImageDraw,
    text: str,
    font: ImageFont.FreeTypeFont,
    max_width: int,
) -> list[str]:
    text = re.sub(r"[\t\r ]+", " ", (text or "").strip())
    if not text:
        return []
    lines: list[str] = []
    for paragraph in text.split("\n"):
        paragraph = paragraph.strip()
        if not paragraph:
            lines.append("")
            continue
        current = ""
        for char in paragraph:
            candidate = current + char
            if current and _text_width(draw, candidate, font) > max_width:
                lines.append(current.rstrip())
                current = char.lstrip() if char == " " else char
            else:
                current = candidate
        if current:
            lines.append(current.rstrip())
    return lines


def _limit_lines(lines: list[str], maximum: int) -> list[str]:
    """Bound canvas growth when a provider or caller returns unexpectedly long copy."""

    if len(lines) <= maximum:
        return lines
    limited = lines[:maximum]
    limited[-1] = limited[-1].rstrip("…") + "…"
    return limited


def _multiline_height(lines: list[str], line_height: int) -> int:
    return max(0, len(lines) * line_height)


def _gradient_background(width: int, height: int, start: str, end: str) -> Image.Image:
    start_rgb = Image.new("RGB", (1, 1), start).getpixel((0, 0))
    end_rgb = Image.new("RGB", (1, 1), end).getpixel((0, 0))
    image = Image.new("RGB", (width, height), start_rgb)
    draw = ImageDraw.Draw(image)
    for y in range(height):
        ratio = y / max(1, height - 1)
        color = tuple(round(a + (b - a) * ratio) for a, b in zip(start_rgb, end_rgb))
        draw.line((0, y, width, y), fill=color)
    return image


def render_card_png(
    card: VerificationCard,
    style: CardStyle,
    *,
    scale: int = 2,
    font_path: str = "",
    bold_font_path: str = "",
) -> RenderedCard:
    """Render a VerificationCard into a mobile-friendly PNG matching the Web card style."""

    if scale not in (1, 2, 3):
        raise CardRenderingError("scale 只支持 1、2 或 3")

    regular_path = _resolve_font(font_path or os.getenv("CARD_FONT_PATH", ""), _REGULAR_FONT_CANDIDATES)
    bold_path = _resolve_font(
        bold_font_path or os.getenv("CARD_BOLD_FONT_PATH", ""), _BOLD_FONT_CANDIDATES
    )
    try:
        serif_path = _resolve_font("", _SERIF_FONT_CANDIDATES)
    except CardRenderingError:
        serif_path = bold_path

    logical_width = 375
    width = logical_width * scale
    outer_pad = 22 * scale
    content_width = width - outer_pad * 2
    title_font = _font(serif_path, 27 * scale)
    body_font = _font(regular_path, 21 * scale)
    label_font = _font(bold_path, 14 * scale)
    source_font = _font(regular_path, 13 * scale)
    notice_font = _font(regular_path, 12 * scale)

    probe = Image.new("RGB", (width, 100), "white")
    measure = ImageDraw.Draw(probe)
    title_lines = _limit_lines(
        _wrap_text(measure, card.title or "安心核验卡", title_font, content_width), 3
    )
    greeting_lines = _limit_lines(_wrap_text(measure, card.greeting, body_font, content_width), 6)
    fact_lines = _limit_lines(_wrap_text(measure, card.fact, body_font, content_width), 10)
    suggestion_lines = _limit_lines(_wrap_text(measure, card.suggestion, body_font, content_width), 10)
    verify_lines = _limit_lines(_wrap_text(measure, card.self_verify, body_font, content_width), 8)
    closing_lines = _limit_lines(_wrap_text(measure, card.closing, body_font, content_width), 6)
    source_text = ""
    if card.sources:
        source_names = [item.publisher or item.title for item in card.sources[:2]]
        source_text = "来源：" + "　·　".join(name for name in source_names if name)
    source_lines = _limit_lines(
        _wrap_text(measure, source_text, source_font, content_width), 3
    )
    notice_lines = _limit_lines(
        _wrap_text(
            measure,
            card.medical_notice or "内容仅供健康信息核验，不能替代医生诊断。",
            notice_font,
            content_width,
        ),
        3,
    )

    title_line_h = 39 * scale
    body_line_h = 38 * scale
    source_line_h = 22 * scale
    notice_line_h = 20 * scale
    section_gap = 15 * scale
    label_h = 27 * scale
    label_to_text = 7 * scale

    height = outer_pad
    height += _multiline_height(title_lines, title_line_h) + 16 * scale
    if greeting_lines:
        height += _multiline_height(greeting_lines, body_line_h) + section_gap
    for lines in (fact_lines, suggestion_lines, verify_lines):
        if lines:
            height += label_h + label_to_text + _multiline_height(lines, body_line_h) + section_gap
    if closing_lines:
        height += _multiline_height(closing_lines, body_line_h) + section_gap
    if source_lines:
        height += 1 * scale + 11 * scale + _multiline_height(source_lines, source_line_h) + 10 * scale
    height += _multiline_height(notice_lines, notice_line_h) + outer_pad
    height = max(height, 420 * scale)

    image = _gradient_background(width, height, "#FFFDF7", "#FCEFE0")
    rounded = Image.new("L", (width, height), 0)
    ImageDraw.Draw(rounded).rounded_rectangle(
        (0, 0, width - 1, height - 1), radius=24 * scale, fill=255
    )
    transparent = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    transparent.paste(image.convert("RGBA"), (0, 0), rounded)
    draw = ImageDraw.Draw(transparent)
    draw.rounded_rectangle(
        (1 * scale, 1 * scale, width - 1 * scale - 1, height - 1 * scale - 1),
        radius=24 * scale,
        outline="#EFD3AC",
        width=max(2, round(2.5 * scale)),
    )

    ink = "#3A2A1B"
    accent = "#D8491B"
    accent_deep = "#B23A12"
    muted = "#8C7A6B"
    faint = "#A3907A"
    y = outer_pad

    for line in title_lines:
        line_w = _text_width(draw, line, title_font)
        draw.text(((width - line_w) / 2, y), line, font=title_font, fill=accent_deep)
        y += title_line_h
    y += 16 * scale

    def draw_body(lines: list[str]) -> None:
        nonlocal y
        for line in lines:
            draw.text((outer_pad, y), line, font=body_font, fill=ink)
            y += body_line_h

    def draw_labeled(label: str, lines: list[str]) -> None:
        nonlocal y
        if not lines:
            return
        label_w = int(_text_width(draw, label, label_font) + 22 * scale)
        draw.rounded_rectangle(
            (outer_pad, y, outer_pad + label_w, y + label_h),
            radius=8 * scale,
            fill=accent,
        )
        draw.text((outer_pad + 11 * scale, y + 3 * scale), label, font=label_font, fill="white")
        y += label_h + label_to_text
        draw_body(lines)
        y += section_gap

    if greeting_lines:
        draw_body(greeting_lines)
        y += section_gap
    draw_labeled("事 实", fact_lines)
    draw_labeled("怎么做", suggestion_lines)
    draw_labeled("自己动手查", verify_lines)
    if closing_lines:
        draw_body(closing_lines)
        y += section_gap
    if source_lines:
        draw.line((outer_pad, y, width - outer_pad, y), fill="#E3C89E", width=scale)
        y += 11 * scale
        for line in source_lines:
            draw.text((outer_pad, y), line, font=source_font, fill=muted)
            y += source_line_h
        y += 10 * scale
    for line in notice_lines:
        line_w = _text_width(draw, line, notice_font)
        draw.text(((width - line_w) / 2, y), line, font=notice_font, fill=faint)
        y += notice_line_h

    output = io.BytesIO()
    transparent.save(output, format="PNG", optimize=True)
    png_bytes = output.getvalue()
    return RenderedCard(
        png_bytes=png_bytes,
        width=width,
        height=height,
        sha256=hashlib.sha256(png_bytes).hexdigest(),
    )
