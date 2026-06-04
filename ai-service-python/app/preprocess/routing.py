from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Protocol

from app.preprocess.ir.models import IRDoc
from app.preprocess.parsers import csv_parser, markdown_parser, sql_parser, text_parser


class Parser(Protocol):
    def parse(self, raw_text: str, source_id: Any, graph_category_id: Any) -> IRDoc:
        ...


@dataclass(frozen=True)
class ParserDirective:
    source_type: str
    parser_name: str
    parser: Parser


PARSER_REGISTRY: dict[str, Parser] = {
    "text": text_parser.TextParser(),
    "json": text_parser.JsonParser(),
    "md": markdown_parser.MarkdownParser(),
    "markdown": markdown_parser.MarkdownParser(),
    "sql": sql_parser.SqlParser(),
    "ddl": sql_parser.SqlParser(),
    "csv": csv_parser.CsvParser(),
}


SUFFIX_TO_SOURCE_TYPE = {
    "md": "md",
    "markdown": "md",
    "sql": "sql",
    "ddl": "sql",
    "csv": "csv",
    "json": "json",
}


def infer_source_type(source_type: str, file_name: str) -> str:
    normalized = (source_type or "text").strip().lower()
    suffix = file_name.rsplit(".", 1)[-1].lower() if "." in file_name else ""
    if normalized == "text" and suffix in SUFFIX_TO_SOURCE_TYPE:
        return SUFFIX_TO_SOURCE_TYPE[suffix]
    return normalized


def resolve_parser(source_type: str, file_name: str = "") -> ParserDirective:
    resolved = infer_source_type(source_type, file_name)
    parser = PARSER_REGISTRY.get(resolved) or PARSER_REGISTRY["text"]
    parser_name = resolved if resolved in PARSER_REGISTRY else "text"
    return ParserDirective(source_type=resolved, parser_name=parser_name, parser=parser)
