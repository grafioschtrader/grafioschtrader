import base64, email.message, email.utils, smtplib, ssl, sys
host, port, user, password, auth, security, recipient, send, message_id = sys.stdin.buffer.read().decode().split('\0')[:-1]
accepted = False
try:
    context = ssl.create_default_context()
    client = smtplib.SMTP_SSL(host, int(port), timeout=15, context=context) if security == 'tls' else smtplib.SMTP(host, int(port), timeout=15)
    with client:
        client.ehlo_or_helo_if_needed()
        if security == 'starttls':
            client.starttls(context=context); client.ehlo()
        if auth == 'yes':
            # smtplib.login encodes SASL responses as ASCII. Preserve UTF-8
            # credentials for PLAIN/LOGIN, as the application mail client does.
            mechanisms = client.esmtp_features.get('auth', '').upper().split()
            encode = lambda value: base64.b64encode(value.encode('utf-8')).decode('ascii')
            if 'PLAIN' in mechanisms:
                code, reply = client.docmd('AUTH', 'PLAIN ' + encode('\0'+user+'\0'+password))
                if code == 334: code, reply = client.docmd(encode('\0'+user+'\0'+password))
            elif 'LOGIN' in mechanisms:
                code, reply = client.docmd('AUTH', 'LOGIN')
                if code == 334: code, reply = client.docmd(encode(user))
                if code == 334: code, reply = client.docmd(encode(password))
            else:
                client.login(user, password)
                code, reply = 235, b''
            if code != 235: raise smtplib.SMTPAuthenticationError(code, reply)
        if send == 'yes':
            message = email.message.EmailMessage()
            message['From'] = user; message['To'] = recipient
            message['Subject'] = 'Grafioschtrader installation: SMTP test'
            message['Message-ID'] = message_id
            message['Date'] = email.utils.formatdate(localtime=True)
            message.set_content('The Grafioschtrader installer successfully submitted this test message using the selected SMTP settings.')
            if client.send_message(message): raise smtplib.SMTPException()
            # DATA acceptance is the milestone; a later QUIT/network error must
            # not cause a duplicate on recovery.
            accepted = True
            print('accepted', flush=True)
        else:
            code, _ = client.noop()
            if code != 250: raise smtplib.SMTPException()
            print('connected', flush=True)
except Exception:
    if accepted: sys.exit(0)
    print('SMTP check failed (connection, TLS, authentication or recipient); credentials and server reply suppressed.', file=sys.stderr)
    sys.exit(2)
