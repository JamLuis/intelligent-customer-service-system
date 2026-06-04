from typing import Any, Literal

from pydantic import BaseModel, Field


class BlockEnvelope(BaseModel):
    blockId: str
    sourceId: str | int | None = None
    graphCategoryId: str | int | None = None
    blockType: str
    sectionPath: str = ""
    pageNo: int | None = None
    rowNo: int | None = None
    colNo: int | None = None
    rawText: str
    normalizedText: str
    contentHash: str
    metadata: dict[str, Any] = Field(default_factory=dict)


class ParseKnowledgeRequest(BaseModel):
    sourceId: str | int | None = None
    sourceType: str = "text"
    fileName: str = ""
    graphCategoryId: str | int | None = None
    rawText: str = ""


class ParseKnowledgeResponse(BaseModel):
    sourceId: str | int | None = None
    blocks: list[BlockEnvelope]
    degraded: bool = False


class EmbedBlockRequest(BaseModel):
    blockId: str
    text: str


class EmbedKnowledgeRequest(BaseModel):
    blocks: list[EmbedBlockRequest] = Field(default_factory=list)
    llmConfig: dict[str, Any] | None = None


class EmbedItem(BaseModel):
    blockId: str
    vector: list[float]


class EmbedKnowledgeResponse(BaseModel):
    embeddings: list[EmbedItem]
    embeddingModel: str
    embeddingVersion: str
    embeddingDim: int


class EvidenceRef(BaseModel):
    blockId: str
    weight: float | None = None
    sourceType: str | None = None


class CandidateEntity(BaseModel):
    tempId: str
    blockId: str | None = None
    graphCategoryId: str | int | None = None
    entityType: str
    rawName: str
    canonicalName: str
    uniqueKey: dict[str, Any] = Field(default_factory=dict)
    properties: dict[str, Any] = Field(default_factory=dict)
    evidence: list[EvidenceRef] = Field(default_factory=list)
    evidenceBlockIds: list[str] = Field(default_factory=list)
    confidence: float
    extractor: str
    status: Literal["accepted", "reviewing", "rejected", "merged"] = "reviewing"


class CandidateRelation(BaseModel):
    sourceTempId: str
    targetTempId: str
    graphCategoryId: str | int | None = None
    relationType: str
    properties: dict[str, Any] = Field(default_factory=dict)
    evidence: list[EvidenceRef] = Field(default_factory=list)
    evidenceBlockIds: list[str] = Field(default_factory=list)
    confidence: float
    extractor: str
    status: Literal["accepted", "reviewing", "rejected", "merged"] = "reviewing"


class ExtractKnowledgeRequest(BaseModel):
    sourceId: str | int | None = None
    fileName: str = ""
    graphCategoryId: str | int | None = None
    blocks: list[dict[str, Any]] = Field(default_factory=list)
    taxonomy: dict[str, Any] = Field(default_factory=dict)
    enableLlmExtract: bool | None = None
    llmConfig: dict[str, Any] | None = None


class ExtractKnowledgeResponse(BaseModel):
    candidateEntities: list[CandidateEntity]
    candidateRelations: list[CandidateRelation]
    degraded: bool = False
    llmExtractedBlocks: int = 0
    llmExtractBlockBudget: int = 0
