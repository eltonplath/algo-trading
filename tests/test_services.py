import unittest

from openclaw.services import ServiceContainer, ServiceStartupError


class _StubService:
    def __init__(self, name: str, calls: list[str], fail_on_start: bool = False) -> None:
        self.name = name
        self._calls = calls
        self._fail_on_start = fail_on_start

    def start(self, runtime) -> None:
        self._calls.append(f"start:{self.name}")
        if self._fail_on_start:
            raise RuntimeError("boom")

    def stop(self) -> None:
        self._calls.append(f"stop:{self.name}")


class ServiceContainerTests(unittest.TestCase):
    def test_register_rejects_duplicate_service_names(self) -> None:
        container = ServiceContainer()
        calls: list[str] = []
        container.register(_StubService("db", calls))

        with self.assertRaisesRegex(ValueError, "already registered"):
            container.register(_StubService("db", calls))

    def test_startup_failure_rolls_back_started_services(self) -> None:
        container = ServiceContainer()
        calls: list[str] = []
        container.register(_StubService("a", calls))
        container.register(_StubService("b", calls, fail_on_start=True))

        with self.assertRaises(ServiceStartupError):
            container.start_all(runtime=object())

        self.assertEqual(calls, ["start:a", "start:b", "stop:a"])

    def test_stop_all_runs_in_reverse_startup_order(self) -> None:
        container = ServiceContainer()
        calls: list[str] = []
        container.register(_StubService("first", calls))
        container.register(_StubService("second", calls))

        container.start_all(runtime=object())
        container.stop_all()

        self.assertEqual(
            calls,
            ["start:first", "start:second", "stop:second", "stop:first"],
        )


if __name__ == "__main__":
    unittest.main()
