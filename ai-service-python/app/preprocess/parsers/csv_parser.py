from __future__ import annotations

import csv
import io
from typing import Any

from app.preprocess.ir.models import IRBlock, IRDoc, IRSpan
from app.preprocess.artifact import blocks_from_ir


class CsvParser:
    parser_name = "csv"

    def parse(self, raw_text: str, source_id: Any, graph_category_id: Any) -> IRDoc:
        reader = csv.reader(io.StringIO(raw_text))
        rows = list(reader)
        blocks: list[IRBlock] = []
        for index, row in enumerate(rows, start=1):
            normalized = " | ".join(cell.strip() for cell in row)
            if normalized.strip():
                blocks.append(IRBlock(block_type="csv", raw_text=normalized, normalized_text=normalized, span=IRSpan(line_no=index, page_no=index, row_no=index - 1), metadata={"parser": self.parser_name}))
        return IRDoc(source_id=source_id, graph_category_id=graph_category_id, source_type="csv", parser=self.parser_name, blocks=blocks)


def csv_blocks(raw_text: str, source_id: Any, graph_category_id: Any) -> list[dict[str, Any]]:
    return blocks_from_ir(CsvParser().parse(raw_text, source_id, graph_category_id))
