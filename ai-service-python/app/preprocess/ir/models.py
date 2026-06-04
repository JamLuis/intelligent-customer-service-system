from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass(frozen=True)
class IRSpan:
    start: int | None = None
    end: int | None = None
    line_no: int | None = None
    page_no: int | None = None
    row_no: int | None = None
    col_no: int | None = None

    def to_dict(self) -> dict[str, Any]:
        return {
            "start": self.start,
            "end": self.end,
            "lineNo": self.line_no,
            "pageNo": self.page_no,
            "rowNo": self.row_no,
            "colNo": self.col_no,
        }


@dataclass(frozen=True)
class IRAsset:
    asset_id: str
    asset_type: str
    uri: str = ""
    metadata: dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        return {
            "assetId": self.asset_id,
            "assetType": self.asset_type,
            "uri": self.uri,
            "metadata": self.metadata,
        }


@dataclass(frozen=True)
class IRBlock:
    block_type: str
    raw_text: str
    normalized_text: str
    span: IRSpan = field(default_factory=IRSpan)
    section_path: str = ""
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class IRDoc:
    source_id: Any
    graph_category_id: Any
    source_type: str
    parser: str
    blocks: list[IRBlock]
    assets: list[IRAsset] = field(default_factory=list)
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class QualityReport:
    score: float
    flags: list[str] = field(default_factory=list)

    def to_dict(self) -> dict[str, Any]:
        return {"score": self.score, "flags": self.flags}


@dataclass(frozen=True)
class ParseArtifact:
    source_id: Any
    source_type: str
    parser: str
    blocks: list[dict[str, Any]]
    metadata: dict[str, Any] = field(default_factory=dict)
    quality: QualityReport = field(default_factory=lambda: QualityReport(score=1.0))

    def to_response(self) -> dict[str, Any]:
        return {
            "sourceId": self.source_id,
            "blocks": self.blocks,
            "artifact": {
                "sourceType": self.source_type,
                "parser": self.parser,
                "metadata": self.metadata,
                "quality": self.quality.to_dict(),
            },
            "degraded": False,
        }
