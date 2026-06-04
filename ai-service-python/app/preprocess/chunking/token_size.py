from __future__ import annotations

from app.preprocess.ir.models import IRBlock, IRSpan


def split_long_block(block: IRBlock, max_chars: int = 1200) -> list[IRBlock]:
    text = block.normalized_text
    if len(text) <= max_chars:
        return [block]
    chunks: list[IRBlock] = []
    for index, start in enumerate(range(0, len(text), max_chars), start=1):
        part = text[start:start + max_chars].strip()
        if not part:
            continue
        metadata = dict(block.metadata)
        metadata["chunkPart"] = index
        chunks.append(IRBlock(
            block_type=block.block_type,
            raw_text=part,
            normalized_text=part,
            span=IRSpan(start=start, end=start + len(part), line_no=block.span.line_no, page_no=block.span.page_no, row_no=block.span.row_no, col_no=block.span.col_no),
            section_path=block.section_path,
            metadata=metadata,
        ))
    return chunks
