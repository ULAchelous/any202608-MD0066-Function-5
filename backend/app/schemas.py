from __future__ import annotations

from enum import Enum

from pydantic import BaseModel, Field, HttpUrl, model_validator


class SourceType(str, Enum):
    WECHAT_URL = "wechat_url"
    TEXT = "text"
    IMAGE = "image"


class ExtractRequest(BaseModel):
    type: SourceType
    content: str = Field(min_length=1)

    @model_validator(mode="after")
    def validate_content(self) -> "ExtractRequest":
        if self.type == SourceType.WECHAT_URL:
            HttpUrl(self.content)
        if self.type == SourceType.IMAGE and not self.content.startswith(
            ("data:image/", "iVBOR", "/9j/")
        ):
            raise ValueError("image content must be a Base64 image or data URL")
        return self


class ClaimCandidate(BaseModel):
    claim: str = Field(min_length=2, max_length=2000)
    evidence: str = ""
    risk_hint: str = "medium"  # low / medium / high，仅用于候选排序提示


class ClaimExtraction(BaseModel):
    # claim 保留为默认/最高风险主张，兼容旧客户端；claims 供新客户端选择。
    claim: str
    claims: list[ClaimCandidate] = Field(default_factory=list)
    original_evidence: list[str] = Field(default_factory=list)
    patterns: list[str] = Field(default_factory=list)
    topic_summary: str = ""
    search_keywords: list[str] = Field(default_factory=list)
    audience: str = ""
    emotional_tone: str = ""
    visual_notes: str = ""
    article_title: str | None = None
    article_author: str | None = None
    source_kind: str | None = None  # wechat_article / wechat_video / text / image
    video_id: str | None = None


class VerifyRequest(BaseModel):
    claim: str = Field(min_length=2, max_length=2000)
    target: str = Field(default="elder", max_length=100)
    # 前端会把“关系状态 + 用户补充信息”合并传入；真实场景可能包含
    # 用药、病史、群聊背景等完整描述，30 字会误伤正常输入。
    relationship_state: str = Field(default="normal", max_length=2000)
    search_keywords: list[str] = Field(default_factory=list, max_length=10)


class SourceItem(BaseModel):
    title: str
    url: str
    publisher: str = ""
    evidence: str = ""
    authority_level: str = "other"  # institution / official_factcheck / authoritative_media / research / other
    authority_label: str = "其他来源"
    published_at: str = ""


class Communication(BaseModel):
    channel: str
    reason: str
    opening: str
    fact: str
    suggestion: str


class VerificationReport(BaseModel):
    claim: str
    verdict: str
    risk_level: str
    summary: str
    patterns: list[str] = Field(default_factory=list)
    sources: list[SourceItem] = Field(default_factory=list)
    communication: Communication
    medical_notice: str = "内容仅供健康信息核验，不能替代医生诊断。"


class CardStyle(str, Enum):
    ELDER = "elder"                # 给长辈的安心核验卡：称呼亲昵、语气柔和
    GROUP_NOTICE = "group_notice"  # 群公告版：中性科普、不带称呼


class CardRequest(BaseModel):
    claim: str = Field(min_length=2, max_length=2000)
    verdict: str = Field(default="uncertain", max_length=30)
    risk_level: str = Field(default="low", max_length=30)
    summary: str = Field(default="", max_length=5000)
    target: str = Field(default="elder", max_length=100)
    style: CardStyle = CardStyle.ELDER
    sources: list[SourceItem] = Field(default_factory=list)


class VerificationCard(BaseModel):
    """安心核验卡：给长辈看的最终产物，大字、短句、非冒犯。"""

    title: str
    greeting: str           # 共情开场（群公告版为空字符串）
    fact: str               # 一句话事实
    suggestion: str         # 具体可以怎么做
    self_verify: str = ""   # 长辈可亲手验证的方法，没有则为空
    closing: str            # 收尾安抚
    sources: list[SourceItem] = Field(default_factory=list)
    medical_notice: str = "内容仅供健康信息核验，不能替代医生诊断。"


class CardImageRequest(CardRequest):
    """生成卡片文案并在服务端渲染 PNG。"""

    scale: int = Field(default=2, ge=1, le=3)
    include_data_url: bool = False


class CardImageResponse(BaseModel):
    card: VerificationCard
    style: CardStyle
    mime_type: str = "image/png"
    filename: str
    width: int
    height: int
    byte_size: int
    sha256: str
    image_base64: str
    data_url: str | None = None
