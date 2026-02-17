"""Event system used by OpenClaw core components."""

from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass, field
from threading import RLock
from typing import Any, Callable, DefaultDict, Mapping

EventHandler = Callable[["Event"], None]


@dataclass(frozen=True, slots=True)
class Event:
    """Immutable event model for runtime and services."""

    name: str
    payload: Mapping[str, Any] = field(default_factory=dict)


class EventBus:
    """Simple thread-safe in-process pub/sub bus."""

    def __init__(self) -> None:
        self._handlers: DefaultDict[str, list[EventHandler]] = defaultdict(list)
        self._lock = RLock()

    def subscribe(self, event_name: str, handler: EventHandler) -> Callable[[], None]:
        """Register a handler and return an unsubscribe callback."""

        with self._lock:
            self._handlers[event_name].append(handler)

        def _unsubscribe() -> None:
            with self._lock:
                handlers = self._handlers.get(event_name)
                if not handlers:
                    return
                try:
                    handlers.remove(handler)
                except ValueError:
                    return
                if not handlers:
                    self._handlers.pop(event_name, None)

        return _unsubscribe

    def publish(self, event: Event) -> None:
        """Publish an event to current subscribers."""

        with self._lock:
            handlers = tuple(self._handlers.get(event.name, ()))
        for handler in handlers:
            handler(event)

    def subscriber_count(self, event_name: str) -> int:
        with self._lock:
            return len(self._handlers.get(event_name, ()))
