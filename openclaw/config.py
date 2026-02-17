"""Configuration primitives for OpenClaw runtime."""

from __future__ import annotations

import os
from dataclasses import dataclass
from typing import Mapping

_TRUE_VALUES = {"1", "true", "t", "yes", "y", "on"}


def _parse_bool(value: str) -> bool:
    return value.strip().lower() in _TRUE_VALUES


@dataclass(frozen=True, slots=True)
class RuntimeConfig:
    """Configuration object used by ``OpenClawRuntime``."""

    environment: str = "development"
    debug: bool = False
    log_level: str = "INFO"

    @classmethod
    def from_env(cls, environ: Mapping[str, str] | None = None) -> "RuntimeConfig":
        """Build config from ``OPENCLAW_*`` environment variables."""

        source = os.environ if environ is None else environ
        return cls(
            environment=source.get("OPENCLAW_ENV", "development"),
            debug=_parse_bool(source.get("OPENCLAW_DEBUG", "0")),
            log_level=source.get("OPENCLAW_LOG_LEVEL", "INFO").upper(),
        )
