#!/usr/bin/env python3
"""Run the Python SDK unit tests without requiring pytest to be installed.

If pytest is available, it remains the preferred runner. This fallback keeps the
core SDK tests executable in minimal Windows developer environments where only a
Python runtime is present.
"""

from __future__ import annotations

import importlib.util
import inspect
import os
import re
import shutil
import sys
import tempfile
import traceback
import uuid
from pathlib import Path
from types import ModuleType


ROOT = Path(__file__).resolve().parent
TEMP_ROOT = ROOT / "target" / "test-tmp"
TEST_FILES = (
    ROOT / "tests" / "test_sdk.py",
    ROOT / "tests" / "test_config_facade.py",
)


class Raises:
    def __init__(self, expected_exception: type[BaseException], match: str | None = None):
        self.expected_exception = expected_exception
        self.match = match
        self.value: BaseException | None = None

    def __enter__(self) -> "Raises":
        return self

    def __exit__(self, exc_type, exc_value, exc_traceback) -> bool:
        if exc_type is None:
            raise AssertionError(f"Expected {self.expected_exception.__name__} to be raised")
        if not issubclass(exc_type, self.expected_exception):
            return False
        if self.match and not re.search(self.match, str(exc_value)):
            raise AssertionError(
                f"Expected exception message to match {self.match!r}, got {exc_value!r}"
            )
        self.value = exc_value
        return True


class MonkeyPatch:
    _MISSING = object()

    def __init__(self):
        self._env: dict[str, object] = {}

    def setenv(self, name: str, value: str) -> None:
        self._capture(name)
        os.environ[name] = value

    def delenv(self, name: str, raising: bool = True) -> None:
        self._capture(name)
        if name in os.environ:
            del os.environ[name]
        elif raising:
            raise KeyError(name)

    def undo(self) -> None:
        for name, value in self._env.items():
            if value is self._MISSING:
                os.environ.pop(name, None)
            else:
                os.environ[name] = str(value)

    def _capture(self, name: str) -> None:
        if name not in self._env:
            self._env[name] = os.environ.get(name, self._MISSING)


class WorkspaceTemporaryDirectory:
    """TemporaryDirectory replacement that avoids restrictive Windows ACLs."""

    def __init__(self, suffix: str | None = None, prefix: str | None = None,
                 dir: str | None = None, ignore_cleanup_errors: bool = False):
        base = Path(dir) if dir else TEMP_ROOT
        base.mkdir(parents=True, exist_ok=True)
        name_prefix = prefix or "tmp"
        name_suffix = suffix or ""
        self.path = base / f"{name_prefix}{uuid.uuid4().hex}{name_suffix}"
        self.path.mkdir()
        self.name = str(self.path)
        self.ignore_cleanup_errors = ignore_cleanup_errors

    def __enter__(self) -> str:
        return self.name

    def __exit__(self, exc_type, exc_value, exc_traceback) -> None:
        self.cleanup()

    def cleanup(self) -> None:
        shutil.rmtree(self.path, ignore_errors=self.ignore_cleanup_errors)


def install_pytest_stub() -> None:
    try:
        __import__("pytest")
        return
    except ModuleNotFoundError:
        pass

    pytest_stub = ModuleType("pytest")
    pytest_stub.raises = Raises
    sys.modules["pytest"] = pytest_stub


def load_module(path: Path) -> ModuleType:
    module_name = f"dittoe_sdk_unit_{path.stem}"
    spec = importlib.util.spec_from_file_location(module_name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Unable to load test module: {path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def collect_tests(module: ModuleType):
    for _, test_class in inspect.getmembers(module, inspect.isclass):
        if not test_class.__name__.startswith("Test"):
            continue
        instance = test_class()
        methods = [
            getattr(instance, name)
            for name in dir(instance)
            if name.startswith("test_") and callable(getattr(instance, name))
        ]
        for method in sorted(methods, key=lambda m: m.__func__.__code__.co_firstlineno):
            yield f"{test_class.__name__}.{method.__name__}", method


def fixture_args(method):
    fixtures = {}
    cleanups = []
    for name in inspect.signature(method).parameters:
        if name == "monkeypatch":
            monkeypatch = MonkeyPatch()
            fixtures[name] = monkeypatch
            cleanups.append(monkeypatch.undo)
        elif name == "tmp_path":
            temp_dir = tempfile.TemporaryDirectory()
            fixtures[name] = Path(temp_dir.name)
            cleanups.append(temp_dir.cleanup)
        else:
            raise RuntimeError(f"Unsupported fixture {name!r} for {method.__name__}")
    return fixtures, cleanups


def main() -> int:
    sys.path.insert(0, str(ROOT))
    TEMP_ROOT.mkdir(parents=True, exist_ok=True)
    tempfile.tempdir = str(TEMP_ROOT)
    tempfile.TemporaryDirectory = WorkspaceTemporaryDirectory
    os.environ["TMP"] = str(TEMP_ROOT)
    os.environ["TEMP"] = str(TEMP_ROOT)
    install_pytest_stub()

    total = 0
    failed = []
    print("=== DITTOE Python SDK unit tests ===")

    for test_file in TEST_FILES:
        module = load_module(test_file)
        for name, method in collect_tests(module):
            total += 1
            fixtures, cleanups = fixture_args(method)
            try:
                method(**fixtures)
                print(f"PASS {name}")
            except Exception:
                failed.append(name)
                print(f"FAIL {name}")
                traceback.print_exc()
            finally:
                for cleanup in reversed(cleanups):
                    try:
                        cleanup()
                    except Exception as exc:
                        failed.append(f"{name} cleanup")
                        print(f"FAIL {name} cleanup: {exc}")

    print(f"=== Results: {total - len(failed)}/{total} tests passed ===")
    if failed:
        print("Failures:")
        for name in failed:
            print(f"  - {name}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
