import unittest

from openclaw.runtime import OpenClawRuntime


class _NoopService:
    def __init__(self) -> None:
        self.name = "noop"
        self.started = False
        self.stopped = False

    def start(self, runtime) -> None:
        self.started = True

    def stop(self) -> None:
        self.stopped = True


class RuntimeTests(unittest.TestCase):
    def test_runtime_lifecycle_publishes_events(self) -> None:
        runtime = OpenClawRuntime()
        events: list[str] = []

        runtime.events.subscribe("runtime.starting", lambda event: events.append(event.name))
        runtime.events.subscribe("runtime.started", lambda event: events.append(event.name))
        runtime.events.subscribe("runtime.stopping", lambda event: events.append(event.name))
        runtime.events.subscribe("runtime.stopped", lambda event: events.append(event.name))

        runtime.start()
        runtime.shutdown()

        self.assertEqual(runtime.state, "stopped")
        self.assertEqual(
            events,
            ["runtime.starting", "runtime.started", "runtime.stopping", "runtime.stopped"],
        )

    def test_register_service_and_run_via_context_manager(self) -> None:
        runtime = OpenClawRuntime()
        service = _NoopService()
        runtime.register_service(service)

        with runtime as started_runtime:
            self.assertIs(started_runtime, runtime)
            self.assertEqual(runtime.state, "running")
            self.assertTrue(service.started)
            self.assertFalse(service.stopped)

        self.assertEqual(runtime.state, "stopped")
        self.assertTrue(service.stopped)


if __name__ == "__main__":
    unittest.main()
