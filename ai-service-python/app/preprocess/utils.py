from __future__ import annotations

import hashlib
import re
import uuid
from typing import Any


def normalize_text(value: str) -> str:
    return re.sub(r"\s+", " ", value.replace("\u3000", " ")).strip()


def content_hash(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def block_id(source_id: Any, block_type: str, normalized: str, page_no: int | None, row_no: int | None, col_no: int | None) -> str:
    material = "|".join([str(source_id or ""), block_type, normalized, str(page_no or ""), str(row_no or ""), str(col_no or "")])
    return str(uuid.uuid5(uuid.NAMESPACE_URL, material))


def block_envelope(source_id: Any, graph_category_id: Any, block_type: str, raw: str, normalized: str, page_no: int, row_no: int | None, col_no: int | None, metadata: dict[str, Any]) -> dict[str, Any]:
    material = "|".join([str(source_id or ""), block_type, normalized, str(page_no), str(row_no or ""), str(col_no or "")])
    return {
        "blockId": str(uuid.uuid5(uuid.NAMESPACE_URL, material)),
        "sourceId": source_id,
        "graphCategoryId": graph_category_id,
        "blockType": block_type,
        "unitType": metadata.get("unitType") or block_type,
        "sectionPath": metadata.get("sectionPath", ""),
        "pageNo": page_no,
        "rowNo": row_no,
        "colNo": col_no,
        "rawText": raw,
        "normalizedText": normalized,
        "summary": metadata.get("summary") or normalized[:120],
        "contentHash": hashlib.sha256(material.encode("utf-8")).hexdigest(),
        "metadata": metadata,
        "qualityScore": metadata.get("qualityScore", 1.0),
        "qualityFlags": metadata.get("qualityFlags", []),
    }
