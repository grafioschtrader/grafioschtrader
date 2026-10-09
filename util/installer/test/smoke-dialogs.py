"""Drive the real whiptail front end through a pseudo-terminal. Run only in a fresh disposable container.

A German --dry-run answers the questions by dialog title; an English --prepare additionally generates the three new
passwords. The script checks the dialog sequence, the printed transcript, that every generated password appeared on
the screen exactly once and never in the transcript, and that no scratch directory is left behind.
"""
import fcntl
import os
import pty
import re
import select
import signal
import struct
import sys
import termios
import time

INSTALLER = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "gt-install.sh")
DOWN = b"\x1b[B\r"
ESCAPES = re.compile(rb"\x1b\[[0-9;?]*[A-Za-z]|\x1b[()][A-Z0-9]|[\x0e\x0f]")
TITLE = re.compile(rb"([A-Z][A-Z_]{3,})")


def drive(mode, lang, answers, seconds=240):
    """Answer known titles; any other idle screen gets Tab+Enter, which presses its first button."""
    pid, fd = pty.fork()
    if pid == 0:
        os.environ.update(TERM="xterm", LANG=lang, LC_ALL="", LC_MESSAGES="")
        os.execvp("bash", ["bash", INSTALLER, mode])
    fcntl.ioctl(fd, termios.TIOCSWINSZ, struct.pack("HHHH", 40, 120, 0, 0))
    log, last = b"", None
    deadline = time.time() + seconds
    while time.time() < deadline:
        ready, _, _ = select.select([fd], [], [], 2.5)
        if ready:
            try:
                chunk = os.read(fd, 65536)
            except OSError:
                break
            if not chunk:
                break
            log += chunk
            continue
        found = [title for title in TITLE.findall(log[-6000:]) if title in answers]
        if found and found[-1] != last:
            last = found[-1]
            os.write(fd, answers[last])
        else:
            os.write(fd, b"\t\r")
    done, status = os.waitpid(pid, os.WNOHANG)
    if not done:
        os.kill(pid, signal.SIGKILL)
        os.waitpid(pid, 0)
        screen = ESCAPES.sub(b" ", log[-3000:]).decode("utf-8", "replace")
        raise AssertionError(f"{mode} did not finish within {seconds} seconds; dialogs "
                             f"{dialogs(ESCAPES.sub(b' ', log).decode('utf-8', 'replace'))[-6:]}; "
                             f"screen {' '.join(screen.split())[-600:]}")
    return os.waitstatus_to_exitcode(status), ESCAPES.sub(b" ", log).decode("utf-8", "replace")


def dialogs(text):
    return [title.strip() for title in re.findall(r"┤ ([^├]+?) ├", text)]


def check(condition, message):
    if not condition:
        raise AssertionError(message)


def main():
    base = {b"ADMIN_EMAIL": b"admin@example.org\r", b"SMTP_CONFIGURE": DOWN, b"TIMEZONE": b"UTC\r"}
    scratch_before = set(os.listdir("/tmp"))

    status, text = drive("--dry-run", "de_CH.UTF-8", base)
    seen = dialogs(text)
    check(status in (0, 2), f"dry-run exit {status}")
    check(seen[0] == "Bestandsaufnahme und Kompatibilität", f"first dialog {seen[:1]}")
    check(seen[-1] == "Zusammenfassung und Aktionsplan", f"last dialog {seen[-1:]}")
    for key in ("DOMAIN", "LAN_ADDRESS", "ADMIN_EMAIL", "SMTP_CONFIGURE", "TIMEZONE"):
        check(key in seen, f"no {key} dialog")
    check("<Zurück>" in text and "Tab: Schaltflächen" in text, "German buttons or key help missing")
    transcript = text[text.rfind("Installationsplan"):]
    check("ADMIN_EMAIL=admin@example.org" in transcript and "SMTP_CONFIGURE=no" in transcript,
          "dialog answers missing from the printed plan")

    answers = dict(base)
    answers.update({key: DOWN for key in (b"DB_ROOT_PASSWORD", b"DB_PASSWORD", b"JASYPT_PASSWORD")})
    status, text = drive("--prepare", "C.UTF-8", answers)
    seen = dialogs(text)
    check(status in (0, 2), f"prepare exit {status}")
    check("<Back>" in text and "Tab: buttons" in text, "English buttons or key help missing")
    start = text.rfind("Installation plan")
    check(start >= 0, "no printed plan")
    transcript = text[start:]
    for key in ("DB_PASSWORD", "JASYPT_PASSWORD"):
        check(seen.count(key) == 2, f"{key}: expected the choice and the generated-password box")
        check(re.search(rf"{key} \| .* \| generated", transcript), f"{key} not reported as generated")
    tokens = {token for token in re.findall(r"(?<![A-Za-z0-9])([A-Za-z0-9]{24})(?![A-Za-z0-9])", text)
              if re.search(r"[0-9]", token) and re.search(r"[A-Z]", token) and re.search(r"[a-z]", token)}
    check(len(tokens) >= 2, "generated passwords were not shown")
    for token in tokens:
        check(text.count(token) == 1, "a generated password was shown more than once")
        check(token not in transcript, "a generated password reached the printed plan")

    leftover = {name for name in set(os.listdir("/tmp")) - scratch_before if name.startswith("tmp.")}
    check(not leftover, f"scratch directories left behind: {sorted(leftover)}")
    print("PASS: German dry-run and English preparation with generated passwords through real whiptail dialogs")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
