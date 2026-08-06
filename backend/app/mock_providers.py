"""Offline heuristic providers used when LUNA/TAVILY credentials are missing.

These let the demo and tests run end-to-end without API keys. They never claim
to be accurate — every response is deterministic and marked as offline mode.
"""

from __future__ import annotations

import re
from pathlib import Path

from app.providers import ProviderError
from app.schemas import (
    CardRequest,
    CardStyle,
    ClaimCandidate,
    ClaimExtraction,
    Communication,
    SourceItem,
    VerificationCard,
    VerificationReport,
)


def pick_channel(relationship_state: str, risk_level: str) -> str:
    """四种沟通渠道：私聊/群公告/亲属转达/暂不回应。"""
    if relationship_state in {"recent_conflict", "distant"}:
        return "via_relative" if risk_level == "high" else "private_chat"
    if risk_level == "high":
        return "family_group"
    return "private_chat"

_PATTERN_KEYWORDS: list[tuple[str, list[str]]] = [
    ("夸大因果", ["一定", "必然", "百分百", "彻底", "根治"]),
    ("恐惧驱动", ["致癌", "猝死", "要命", "千万", "赶紧", "再不看", "震惊"]),
    ("冒用权威", ["专家", "院士", "哈佛", "世卫", "央视"]),
    ("伪科学术语", ["排毒", "酸碱", "量子", "磁场", "负离子"]),
    ("情感绑架", ["为家人", "为了孩子", "父母", "转给你爱的人"]),
]


def detect_patterns(text: str) -> list[str]:
    return [name for name, keywords in _PATTERN_KEYWORDS if any(k in text for k in keywords)]


def _candidate_sentences(text: str, limit: int = 5) -> list[str]:
    """离线演示按句子提取多个候选，去重并跳过无信息短句。"""
    cleaned = re.sub(r"\s+", " ", text).strip()
    candidates: list[str] = []
    for part in re.split(r"[。！？!?；;\n]", cleaned):
        sentence = part.strip(" ，,、~!\"'“”")
        if len(sentence) < 8 or sentence in candidates:
            continue
        candidates.append(sentence[:160])
        if len(candidates) >= limit:
            break
    return candidates


def _first_sentence(text: str, fallback: str = "") -> str:
    cleaned = re.sub(r"\s+", " ", text).strip()
    if not cleaned:
        return fallback
    # 跳过“震惊！”“注意！”这类没有信息量的短句，取第一条实质内容
    for part in re.split(r"[。！？!?\n]", cleaned):
        sentence = part.strip(" ，,、~!")
        if len(sentence) >= 6:
            return sentence[:80]
    return cleaned[:80]


