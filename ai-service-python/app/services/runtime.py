import csv
import hashlib
import io
import json
import re
import uuid
from time import perf_counter
from typing import Any

from fastapi import HTTPException

from app.clients.llm_client import LLMClient, LLMClientError, get_llm_client
from app.extractors.canonical_resolver import CanonicalSignals, score_breakdown
from app.extractors.evidence import build_evidence
from app.retrieval.graph_retrieval_planner import build_retrieve_plan
from app.settings import get_settings


PROTECTED_ENTITY_TYPES = {"Document", "Section", "SourceBlock"}
GENERIC_ENTITY_TYPES = {"ExtractedObject", "ExtractedIdentifier", "ExtractedQuantity"}
PROTECTED_RELATION_TYPES = {"HAS_SECTION", "IN_SECTION", "HAS_EVIDENCE"}
GENERIC_RELATION_TYPES = {"HAS_COMPONENT", "HAS_IDENTIFIER", "HAS_QUANTITY", "HAS_VALUE", "MENTIONS"}
GENERIC_BAD_ENTITY_NAMES = {"相关信息", "其他内容", "系统能力"}
LLM_EXTRACT_BLOCK_BUDGET = 12
LLM_EXTRACT_MAX_CHARS = 900


def llm_ping() -> dict[str, Any]:
    """Smoke test: hit chat + embeddings with current LLM_* env."""
    s = get_settings()
    try:
        client = get_llm_client()
        reply = client.chat(
            [{"role": "user", "content": "ping"}], max_tokens=8, temperature=0
        )
        vecs = client.embeddings(["ping"])
    except LLMClientError as e:
        raise HTTPException(status_code=502, detail=str(e)) from e
    return {
        "provider": s.llm_provider,
        "llmModel": s.llm_model,
        "chatReply": reply[:64],
        "embeddingModel": s.embedding_model,
        "embeddingDim": len(vecs[0]) if vecs else 0,
        "embeddingVersion": s.embedding_version,
    }


def llm_check(request: dict[str, Any]) -> dict[str, Any]:
    return check_llm_config(request)

# 本服务为无状态计算服务：
#   - 解析、抽取、归一化、检索规划等真实逻辑见任务 KG-AI-001 ~ KG-AI-005
#   - 严禁内置任何业务本体（设备/船舶/告警/规则…）字符串
#   - 所有可用 entityType / relationType / categoryId 必须由调用方在请求 taxonomy 中显式传入


def health() -> dict[str, str]:
    return {"service": "ai-service-python", "status": "ok"}


def capabilities() -> dict[str, list[str]]:
    return {
        "modules": [
            "document_parser",
            "entity_extractor",
            "graph_builder",
            "rag",
            "diagnosis_agent",
        ]
    }


def run_diagnosis(request: dict[str, Any]) -> dict[str, Any]:
    """诊断真实实现见 KG-AI-005。当前返回占位结构，禁止携带任何业务实体名。"""
    degraded = request.get("mockScenario") == "mcp_degraded"
    return {
        "status": "partial" if degraded else "not_implemented",
        "rootCause": "",
        "confidenceScore": 0.0,
        "graphPaths": [],
        "reasoningSteps": [],
        "recommendedActions": [],
        "degraded": degraded,
        "implHint": "KG-AI-005 待实现",
    }


def answer_chat(request: dict[str, Any]) -> dict[str, Any]:
    question_text = normalize_text(str(request.get("questionText") or ""))
    vector_evidence = request.get("vectorEvidence") if isinstance(request.get("vectorEvidence"), list) else []
    graph_paths = request.get("graphPaths") if isinstance(request.get("graphPaths"), list) else []
    force_graph_grounding = bool(request.get("forceGraphGrounding", True))
    llm_config = request.get("llmConfig") if isinstance(request.get("llmConfig"), dict) else None
    if not vector_evidence and not graph_paths:
        return {
            "answer": "知识库中没有检索到足够证据，暂时不能基于关系图谱回答该问题。",
            "confidence": 0.0,
            "evidenceRefs": [],
            "graphPaths": [],
            "missingContext": [],
            "cannotAnswerReason": "NO_KNOWLEDGE_EVIDENCE",
            "modelProfileId": str((llm_config or {}).get("profileKey") or ""),
            "forceGraphGrounding": force_graph_grounding,
            "degraded": False,
        }
    evidence_pack = build_answer_evidence_pack(vector_evidence, graph_paths)
    if force_graph_grounding and not evidence_pack:
        return {
            "answer": "已开启强制关系图谱回答，但当前没有可引用的知识库关系或证据块。",
            "confidence": 0.0,
            "evidenceRefs": [],
            "graphPaths": graph_paths,
            "missingContext": [],
            "cannotAnswerReason": "NO_GRAPH_EVIDENCE",
            "modelProfileId": str((llm_config or {}).get("profileKey") or ""),
            "forceGraphGrounding": force_graph_grounding,
            "degraded": False,
        }
    extractive_answer = extractive_answer_payload(question_text, evidence_pack, graph_paths, llm_config, force_graph_grounding)
    if extractive_answer:
        return extractive_answer
    try:
        client = LLMClient(settings_from_llm_config(llm_config)) if llm_config else get_llm_client()
        max_tokens = int((llm_config or {}).get("maxTokens") or 512)
        content = client.chat([
            {"role": "system", "content": "你是知识库问答组件。只能依据提供的证据回答。若证据不足，必须说明不足。只输出严格 JSON。"},
            {"role": "user", "content": answer_prompt(question_text, evidence_pack, force_graph_grounding)}
        ], max_tokens=max_tokens, temperature=float((llm_config or {}).get("temperature") or 0.2), think=False)
        payload = parse_json_object(content)
        return normalize_answer_payload(payload, vector_evidence, graph_paths, llm_config, force_graph_grounding, degraded=False)
    except (LLMClientError, ValueError, TypeError, json.JSONDecodeError):
        return fallback_evidence_answer(question_text, vector_evidence, graph_paths, llm_config, force_graph_grounding)


def parse_knowledge(request: dict[str, Any]) -> dict[str, Any]:
    """Parse text-like knowledge sources into stable BlockEnvelope objects.

    The parser is intentionally generic: it never assumes business ontology
    names, and only reflects source metadata supplied by Java.
    """
    from app.preprocess.service import parse_knowledge as preprocess_parse_knowledge

    return preprocess_parse_knowledge(request)


def infer_source_type(source_type: str, file_name: str) -> str:
    from app.preprocess.service import infer_source_type as preprocess_infer_source_type

    return preprocess_infer_source_type(source_type, file_name)


def embed_blocks(request: dict[str, Any]) -> dict[str, Any]:
    """KG-AI-002 MVP: 调用本地 MLX 或 OpenAI 兼容 embeddings，返回 (blockId, vector) 列表。

    入参:
      { "blocks": [ {"blockId": "...", "text": "..."}, ... ] }
    出参:
      { "embeddings": [ {"blockId": "...", "vector": [...]} ],
        "embeddingModel": str, "embeddingVersion": str, "embeddingDim": int }
    维度受 EMBEDDING_DIM 约束（BGE-M3 MLX 默认 1024，对齐 pgvector schema）。
    """
    blocks = request.get("blocks") or []
    llm_config = request.get("llmConfig") if isinstance(request.get("llmConfig"), dict) else None
    if not isinstance(blocks, list):
        raise HTTPException(status_code=422, detail="blocks must be an array")
    texts: list[str] = []
    block_ids: list[str] = []
    for i, b in enumerate(blocks):
        if not isinstance(b, dict):
            raise HTTPException(status_code=422, detail=f"blocks[{i}] must be object")
        text = (b.get("text") or "").strip()
        if not text:
            raise HTTPException(status_code=422, detail=f"blocks[{i}].text is empty")
        texts.append(text)
        block_ids.append(str(b.get("blockId") or i))
    s = settings_from_llm_config(llm_config)
    if not texts:
        return {
            "embeddings": [],
            "embeddingModel": s.embedding_model,
            "embeddingVersion": s.embedding_version,
            "embeddingDim": s.embedding_dim,
        }
    try:
        client = LLMClient(s) if llm_config else get_llm_client()
        vectors = client.embeddings(texts)
    except LLMClientError as e:
        raise HTTPException(status_code=502, detail=str(e)) from e
    return {
        "embeddings": [
            {"blockId": bid, "vector": vec} for bid, vec in zip(block_ids, vectors)
        ],
        "embeddingModel": s.embedding_model,
        "embeddingVersion": s.embedding_version,
        "embeddingDim": s.embedding_dim,
    }


