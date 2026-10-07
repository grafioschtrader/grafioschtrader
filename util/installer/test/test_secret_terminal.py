"""Linux-only deterministic coverage of exit after pseudo-terminal closure."""
import importlib.util
from pathlib import Path
import sys
import unittest
from unittest.mock import patch


@unittest.skipUnless(sys.platform == "linux", "requires Linux PTYs and waitpid")
class ChildWaitTest(unittest.TestCase):
    def setUp(self):
        spec = importlib.util.spec_from_file_location(
            "secret_terminal", Path(__file__).with_name("secret-terminal.py"))
        self.terminal = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.terminal)

    def test_closed_terminal_does_not_mean_child_has_exited(self):
        with patch.object(self.terminal.os, "waitpid", side_effect=[(0, 0), (0, 0), (123, 0)]) as wait:
            with patch.object(self.terminal.time, "monotonic", return_value=10):
                with patch.object(self.terminal.time, "sleep") as sleep:
                    self.assertEqual(self.terminal.wait_for_child(123, 15), 0)
        self.assertEqual(wait.call_count, 3)
        self.assertEqual(sleep.call_count, 2)

    def test_existing_deadline_is_not_extended_after_terminal_closure(self):
        with patch.object(self.terminal.os, "waitpid", return_value=(0, 0)):
            with patch.object(self.terminal.time, "monotonic", return_value=15):
                with self.assertRaisesRegex(AssertionError, "credential reader did not exit"):
                    self.terminal.wait_for_child(123, 15)

    def test_exit_status_is_preserved(self):
        with patch.object(self.terminal.os, "waitpid", return_value=(123, 130 << 8)):
            self.assertEqual(self.terminal.wait_for_child(123, 15), 130 << 8)


if __name__ == "__main__":
    unittest.main()
