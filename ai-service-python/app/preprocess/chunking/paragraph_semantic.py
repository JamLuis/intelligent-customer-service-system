from __future__ import annotations

from app.preprocess.chunking.token_size import split_long_block
from app.preprocess.ir.models import IRBlock


def chunk_blocks(blocks: list[IRBlock], max_chars: int = 1200) -> list[IRBlock]:
    chunks: list[IRBlock] = []
    for block in blocks:
        chunks.extend(split_long_block(block, max_chars=max_chars))
    return chunks