def extract_candidates(request: dict[str, Any]) -> dict[str, Any]:
    """Rule-first extraction constrained by request taxonomy.

    Supported generic input forms for data tests:
    - Entity line: ``<EntityType>: <name>``
    - Relation line: ``<RELATION_TYPE>: <source> -> <target>``
    - Regex rules in taxonomy.extractorRules.rules[].
    """
    blocks = request.get("blocks") or []
    taxonomy = request.get("taxonomy") or {}
    category = str(request.get("graphCategoryId") or "")
    entity_types = scoped_entity_types(taxonomy, category)
    relation_types = scoped_relation_types(taxonomy, category)
    entities_by_key: dict[tuple[str, str], dict[str, Any]] = {}
    relations_by_key: dict[tuple[str, str, str], dict[str, Any]] = {}
    degraded = False
    enable_llm_extract = bool(request.get("enableLlmExtract", get_settings().knowledge_llm_extract_enabled))
    llm_config = request.get("llmConfig") if isinstance(request.get("llmConfig"), dict) else None
    llm_extract_count = 0

    add_document_structure(
        blocks,
        source_id=str(request.get("sourceId") or ""),
        file_name=str(request.get("fileName") or request.get("sourceId") or "Document"),
        category=category,
        entity_types=entity_types,
        relation_types=relation_types,
        entities=entities_by_key,
        relations=relations_by_key,
    )

    for block in blocks:
        if not isinstance(block, dict):
            continue
        text = (block.get("normalizedText") or block.get("rawText") or block.get("text") or "").strip()
        if not text:
            continue
        extract_entities_from_lines(text, block, category, entity_types, entities_by_key)
        extract_entities_from_rules(text, block, category, entity_types, entities_by_key)
        extract_sql_ddl_facts(block, category, entity_types, relation_types, entities_by_key, relations_by_key)
        metadata = block.get("metadata") if isinstance(block.get("metadata"), dict) else {}
        if metadata.get("parser") == "sql":
            continue
        extract_generic_facts(text, block, category, entity_types, relation_types, entities_by_key, relations_by_key)
        if enable_llm_extract and should_llm_extract_block(block, text):
            if llm_extract_count >= LLM_EXTRACT_BLOCK_BUDGET:
                degraded = True
                continue
            llm_extract_count += 1
            llm_text = normalize_text(text)[:LLM_EXTRACT_MAX_CHARS]
            degraded = extract_openie_with_llm(llm_text, block, category, entity_types, relation_types, entities_by_key, relations_by_key, llm_config) or degraded

    for block in blocks:
        if not isinstance(block, dict):
            continue
        text = (block.get("normalizedText") or block.get("rawText") or block.get("text") or "").strip()
        if not text:
            continue
        extract_relations_from_lines(text, block, category, relation_types, entities_by_key, relations_by_key)

    candidate_entities = list(entities_by_key.values())
    candidate_relations = list(relations_by_key.values())
    return {
        "candidateEntities": candidate_entities,
        "candidateRelations": candidate_relations,
        "degraded": degraded,
        "llmExtractedBlocks": llm_extract_count,
        "llmExtractBlockBudget": LLM_EXTRACT_BLOCK_BUDGET,
    }


def should_llm_extract_block(block: dict[str, Any], text: str) -> bool:
    metadata = block.get("metadata") if isinstance(block.get("metadata"), dict) else {}
    if metadata.get("parser") == "sql":
        return False
    if metadata.get("markdownRole") == "heading":
        return False
    normalized = normalize_text(text)
    if len(normalized) < 12:
        return False
    return True


def build_blocks(raw_text: str, source_type: str, source_id: Any, graph_category_id: Any) -> list[dict[str, Any]]:
    from app.preprocess.service import build_blocks as preprocess_build_blocks

    return preprocess_build_blocks(raw_text, source_type, source_id, graph_category_id)


def sql_blocks(raw_text: str, source_id: Any, graph_category_id: Any) -> list[dict[str, Any]]:
    tables = parse_create_table_statements(raw_text)
    if not tables:
        return [block_envelope(source_id, graph_category_id, "sql", raw_text, normalize_text(raw_text), 1, None, None, {"parser": "sql"})]
    blocks: list[dict[str, Any]] = []
    for index, table in enumerate(tables, start=1):
        column_summary = ", ".join(column["name"] for column in table["columns"][:12])
        normalized = f"CREATE TABLE {table['name']} ({column_summary})"
        blocks.append(block_envelope(source_id, graph_category_id, "code", table["raw"], normalized, index, None, None, {"parser": "sql", "ddlKind": "create_table", "table": table}))
    return blocks


def parse_create_table_statements(raw_text: str) -> list[dict[str, Any]]:
    statements = split_sql_statements(raw_text)
    tables: list[dict[str, Any]] = []
    for statement in statements:
        parsed = parse_create_table_statement(statement)
        if parsed:
            tables.append(parsed)
    return tables


def split_sql_statements(raw_text: str) -> list[str]:
    statements: list[str] = []
    start = 0
    quote = ""
    escape = False
    for index, char in enumerate(raw_text):
        if quote:
            if escape:
                escape = False
            elif char == "\\":
                escape = True
            elif char == quote:
                quote = ""
            continue
        if char in {"'", '"', "`"}:
            quote = char
            continue
        if char == ";":
            statement = raw_text[start:index + 1].strip()
            if statement:
                statements.append(statement)
            start = index + 1
    tail = raw_text[start:].strip()
    if tail:
        statements.append(tail)
    return statements


def parse_create_table_statement(statement: str) -> dict[str, Any] | None:
    match = re.search(r"\bcreate\s+table\s+(?:if\s+not\s+exists\s+)?(`[^`]+`|[\w.]+)\s*\(", statement, re.IGNORECASE)
    if not match:
        return None
    table_name = clean_sql_identifier(match.group(1))
    open_index = statement.find("(", match.end() - 1)
    close_index = find_matching_paren(statement, open_index)
    if close_index < 0:
        return None
    body = statement[open_index + 1:close_index]
    tail = statement[close_index + 1:]
    parts = split_top_level_commas(body)
    columns: list[dict[str, Any]] = []
    primary_key: list[str] = []
    constraints: list[dict[str, Any]] = []
    for part in parts:
        definition = part.strip()
        if not definition:
            continue
        primary = parse_primary_key_definition(definition)
        if primary:
            primary_key = primary
            constraints.append({"type": "primary_key", "columns": primary})
            continue
        column = parse_column_definition(definition)
        if column:
            columns.append(column)
        else:
            constraints.append({"type": "constraint", "raw": normalize_text(definition)})
    table_comment = extract_sql_comment(tail)
    return {"name": table_name, "comment": table_comment, "columns": columns, "primaryKey": primary_key, "constraints": constraints, "raw": statement.strip()}


def find_matching_paren(text: str, open_index: int) -> int:
    depth = 0
    quote = ""
    escape = False
    for index in range(open_index, len(text)):
        char = text[index]
        if quote:
            if escape:
                escape = False
            elif char == "\\":
                escape = True
            elif char == quote:
                quote = ""
            continue
        if char in {"'", '"', "`"}:
            quote = char
            continue
        if char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return index
    return -1


def split_top_level_commas(text: str) -> list[str]:
    parts: list[str] = []
    start = 0
    depth = 0
    quote = ""
    escape = False
    for index, char in enumerate(text):
        if quote:
            if escape:
                escape = False
            elif char == "\\":
                escape = True
            elif char == quote:
                quote = ""
            continue
        if char in {"'", '"', "`"}:
            quote = char
            continue
        if char == "(":
            depth += 1
        elif char == ")" and depth:
            depth -= 1
        elif char == "," and depth == 0:
            parts.append(text[start:index])
            start = index + 1
    parts.append(text[start:])
    return parts


def parse_column_definition(definition: str) -> dict[str, Any] | None:
    match = re.match(r"\s*(`[^`]+`|[A-Za-z_][\w$]*)\s+(.+?)\s*$", definition, re.DOTALL)
    if not match:
        return None
    name = clean_sql_identifier(match.group(1))
    rest = normalize_text(match.group(2))
    leading = name.lower()
    if leading in {"primary", "foreign", "unique", "constraint", "key", "index", "fulltext", "spatial", "check"}:
        return None
    data_type = extract_sql_data_type(rest)
    comment = extract_sql_comment(rest)
    nullable = "not null" not in rest.lower()
    default_value = extract_sql_default(rest)
    return {"name": name, "dataType": data_type, "nullable": nullable, "comment": comment, "default": default_value, "raw": definition.strip()}


def extract_sql_data_type(rest: str) -> str:
    stop_words = {"not", "null", "default", "comment", "primary", "unique", "key", "auto_increment", "collate", "character", "references"}
    tokens = rest.split()
    collected: list[str] = []
    for token in tokens:
        if token.lower() in stop_words:
            break
        collected.append(token)
    return " ".join(collected) if collected else rest.split()[0]


