from __future__ import annotations

import re
from typing import Any

from app.preprocess.ir.models import IRBlock, IRDoc, IRSpan
from app.preprocess.artifact import blocks_from_ir
from app.preprocess.utils import normalize_text


class SqlParser:
    parser_name = "sql"

    def parse(self, raw_text: str, source_id: Any, graph_category_id: Any) -> IRDoc:
        tables = parse_create_table_statements(raw_text)
        if not tables:
            blocks = [IRBlock(block_type="sql", raw_text=raw_text, normalized_text=normalize_text(raw_text), span=IRSpan(line_no=1, page_no=1), metadata={"parser": self.parser_name})] if raw_text.strip() else []
            return IRDoc(source_id=source_id, graph_category_id=graph_category_id, source_type="sql", parser=self.parser_name, blocks=blocks)
        blocks: list[IRBlock] = []
        for index, table in enumerate(tables, start=1):
            column_summary = ", ".join(column["name"] for column in table["columns"][:12])
            normalized = f"CREATE TABLE {table['name']} ({column_summary})"
            blocks.append(IRBlock(block_type="code", raw_text=table["raw"], normalized_text=normalized, span=IRSpan(line_no=index, page_no=index), metadata={"parser": self.parser_name, "ddlKind": "create_table", "table": table}))
        return IRDoc(source_id=source_id, graph_category_id=graph_category_id, source_type="sql", parser=self.parser_name, blocks=blocks)


def sql_blocks(raw_text: str, source_id: Any, graph_category_id: Any) -> list[dict[str, Any]]:
    return blocks_from_ir(SqlParser().parse(raw_text, source_id, graph_category_id))


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
