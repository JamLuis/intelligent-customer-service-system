from __future__ import annotations

import json
from typing import Any

from app.preprocess.ir.models import IRBlock, IRDoc, IRSpan
from app.preprocess.utils import normalize_text


class TextParser:
    parser_name = "text"

    def parse(self, raw_text: str, source_id: Any, graph_category_id: Any) -> IRDoc:
        blocks: list[IRBlock] = []
        lines = [line.strip() for line in raw_text.splitlines() if line.strip()]
        for index, line in enumerate(lines, start=1):
            block_type = "kv" if ":" in line or "=" in line else "paragraph"
            blocks.append(IRBlock(
                block_type=block_type,
                raw_text=line,
                normalized_text=normalize_text(line),
                span=IRSpan(line_no=index, page_no=index),
                metadata={"parser": self.parser_name},
            ))
        return IRDoc(source_id=source_id, graph_category_id=graph_category_id, source_type="text", parser=self.parser_name, blocks=blocks)


class JsonParser:
    parser_name = "json"

    def parse(self, raw_text: str, source_id: Any, graph_category_id: Any) -> IRDoc:
        try:
            parsed = json.loads(raw_text)
            text = json.dumps(parsed, ensure_ascii=False, sort_keys=True)
        except json.JSONDecodeError:
            text = normalize_text(raw_text)
        blocks = [IRBlock(
            block_type="json",
            raw_text=text,
            normalized_text=text,
            span=IRSpan(line_no=1, page_no=1),
            metadata={"parser": self.parser_name},
        )] if text.strip() else []
        return IRDoc(source_id=source_id, graph_category_id=graph_category_id, source_type="json", parser=self.parser_name, blocks=blocks)