def parse_primary_key_definition(definition: str) -> list[str]:
    match = re.search(r"\bprimary\s+key\s*\((.*?)\)", definition, re.IGNORECASE | re.DOTALL)
    if not match:
        return []
    return [clean_sql_identifier(item.strip()) for item in split_top_level_commas(match.group(1)) if item.strip()]


def extract_sql_comment(text: str) -> str:
    match = re.search(r"\bcomment\s*(?:=\s*)?'((?:\\'|[^'])*)'", text, re.IGNORECASE)
    if not match:
        return ""
    return match.group(1).replace("\\'", "'")


def extract_sql_default(text: str) -> str:
    match = re.search(r"\bdefault\s+((?:'[^']*')|\S+)", text, re.IGNORECASE)
    if not match:
        return ""
    return match.group(1).strip().strip("'")


def clean_sql_identifier(value: str) -> str:
    return value.strip().strip("`").split(".")[-1].strip("`")


def markdown_blocks(raw_text: str, source_id: Any, graph_category_id: Any) -> list[dict[str, Any]]:
    blocks: list[dict[str, Any]] = []
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
            blocks.append(block_envelope(source_id, graph_category_id, block_type, stripped, title, block_index, None, None, {"parser": "markdown", "sectionPath": section_path, "lineNo": line_no, "level": len(heading.group(1)), "markdownRole": "heading"}))
            continue
        is_list = stripped.startswith(("- ", "* ")) or re.match(r"^\d+\.\s+", stripped)
        block_type = "table_row" if stripped.startswith("|") and stripped.endswith("|") else "kv" if ":" in stripped or "=" in stripped else "paragraph"
        metadata = {"parser": "markdown", "sectionPath": section_path, "lineNo": line_no}
        if is_list:
            metadata["markdownRole"] = "list_item"
            block_type = "paragraph"
        blocks.append(block_envelope(source_id, graph_category_id, block_type, stripped, normalize_text(stripped), block_index, None, None, metadata))
    return blocks


def csv_blocks(raw_text: str, source_id: Any, graph_category_id: Any) -> list[dict[str, Any]]:
    reader = csv.reader(io.StringIO(raw_text))
    rows = list(reader)
    blocks = []
    for index, row in enumerate(rows, start=1):
        normalized = " | ".join(cell.strip() for cell in row)
        if normalized.strip():
            blocks.append(block_envelope(source_id, graph_category_id, "csv", normalized, normalized, index, index - 1, None, {"parser": "csv"}))
    return blocks


def block_envelope(source_id: Any, graph_category_id: Any, block_type: str, raw: str, normalized: str, page_no: int, row_no: int | None, col_no: int | None, metadata: dict[str, Any]) -> dict[str, Any]:
    material = "|".join([str(source_id or ""), block_type, normalized, str(page_no), str(row_no or ""), str(col_no or "")])
    return {
        "blockId": str(uuid.uuid5(uuid.NAMESPACE_URL, material)),
        "sourceId": source_id,
        "graphCategoryId": graph_category_id,
        "blockType": block_type,
        "sectionPath": metadata.get("sectionPath", ""),
        "pageNo": page_no,
        "rowNo": row_no,
        "colNo": col_no,
        "rawText": raw,
        "normalizedText": normalized,
        "contentHash": hashlib.sha256(material.encode("utf-8")).hexdigest(),
        "metadata": metadata,
    }


def normalize_text(value: str) -> str:
    return re.sub(r"\s+", " ", value.replace("\u3000", " ")).strip()


def collect_entity_types(taxonomy: dict[str, Any]) -> dict[str, dict[str, Any]]:
    return {str(item.get("entityType")): item for item in taxonomy.get("entityTypes", []) if isinstance(item, dict) and item.get("entityType")}


def collect_relation_types(taxonomy: dict[str, Any]) -> dict[str, dict[str, Any]]:
    return {str(item.get("relationType")): item for item in taxonomy.get("relationTypes", []) if isinstance(item, dict) and item.get("relationType")}


def scoped_entity_types(taxonomy: dict[str, Any], category: str) -> dict[str, dict[str, Any]]:
    all_types = collect_entity_types(taxonomy)
    allowed = set(category_scope(taxonomy, category, "entityTypeScope"))
    if allowed:
        allowed.update(PROTECTED_ENTITY_TYPES)
    else:
        allowed = set(PROTECTED_ENTITY_TYPES | GENERIC_ENTITY_TYPES)
    return {entity_type: all_types.get(entity_type, {"entityType": entity_type}) for entity_type in allowed}


def scoped_relation_types(taxonomy: dict[str, Any], category: str) -> dict[str, dict[str, Any]]:
    all_types = collect_relation_types(taxonomy)
    allowed = set(category_scope(taxonomy, category, "relationTypeScope"))
    if allowed:
        allowed.update(PROTECTED_RELATION_TYPES)
    else:
        allowed = set(PROTECTED_RELATION_TYPES | GENERIC_RELATION_TYPES)
    return {relation_type: all_types.get(relation_type, {"relationType": relation_type}) for relation_type in allowed}


def category_scope(taxonomy: dict[str, Any], category: str, field: str) -> list[str]:
    for item in taxonomy.get("categories", []):
        if isinstance(item, dict) and str(item.get("categoryId") or "") == category:
            value = item.get(field) or []
            return [str(entry) for entry in value if entry]
    return []


def add_document_structure(blocks: list[Any], source_id: str, file_name: str, category: str, entity_types: dict[str, dict[str, Any]], relation_types: dict[str, dict[str, Any]], entities: dict[tuple[str, str], dict[str, Any]], relations: dict[tuple[str, str, str], dict[str, Any]]) -> None:
    if not blocks or "Document" not in entity_types:
        return
    first_block = next((block for block in blocks if isinstance(block, dict)), None)
    if not first_block:
        return
    document = add_entity(entities, "Document", first_heading(blocks) or file_name or source_id, first_block, category, "rule", 0.95)
    section_by_path: dict[str, dict[str, Any]] = {}
    for block in blocks:
        if not isinstance(block, dict):
            continue
        if "SourceBlock" in entity_types:
            block_name = f"Block {block.get('pageNo') or len(section_by_path) + 1}: {normalize_text(str(block.get('normalizedText') or block.get('rawText') or ''))[:80]}"
            source_block = add_entity(entities, "SourceBlock", block_name, block, category, "rule", 0.92)
            if "HAS_EVIDENCE" in relation_types:
                add_relation(relations, "HAS_EVIDENCE", document, source_block, block, category)
        section_path = normalize_text(str((block.get("metadata") or {}).get("sectionPath") or block.get("sectionPath") or ""))
        if not section_path or "Section" not in entity_types:
            continue
        section = section_by_path.get(section_path)
        if section is None:
            section = add_entity(entities, "Section", section_path, block, category, "rule", 0.94)
            section_by_path[section_path] = section
            if "HAS_SECTION" in relation_types:
                add_relation(relations, "HAS_SECTION", document, section, block, category)
        if "SourceBlock" in entity_types and "IN_SECTION" in relation_types:
            source_block = entities.get(("SourceBlock", block_name.casefold()))
            if source_block:
                add_relation(relations, "IN_SECTION", source_block, section, block, category)


def first_heading(blocks: list[Any]) -> str:
    for block in blocks:
        metadata = block.get("metadata") if isinstance(block, dict) else {}
        if isinstance(block, dict) and isinstance(metadata, dict) and metadata.get("markdownRole") == "heading":
            return normalize_text(str(block.get("normalizedText") or block.get("rawText") or ""))
    return ""


def extract_entities_from_lines(text: str, block: dict[str, Any], category: str, entity_types: dict[str, dict[str, Any]], output: dict[tuple[str, str], dict[str, Any]]) -> None:
    for line in text.splitlines() or [text]:
        for entity_type in entity_types:
            match = re.match(rf"^\s*{re.escape(entity_type)}\s*[:：=]\s*(.+?)\s*$", line)
            if match:
                add_entity(output, entity_type, match.group(1), block, category, "rule", 0.9)


def extract_entities_from_rules(text: str, block: dict[str, Any], category: str, entity_types: dict[str, dict[str, Any]], output: dict[tuple[str, str], dict[str, Any]]) -> None:
    for entity_type, meta in entity_types.items():
        rules = meta.get("extractorRules") or {}
        if isinstance(rules, dict):
            rule_items = rules.get("rules") or rules.get("patterns") or []
        elif isinstance(rules, list):
            rule_items = rules
        else:
            rule_items = []
        for rule in rule_items:
            expression = rule.get("expression") if isinstance(rule, dict) else str(rule)
            if not expression:
                continue
            try:
                pattern = re.compile(expression)
            except re.error:
                continue
            for match in pattern.finditer(text):
                raw_name = match.groupdict().get("name") if match.groupdict() else match.group(1) if match.groups() else match.group(0)
                add_entity(output, entity_type, raw_name, block, category, "rule", 0.88)


