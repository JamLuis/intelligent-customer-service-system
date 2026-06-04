from __future__ import annotations

import re
from typing import Any

from app.preprocess.ir.models import IRBlock, IRDoc, IRSpan
from app.preprocess.artifact import blocks_from_ir
from app.preprocess.utils import normalize_text


class MarkdownParser:
    parser_name = "markdown"

    def parse(self, raw_text: str, source_id: Any, graph_category_id: Any) -> IRDoc:
        blocks: list[IRBlock] = []
        section_path = ""
        block_index = 0
        for line_no, line in enumerate(raw_text.splitlines(), start=1):
            stripped = line.strip()
            if not stripped:
                continue
            block_index += 1
            heading = re.match(r"^(#{1,6})\s+(.+?)\s*$", stripped)
            if heading:
                title = normalize_text(heading.group(2))
                section_path = title
                block_type = "title" if len(heading.group(1)) == 1 else "paragraph"
                blocks.append(IRBlock(block_type=block_type, raw_text=stripped, normalized_text=title, span=IRSpan(line_no=line_no, page_no=block_index), section_path=section_path, metadata={"parser": self.parser_name, "lineNo": line_no, "level": len(heading.group(1)), "markdownRole": "heading"}))
                continue
            is_list = stripped.startswith(("- ", "* ")) or re.match(r"^\d+\.\s+", stripped)
            block_type = "table_row" if stripped.startswith("|") and stripped.endswith("|") else "kv" if ":" in stripped or "=" in stripped else "paragraph"
            metadata = {"parser": self.parser_name, "lineNo": line_no}
            if is_list:
                metadata["markdownRole"] = "list_item"
                block_type = "paragraph"
            blocks.append(IRBlock(block_type=block_type, raw_text=stripped, normalized_text=normalize_text(stripped), span=IRSpan(line_no=line_no, page_no=block_index), section_path=section_path, metadata=metadata))
        return IRDoc(source_id=source_id, graph_category_id=graph_category_id, source_type="md", parser=self.parser_name, blocks=blocks)


def markdown_blocks(raw_text: str, source_id: Any, graph_category_id: Any) -> list[dict[str, Any]]:
    return blocks_from_ir(MarkdownParser().parse(raw_text, source_id, graph_category_id))
