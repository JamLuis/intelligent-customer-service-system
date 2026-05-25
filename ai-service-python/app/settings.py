from functools import lru_cache
from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


_REPO_ROOT = Path(__file__).resolve().parents[2]


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=str(_REPO_ROOT / ".env"),
        env_file_encoding="utf-8",
        extra="ignore",
    )

    llm_provider: str = Field(default="bailian", alias="LLM_PROVIDER")
    llm_api_base_url: str = Field(
        default="https://dashscope.aliyuncs.com/compatible-mode/v1",
        alias="LLM_API_BASE_URL",
    )
    llm_api_key: str = Field(default="", alias="LLM_API_KEY")
    llm_workspace_id: str = Field(default="", alias="LLM_WORKSPACE_ID")
    llm_model: str = Field(default="qwen3.6-flash", alias="LLM_MODEL")

    embedding_model: str = Field(default="text-embedding-v4", alias="EMBEDDING_MODEL")
    embedding_dim: int = Field(default=1536, alias="EMBEDDING_DIM")
    embedding_version: str = Field(default="v1", alias="EMBEDDING_VERSION")

    http_timeout_s: float = Field(default=30.0, alias="AI_HTTP_TIMEOUT_S")
    knowledge_llm_extract_enabled: bool = Field(default=True, alias="KNOWLEDGE_LLM_EXTRACT_ENABLED")


@lru_cache
def get_settings() -> Settings:
    return Settings()
