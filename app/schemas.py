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


class ClaimExtraction(BaseModel):
    claim: str
    original_evidence: list[str] = Field(default_factory=list)
    patterns: list[str] = Field(default_factory=list)
    article_title: str | None = None
    video_id: str | None = None


class VerifyRequest(BaseModel):
    claim: str = Field(min_length=2, max_length=500)
    target: str = Field(default="elder", max_length=30)
    relationship_state: str = Field(default="normal", max_length=30)


class SourceItem(BaseModel):
    title: str
    url: str
    publisher: str = ""
    evidence: str = ""


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
    claim: str = Field(min_length=2, max_length=500)
    verdict: str = Field(default="uncertain")
    risk_level: str = Field(default="low")
    summary: str = Field(default="", max_length=1000)
    target: str = Field(default="elder", max_length=30)
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
