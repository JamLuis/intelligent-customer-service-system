from __future__ import annotations

from app.preprocess.ir.models import IRBlock


def ensure_section_path(blocks: list[IRBlock]) -> list[IRBlock]:
    section_path = ""
    output: list[IRBlock] = []
    for block in blocks:
        if block.metadata.get("markdownRole") == "heading" and block.normalized_text:
            section_path = block.normalized_text
        if block.section_path:
            section_path = block.section_path
        output.append(IRBlock(
            block_type=block.block_type,
            raw_text=block.raw_text,
            normalized_text=block.normalized_text,
            span=block.span,
            section_path=section_path,
            metadata=block.metadata,
        ))
    return output
