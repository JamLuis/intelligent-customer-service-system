from typing import Any

from fastapi import APIRouter

from app.answer import service as answer_service
from app.retrieval import planner

router = APIRouter()


@router.post("/chat/answer")
def answer_chat(request: dict[str, Any]) -> dict[str, Any]:
    return answer_service.answer_chat(request)


@router.post("/graphs/retrieve-plan")
def retrieve_plan(request: dict[str, Any]) -> dict[str, Any]:
    return planner.retrieve_plan(request)
