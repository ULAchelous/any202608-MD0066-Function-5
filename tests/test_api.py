from __future__ import annotations

import asyncio
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app
from app.mock_providers import MockLunaProvider
from app.schemas import (
    ClaimExtraction,
    Communication,
    ExtractRequest,
    VerificationCard,
    VerificationReport,
)
from app.service import DemoService
from app.wechat_video import WeChatVideoInfo


class FakeService:
    mock_mode = False

    async def extract_claim(self, request):
        return ClaimExtraction(
            claim="隔夜菜一定会致癌",
            original_evidence=["隔夜菜致癌"],
            patterns=["夸大因果"],
        )

    async def verify(self, request):
        return VerificationReport(
            claim=request.claim,
            verdict="misleading",
            risk_level="low",
            summary="标题将有条件的食品安全风险夸大成必然致癌。",
            communication=Communication(
                channel="private_chat",
                reason="最近有冲突，优先私聊",
                opening="妈，我知道你是担心我们的身体。",
                fact="这个说法有点太绝对了。",
                suggestion="剩菜及时冷藏并彻底加热更重要。",
            ),
        )

    async def generate_card(self, request):
        return VerificationCard(
            title="安心核验卡：这条说法不太准确",
            greeting="妈，我知道你是担心我们的身体。",
            fact="隔夜菜亚硝酸盐含量远低于有害剂量。",
            suggestion="剩菜及时冷藏、吃前彻底加热就好。",
            self_verify="",
            closing="以后看到这种消息先别急着转。",
        )


class ApiTests(unittest.TestCase):
    def setUp(self) -> None:
        app = create_app(FakeService(), Settings())
        self.client = TestClient(app)

    def test_health(self) -> None:
        response = self.client.get("/health")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["status"], "ok")

    def test_extract(self) -> None:
        response = self.client.post(
            "/api/extract", json={"type": "text", "content": "隔夜菜致癌"}
        )
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["claim"], "隔夜菜一定会致癌")

    def test_verify(self) -> None:
        response = self.client.post(
            "/api/verify",
            json={
                "claim": "隔夜菜一定会致癌",
                "target": "mother",
                "relationship_state": "recent_conflict",
            },
        )
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["verdict"], "misleading")

    def test_card(self) -> None:
        response = self.client.post(
            "/api/card",
            json={
                "claim": "隔夜菜一定会致癌",
                "verdict": "misleading",
                "risk_level": "low",
                "summary": "标题将有条件的食品安全风险夸大成必然致癌。",
                "target": "mother",
                "style": "elder",
            },
        )
        self.assertEqual(response.status_code, 200)
        body = response.json()
        self.assertTrue(body["fact"])
        self.assertTrue(body["medical_notice"])

    def test_card_group_notice(self) -> None:
        response = self.client.post(
            "/api/card",
            json={"claim": "隔夜菜一定会致癌", "style": "group_notice"},
        )
        self.assertEqual(response.status_code, 200)

    def test_wechat_temporary_files_are_removed_after_success(self) -> None:
        service = DemoService(
            Settings(luna_base_url="", luna_api_key="", tavily_api_key="")
        )
        service.luna = MockLunaProvider()
        created_dirs: list[Path] = []
        original_temporary_directory = tempfile.TemporaryDirectory

        class TrackingTemporaryDirectory(original_temporary_directory):
            def __enter__(self):
                value = super().__enter__()
                created_dirs.append(Path(value))
                return value

        async def fake_extract(article_url: str) -> WeChatVideoInfo:
            return WeChatVideoInfo(
                article_url=article_url,
                title="测试视频",
                video_ids=("wxv_1234567890",),
                video_urls=("https://mpvideo.qpic.cn/test.mp4?token=demo",),
            )

        async def fake_download(video_urls, output_path, *, article_url: str) -> int:
            output_path.write_bytes(b"fake-video")
            return output_path.stat().st_size

        async def fake_extract_frames(video_path, output_dir, **kwargs):
            output_dir.mkdir(parents=True)
            frame_path = output_dir / "frame_001.jpg"
            frame_path.write_bytes(b"fake-frame")
            return [frame_path]

        service.wechat.extract = fake_extract
        service.wechat.download = fake_download
        app = create_app(service, Settings())

        with patch(
            "app.service.tempfile.TemporaryDirectory", TrackingTemporaryDirectory
        ), patch("app.service.extract_frames", fake_extract_frames):
            response = TestClient(app).post(
                "/api/extract",
                json={
                    "type": "wechat_url",
                    "content": "https://mp.weixin.qq.com/s/test",
                },
            )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(created_dirs), 1)
        self.assertFalse(created_dirs[0].exists())

    def test_wechat_temporary_files_are_removed_after_failure(self) -> None:
        service = DemoService(
            Settings(luna_base_url="", luna_api_key="", tavily_api_key="")
        )
        created_dirs: list[Path] = []
        original_temporary_directory = tempfile.TemporaryDirectory

        class TrackingTemporaryDirectory(original_temporary_directory):
            def __enter__(self):
                value = super().__enter__()
                created_dirs.append(Path(value))
                return value

        async def fake_extract(article_url: str) -> WeChatVideoInfo:
            return WeChatVideoInfo(
                article_url=article_url,
                title="测试视频",
                video_ids=("wxv_1234567890",),
                video_urls=("https://mpvideo.qpic.cn/test.mp4?token=demo",),
            )

        async def failing_download(video_urls, output_path, *, article_url: str) -> int:
            output_path.write_bytes(b"partial-video")
            raise RuntimeError("simulated download failure")

        service.wechat.extract = fake_extract
        service.wechat.download = failing_download

        with patch(
            "app.service.tempfile.TemporaryDirectory", TrackingTemporaryDirectory
        ):
            with self.assertRaises(RuntimeError):
                service_runner = service.extract_claim(
                    ExtractRequest(
                        type="wechat_url",
                        content="https://mp.weixin.qq.com/s/test",
                    )
                )
                asyncio.run(service_runner)

        self.assertEqual(len(created_dirs), 1)
        self.assertFalse(created_dirs[0].exists())


if __name__ == "__main__":
    unittest.main()
