from __future__ import annotations

from typing import Any


DEFAULT_EVIDENCE_WEIGHTS: dict[str, float] = {
    "table_cell": 1.0,
    "paragraph": 0.85,
    "title": 0.9,
    "list_item": 0.75,
    "json_field": 0.95,
    "csv_cell": 0.95,
    "kv_pair": 0.9,
    "table_caption": 0.7,
    "ocr": 0.4,
    "code": 0.5,
    "code_comment": 0.5,
}


def normalize_source_type(value: str | None) -> str:
    source_type = (value or "paragraph").strip()
    if source_type == "image_ocr":
        return "ocr"
    if source_type == "table":
        return "table_cell"
    if source_type == "json":
        return "json_field"
    if source_type == "csv":
        return "csv_cell"
    if source_type == "kv":
        return "kv_pair"
    return source_type if source_type in DEFAULT_EVIDENCE_WEIGHTS else "paragraph"


def clamp_weight(value: Any, source_type: str) -> float:
    if value is None:
        return DEFAULT_EVIDENCE_WEIGHTS[source_type]
    try:
        return max(0.0, min(1.0, float(value)))
    except (TypeError, ValueError):
        return DEFAULT_EVIDENCE_WEIGHTS[source_type]


def build_evidence(block: dict[str, Any]) -> dict[str, Any]:
    source_type = normalize_source_type(block.get("sourceType") or block.get("blockType"))
    return {
        "blockId": str(block.get("blockId") or ""),
        "weight": clamp_weight(block.get("weight"), source_type),
        "sourceType": source_type,
    }