def extract_generic_facts(text: str, block: dict[str, Any], category: str, entity_types: dict[str, dict[str, Any]], relation_types: dict[str, dict[str, Any]], entities: dict[tuple[str, str], dict[str, Any]], relations: dict[tuple[str, str, str], dict[str, Any]]) -> None:
    if not GENERIC_ENTITY_TYPES.intersection(entity_types):
        return
    normalized = normalize_text(text)
    object_entity = extract_labeled_identifier(normalized, block, category, entity_types, relation_types, entities, relations)
    owner_entity, component_entity, quantity_entity, quantity_value = extract_component_quantity(normalized, block, category, entity_types, relation_types, entities, relations)
    if owner_entity is None:
        owner_entity = object_entity
    if owner_entity and component_entity and "HAS_COMPONENT" in relation_types:
        add_relation(relations, "HAS_COMPONENT", owner_entity, component_entity, block, category, {"quantity": quantity_value, "unit": "个"})
    if component_entity:
        for identifier in extract_identifiers_after_keywords(normalized):
            identifier_entity = add_generic_identifier(identifier, block, category, entity_types, entities)
            if identifier_entity and "HAS_IDENTIFIER" in relation_types:
                add_relation(relations, "HAS_IDENTIFIER", component_entity, identifier_entity, block, category)
    if component_entity and quantity_entity and "HAS_QUANTITY" in relation_types:
        add_relation(relations, "HAS_QUANTITY", component_entity, quantity_entity, block, category, {"value": quantity_value, "unit": "个"})
    extract_enumerated_component_facts(normalized, block, category, entity_types, relation_types, entities, relations)
    extract_usage_value_facts(normalized, block, category, entity_types, relation_types, entities, relations)
    extract_key_value_facts(normalized, block, category, entity_types, relation_types, entities, relations, component_entity)


def extract_enumerated_component_facts(text: str, block: dict[str, Any], category: str, entity_types: dict[str, dict[str, Any]], relation_types: dict[str, dict[str, Any]], entities: dict[tuple[str, str], dict[str, Any]], relations: dict[tuple[str, str, str], dict[str, Any]]) -> None:
    if "ExtractedObject" not in entity_types or "HAS_COMPONENT" not in relation_types:
        return
    for match in re.finditer(r"([^，,。；;]{2,40}?)(?:需要)?(?:覆盖|包含)([^。；;]{2,120})", text):
        owner_name = clean_generic_phrase(match.group(1))
        if not owner_name:
            continue
        owner = add_entity(entities, "ExtractedObject", owner_name, block, category, "rule", 0.86)
        owner["properties"].update({"mentionRole": "enumerationOwner", "sourceRule": "enumerated_component"})
        for component_name in split_enumerated_terms(match.group(2)):
            component = add_entity(entities, "ExtractedObject", component_name, block, category, "rule", 0.86)
            component["properties"].update({"mentionRole": "component", "sourceRule": "enumerated_component"})
            add_relation(relations, "HAS_COMPONENT", owner, component, block, category, {"sourceRule": "enumerated_component"}, extractor="rule", confidence=0.86)


def extract_usage_value_facts(text: str, block: dict[str, Any], category: str, entity_types: dict[str, dict[str, Any]], relation_types: dict[str, dict[str, Any]], entities: dict[tuple[str, str], dict[str, Any]], relations: dict[tuple[str, str, str], dict[str, Any]]) -> None:
    if "ExtractedObject" not in entity_types or "HAS_VALUE" not in relation_types:
        return
    for match in re.finditer(r"([^，,。；;]{2,40}?)(?:适合|用于)([^，,。；;]{2,40})", text):
        source_name = clean_generic_phrase(match.group(1))
        target_name = clean_generic_phrase(match.group(2))
        if not source_name or not target_name:
            continue
        source = add_entity(entities, "ExtractedObject", source_name, block, category, "rule", 0.85)
        source["properties"].update({"mentionRole": "valueOwner", "sourceRule": "usage_value"})
        target = add_entity(entities, "ExtractedObject", target_name, block, category, "rule", 0.85)
        target["properties"].update({"mentionRole": "value", "sourceRule": "usage_value"})
        add_relation(relations, "HAS_VALUE", source, target, block, category, {"sourceRule": "usage_value"}, extractor="rule", confidence=0.85)


def split_enumerated_terms(text: str) -> list[str]:
    normalized = normalize_text(text)
    normalized = re.sub(r"[。；;].*$", "", normalized)
    normalized = normalized.replace("以及", "、").replace("并", "、").replace("和", "、")
    return [item for item in (clean_generic_phrase(part) for part in re.split(r"[、,，/]", normalized)) if item]


def clean_generic_phrase(text: str) -> str:
    value = normalize_text(text)
    value = re.sub(r"^(?:且|并且|同时|所有|当前|该|此|本)", "", value)
    value = re.sub(r"(?:需要|必须|应当|应该|可以|能够|能)$", "", value)
    value = value.strip(" ：:，,。；;、")
    if value in GENERIC_BAD_ENTITY_NAMES:
        return ""
    if re.fullmatch(r"[0-9一二三四五六七八九十两]+个.+", value):
        return ""
    if len(value) < 2 or len(value) > 40:
        return ""
    return value


def extract_sql_ddl_facts(block: dict[str, Any], category: str, entity_types: dict[str, dict[str, Any]], relation_types: dict[str, dict[str, Any]], entities: dict[tuple[str, str], dict[str, Any]], relations: dict[tuple[str, str, str], dict[str, Any]]) -> None:
    metadata = block.get("metadata") if isinstance(block.get("metadata"), dict) else {}
    table = metadata.get("table") if isinstance(metadata.get("table"), dict) else None
    if not table or "ExtractedObject" not in entity_types:
        return
    table_name = str(table.get("name") or "").strip()
    if not table_name:
        return
    table_comment = str(table.get("comment") or "").strip()
    table_label = f"{table_name} 表" if not table_comment else f"{table_name} 表：{table_comment}"
    table_entity = add_entity(entities, "ExtractedObject", table_label, block, category, "rule", 0.94)
    table_entity["uniqueKey"] = {"ddlKind": "table", "name": table_name}
    table_entity["properties"].update({"ddlKind": "table", "tableName": table_name, "comment": table_comment, "columnCount": len(table.get("columns") or [])})
    if "ExtractedIdentifier" in entity_types:
        identifier_entity = add_entity(entities, "ExtractedIdentifier", table_name, block, category, "rule", 0.93)
        identifier_entity["uniqueKey"] = {"ddlKind": "table_identifier", "name": table_name}
        identifier_entity["properties"].update({"identifier": table_name, "identifierKind": "table_name"})
        if "HAS_IDENTIFIER" in relation_types:
            add_relation(relations, "HAS_IDENTIFIER", table_entity, identifier_entity, block, category, {"identifierKind": "table_name", "sourceParser": "sql_ddl"}, extractor="rule", confidence=0.92)

    primary_key = set(str(item) for item in (table.get("primaryKey") or []))
    for column in table.get("columns") or []:
        if not isinstance(column, dict):
            continue
        column_name = str(column.get("name") or "").strip()
        if not column_name:
            continue
        column_comment = str(column.get("comment") or "").strip()
        column_label = f"{column_name} 字段" if not column_comment else f"{column_name} 字段：{column_comment}"
        column_entity = add_entity(entities, "ExtractedObject", column_label, block, category, "rule", 0.93)
        column_entity["uniqueKey"] = {"ddlKind": "column", "tableName": table_name, "columnName": column_name}
        column_entity["properties"].update({
            "ddlKind": "column",
            "tableName": table_name,
            "columnName": column_name,
            "dataType": column.get("dataType") or "",
            "nullable": bool(column.get("nullable", True)),
            "comment": column_comment,
            "default": column.get("default") or "",
            "primaryKey": column_name in primary_key,
        })
        if "HAS_COMPONENT" in relation_types:
            add_relation(relations, "HAS_COMPONENT", table_entity, column_entity, block, category, {"componentKind": "column", "ordinal": len([k for k in entities if k[0] == "ExtractedObject"]), "sourceParser": "sql_ddl"}, extractor="rule", confidence=0.93)
        if "ExtractedIdentifier" in entity_types:
            column_identifier = add_entity(entities, "ExtractedIdentifier", column_name, block, category, "rule", 0.92)
            column_identifier["uniqueKey"] = {"ddlKind": "column_identifier", "tableName": table_name, "columnName": column_name}
            column_identifier["properties"].update({"identifier": column_name, "identifierKind": "column_name", "tableName": table_name})
            if "HAS_IDENTIFIER" in relation_types:
                add_relation(relations, "HAS_IDENTIFIER", column_entity, column_identifier, block, category, {"identifierKind": "column_name", "sourceParser": "sql_ddl"}, extractor="rule", confidence=0.91)
        add_sql_value_node(column_entity, "类型", str(column.get("dataType") or ""), block, category, entity_types, relation_types, entities, relations, {"valueKind": "sql_data_type", "columnName": column_name})
        add_sql_value_node(column_entity, "约束", "NOT NULL" if not bool(column.get("nullable", True)) else "NULL", block, category, entity_types, relation_types, entities, relations, {"valueKind": "sql_nullable", "columnName": column_name})
        if column_comment:
            add_sql_value_node(column_entity, "注释", column_comment, block, category, entity_types, relation_types, entities, relations, {"valueKind": "sql_comment", "columnName": column_name})

    if primary_key:
        pk_entity = add_entity(entities, "ExtractedObject", f"{table_name} 主键", block, category, "rule", 0.93)
        pk_entity["uniqueKey"] = {"ddlKind": "primary_key", "tableName": table_name, "columns": sorted(primary_key)}
        pk_entity["properties"].update({"ddlKind": "primary_key", "tableName": table_name, "columns": list(primary_key)})
        if "HAS_COMPONENT" in relation_types:
            add_relation(relations, "HAS_COMPONENT", table_entity, pk_entity, block, category, {"componentKind": "primary_key", "sourceParser": "sql_ddl"}, extractor="rule", confidence=0.93)
        for column_name in primary_key:
            add_sql_value_node(pk_entity, "主键字段", column_name, block, category, entity_types, relation_types, entities, relations, {"valueKind": "sql_primary_key_column", "tableName": table_name})