class MockLunaProvider:
    """Heuristic stand-in for LunaProvider (offline demo mode)."""

    async def extract_from_text(self, text: str, *, title: str = "") -> ClaimExtraction:
        sentences = _candidate_sentences(text)
        claim = sentences[0] if sentences else _first_sentence(title, "未识别到主张")
        candidates = [
            ClaimCandidate(
                claim=s,
                evidence=s,
                risk_hint="high" if any(k in s for k in ["致癌", "中毒", "停药", "伤身"]) else "medium",
            )
            for s in (sentences or [claim])
        ]
        return ClaimExtraction(
            claim=claim,
            claims=candidates,
            original_evidence=[claim] if claim != "未识别到主张" else [],
            patterns=detect_patterns(text + title),
        )

    async def extract_from_image(self, image_data_url: str) -> ClaimExtraction:
        raise ProviderError("离线模式不支持图片识别，请配置视觉模型密钥")

    async def extract_from_frames(
        self, frames: list[Path], *, title: str, video_id: str | None
    ) -> ClaimExtraction:
        claim = _first_sentence(title, fallback=f"离线模式：已抽取 {len(frames)} 帧，未配置视觉模型")
        return ClaimExtraction(
            claim=claim,
            claims=[ClaimCandidate(claim=claim, evidence="", risk_hint="medium")],
            original_evidence=[],
            patterns=detect_patterns(title),
            article_title=title,
            video_id=video_id,
        )

    async def generate_card(self, request: CardRequest) -> VerificationCard:
        verdict_label = {
            "misleading": "这条说法不太准确",
            "credible": "这条说法基本可信",
            "uncertain": "这条说法暂时没法确定",
        }.get(request.verdict, "这条说法暂时没法确定")
        if request.style == CardStyle.GROUP_NOTICE:
            return VerificationCard(
                title=f"健康信息小科普：{verdict_label}",
                greeting="",
                fact=request.summary or f"关于「{request.claim}」，{verdict_label}。",
                suggestion="大家看到类似说法，可以先查一查权威机构发布的信息再决定要不要转发。",
                self_verify="",
                closing="一起维护靠谱的群环境，谢谢理解。",
                sources=request.sources[:3],
            )
        return VerificationCard(
            title=f"安心核验卡：{verdict_label}",
            greeting="妈，我知道你发这个是为了我们好，怕我们吃出不健康。",
            fact=request.summary or f"「{request.claim}」这个说法把风险说得太绝对了。",
            suggestion="真正要注意的是剩菜及时放冰箱、吃之前彻底加热，不用整盘倒掉。",
            self_verify="",
            closing="以后看到这种消息先别急着转，我帮你查一查，好吗？",
            sources=request.sources[:3],
        )

    async def verify(
        self,
        claim: str,
        target: str,
        relationship_state: str,
        sources: list[SourceItem],
    ) -> VerificationReport:
        patterns = detect_patterns(claim)
        verdict = "misleading" if patterns else "uncertain"
        if len(sources) < 2:
            verdict = "uncertain"
        risk = "medium" if "恐惧驱动" in patterns else "low"
        channel = pick_channel(relationship_state, risk)
        return VerificationReport(
            claim=claim,
            verdict=verdict,
            risk_level=risk,
            summary=f"（离线演示结果）该说法包含「{'、'.join(patterns) or '待核验'}」特征，建议参考权威机构来源进一步核实。",
            patterns=patterns,
            sources=sources[:3],
            communication=Communication(
                channel=channel,
                reason="根据关系状态选择沟通渠道（离线演示）",
                opening="妈，我知道你是担心我们的健康，看到这种消息肯定想第一时间提醒我们。",
                fact=f"「{claim}」这个说法把一些有条件的风险说得太绝对了。",
                suggestion="我把权威机构的说明找给你看，以后咱们先核实再转发，好不好？",
            ),
        )


class MockTavilyProvider:
    """Deterministic stand-in for TavilyProvider (offline demo mode)."""

    async def search(self, claim: str) -> list[SourceItem]:
        return [
            SourceItem(
                title=f"关于「{claim[:20]}」的科学解读（演示数据）",
                url="https://www.nhc.gov.cn/example/demo-evidence",
                publisher="nhc.gov.cn",
                evidence="离线演示占位证据：真实模式下此处为 Tavily 检索到的权威机构网页摘要。",
            ),
            SourceItem(
                title="健康谣言识别与辟谣指南（演示数据）",
                url="https://www.chinacdc.cn/example/demo-guide",
                publisher="chinacdc.cn",
                evidence="离线演示占位证据：配置 TAVILY_API_KEY 后将替换为真实检索结果。",
            ),
        ]


class MockExaProvider:
    """Deterministic stand-in for ExaProvider (offline demo mode)."""

    async def search(self, claim: str) -> list[SourceItem]:
        return [
            SourceItem(
                title=f"「{claim[:20]}」的权威信源核验（Exa 演示数据）",
                url="https://www.who.int/example/demo-exa",
                publisher="who.int",
                evidence="离线演示占位证据：真实模式下此处为 Exa 检索到的权威机构内容高亮片段。",
                authority_level="institution",
                authority_label="政府 / 权威机构",
                published_at="2026-01-01T00:00:00.000Z",
            ),
            SourceItem(
                title="医药健康信息辟谣专栏（Exa 演示数据）",
                url="https://www.piyao.org.cn/example/demo-exa-2",
                publisher="piyao.org.cn",
                evidence="离线演示占位证据：配置 EXA_API_KEY 后将替换为真实检索结果。",
                authority_level="official_factcheck",
                authority_label="官方辟谣平台",
                published_at="2026-01-02T00:00:00.000Z",
            ),
        ]
