from __future__ import annotations

import asyncio
import shutil
from pathlib import Path


class FrameExtractionError(RuntimeError):
    pass


def ffmpeg_available() -> bool:
    return shutil.which("ffmpeg") is not None


async def probe_duration(video_path: Path) -> float | None:
    """Return video duration in seconds, or None if ffprobe is unavailable/fails."""
    ffprobe = shutil.which("ffprobe")
    if not ffprobe:
        return None
    command = [
        ffprobe,
        "-v",
        "error",
        "-show_entries",
        "format=duration",
        "-of",
        "default=noprint_wrappers=1:nokey=1",
        str(video_path),
    ]
    process = await asyncio.create_subprocess_exec(
        *command,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )
    try:
        stdout, _ = await asyncio.wait_for(process.communicate(), timeout=30)
    except TimeoutError:
        process.kill()
        await process.communicate()
        return None
    if process.returncode != 0:
        return None
    try:
        duration = float(stdout.decode("utf-8", errors="replace").strip())
    except ValueError:
        return None
    return duration if duration > 0 else None


async def extract_frames(
    video_path: Path,
    output_dir: Path,
    *,
    interval_seconds: float = 3,
    max_frames: int = 16,
) -> list[Path]:
    """Extract frames uniformly across the whole video.

    interval_seconds is the *minimum* spacing between frames: short videos use it
    directly, long videos are sampled at max_frames points spread over the full
    duration so the model always sees the whole video instead of just the opening.
    """
    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        raise FrameExtractionError("服务器未安装 FFmpeg，无法从视频抽帧")

    min_interval = max(float(interval_seconds), 0.1)
    duration = await probe_duration(video_path)
    if duration:
        fps = min(1.0 / min_interval, max_frames / duration)
    else:
        fps = 1.0 / min_interval

    output_dir.mkdir(parents=True, exist_ok=True)
    output_pattern = output_dir / "frame_%03d.jpg"
    command = [
        ffmpeg,
        "-hide_banner",
        "-loglevel",
        "error",
        "-i",
        str(video_path),
        "-vf",
        f"fps={fps:.4f},scale=1280:-2:force_original_aspect_ratio=decrease",
        "-frames:v",
        str(max_frames),
        "-q:v",
        "3",
        str(output_pattern),
    ]
    process = await asyncio.create_subprocess_exec(
        *command,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )
    try:
        _, stderr = await asyncio.wait_for(process.communicate(), timeout=90)
    except TimeoutError:
        process.kill()
        await process.communicate()
        raise FrameExtractionError("视频抽帧超时") from None

    if process.returncode != 0:
        raise FrameExtractionError(stderr.decode("utf-8", errors="replace")[-1000:])
    frames = sorted(output_dir.glob("frame_*.jpg"))
    if not frames:
        raise FrameExtractionError("视频中没有提取到画面")
    return frames
