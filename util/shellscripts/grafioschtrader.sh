#!/bin/sh
export JASYPT_ENCRYPTOR_PASSWORD=YOUR_Jasypt_PASSWORD
java -Xms528m -Xmx2048m -Duser.language=en -Dhttps.protocols=TLSv1,TLSv1.1,TLSv1.2 -jar /home/grafioschtrader/grafioschtrader-server-0.10.0.jar >> /var/log/grafioschtrader.log
