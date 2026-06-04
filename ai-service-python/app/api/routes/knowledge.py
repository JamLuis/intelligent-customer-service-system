from typing import Any

from fastapi import APIRouter

from app.embedding import service as embedding_service
from app.extraction import pipeline as extraction_pipeline
from app.preprocess import service as preprocess_service

router = APIRouter()


@router.post("/knowledge/parse")
def parse_knowledge(request: dict[str, Any]) -> dict[str, Any]:
    return preprocess_service.parse_knowledge(request)


@router.post("/knowledge/embed")
def embed_blocks(request: dict[str, Any]) -> dict[str, Any]:
    return embedding_service.embed_blocks(request)


@router.post("/knowledge/extract")
def extract_candidates(request: dict[str, Any]) -> dict[str, Any]:
    return extraction_pipeline.extract_candidates(request)


@router.post("/knowledge/normalize")
def normalize_candidates(request: dict[str, Any]) -> dict[str, Any]:
    return extraction_pipeline.normalize_candidates(request)
