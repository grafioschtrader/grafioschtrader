#!/bin/sh
export JASYPT_ENCRYPTOR_PASSWORD=YOUR_Jasypt_PASSWORD
java -Xms528m -Xmx2048m -Duser.language=en -jar /home/grafioschtrader/grafioschtrader-server-*.jar >> /var/log/grafioschtrader.log
