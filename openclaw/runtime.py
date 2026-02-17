"""Application runtime object for OpenClaw."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Literal

from .config import RuntimeConfig
from .events import Event, EventBus
from .services import Service, ServiceContainer

RuntimeState = Literal["created", "running", "stopped"]


@dataclass(slots=True)
class OpenClawRuntime:
    """Coordinates configuration, events, and managed services."""

    config: RuntimeConfig = field(default_factory=RuntimeConfig.from_env)
    events: EventBus = field(default_factory=EventBus)
    services: ServiceContainer = field(default_factory=ServiceContainer)
    _state: RuntimeState = field(default="created", init=False)

    @property
    def state(self) -> RuntimeState:
        return self._state

    def register_service(self, service: Service) -> None:
        if self._state != "created":
            raise RuntimeError("Services can only be registered before runtime start")
        self.services.register(service)

    def start(self) -> None:
        if self._state == "running":
            return
        if self._state == "stopped":
            raise RuntimeError("Runtime cannot be restarted once stopped")

        self.events.publish(Event(name="runtime.starting"))
        try:
            self.services.start_all(self)
        except Exception as exc:
            self.events.publish(
                Event(name="runtime.start_failed", payload={"error": str(exc)})
            )
            raise

        self._state = "running"
        self.events.publish(Event(name="runtime.started"))

    def shutdown(self) -> None:
        if self._state == "stopped":
            return

        self.events.publish(Event(name="runtime.stopping"))
        self.services.stop_all()
        self._state = "stopped"
        self.events.publish(Event(name="runtime.stopped"))

    def __enter__(self) -> "OpenClawRuntime":
        self.start()
        return self

    def __exit__(self, exc_type, exc, tb) -> None:
        self.shutdown()
