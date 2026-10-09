"""Acceptance of the whiptail front end over SSH in the disposable QEMU guest; called by vm-host.sh.

Usage: vm-dialogs.py INSTALL_LANG RESULTS_DIR ssh-command...

Three sessions, each in a pseudo-terminal whose size reaches the guest through ssh -tt, with LANG exported before
sudo as in a user's SSH session:
1. 23 rows: the dry-run must fall back to the plain prompts.
2. The other language: a dry-run through dialogs with that language's buttons and key help.
3. INSTALL_LANG: a modeless installation driven only by dialogs, with a generated root and Jasypt password and a
   typed, confirmed database password, until the printed result reports status=complete.
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

INSTALLER = "sudo bash /opt/gt-acceptance/installer.sh"
DOWN = b"\x1b[B"
CLEAR = b"\x15"
TITLE = re.compile(rb"([A-Z][A-Z_]{3,})")
ESCAPES = re.compile(rb"\x1b\[[0-9;?]*[A-Za-z]|\x1b[()][A-Z0-9]|[\x0e\x0f]")
# While the gauge is shown no key is sent: commands of the installation share the terminal. The framed dialog
# titles are matched, which the guest's UTF-8 locales draw with box characters.
GAUGE = tuple(f"┤ {title} ├".encode() for title in ("Installing", "Installation läuft"))
FINAL = tuple(f"┤ {title} ├".encode() for title in ("Result", "Ergebnis"))
LANGUAGE = {
    "de_CH.UTF-8": ("<Zurück>", "Tab: Schaltflächen", "<Installieren>"),
    "en_US.UTF-8": ("<Back>", "Tab: buttons", "<Install>"),
}


def session(name, ssh, remote, rows, answers, seconds, results):
    """Answer the dialogs by title; an idle screen without a scripted answer gets Tab+Enter (its first button)."""
    pid, fd = pty.fork()
    if pid == 0:
        fcntl.ioctl(0, termios.TIOCSWINSZ, struct.pack("HHHH", rows, 120, 0, 0))
        os.environ["TERM"] = "xterm"
        os.execvp(ssh[0], ssh + [remote])
    log, last = b"", None
    deadline = time.time() + seconds
    with open(os.path.join(results, f"dialogs-{name}.raw"), "wb") as raw:
        while time.time() < deadline:
            ready, _, _ = select.select([fd], [], [], 3)
            if ready:
                try:
                    chunk = os.read(fd, 65536)
                except OSError:
                    break
                if not chunk:
                    break
                log += chunk
                raw.write(chunk)
                continue
            gauge = max(log.rfind(marker) for marker in GAUGE)
            if gauge >= 0 and max(log.rfind(marker) for marker in FINAL) < gauge:
                continue
            found = [title for title in TITLE.findall(log[-8000:]) if title in answers]
            if found and found[-1] != last:
                last = found[-1]
                for keys in answers[last]:
                    os.write(fd, keys)
                    time.sleep(3)
            else:
                os.write(fd, b"\t\r")
    done, status = os.waitpid(pid, os.WNOHANG)
    if not done:
        os.kill(pid, signal.SIGKILL)
        os.waitpid(pid, 0)
        raise AssertionError(f"{name}: no end within {seconds} seconds")
    text = ESCAPES.sub(b" ", log).decode("utf-8", "replace")
    with open(os.path.join(results, f"dialogs-{name}.txt"), "w", encoding="utf-8") as output:
        output.write(text)
    return os.waitstatus_to_exitcode(status), text


def check(condition, message):
    if not condition:
        raise AssertionError(message)


def main():
    install_lang, results, ssh = sys.argv[1], sys.argv[2], sys.argv[3:]
    other_lang = next(lang for lang in LANGUAGE if lang != install_lang)
    dry_run = {b"ADMIN_EMAIL": [CLEAR + b"admin@example.invalid\r"], b"SMTP_CONFIGURE": [DOWN + b"\r"]}

    status, text = session("small", ssh, f"export LANG={other_lang}; {INSTALLER} --dry-run", 23,
                           {b"DOMAIN": [b"!quit\r"]}, 300, results)
    check("(DOMAIN)" in text, "small terminal: no plain DOMAIN prompt")
    check(status == 130, f"small terminal: !quit should cancel with 130, got {status}")
    check(not any(marker in text for marker in ("Tab: buttons", "Tab: Schaltflächen")),
          "small terminal: dialogs were drawn")
    print(f"PASS: 23 rows fall back to the plain prompts (exit {status})")

    status, text = session("other", ssh, f"export LANG={other_lang}; {INSTALLER} --dry-run", 40,
                           dry_run, 600, results)
    check(status in (0, 2), f"{other_lang} dry-run exit {status}")
    for marker in LANGUAGE[other_lang][:2]:
        check(marker in text, f"{other_lang}: {marker} missing")
    print(f"PASS: {other_lang} dialogs, buttons and key help in a dry-run")

    install = {
        b"ADMIN_EMAIL": [CLEAR + b"admin@example.invalid\r"],
        b"SMTP_HOST": [CLEAR + b"127.0.0.1\r"],
        b"SMTP_PORT": [CLEAR + b"2526\r"],
        b"SMTP_AUTH": [DOWN + b"\r"],
        b"SMTP_USER": [CLEAR + b"sender@example.invalid\r"],
        b"SMTP_SECURITY": [DOWN + DOWN + b"\r"],
        b"DB_ROOT_PASSWORD": [DOWN + b"\r"],
        b"DB_PASSWORD": [b"\r", b"Typed-Dialog-Pass-26\r", b"Typed-Dialog-Pass-26\r"],
        b"JASYPT_PASSWORD": [DOWN + b"\r"],
    }
    status, text = session("install", ssh, f"export LANG={install_lang}; {INSTALLER}", 40, install, 3 * 3600,
                           results)
    check(status == 0, f"installation exit {status}")
    for marker in LANGUAGE[install_lang]:
        check(marker in text, f"{install_lang}: {marker} missing")
    transcript = text[text.rfind("status="):]
    check(transcript.startswith("status=complete"), "printed result is not status=complete")
    check("Typed-Dialog-Pass-26" not in text, "a typed password was echoed")
    print(f"PASS: installation in {install_lang} driven only by dialogs, status=complete")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