def add_sql_value_node(parent: dict[str, Any], label: str, value: str, block: dict[str, Any], category: str, entity_types: dict[str, dict[str, Any]], relation_types: dict[str, dict[str, Any]], entities: dict[tuple[str, str], dict[str, Any]], relations: dict[tuple[str, str, str], dict[str, Any]], properties: dict[str, Any]) -> None:
    normalized_value = normalize_text(value)
    if not normalized_value or "ExtractedObject" not in entity_types:
        return
    value_entity = add_entity(entities, "ExtractedObject", f"{label}：{normalized_value}", block, category, "rule", 0.9)
    value_entity["properties"].update(properties | {"label": label, "value": normalized_value})
    if "HAS_VALUE" in relation_types:
        add_relation(relations, "HAS_VALUE", parent, value_entity, block, category, properties | {"sourceParser": "sql_ddl"}, extractor="rule", confidence=0.9)


def extract_labeled_identifier(text: str, block: dict[str, Any], category: str, entity_types: dict[str, dict[str, Any]], relation_types: dict[str, dict[str, Any]], entities: dict[tuple[str, str], dict[str, Any]], relations: dict[tuple[str, str, str], dict[str, Any]]) -> dict[str, Any] | None:
    if "ExtractedObject" not in entity_types:
        return None
    match = re.search(r"([\u4e00-\u9fa5]{1,16})([A-Z][A-Z0-9-]*\d[A-Z0-9-]*)", text)
    if not match:
        return None
    label = f"{match.group(1)}{match.group(2)}"
    entity = add_entity(entities, "ExtractedObject", label, block, category, "rule", 0.86)
    entity["properties"].update({"nameText": match.group(1), "identifier": match.group(2)})
    identifier_entity = add_generic_identifier(match.group(2), block, category, entity_types, entities)
    if identifier_entity and "HAS_IDENTIFIER" in relation_types:
        add_relation(relations, "HAS_IDENTIFIER", entity, identifier_entity, block, category)
    return entity


def extract_component_quantity(text: str, block: dict[str, Any], category: str, entity_types: dict[str, dict[str, Any]], relation_types: dict[str, dict[str, Any]], entities: dict[tuple[str, str], dict[str, Any]], relations: dict[tuple[str, str, str], dict[str, Any]]) -> tuple[dict[str, Any] | None, dict[str, Any] | None, dict[str, Any] | None, int | None]:
    match = re.search(r"^\s*([^，,。；;：:]{1,40}?)\s*有\s*([0-9一二三四五六七八九十两]+)\s*个\s*([\u4e00-\u9fa5A-Za-z0-9_-]{1,30})", text)
    if not match:
        return None, None, None, None
    quantity_value = parse_chinese_int(match.group(2))
    owner = None
    component = None
    quantity = None
    if "ExtractedObject" in entity_types:
        owner_name = normalize_text(match.group(1))
        owner = add_entity(entities, "ExtractedObject", owner_name, block, category, "rule", 0.84)
        owner["properties"].update({"mentionRole": "owner"})
        owner_id_match = re.search(r"([A-Z][A-Z0-9-]*\d[A-Z0-9-]*)", owner_name)
        if owner_id_match:
            owner["properties"].update({"identifier": owner_id_match.group(1)})
            identifier_entity = add_generic_identifier(owner_id_match.group(1), block, category, entity_types, entities)
            if identifier_entity and "HAS_IDENTIFIER" in relation_types:
                add_relation(relations, "HAS_IDENTIFIER", owner, identifier_entity, block, category)
        component = add_entity(entities, "ExtractedObject", match.group(3), block, category, "rule", 0.84)
        component["properties"].update({"mentionRole": "component"})
    if "ExtractedQuantity" in entity_types and quantity_value is not None:
        quantity = add_entity(entities, "ExtractedQuantity", f"{quantity_value}个", block, category, "rule", 0.83)
        quantity["properties"].update({"value": quantity_value, "unit": "个"})
    return owner, component, quantity, quantity_value


def extract_key_value_facts(text: str, block: dict[str, Any], category: str, entity_types: dict[str, dict[str, Any]], relation_types: dict[str, dict[str, Any]], entities: dict[tuple[str, str], dict[str, Any]], relations: dict[tuple[str, str, str], dict[str, Any]], parent_entity: dict[str, Any] | None) -> None:
    if "ExtractedObject" not in entity_types:
        return
    pairs = re.findall(r"([^，,、。；;:：]{1,24})\s*[:：]\s*([^，,、。；;:：]{1,30})", text)
    for key, value in pairs:
        key_name = normalize_text(key)
        value_name = normalize_text(value)
        if not key_name or not value_name:
            continue
        key_entity = add_entity(entities, "ExtractedObject", key_name, block, category, "rule", 0.84)
        key_entity["properties"].update({"mentionRole": "enumerationKey"})
        value_entity = add_entity(entities, "ExtractedObject", value_name, block, category, "rule", 0.84)
        value_entity["properties"].update({"mentionRole": "enumerationValue"})
        if parent_entity and "HAS_COMPONENT" in relation_types:
            add_relation(relations, "HAS_COMPONENT", parent_entity, key_entity, block, category)
        if "HAS_VALUE" in relation_types:
            add_relation(relations, "HAS_VALUE", key_entity, value_entity, block, category)


def extract_openie_with_llm(text: str, block: dict[str, Any], category: str, entity_types: dict[str, dict[str, Any]], relation_types: dict[str, dict[str, Any]], entities: dict[tuple[str, str], dict[str, Any]], relations: dict[tuple[str, str, str], dict[str, Any]], llm_config: dict[str, Any] | None = None) -> bool:
    if not GENERIC_ENTITY_TYPES.intersection(entity_types):
        return False
    try:
        client = LLMClient(settings_from_llm_config(llm_config)) if llm_config else get_llm_client()
        content = client.chat([
            {"role": "system", "content": "你是开放信息抽取(OpenIE)组件。只输出严格 JSON，不要解释。"},
            {"role": "user", "content": openie_prompt(text, sorted(GENERIC_RELATION_TYPES.intersection(relation_types)))}
        ], max_tokens=900, temperature=0, think=False)
        payload = parse_json_object(content)
    except (LLMClientError, ValueError, TypeError, json.JSONDecodeError):
        return True
    apply_openie_payload(payload, block, category, entity_types, relation_types, entities, relations)
    return False


def settings_from_llm_config(config: dict[str, Any] | None) -> Any:
    settings = get_settings()
    if not config:
        return settings
    updates: dict[str, Any] = {}
    field_map = {
        "provider": "llm_provider",
        "apiBaseUrl": "llm_api_base_url",
        "apiKey": "llm_api_key",
        "workspaceId": "llm_workspace_id",
        "model": "llm_model",
        "embeddingModel": "embedding_model",
        "embeddingDim": "embedding_dim",
    }
    for request_key, field_name in field_map.items():
        value = config.get(request_key)
        if value is not None:
            updates[field_name] = value
    return settings.model_copy(update=updates)


