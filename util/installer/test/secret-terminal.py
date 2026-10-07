#!/usr/bin/env python3
"""Exercise hidden input and cancellation on a real Linux pseudo-terminal, with fake secrets."""
import os
from pathlib import Path
import pty
import select
import signal
import termios
import time


SCRIPT = Path(__file__).resolve().parents[1] / "gt-install.sh"
FIXTURE = b'  fake-$HOME-"quote"-\\backslash=only  '


def wait_for_child(child, deadline):
    """PTY closure can precede process exit; share the terminal's original deadline."""
    while True:
        waited, status = os.waitpid(child, os.WNOHANG)
        if waited == child:
            return status
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise AssertionError("credential reader did not exit")
        time.sleep(min(0.01, remaining))


def exercise(cancel=None):
    child, terminal = pty.fork()
    if child == 0:
        # Start with tracing deliberately enabled; the credential reader must switch it off.
        os.execvp("bash", ["bash", "-c", '''
export GT_INSTALL_SOURCE_ONLY=1
source "$1"
gt_reset; gt_question_model
QUESTION_FD=0 QUESTION_OUTPUT=1
trap gt_cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
set -x
gt_ask_secret DB_PASSWORD yes || exit $?
printf 'FINISHED\n'
''', "terminal-test", str(SCRIPT)])
    output = bytearray()
    initial = termios.tcgetattr(terminal)
    deadline = time.monotonic() + 15
    reaped = False

    def receive_until(marker):
        start = len(output)
        while marker not in output[start:]:
            if time.monotonic() >= deadline:
                raise AssertionError("terminal test timed out")
            if select.select([terminal], [], [], 0.1)[0]:
                try:
                    block = os.read(terminal, 65536)
                except OSError:
                    block = b""
                if not block:
                    raise AssertionError("terminal closed before the expected prompt")
                output.extend(block)

    try:
        receive_until(b"(DB_PASSWORD): ")
        assert not termios.tcgetattr(terminal)[3] & termios.ECHO
        if cancel:
            os.write(terminal, FIXTURE[:12])
            if cancel == signal.SIGINT:
                os.write(terminal, b"\x03")
            else:
                os.kill(child, cancel)
        else:
            os.write(terminal, FIXTURE + b"\n")
            receive_until(b"Repeat password (DB_PASSWORD): ")
            os.write(terminal, b"mismatch-test-only\n")
            receive_until(b"try again.")
            # The retry prompt may already be in the same read as the error.
            while not output.endswith(b"(DB_PASSWORD): "):
                receive_until(b"(DB_PASSWORD): ")
            os.write(terminal, FIXTURE + b"\n")
            receive_until(b"Repeat password (DB_PASSWORD): ")
            os.write(terminal, FIXTURE + b"\n")
        while time.monotonic() < deadline:
            if select.select([terminal], [], [], 0.1)[0]:
                try:
                    block = os.read(terminal, 65536)
                except OSError:
                    break
                if not block:
                    break
                output.extend(block)
        status = wait_for_child(child, deadline)
        reaped = True
        expected = 128 + cancel if cancel else 0
        assert os.waitstatus_to_exitcode(status) == expected, "unexpected terminal exit status"
        assert b"fake-$HOME" not in output and b"mismatch-test-only" not in output, "secret echoed"
        assert bool(termios.tcgetattr(terminal)[3] & termios.ECHO) == bool(initial[3] & termios.ECHO)
        if not cancel:
            assert b"FINISHED" in output
    finally:
        try:
            if not reaped:
                os.kill(child, signal.SIGKILL)
                os.waitpid(child, 0)
        except (ProcessLookupError, ChildProcessError):
            pass
        os.close(terminal)


if __name__ == "__main__":
    for cancellation in (None, signal.SIGINT, signal.SIGTERM):
        exercise(cancellation)
    print("PASS: hidden input, mismatch retry, trace suppression, Ctrl-C/SIGTERM and echo restoration")
