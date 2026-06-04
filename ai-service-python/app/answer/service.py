from typing import Any

from app.services import runtime


def answer_chat(request: dict[str, Any]) -> dict[str, Any]:
    return runtime.answer_chat(request)
