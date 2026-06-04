from app.services import runtime


extract_openie_with_llm = runtime.extract_openie_with_llm
openie_prompt = runtime.openie_prompt
parse_json_object = runtime.parse_json_object
apply_openie_payload = runtime.apply_openie_payload

__all__ = [
    "extract_openie_with_llm",
    "openie_prompt",
    "parse_json_object",
    "apply_openie_payload",
]
