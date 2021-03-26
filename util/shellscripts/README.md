File `/etc/sudoers.d/grafioschtrader`
```
Cmnd_Alias MYSERVICE = \
    /bin/systemctl stop grafioschtrader.service, \
    /bin/systemctl start grafioschtrader.service

grafioschtrader ALL = (root) NOPASSWD: MYSERVICE
```