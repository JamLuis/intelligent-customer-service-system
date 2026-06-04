from typing import Any

from app.services import runtime


extract_entities_from_lines = runtime.extract_entities_from_lines
extract_entities_from_rules = runtime.extract_entities_from_rules
extract_generic_facts = runtime.extract_generic_facts
extract_sql_ddl_facts = runtime.extract_sql_ddl_facts
extract_relations_from_lines = runtime.extract_relations_from_lines
add_entity = runtime.add_entity
add_relation = runtime.add_relation

__all__ = [
    "extract_entities_from_lines",
    "extract_entities_from_rules",
    "extract_generic_facts",
    "extract_sql_ddl_facts",
    "extract_relations_from_lines",
    "add_entity",
    "add_relation",
]
