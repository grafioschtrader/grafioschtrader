"""Local SMTP fixture with STARTTLS, implicit TLS, authentication and rejection."""
import logging
import pathlib
import signal
import ssl
import sys
from aiosmtpd.controller import Controller
from aiosmtpd.smtp import AuthResult, LoginPassword

directory = pathlib.Path(sys.argv[1])
password = (directory / 'password').read_bytes()
logging.disable(logging.CRITICAL)
context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
context.load_cert_chain('/root/gt-test-certificates/fullchain.pem', '/root/gt-test-certificates/server.key')


def authenticate(server, session, envelope, mechanism, data):
    success = isinstance(data, LoginPassword) and data.login == b'sender@example.test' and data.password == password
    return AuthResult(success=success, handled=False)


class Handler:
    async def handle_RCPT(self, server, session, envelope, address, options):
        if address == 'reject@example.test':
            return '550 fixture rejection'
        envelope.rcpt_tos.append(address)
        return '250 OK'

    async def handle_DATA(self, server, session, envelope):
        with (directory / 'messages').open('ab') as output:
            output.write(envelope.original_content + b'\n---\n')
        with (directory / 'count').open('a') as output:
            output.write('message\n')
        return '250 accepted'


controllers = [
    Controller(Handler(), hostname='127.0.0.1', port=2525, tls_context=context,
               require_starttls=True, auth_required=True, authenticator=authenticate),
    Controller(Handler(), hostname='127.0.0.1', port=2465, ssl_context=context,
               auth_required=True, auth_require_tls=False, authenticator=authenticate),
    Controller(Handler(), hostname='127.0.0.1', port=2526, auth_exclude_mechanism=['PLAIN', 'LOGIN'])
]
for controller in controllers:
    controller.start()
(directory / 'ready').touch()
signal.pause()