def check_llm_config(config: dict[str, Any]) -> dict[str, Any]:
    started = perf_counter()
    settings = settings_from_llm_config(config)
    provider_mode = str(config.get("providerMode") or "cloud").strip().lower()
    local_strict = provider_mode == "local"
    result: dict[str, Any] = {
        "providerMode": provider_mode,
        "provider": settings.llm_provider,
        "apiBaseUrl": settings.llm_api_base_url,
        "model": settings.llm_model,
        "apiKeyConfigured": bool(settings.llm_api_key),
        "modelsOk": False,
        "modelFound": False,
        "chatOk": False,
        "embeddingOk": False,
        "ok": False,
    }
    client = LLMClient(settings)
    messages: list[str] = []
    try:
        models = client.models()
        result["modelsOk"] = True
        result["modelCount"] = len(models)
        result["modelFound"] = settings.llm_model in models
        result["sampleModels"] = models[:8]
        if not models:
            messages.append("models 返回为空")
        elif not result["modelFound"]:
            messages.append(f"未找到模型: {settings.llm_model}")
    except LLMClientError as ex:
        messages.append(str(ex))
    try:
        reply = client.chat([{"role": "user", "content": "只回复 pong"}], max_tokens=128, temperature=0)
        result["chatOk"] = bool(reply.strip())
        result["chatSample"] = reply.strip()[:80]
    except LLMClientError as ex:
        messages.append(str(ex))
    if bool(config.get("checkEmbedding", False)):
        try:
            vectors = client.embeddings(["ping"])
            result["embeddingOk"] = bool(vectors)
            result["embeddingDim"] = len(vectors[0]) if vectors else 0
        except LLMClientError as ex:
            messages.append(str(ex))
    embedding_required = bool(config.get("checkEmbedding", False))
    if local_strict:
        if not result["modelsOk"]:
            messages.append("本地模式要求 /models 可访问")
        if embedding_required and not result["embeddingOk"]:
            messages.append("本地知识抽取要求 embedding 可用")
        result["ok"] = bool(result["modelsOk"] and result["modelFound"] and result["chatOk"] and (not embedding_required or result["embeddingOk"]))
    else:
        result["ok"] = bool(result["chatOk"] and (result["modelFound"] or not result["modelsOk"]))
    result["latencyMs"] = int((perf_counter() - started) * 1000)
    result["message"] = "连接正常" if result["ok"] else "；".join(messages[:3]) or "模型验证未通过"
    return result


def build_answer_evidence_pack(vector_evidence: list[Any], graph_paths: list[Any]) -> list[dict[str, Any]]:
    pack: list[dict[str, Any]] = []
    for item in vector_evidence[:32]:
        if not isinstance(item, dict):
            continue
        summary = normalize_text(str(item.get("rawTextSummary") or item.get("summary") or ""))
        if not summary:
            continue
        pack.append({
            "kind": "source_block",
            "blockId": str(item.get("blockId") or ""),
            "sourceId": str(item.get("sourceId") or ""),
            "fileName": str(item.get("sourceFileName") or item.get("fileName") or ""),
            "sourceType": str(item.get("sourceType") or ""),
            "graphCategoryName": str(item.get("graphCategoryName") or ""),
            "quote": summary[:500],
            "score": item.get("hybridScore") or item.get("score") or 0,
            "metadata": item.get("metadata") if isinstance(item.get("metadata"), dict) else {},
        })
    for item in graph_paths[:5]:
        if isinstance(item, dict):
            pack.append({"kind": "graph_path", **item})
    return pack


def answer_prompt(question_text: str, evidence_pack: list[dict[str, Any]], force_graph_grounding: bool) -> str:
    return json.dumps({
        "task": "基于知识库证据回答用户问题。",
        "rules": [
            "只能使用 evidencePack 中的来源块、实体或关系路径。",
            "不得根据常识或模型记忆补充证据外结论。",
            "证据不足时 answer 必须说明无法判断，并填写 cannotAnswerReason。",
            "每个关键判断必须在 evidenceRefs 中引用 blockId/sourceId/fileName。",
            "forceGraphGrounding=true 时，答案必须优先描述知识库关系图谱中的关系与来源。"
        ],
        "forceGraphGrounding": force_graph_grounding,
        "outputSchema": {
            "answer": "字符串",
            "confidence": 0.0,
            "evidenceRefs": [{"blockId": "字符串", "sourceId": "字符串", "fileName": "字符串", "quote": "字符串"}],
            "graphPaths": [],
            "missingContext": [],
            "cannotAnswerReason": "证据不足时填写，否则为空"
        },
        "questionText": question_text,
        "evidencePack": evidence_pack,
    }, ensure_ascii=False)


def normalize_answer_payload(payload: dict[str, Any], vector_evidence: list[Any], graph_paths: list[Any], llm_config: dict[str, Any] | None, force_graph_grounding: bool, degraded: bool) -> dict[str, Any]:
    answer = normalize_text(str(payload.get("answer") or ""))
    evidence_refs = payload.get("evidenceRefs") if isinstance(payload.get("evidenceRefs"), list) else []
    if not answer:
        return fallback_evidence_answer("", vector_evidence, graph_paths, llm_config, force_graph_grounding)
    if force_graph_grounding and not evidence_refs:
        fallback = fallback_evidence_answer("", vector_evidence, graph_paths, llm_config, force_graph_grounding)
        fallback["cannotAnswerReason"] = "ANSWER_MISSING_EVIDENCE_REFS"
        return fallback
    return {
        "answer": answer,
        "confidence": clamp_float(payload.get("confidence"), 0.0, 1.0, 0.5 if evidence_refs else 0.2),
        "evidenceRefs": evidence_refs,
        "graphPaths": payload.get("graphPaths") if isinstance(payload.get("graphPaths"), list) else graph_paths,
        "missingContext": payload.get("missingContext") if isinstance(payload.get("missingContext"), list) else [],
        "cannotAnswerReason": str(payload.get("cannotAnswerReason") or ""),
        "modelProfileId": str((llm_config or {}).get("profileKey") or ""),
        "forceGraphGrounding": force_graph_grounding,
        "degraded": degraded,
    }


def fallback_evidence_answer(question_text: str, vector_evidence: list[Any], graph_paths: list[Any], llm_config: dict[str, Any] | None, force_graph_grounding: bool) -> dict[str, Any]:
    pack = build_answer_evidence_pack(vector_evidence, graph_paths)
    if not pack:
        answer = "知识库中没有检索到足够证据，暂时不能基于关系图谱回答该问题。"
        reason = "NO_KNOWLEDGE_EVIDENCE"
        confidence = 0.0
    else:
        section_answer = section_answer_from_evidence(question_text, pack)
        direct_answer = direct_answer_from_evidence(question_text, pack)
        if section_answer:
            answer = section_answer
            reason = "MODEL_DEGRADED_SECTION_EVIDENCE"
            confidence = 0.75
        elif direct_answer:
            answer = direct_answer
            reason = "MODEL_DEGRADED_DIRECT_EVIDENCE"
            confidence = 0.75
        else:
            quotes = [f"- {item.get('fileName') or item.get('sourceId')}: {item.get('quote')}" for item in pack if item.get("kind") == "source_block"]
            answer = "已根据知识库检索到以下证据，但模型生成不可用，先返回证据摘要：\n" + "\n".join(quotes[:5])
            reason = "MODEL_DEGRADED_EVIDENCE_SUMMARY"
            confidence = 0.35
    return {
        "answer": answer,
        "confidence": confidence,
        "evidenceRefs": [item for item in pack if item.get("kind") == "source_block"],
        "graphPaths": graph_paths,
        "missingContext": [],
        "cannotAnswerReason": reason,
        "modelProfileId": str((llm_config or {}).get("profileKey") or ""),
        "forceGraphGrounding": force_graph_grounding,
        "degraded": True,
    }


def extractive_answer_payload(question_text: str, evidence_pack: list[dict[str, Any]], graph_paths: list[Any], llm_config: dict[str, Any] | None, force_graph_grounding: bool) -> dict[str, Any] | None:
    section_answer = section_answer_from_evidence(question_text, evidence_pack)
    direct_answer = direct_answer_from_evidence(question_text, evidence_pack)
    answer = section_answer or direct_answer
    if not answer:
        return None
    return {
        "answer": answer,
        "confidence": 0.85 if section_answer else 0.8,
        "evidenceRefs": [item for item in evidence_pack if item.get("kind") == "source_block"],
        "graphPaths": graph_paths,
        "missingContext": [],
        "cannotAnswerReason": "",
        "modelProfileId": str((llm_config or {}).get("profileKey") or ""),
        "forceGraphGrounding": force_graph_grounding,
        "degraded": False,
        "answerMode": "extractive",
    }


