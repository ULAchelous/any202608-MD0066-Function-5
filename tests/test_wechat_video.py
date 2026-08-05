from __future__ import annotations

import unittest

from app.wechat_video import WeChatVideoError, WeChatVideoExtractor


class WeChatVideoExtractorTests(unittest.TestCase):
    def test_extracts_iframe_id_title_and_escaped_mp4_url(self) -> None:
        page = r'''
        <meta property="og:title" content="最垃圾食品排行">
        <iframe data-mpvid="wxv_3707306132730150913"></iframe>
        <script>
        var url="https://mpvideo.qpic.cn/a.f10004.mp4?dis_k=abc\x26amp;auth_key=xyz\x22";
        </script>
        '''
        info = WeChatVideoExtractor.parse_html(
            page, "https://mp.weixin.qq.com/s/example"
        )
        self.assertEqual(info.title, "最垃圾食品排行")
        self.assertEqual(info.video_ids, ("wxv_3707306132730150913",))
        self.assertEqual(
            info.video_urls,
            ("https://mpvideo.qpic.cn/a.f10004.mp4?dis_k=abc&auth_key=xyz",),
        )

    def test_upgrades_http_mpvideo_url_to_https(self) -> None:
        page = (
            r"var url='http://mpvideo.qpic.cn/a.f10002.mp4?"
            r"dis_k=abc\x26amp;auth_key=xyz';"
        )
        info = WeChatVideoExtractor.parse_html(
            page, "https://mp.weixin.qq.com/s/example"
        )
        self.assertEqual(
            info.video_urls,
            ("https://mpvideo.qpic.cn/a.f10002.mp4?dis_k=abc&auth_key=xyz",),
        )

    def test_rejects_non_wechat_url(self) -> None:
        with self.assertRaises(WeChatVideoError):
            WeChatVideoExtractor.validate_article_url("https://example.com/video")

    def test_raises_when_article_has_no_video(self) -> None:
        with self.assertRaises(WeChatVideoError):
            WeChatVideoExtractor.parse_html(
                "<html></html>", "https://mp.weixin.qq.com/s/empty"
            )


if __name__ == "__main__":
    unittest.main()
