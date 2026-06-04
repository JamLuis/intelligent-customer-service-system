from typing import Any

from app.services import runtime


def retrieve_plan(request: dict[str, Any]) -> dict[str, Any]:
    return runtime.retrieve_plan(request)