def section_answer_from_evidence(question_text: str, evidence_pack: list[dict[str, Any]]) -> str:
    groups: dict[tuple[str, str, str], list[dict[str, Any]]] = {}
    for item in evidence_pack:
        if item.get("kind") != "source_block":
            continue
        metadata = item.get("metadata") if isinstance(item.get("metadata"), dict) else {}
        section_path = normalize_text(str(metadata.get("sectionPath") or ""))
        if not section_path:
            continue
        key = (str(item.get("sourceId") or ""), str(item.get("fileName") or ""), section_path)
        groups.setdefault(key, []).append(item)
    best_key = None
    best_items: list[dict[str, Any]] = []
    question_norm = normalize_for_section_match(question_text)
    for key, items in groups.items():
        section_norm = normalize_for_section_match(key[2])
        has_heading = any((item.get("metadata") or {}).get("markdownRole") == "heading" for item in items)
        if len(items) < 3:
            continue
        if has_heading or (question_norm and section_norm and (question_norm in section_norm or section_norm in question_norm)):
            if len(items) > len(best_items):
                best_key = key
                best_items = items
    if not best_key or not best_items:
        return ""
    ordered = sorted(best_items, key=lambda item: int((item.get("metadata") or {}).get("lineNo") or 10**9))
    lines: list[str] = []
    for item in ordered:
        quote = normalize_text(str(item.get("quote") or ""))
        if not quote or quote == "---":
            continue
        quote = re.sub(r"^#{1,6}\s*", "", quote)
        lines.append(quote)
    if not lines:
        return ""
    content = "\n".join(lines[:28])
    if len(content) > 5000:
        content = content[:5000].rstrip() + "\n..."
    return f"根据知识库《{best_key[1]}》中「{best_key[2]}」章节，内容如下：\n\n{content}"


def normalize_for_section_match(value: str) -> str:
    return re.sub(r"[#*_`|\s、，。；：:（）()\[\]【】/\\-]+", "", value or "")


def direct_answer_from_evidence(question_text: str, evidence_pack: list[dict[str, Any]]) -> str:
    if not re.search(r"几个|多少", question_text):
        return ""
    for item in evidence_pack:
        if item.get("kind") != "source_block":
            continue
        quote = normalize_text(str(item.get("quote") or ""))
        if not quote:
            continue
        match = re.search(r"([^，。；;:：]{0,24}?有[一二三四五六七八九十两0-9]+个[^，。；;:：]{1,12})", quote)
        if match:
            return f"根据知识库证据：{match.group(1)}。"
    return ""


def clamp_float(value: Any, min_value: float, max_value: float, fallback: float) -> float:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return fallback
    return max(min_value, min(max_value, number))


def openie_prompt(text: str, allowed_relations: list[str]) -> str:
    relation_list = allowed_relations or sorted(GENERIC_RELATION_TYPES)
    return json.dumps({
        "task": "从中文自然语言中抽取平台中立的实体和关系候选。不要使用业务分类，不要把实体强行归为设备/人员/告警等业务类型。",
        "allowedEntityTypes": ["ExtractedObject", "ExtractedIdentifier", "ExtractedQuantity"],
        "allowedRelationTypes": relation_list,
        "relationHints": {
            "HAS_COMPONENT": "整体包含组成、集合包含成员或类别包含子项",
            "HAS_IDENTIFIER": "对象拥有编号、编码、ID",
            "HAS_QUANTITY": "对象拥有数量事实",
            "HAS_VALUE": "字段、角色、键名、称谓拥有文本取值",
            "MENTIONS": "证据块提及候选对象"
        },
        "outputSchema": {
            "entities": [{"name": "字符串", "entityType": "ExtractedObject|ExtractedIdentifier|ExtractedQuantity", "properties": {}}],
            "relations": [{"source": "实体name", "relationType": "允许的关系类型", "target": "实体name", "properties": {}}]
        },
        "text": text
    }, ensure_ascii=False)


def parse_json_object(content: str) -> dict[str, Any]:
    stripped = content.strip()
    if stripped.startswith("```"):
        stripped = re.sub(r"^```(?:json)?\s*", "", stripped)
        stripped = re.sub(r"\s*```$", "", stripped)
    if not stripped.startswith("{"):
        start = stripped.find("{")
        end = stripped.rfind("}")
        if start < 0 or end <= start:
            raise ValueError("LLM response does not contain JSON object")
        stripped = stripped[start:end + 1]
    payload = json.loads(stripped)
    if not isinstance(payload, dict):
        raise ValueError("LLM response JSON must be object")
    return payload


def apply_openie_payload(payload: dict[str, Any], block: dict[str, Any], category: str, entity_types: dict[str, dict[str, Any]], relation_types: dict[str, dict[str, Any]], entities: dict[tuple[str, str], dict[str, Any]], relations: dict[tuple[str, str, str], dict[str, Any]]) -> None:
    by_name: dict[str, dict[str, Any]] = {}
    for item in payload.get("entities") or []:
        if not isinstance(item, dict):
            continue
        name = normalize_text(str(item.get("name") or ""))
        entity_type = str(item.get("entityType") or infer_generic_entity_type(name))
        if not name or entity_type not in entity_types or entity_type not in GENERIC_ENTITY_TYPES:
            continue
        entity = add_entity(entities, entity_type, name, block, category, "llm", 0.72)
        if isinstance(item.get("properties"), dict):
            entity["properties"].update(item["properties"])
        by_name[name.casefold()] = entity
    for item in payload.get("relations") or []:
        if not isinstance(item, dict):
            continue
        relation_type = str(item.get("relationType") or "")
        if relation_type not in relation_types or relation_type not in GENERIC_RELATION_TYPES:
            continue
        source = by_name.get(normalize_text(str(item.get("source") or "")).casefold())
        target = by_name.get(normalize_text(str(item.get("target") or "")).casefold())
        if not source or not target:
            continue
        properties = item.get("properties") if isinstance(item.get("properties"), dict) else {}
        add_relation(relations, relation_type, source, target, block, category, properties, extractor="llm", confidence=0.72, status="reviewing")


def infer_generic_entity_type(name: str) -> str:
    if re.fullmatch(r"[0-9一二三四五六七八九十两]+\s*个", name):
        return "ExtractedQuantity"
    if re.fullmatch(r"[A-Z][A-Z0-9-]*\d[A-Z0-9-]*", name):
        return "ExtractedIdentifier"
    return "ExtractedObject"


def add_generic_identifier(identifier: str, block: dict[str, Any], category: str, entity_types: dict[str, dict[str, Any]], entities: dict[tuple[str, str], dict[str, Any]]) -> dict[str, Any] | None:
    if "ExtractedIdentifier" not in entity_types:
        return None
    entity = add_entity(entities, "ExtractedIdentifier", identifier, block, category, "rule", 0.87)
    entity["properties"].update({"identifier": identifier})
    return entity


def extract_identifiers_after_keywords(text: str) -> list[str]:
    match = re.search(r"(?:编号|编码|ID|Id|id)\s*(?:分别)?\s*(?:为|是|:|：)?\s*(.+)$", text)
    if not match:
        return []
    tail = match.group(1)
    return unique([item for item in re.findall(r"(?<![A-Za-z0-9])([A-Z][A-Z0-9-]*\d[A-Z0-9-]*)(?![A-Za-z0-9])", tail)])


def unique(values: list[str]) -> list[str]:
    seen: set[str] = set()
    result: list[str] = []
    for value in values:
        key = value.casefold()
        if key not in seen:
            seen.add(key)
            result.append(value)
    return result


def parse_chinese_int(value: str) -> int | None:
    if value.isdigit():
        return int(value)
    digits = {"零": 0, "一": 1, "二": 2, "两": 2, "三": 3, "四": 4, "五": 5, "六": 6, "七": 7, "八": 8, "九": 9}
    if value == "十":
        return 10
    if "十" in value:
        left, _, right = value.partition("十")
        tens = digits.get(left, 1 if left == "" else 0)
        ones = digits.get(right, 0) if right else 0
        return tens * 10 + ones
    return digits.get(value)


def extract_relations_from_lines(text: str, block: dict[str, Any], category: str, relation_types: dict[str, dict[str, Any]], entities: dict[tuple[str, str], dict[str, Any]], output: dict[tuple[str, str, str], dict[str, Any]]) -> None:
    for line in text.splitlines() or [text]:
        for relation_type, meta in relation_types.items():
            match = re.match(rf"^\s*{re.escape(relation_type)}\s*[:：=]\s*(.+?)\s*(?:->|→)\s*(.+?)\s*$", line)
            if not match:
                continue
            from_types = [str(x) for x in meta.get("fromEntityTypes", [])]
            to_types = [str(x) for x in meta.get("toEntityTypes", [])]
            source = ensure_entity_for_name(entities, from_types, match.group(1), block, category)
            target = ensure_entity_for_name(entities, to_types, match.group(2), block, category)
            if source and target:
                add_relation(output, relation_type, source, target, block, category)


