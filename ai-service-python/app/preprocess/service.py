from typing import Any

from app.preprocess.artifact import artifact_from_ir, blocks_from_ir
from app.preprocess.routing import resolve_parser


def parse_knowledge(request: dict[str, Any]) -> dict[str, Any]:
    raw_text = str(request.get("rawText") or "")
    source_type = str(request.get("sourceType") or "text").lower()
    file_name = str(request.get("fileName") or "")
    source_id = request.get("sourceId")
    graph_category_id = request.get("graphCategoryId")
    directive = resolve_parser(source_type, file_name)
    doc = directive.parser.parse(raw_text, source_id, graph_category_id)
    return artifact_from_ir(doc, raw_text).to_response()


def infer_source_type(source_type: str, file_name: str) -> str:
    directive = resolve_parser(source_type, file_name)
    return directive.source_type


def build_blocks(raw_text: str, source_type: str, source_id: Any, graph_category_id: Any) -> list[dict[str, Any]]:
    directive = resolve_parser(source_type, "")
    doc = directive.parser.parse(raw_text, source_id, graph_category_id)
    return blocks_from_ir(doc)
