import unittest

from openclaw.events import Event, EventBus


class EventBusTests(unittest.TestCase):
    def test_publish_dispatches_to_subscribers_in_registration_order(self) -> None:
        bus = EventBus()
        calls: list[str] = []

        bus.subscribe("runtime.started", lambda event: calls.append(f"first:{event.name}"))
        bus.subscribe("runtime.started", lambda event: calls.append(f"second:{event.name}"))

        bus.publish(Event(name="runtime.started"))

        self.assertEqual(calls, ["first:runtime.started", "second:runtime.started"])

    def test_unsubscribe_removes_handler(self) -> None:
        bus = EventBus()
        calls: list[str] = []

        unsubscribe = bus.subscribe("runtime.started", lambda event: calls.append(event.name))
        unsubscribe()

        bus.publish(Event(name="runtime.started"))

        self.assertEqual(calls, [])
        self.assertEqual(bus.subscriber_count("runtime.started"), 0)


if __name__ == "__main__":
    unittest.main()
