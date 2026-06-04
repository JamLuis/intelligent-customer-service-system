from typing import Any

from pydantic import BaseModel, Field


class ChatAnswerRequest(BaseModel):
    questionText: str = ""
    vectorEvidence: list[Any] = Field(default_factory=list)
    graphPaths: list[Any] = Field(default_factory=list)
    forceGraphGrounding: bool = True
    llmConfig: dict[str, Any] | None = None


class ChatAnswerResponse(BaseModel):
    answer: str
    confidence: float
    evidenceRefs: list[Any] = Field(default_factory=list)
    graphPaths: list[Any] = Field(default_factory=list)
    missingContext: list[Any] = Field(default_factory=list)
    cannotAnswerReason: str | None = None
    modelProfileId: str = ""
    forceGraphGrounding: bool = True
    degraded: bool = False
