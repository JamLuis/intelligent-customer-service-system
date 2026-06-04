from typing import Any

from app.services import runtime


def embed_blocks(request: dict[str, Any]) -> dict[str, Any]:
    return runtime.embed_blocks(request)
