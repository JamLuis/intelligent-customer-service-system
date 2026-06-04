from __future__ import annotations

from app.preprocess.ir.models import IRBlock, QualityReport


def check_block_quality(block: IRBlock) -> QualityReport:
    flags: list[str] = []
    text = block.normalized_text.strip()
    if not text:
        flags.append("empty")
    if len(text) > 1800:
        flags.append("too_long")
    if len(text) < 2:
        flags.append("too_short")
    score = 1.0
    if flags:
        score = 0.5 if "empty" not in flags else 0.0
    return QualityReport(score=score, flags=flags)


def check_artifact_quality(blocks: list[dict]) -> QualityReport:
    if not blocks:
        return QualityReport(score=0.0, flags=["no_blocks"])
    low_quality = sum(1 for block in blocks if float(block.get("qualityScore") or 0) < 0.6)
    flags = ["has_low_quality_blocks"] if low_quality else []
    score = max(0.0, 1.0 - low_quality / len(blocks))
    return QualityReport(score=score, flags=flags)
