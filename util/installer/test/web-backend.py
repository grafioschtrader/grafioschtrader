"""Disposable HTTP/WebSocket and AJP13 fixture; no application database or secrets."""
import base64
import hashlib
import http.server
import json
import socketserver
import struct
import threading


def payload(path, headers):
    return json.dumps({"databaseName": "grafioschtrader", "activeProfile": "production",
                       "path": path, "headers": headers}).encode()


class HTTP(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_GET(self):
        if self.headers.get("Upgrade") == "websocket":
            accept = base64.b64encode(hashlib.sha1((self.headers["Sec-WebSocket-Key"] +
                "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encode()).digest()).decode()
            self.send_response(101)
            self.send_header("Upgrade", "websocket")
            self.send_header("Connection", "Upgrade")
            self.send_header("Sec-WebSocket-Accept", accept)
            self.end_headers()
            self.wfile.write(b"\x81\x02OK")
            self.wfile.flush()
            self.close_connection = True
            return
        body = payload(self.path, dict(self.headers))
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


def string(value):
    data = value.encode()
    return struct.pack("!H", len(data)) + data + b"\0"


class AJP(socketserver.StreamRequestHandler):
    def send(self, data):
        self.wfile.write(b"AB" + struct.pack("!H", len(data)) + data)
        self.wfile.flush()

    def handle(self):
        while True:
            header = self.rfile.read(4)
            if not header:
                return
            assert header[:2] == b"\x12\x34"
            data = self.rfile.read(struct.unpack("!H", header[2:])[0])
            if data[0] == 10:
                self.send(b"\x09")
                continue
            assert data[0] == 2
            offset = 2

            def number():
                nonlocal offset
                result = struct.unpack("!H", data[offset:offset+2])[0]
                offset += 2
                return result

            def text(size=None):
                nonlocal offset
                size = number() if size is None else size
                if size == 65535:
                    return ""
                result = data[offset:offset+size].decode()
                offset += size + 1
                return result

            text()  # protocol
            path = text()
            text(); text(); text()  # remote address, remote name, server name
            number(); offset += 1  # port, SSL flag
            headers = {}
            common = ("accept", "accept-charset", "accept-encoding", "accept-language", "authorization",
                      "connection", "content-type", "content-length", "cookie", "cookie2", "host",
                      "pragma", "referer", "user-agent")
            for _ in range(number()):
                size = number()
                name = common[(size & 255)-1] if size & 0xFF00 == 0xA000 else text(size)
                headers[name.lower()] = text()
            body = payload(path, headers)
            self.send(b"\x04" + struct.pack("!H", 200) + string("OK") + struct.pack("!H", 2) +
                      b"\xa0\x01" + string("application/json") + b"\xa0\x03" + string(str(len(body))))
            self.send(b"\x03" + struct.pack("!H", len(body)) + body + b"\0")
            self.send(b"\x05\x01")


socketserver.ThreadingTCPServer.allow_reuse_address = True
ajp = socketserver.ThreadingTCPServer(("127.0.0.1", 9090), AJP)
threading.Thread(target=ajp.serve_forever, daemon=True).start()
http.server.ThreadingHTTPServer(("127.0.0.1", 9091), HTTP).serve_forever()
