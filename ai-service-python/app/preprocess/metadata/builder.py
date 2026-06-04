from __future__ import annotations

from typing import Any

from app.preprocess.ir.models import IRBlock


def build_block_metadata(block: IRBlock, parser: str, chunk_index: int, source_type: str, extra: dict[str, Any] | None = None) -> dict[str, Any]:
    metadata = dict(block.metadata)
    metadata.update(extra or {})
    metadata.setdefault("parser", parser)
    metadata.setdefault("sourceType", source_type)
    metadata.setdefault("chunkIndex", chunk_index)
    metadata.setdefault("sectionPath", block.section_path)
    metadata.setdefault("span", block.span.to_dict())
    metadata.setdefault("unitType", block.block_type)
    return metadata
