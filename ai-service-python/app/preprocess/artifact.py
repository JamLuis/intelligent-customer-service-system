from __future__ import annotations

from app.preprocess.chunking.paragraph_semantic import chunk_blocks
from app.preprocess.ir.models import IRDoc, ParseArtifact
from app.preprocess.metadata.builder import build_block_metadata
from app.preprocess.normalize.structure_detector import ensure_section_path
from app.preprocess.quality.checker import check_artifact_quality, check_block_quality
from app.preprocess.quality.dedup import document_hash, mark_duplicate_blocks
from app.preprocess.utils import block_envelope


def blocks_from_ir(doc: IRDoc) -> list[dict]:
    structured_blocks = ensure_section_path(doc.blocks)
    chunks = chunk_blocks(structured_blocks)
    blocks: list[dict] = []
    for index, block in enumerate(chunks, start=1):
        quality = check_block_quality(block)
        metadata = build_block_metadata(
            block,
            parser=doc.parser,
            chunk_index=index,
            source_type=doc.source_type,
            extra={
                "summary": block.normalized_text[:120],
                "qualityScore": quality.score,
                "qualityFlags": quality.flags,
            },
        )
        blocks.append(block_envelope(
            doc.source_id,
            doc.graph_category_id,
            block.block_type,
            block.raw_text,
            block.normalized_text,
            block.span.page_no or index,
            block.span.row_no,
            block.span.col_no,
            metadata,
        ))
    return mark_duplicate_blocks(blocks)


def artifact_from_ir(doc: IRDoc, raw_text: str) -> ParseArtifact:
    blocks = blocks_from_ir(doc)
    return ParseArtifact(
        source_id=doc.source_id,
        source_type=doc.source_type,
        parser=doc.parser,
        blocks=blocks,
        metadata={
            **doc.metadata,
            "documentHash": document_hash(raw_text),
            "blockCount": len(blocks),
        },
        quality=check_artifact_quality(blocks),
    )
