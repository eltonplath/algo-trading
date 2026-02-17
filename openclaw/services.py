"""Service lifecycle abstractions for OpenClaw runtime."""

from __future__ import annotations

from collections.abc import Iterable
from dataclasses import dataclass
from typing import TYPE_CHECKING, Protocol

if TYPE_CHECKING:
    from .runtime import OpenClawRuntime


class Service(Protocol):
    """Protocol for runtime-managed services."""

    name: str

    def start(self, runtime: "OpenClawRuntime") -> None:
        ...

    def stop(self) -> None:
        ...


@dataclass(frozen=True, slots=True)
class ServiceStartupError(RuntimeError):
    service_name: str
    cause: Exception

    def __str__(self) -> str:
        return f"Service '{self.service_name}' failed to start: {self.cause}"


class ServiceContainer:
    """Ordered registry and lifecycle manager for services."""

    def __init__(self) -> None:
        self._services: dict[str, Service] = {}
        self._started: list[Service] = []

    def __iter__(self) -> Iterable[Service]:
        return iter(self._services.values())

    def register(self, service: Service) -> None:
        if service.name in self._services:
            raise ValueError(f"Service '{service.name}' is already registered")
        self._services[service.name] = service

    def get(self, name: str) -> Service:
        return self._services[name]

    def start_all(self, runtime: "OpenClawRuntime") -> None:
        if self._started:
            return

        for service in self._services.values():
            try:
                service.start(runtime)
                self._started.append(service)
            except Exception as exc:  # pragma: no cover - exercised via tests
                self._rollback_started()
                raise ServiceStartupError(service.name, exc) from exc

    def stop_all(self) -> None:
        errors: list[Exception] = []
        for service in reversed(self._started):
            try:
                service.stop()
            except Exception as exc:  # pragma: no cover - defensive
                errors.append(exc)
        self._started.clear()
        if errors:
            raise RuntimeError(
                f"{len(errors)} service(s) failed to stop cleanly; first error: {errors[0]}"
            ) from errors[0]

    def _rollback_started(self) -> None:
        for service in reversed(self._started):
            try:
                service.stop()
            except Exception:
                # Startup failure is primary. Rollback should be best effort.
                pass
        self._started.clear()
