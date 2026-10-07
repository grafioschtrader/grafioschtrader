"""Replace all application settings in a built test JAR with disposable mail-only fixtures."""
from pathlib import Path
import sys
import zipfile

source, target = map(Path, sys.argv[1:])
properties = """spring.profiles.active=production
spring.mail.host=wrong.example.test
spring.mail.port=2525
spring.mail.username=sender@example.test
spring.mail.password=ENC(ilmsU5syhMI4Fqn3V5xMj3Hb4yCLR+MWfE9hW6Ot3yk=)
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.properties.mail.smtp.ssl.enable=false
g.main.user.admin.mail=admin@example.test
jasypt.encryptor.algorithm=PBEWithMD5AndDES
jasypt.encryptor.iv-generator-classname=org.jasypt.iv.NoIvGenerator
"""
production = """spring.mail.host=localhost
spring.mail.properties.mail.smtp.starttls.required=true
"""
with zipfile.ZipFile(source) as original, zipfile.ZipFile(target, "w") as output:
    for entry in original.infolist():
        if entry.filename.startswith("BOOT-INF/classes/application") and entry.filename.endswith(
                (".properties", ".yaml", ".yml")):
            continue
        output.writestr(entry, original.read(entry))
    output.writestr("BOOT-INF/classes/application.properties", properties)
    output.writestr("BOOT-INF/classes/application-production.properties", production)
