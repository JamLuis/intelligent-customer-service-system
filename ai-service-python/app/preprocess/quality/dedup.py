from __future__ import annotations

from app.preprocess.utils import content_hash, normalize_text


def document_hash(raw_text: str) -> str:
    return content_hash(normalize_text(raw_text))


def mark_duplicate_blocks(blocks: list[dict]) -> list[dict]:
    seen: set[str] = set()
    output: list[dict] = []
    for block in blocks:
        item = dict(block)
        block_hash = str(item.get("contentHash") or "")
        flags = list(item.get("qualityFlags") or [])
        if block_hash and block_hash in seen:
            flags.append("duplicate")
        if block_hash:
            seen.add(block_hash)
        item["qualityFlags"] = flags
        item["qualityScore"] = min(float(item.get("qualityScore") or 1.0), 0.6) if "duplicate" in flags else item.get("qualityScore", 1.0)
        output.append(item)
    return output