def ensure_entity_for_name(entities: dict[tuple[str, str], dict[str, Any]], preferred_types: list[str], name: str, block: dict[str, Any], category: str) -> dict[str, Any] | None:
    canonical = normalize_text(name)
    for entity_type in preferred_types:
        key = (entity_type, canonical.casefold())
        if key in entities:
            return entities[key]
    if preferred_types:
        return add_entity(entities, preferred_types[0], canonical, block, category, "rule", 0.82)
    for (entity_type, value), entity in entities.items():
        if value == canonical.casefold():
            return entity
    return None


def add_entity(output: dict[tuple[str, str], dict[str, Any]], entity_type: str, raw_name: str, block: dict[str, Any], category: str, extractor: str, confidence: float) -> dict[str, Any]:
    canonical = normalize_text(raw_name)
    key = (entity_type, canonical.casefold())
    evidence = build_evidence(block)
    span = candidate_span(block, canonical)
    if key not in output:
        temp_id = f"e{len(output) + 1}"
        status = "accepted" if confidence >= 0.8 else "reviewing"
        output[key] = {
            "tempId": temp_id,
            "blockId": evidence["blockId"],
            "graphCategoryId": category,
            "entityType": entity_type,
            "rawName": raw_name,
            "canonicalName": canonical,
            "uniqueKey": {"name": canonical},
            "properties": {},
            "evidence": [evidence],
            "evidenceRefs": [evidence],
            "evidenceBlockIds": [evidence["blockId"]],
            "mentionSpans": [span],
            "confidence": confidence,
            "confidenceBreakdown": entity_confidence_breakdown(confidence, extractor, span),
            "extractor": extractor,
            "extractorVersion": "v1",
            "mergeKey": f"{entity_type}:{canonical.casefold()}",
            "qualityFlags": candidate_quality_flags(canonical, span),
            "reviewStatus": status,
            "status": status,
        }
    else:
        item = output[key]
        if evidence["blockId"] and evidence["blockId"] not in item["evidenceBlockIds"]:
            item["evidence"].append(evidence)
            item.setdefault("evidenceRefs", []).append(evidence)
            item["evidenceBlockIds"].append(evidence["blockId"])
            item.setdefault("mentionSpans", []).append(span)
            item["confidence"] = min(1.0, float(item["confidence"]) + 0.03)
            item["confidenceBreakdown"] = entity_confidence_breakdown(float(item["confidence"]), extractor, span)
    return output[key]


def add_relation(output: dict[tuple[str, str, str], dict[str, Any]], relation_type: str, source: dict[str, Any], target: dict[str, Any], block: dict[str, Any], category: str, properties: dict[str, Any] | None = None, extractor: str = "rule", confidence: float = 0.84, status: str = "accepted") -> None:
    key = (relation_type, source["tempId"], target["tempId"])
    evidence = build_evidence(block)
    evidence_span = relation_span(block, source.get("canonicalName", ""), target.get("canonicalName", ""))
    if key not in output:
        output[key] = {
            "sourceTempId": source["tempId"],
            "targetTempId": target["tempId"],
            "graphCategoryId": category,
            "relationType": relation_type,
            "rawPredicate": relation_type,
            "direction": "forward",
            "properties": properties or {},
            "evidence": [evidence],
            "evidenceRefs": [evidence],
            "evidenceBlockIds": [evidence["blockId"]],
            "evidenceSpan": evidence_span,
            "confidence": confidence,
            "confidenceBreakdown": relation_confidence_breakdown(confidence, extractor, evidence_span),
            "extractor": extractor,
            "extractorVersion": "v1",
            "dedupKey": f"{source['tempId']}:{relation_type}:{target['tempId']}",
            "validationFlags": relation_validation_flags(source, target, evidence_span),
            "reviewStatus": status,
            "status": status,
        }


def candidate_span(block: dict[str, Any], text: str) -> dict[str, Any]:
    raw_text = str(block.get("rawText") or block.get("normalizedText") or "")
    start = raw_text.find(text) if text else -1
    if start < 0:
        start = None
        end = None
    else:
        end = start + len(text)
    metadata = block.get("metadata") if isinstance(block.get("metadata"), dict) else {}
    span_meta = metadata.get("span") if isinstance(metadata.get("span"), dict) else {}
    return {
        "sourceId": block.get("sourceId"),
        "blockId": block.get("blockId"),
        "start": start,
        "end": end,
        "lineNo": metadata.get("lineNo") or span_meta.get("lineNo"),
        "pageNo": block.get("pageNo") or span_meta.get("pageNo"),
        "rowNo": block.get("rowNo") or span_meta.get("rowNo"),
        "colNo": block.get("colNo") or span_meta.get("colNo"),
    }


def relation_span(block: dict[str, Any], source_name: str, target_name: str) -> dict[str, Any]:
    raw_text = str(block.get("rawText") or block.get("normalizedText") or "")
    starts = [index for index in [raw_text.find(str(source_name or "")), raw_text.find(str(target_name or ""))] if index >= 0]
    if starts:
        start = min(starts)
        end = max(raw_text.find(str(source_name or "")) + len(str(source_name or "")), raw_text.find(str(target_name or "")) + len(str(target_name or "")))
    else:
        start = None
        end = None
    metadata = block.get("metadata") if isinstance(block.get("metadata"), dict) else {}
    span_meta = metadata.get("span") if isinstance(metadata.get("span"), dict) else {}
    return {
        "sourceId": block.get("sourceId"),
        "blockId": block.get("blockId"),
        "start": start,
        "end": end,
        "lineNo": metadata.get("lineNo") or span_meta.get("lineNo"),
        "pageNo": block.get("pageNo") or span_meta.get("pageNo"),
    }


def candidate_quality_flags(canonical: str, span: dict[str, Any]) -> list[str]:
    flags: list[str] = []
    if span.get("start") is None:
        flags.append("no_exact_span")
    if len(canonical) < 2:
        flags.append("too_short")
    return flags


def relation_validation_flags(source: dict[str, Any], target: dict[str, Any], span: dict[str, Any]) -> list[str]:
    flags: list[str] = []
    if not source or not target:
        flags.append("missing_entity")
    if span.get("start") is None:
        flags.append("no_exact_span")
    return flags


def entity_confidence_breakdown(confidence: float, extractor: str, span: dict[str, Any]) -> dict[str, float]:
    span_score = 0.2 if span.get("start") is not None else 0.1
    extractor_score = 0.35 if extractor == "rule" else 0.2
    return {
        "extractor": extractor_score,
        "taxonomy": 0.2,
        "span": span_score,
        "context": max(0.0, round(confidence - extractor_score - 0.2 - span_score, 4)),
        "total": confidence,
    }


def relation_confidence_breakdown(confidence: float, extractor: str, span: dict[str, Any]) -> dict[str, float]:
    span_score = 0.15 if span.get("start") is not None else 0.08
    trigger_score = 0.25 if extractor == "rule" else 0.15
    return {
        "entity": 0.25,
        "trigger": trigger_score,
        "typePair": 0.2,
        "span": span_score,
        "direction": 0.1,
        "total": confidence,
    }


def normalize_candidates(request: dict[str, Any]) -> dict[str, Any]:
    """别名归一 / Cross link / Conflict detection 真实实现见 KG-AI-003。"""
    frozen_ids = {str(x) for x in (request.get("frozenCandidateIds") or [])}
    candidates = request.get("candidates") or []
    normalized = []
    for item in candidates:
        if not isinstance(item, dict):
            continue
        candidate_id = str(item.get("candidateId") or "")
        if candidate_id and candidate_id in frozen_ids:
            normalized.append({**item, "skipped": True, "reason": "frozen"})
            continue
        signals = item.get("signals") or {}
        breakdown = score_breakdown(CanonicalSignals(
            unique_key=bool(signals.get("uniqueKey")),
            alias=bool(signals.get("alias")),
            regex=bool(signals.get("regex")),
            embedding=float(signals.get("embedding") or 0),
            code_graph_ref=bool(signals.get("codeGraphRef")),
            llm_verify=bool(signals.get("llmVerify")),
        ))
        normalized.append({**item, "scoreBreakdown": breakdown, "crossLinkHint": breakdown["crossLinkHint"]})
    return {
        "normalized": {"entities": normalized, "relations": []},
        "conflicts": [],
        "implHint": "KG-AI-007 scoreBreakdown/frozen skip 已接入；完整归一策略待 Java 表决链联调",
    }


def retrieve_plan(request: dict[str, Any]) -> dict[str, Any]:
    """检索规划真实实现见 KG-AI-004。"""
    s = get_settings()
    plan = build_retrieve_plan(
        str(request.get("questionText") or ""),
        request.get("keywords") if isinstance(request.get("keywords"), list) else None,
    )
    return {
        **plan,
        "embeddingModel": s.embedding_model,
        "embeddingVersion": s.embedding_version,
        "implHint": "KG-AI-004 planner 待细化；KG-AI-006 keywords/vectorQueryText fallback 已接入",
    }
