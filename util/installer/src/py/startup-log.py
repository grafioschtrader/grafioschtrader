"""Inspect only a journaled startup's log suffix; never print application output."""

import hashlib
import os
import re
import stat
import sys


def cursor(stream, info, offset):
    stream.seek(max(0, offset - 64))
    anchor = hashlib.sha256(stream.read(min(offset, 64))).hexdigest()
    return f"{info.st_dev}:{info.st_ino}:{offset}:{anchor}"


def main():
    operation, path = sys.argv[1:3]
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(fd, "rb") as stream:
        info = os.fstat(stream.fileno())
        if not stat.S_ISREG(info.st_mode):
            return 2
        if operation == "cursor":
            print(cursor(stream, info, info.st_size))
            return 0
        saved = sys.argv[3]
        if operation != "scan" or not re.fullmatch(r"\d+:\d+:\d+:[a-f0-9]{64}", saved):
            return 2
        device, inode, offset, _ = saved.split(":")
        offset = int(offset)
        # Rotation, truncation and copytruncate followed by regrowth invalidate
        # the old offset. A short boundary hash detects the latter case too.
        if (int(device), int(inode)) != (info.st_dev, info.st_ino) or offset > info.st_size:
            offset = 0
        elif cursor(stream, info, offset) != saved:
            offset = 0
        stream.seek(offset)
        remaining = info.st_size - offset
        tail = b""
        patterns = (
            (b"Access denied for user", "database-authentication"),
            (b"FlywayException", "migration"),
            (b"APPLICATION FAILED TO START", "application-start"),
        )
        while remaining > 0:
            chunk = stream.read(min(65536, remaining))
            if not chunk:
                break
            remaining -= len(chunk)
            text = tail + chunk
            for pattern, label in patterns:
                if pattern in text:
                    print(label)
                    return 10
            tail = text[-64:]
        return 0


try:
    sys.exit(main())
except (OSError, ValueError, IndexError):
    sys.exit(2)
